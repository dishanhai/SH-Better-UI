// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.controls.AnimatedListState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** UUK native port of AnimatedList. Mount as one widget; call cancelInteraction() when unmounting. */
public final class UiAnimatedList<T> extends UiAnimatedListPlatform {
    public enum Theme { CURRENT, DARK, LIGHT }

    /** A GraphicIcon may wrap the host's texture; image=true preserves its original colors. */
    public record Item<T>(String id, T value, Component title, Component description, Component meta,
                          GraphicIcon lead, boolean image, int height) {
        public Item {
            Objects.requireNonNull(id);
            Objects.requireNonNull(title);
            Objects.requireNonNull(description);
            Objects.requireNonNull(meta);
            if (height < 24) throw new IllegalArgumentException("Row height must be >= 24");
        }
        public Item(String id, T value, Component title, Component description, Component meta, GraphicIcon icon) {
            this(id, value, title, description, meta, icon, false, 56);
        }
    }

    public record Style(float radius, int gap, boolean frame, boolean scrollbar, Theme theme, Integer accent) {
        public static final Style DEFAULT = new Style(14, 8, true, true, Theme.CURRENT, null);
        public Style {
            Objects.requireNonNull(theme);
            if (!Float.isFinite(radius) || radius < 0 || gap < 0) throw new IllegalArgumentException("Invalid list style");
        }
    }

    /** Platform-neutral drawing surface. Alpha/row transforms and viewport clipping are supplied by UUK. */
    public interface Canvas {
        void box(float x, float y, float width, float height, float radius, int edge, int fill);
        void fill(int x, int y, int right, int bottom, int color);
        void text(Font font, FormattedCharSequence text, int x, int y, int color);
        void icon(GraphicIcon icon, int x, int y, int color, UiPreferences preferences);
        void push(float x, float y, float scale);
        void pop();
        void clip(int x, int y, int right, int bottom);
        void unclip();
        void opacity(float alpha);
        void plate(Bounds area, float radius, int edge, int fill, UiPreferences preferences);
    }

    @FunctionalInterface public interface ItemRenderer<T> {
        /** area is row-local; called for each mask band, so keep this callback free of side effects. */
        void draw(Canvas canvas, Font font, Bounds area, Item<T> item, int index, boolean selected, int ink, int muted);
    }

    private static final String KEY = "sh_better_ui.animated_list.";
    private final Font font;
    private final AnimatedListState state;
    private final Style style;
    private final BiConsumer<T, Integer> onSelect;
    private List<Item<T>> items;
    private UiPreferences preferences;
    private ItemRenderer<T> renderer;
    private BooleanSupplier inputEnabled = () -> true;
    private int layoutHeight = -1, pressed = -1;
    private int maxHeight;
    private net.minecraft.client.gui.screens.Screen pressOwner;

    public UiAnimatedList(Font font, Bounds area, Component name, List<Item<T>> items, int initialSelectedIndex,
                          AnimatedListState.Options options, Style style, UiPreferences preferences,
                          BiConsumer<T, Integer> onSelect) {
        super(area, name);
        this.font = Objects.requireNonNull(font);
        this.style = Objects.requireNonNull(style);
        this.preferences = Objects.requireNonNull(preferences);
        this.onSelect = Objects.requireNonNull(onSelect);
        maxHeight = area.height();
        state = new AnimatedListState(options);
        setItems(items);
        state.select(initialSelectedIndex);
    }

    public int selectedIndex() { return state.selected(); }
    /** Programmatic selection does not invoke the activation callback. */
    public void setSelectedIndex(int index) { state.select(index); }
    public void setRenderer(ItemRenderer<T> renderer) { this.renderer = renderer; }
    public void setPreferences(UiPreferences value) { preferences = Objects.requireNonNull(value); }
    public void setOptions(AnimatedListState.Options options) { state.options(options); }
    public void setInputEnabled(BooleanSupplier value) { inputEnabled = Objects.requireNonNull(value); }

    /** Reconcile by unique ID, preserving surviving selections and animateOnce history. */
    public void setItems(List<Item<T>> value) {
        var copy = List.copyOf(value);
        if (layoutHeight >= 0 && layoutHeight != getHeight()) maxHeight = getHeight();
        long content = copy.stream().mapToLong(Item::height).sum() + (long) style.gap() * Math.max(0, copy.size() - 1);
        int height = (int) Math.min(maxHeight, content + inset() * 2);
        cancelInteraction();
        state.layout(copy.stream().map(Item::id).toList(), copy.stream().mapToDouble(Item::height).toArray(),
                style.gap(), Math.max(0, height - inset() * 2));
        setHeight(height);
        items = copy;
        layoutHeight = getHeight();
    }

