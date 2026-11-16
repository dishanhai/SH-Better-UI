// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.IntConsumer;

/**
 * One tab stop for a small page navigation group; arrow keys explore, Enter opens.
 * Hosts must reserve at least 24 x 20 GUI units per entry plus the 3-unit trailing gap
 * along the navigation axis. Undersized groups render no controls and accept no input.
 */
public final class UiNavigationRail extends UiNavigationRailPlatform implements AutoCloseable {
    public enum Orientation { VERTICAL, HORIZONTAL }
    public record Item(Component label, GraphicIcon icon, boolean enabled) {
        public Item { Objects.requireNonNull(label); }
        public Item(Component label, GraphicIcon icon) { this(label, icon, true); }
        public Item(Component label) { this(label, null, true); }
    }
    record Cell(Bounds area, UiButton button) {}
    private final List<Item> items;
    private final NavigationRailState state;
    private final Orientation orientation;
    private final IntConsumer onSelect;
    private final InterruptibleAnimation indicator;
    private UiPreferences preferences;
    private List<Cell> cells = List.of();
    private Bounds lastBounds;
    private boolean closed;
    private int hovered = -1;
    public UiNavigationRail(Font font, Bounds area, Component name, List<Item> items, int selected,
                            Orientation orientation, UiPreferences preferences, IntConsumer onSelect) {
        super(area, name); Objects.requireNonNull(font);
        this.items = List.copyOf(items);
        if (items.size() > 32) throw new IllegalArgumentException("Navigation is limited to 32 entries");
        this.state = new NavigationRailState(items.size(), selected, i -> this.items.get(i).enabled());
        this.orientation = Objects.requireNonNull(orientation);
        this.preferences = Objects.requireNonNull(preferences); this.onSelect = Objects.requireNonNull(onSelect);
        indicator = new InterruptibleAnimation(new Spring.Preset(.22, 1, .001f, .01f), selected);
    }
    public NavigationRailState state() { return state; }
    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        for (var cell : cells) cell.button().setPreferences(preferences);
    }
    public void cancelInteraction() { state.cancel(); for (var cell : cells) cell.button().setHeld(false); }
    @Override public void setFocused(boolean focused) {
        if (focused && !isFocused()) state.focusEntered();
        super.setFocused(focused); if (!focused) cancelInteraction();
    }
    private boolean interactive() { return !closed && active && visible && Minecraft.getInstance().isWindowActive(); }
    private void layout() {
        var area = new Bounds(getX(), getY(), Math.max(0, getWidth()), Math.max(0, getHeight()));
        if (area.equals(lastBounds)) return;
        lastBounds = area; cancelInteraction();
        int minWidth = orientation == Orientation.VERTICAL ? 24 : items.size() * 27;
        int minHeight = orientation == Orientation.VERTICAL ? items.size() * 23 : 20;
        if (area.width() < minWidth || area.height() < minHeight) {
            cells = List.of(); indicator.snapTo(state.selectedIndex()); return;
        }
        var result = new ArrayList<Cell>(); int count = items.size();
        int length = orientation == Orientation.VERTICAL ? getHeight() : getWidth();
        for (int i = 0; i < count; i++) {
            int start = (int)((long)i * length / count), end = (int)((long)(i + 1) * length / count);
            var box = orientation == Orientation.VERTICAL
                    ? new Bounds(getX(), getY() + start, getWidth(), Math.max(0, end - start - 3))
                    : new Bounds(getX() + start, getY(), Math.max(0, end - start - 3), getHeight());
            var item = items.get(i);
            var button = new UiButton(box.x(), box.y(), box.width(), box.height(), item.label(), b -> {}, preferences, false);
            if (item.icon() != null) button.setLeadingIcon(item.icon());
            button.setPreview(item.label());
            result.add(new Cell(box, button));
        }
        cells = List.copyOf(result); indicator.snapTo(state.selectedIndex());
    }
    private int hit(double x, double y) {
        if (!interactive()) return -1;
        for (int i = 0; i < cells.size(); i++) if (state.isEnabled(i) && cells.get(i).area().contains(x, y)) return i;
        return -1;
    }
    @Override public boolean isMouseOver(double x, double y) { layout(); return hit(x, y) >= 0; }
    @Override boolean pointerDown(double x, double y) { layout(); return state.press(hit(x, y)); }
    @Override boolean pointerDrag(double x, double y) {
        boolean held = state.pressedIndex() >= 0; state.drag(hit(x, y)); return held;
    }
    @Override boolean pointerUp(double x, double y) {
        layout(); boolean held = state.pressedIndex() >= 0;
        if (state.release(hit(x, y))) onSelect.accept(state.selectedIndex());
        return held;
    }
    @Override boolean key(int key) {
        layout();
        if (!interactive() || !isFocused() || cells.isEmpty()) { cancelInteraction(); return false; }
        if (key == GLFW.GLFW_KEY_ESCAPE && state.pressedIndex() >= 0) { cancelInteraction(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            if (state.activate()) onSelect.accept(state.selectedIndex()); return true;
        }
        if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) { state.edge(key == GLFW.GLFW_KEY_END); return true; }
        int previous = orientation == Orientation.VERTICAL ? GLFW.GLFW_KEY_UP : GLFW.GLFW_KEY_LEFT;
        int next = orientation == Orientation.VERTICAL ? GLFW.GLFW_KEY_DOWN : GLFW.GLFW_KEY_RIGHT;
        if (key != previous && key != next) return false;
        state.move(key == previous ? -1 : 1); return true;
    }
    @Override void prepare(int mx, int my) {
        layout(); hovered = hit(mx, my);
        if (!interactive()) cancelInteraction(); else state.drag(hovered);
        long now = navigationNowMs();
        if (indicator.target() != state.selectedIndex()) indicator.animateTo(state.selectedIndex(), now);
        indicator.sample(now, preferences.reducedMotion());
        for (int i = 0; i < cells.size(); i++) {
            var button = cells.get(i).button();
            button.active = interactive() && state.isEnabled(i);
            button.setSelected(i == state.selectedIndex());
            button.setFocused(isFocused() && i == state.focusedIndex());
            button.setHeld(i == state.pressedIndex());
        }
    }
    @Override List<Cell> cells() { return cells; }
    @Override Bounds indicatorBounds() {
        if (closed || cells.isEmpty()) return new Bounds(0, 0, 0, 0);
        float value = Math.max(0, Math.min(items.size() - 1, indicator.value()));
        int low = (int)value, high = Math.min(items.size() - 1, low + 1);
        var a = cells.get(low).area(); var b = cells.get(high).area(); float t = value - low;
        int x = Math.round(a.x() + (b.x() - a.x()) * t), y = Math.round(a.y() + (b.y() - a.y()) * t);
        return orientation == Orientation.VERTICAL ? new Bounds(x, y + 3, 2, Math.max(0, a.height() - 6))
                : new Bounds(x + 3, y + a.height() - 2, Math.max(0, a.width() - 6), 2);
    }
    @Override int indicatorColor() { return preferences.highContrast() ? UiTheme.current().text() : UiTheme.current().accent(); }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("sh_better_ui.navigation.narration", getMessage(),
                items.get(state.narrationIndex(isFocused(), hovered)).label(),
                state.narrationIndex(isFocused(), hovered) + 1, items.size()));
        output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.navigation.selected", items.get(state.selectedIndex()).label()));
        output.add(NarratedElementType.USAGE, Component.translatable("sh_better_ui.navigation.usage"));
    }
    @Override public void close() { cancelInteraction(); indicator.close(); closed = true; active = false; }
}