    private int inset() { return style.frame() ? 12 : 6; }
    private int rowWidth() { return Math.max(0, getWidth() - inset() * 2 - (style.scrollbar() ? 8 : 0)); }
    private Bounds viewport() { return new Bounds(getX() + inset(), getY() + inset(), rowWidth(), Math.max(0, getHeight() - inset() * 2)); }
    private boolean interactive() { return visible && active && Minecraft.getInstance().isWindowActive() && inputEnabled.getAsBoolean(); }
    private static long now() { return System.nanoTime() / 1_000_000L; }

    public void cancelInteraction() {
        pressed = -1;
        pressOwner = null;
        if (state != null && state.scroll().cancelDrag()) state.synchronizeScroll();
    }

    @Override public void setFocused(boolean value) { super.setFocused(value); if (!value) cancelInteraction(); }

    private int rowAt(double x, double y) {
        var v = viewport();
        if (x < v.x() || x >= v.x() + v.width() || y < v.y() || y >= v.y() + v.height()) return -1;
        return state.hit(x - v.x(), y - v.y(), v.width(), now());
    }

    @Override boolean pointerDown(double x, double y) {
        if (!interactive() || !isMouseOver(x, y)) return false;
        cancelInteraction();
        var v = viewport();
        if (style.scrollbar() && x >= v.x() + v.width() && x < getX() + getWidth() - 6
                && y >= v.y() && y < v.y() + v.height()) {
            state.synchronizeScroll();
            if (!state.scroll().beginDrag(y - v.y(), v.height())) return false;
            state.synchronizeScroll();
        } else {
            pressed = rowAt(x, y);
            if (pressed < 0) return false;
            state.select(pressed);
        }
        pressOwner = currentScreen();
        return true;
    }

    @Override boolean pointerDrag(double x, double y) {
        if (!interactive()) { cancelInteraction(); return false; }
        if (!state.scroll().dragging()) {
            if (pressed >= 0 && rowAt(x, y) != pressed) pressed = -1;
            return pressOwner != null;
        }
        state.scroll().dragTo(y - viewport().y(), viewport().height());
        state.synchronizeScroll();
        return true;
    }

    @Override boolean pointerUp(double x, double y) {
        boolean handled = pressOwner != null;
        if (state.scroll().endDrag()) {
            state.synchronizeScroll();
            pressOwner = null;
            return true;
        }
        int index = pressed;
        cancelInteraction();
        if (interactive() && index >= 0 && rowAt(x, y) == index) activate(index);
        return handled;
    }

    @Override boolean wheel(double x, double y, double delta) {
        if (!interactive() || !isMouseOver(x, y) || state.scroll().dragging() || !Double.isFinite(delta) || delta == 0) return false;
        pressed = -1;
        state.scrollBy(-delta * 36, now(), preferences.reducedMotion());
        return state.scroll().maxOffset() > 0;
    }

    @Override boolean key(int key, int modifiers) {
        if (!isFocused() || !interactive()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE && (pressed >= 0 || state.scroll().dragging())) { cancelInteraction(); return true; }
        if ((modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) return false;
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) && state.selected() >= 0) {
            activate(state.selected());
            return true;
        }
        AnimatedListState.Navigation command = switch (key) {
            case GLFW.GLFW_KEY_UP -> AnimatedListState.Navigation.UP;
            case GLFW.GLFW_KEY_DOWN -> AnimatedListState.Navigation.DOWN;
            case GLFW.GLFW_KEY_HOME -> AnimatedListState.Navigation.HOME;
            case GLFW.GLFW_KEY_END -> AnimatedListState.Navigation.END;
            default -> null;
        };
        if (command == null || !state.navigate(command, now(), preferences.reducedMotion())) return false;
        narrate(narration());
        return true;
    }

    private void activate(int index) {
        state.select(index);
        playDownSound(Minecraft.getInstance().getSoundManager());
        onSelect.accept(items.get(index).value(), index);
    }

    private Component narration() {
        int i = state.selected();
        if (i < 0) return CommonComponents.joinForNarration(getMessage(), Component.translatable(KEY + "count", items.size()));
        var item = items.get(i);
        return CommonComponents.joinForNarration(getMessage(), item.title(), item.description(), item.meta(),
                Component.translatable(KEY + "position", i + 1, items.size()));
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, narration());
        output.add(NarratedElementType.USAGE, Component.translatable(KEY + (state.options().arrowNavigation() ? "keys" : "activate")));
    }

    @Override void paint(Canvas canvas, int mouseX, int mouseY) {
        if (layoutHeight != getHeight()) setItems(items);
        if (!interactive() || (pressOwner != null && pressOwner != currentScreen())) cancelInteraction();
        // Keyboard selection survives both scrolling and a stationary pointer over the list.
        if (interactive() && pressed < 0 && !state.scroll().dragging()) state.pointer(mouseX, mouseY, rowAt(mouseX, mouseY));
        long time = now();
        state.advance(time, preferences.reducedMotion());
        AnimatedListPaint.draw(canvas, font, new Bounds(getX(), getY(), getWidth(), getHeight()), viewport(),
                items, state, style, preferences, isFocused(), active, pressed, rowAt(mouseX, mouseY), renderer, time);
    }
}
