// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.controls.PaginationRange;
import com.dishanhai.sh_better_ui.kernel.api.controls.PaginationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/** One focusable pagination widget. Links dispatch host callbacks instead of opening URLs. */
public final class UiPagination extends UiPaginationPlatform {
    private static final String KEY = "sh_better_ui.pagination.";
    private static final int GAP = 4, HEIGHT = 22;
    public enum Mode { STANDARD, SIMPLE, ICONS_ONLY }
    public enum Direction { AUTO, LTR, RTL }
    public enum Kind { PREVIOUS, PAGE, ELLIPSIS, NEXT }
    /** Composition equivalent of PaginationItem/Link/Previous/Next/Ellipsis. Null text uses defaults. */
    public record Item(Kind kind, long page, Component text) {
        public Item {
            Objects.requireNonNull(kind);
            if (kind == Kind.PAGE && page < 1) throw new IllegalArgumentException("Pages start at one");
        }
        public static Item link(long page) { return new Item(Kind.PAGE, page, null); }
        public static Item previous(Component text) { return new Item(Kind.PREVIOUS, 0, text); }
        public static Item next(Component text) { return new Item(Kind.NEXT, 0, text); }
        public static Item ellipsis() { return new Item(Kind.ELLIPSIS, 0, null); }
    }
    record Cell(Item item, Bounds area, UiButton button, Component label) {}
    private final Font font;
    private final PaginationState state;
    private final LongConsumer onChange;
    private UiPreferences preferences;
    private Mode mode = Mode.STANDARD;
    private Direction direction = Direction.AUTO;
    private LongFunction<Component> number = page -> Component.literal(Long.toString(page));
    private Component previous = Component.translatable(KEY + "previous"), next = Component.translatable(KEY + "next");
    private BooleanSupplier inputEnabled = () -> true;
    private List<Item> content;
    private List<Cell> cells = List.of();
    private List<Item> lastItems = List.of();
    private Bounds lastBounds;
    private int focused, pressed = -1;
    private boolean lastRtl, lastLabels, dirty = true;
    private long lastPage = -1, lastCount = -1;
    private long lastTotal = -1;
    private int lastPageSize = -1;

    public UiPagination(Font font, Bounds area, Component name, PaginationState state,
                        UiPreferences preferences, LongConsumer onChange) {
        super(area, name);
        this.font = Objects.requireNonNull(font);
        this.state = Objects.requireNonNull(state);
        this.preferences = Objects.requireNonNull(preferences);
        this.onChange = Objects.requireNonNull(onChange);
    }
    /** External state synchronization does not fire the callback. */
    public PaginationState state() { return state; }
    public void setMode(Mode mode) { this.mode = Objects.requireNonNull(mode); invalidate(); }
    public void setDirection(Direction direction) { this.direction = Objects.requireNonNull(direction); invalidate(); }
    public void setLabels(Component previous, Component next) {
        this.previous = Objects.requireNonNull(previous); this.next = Objects.requireNonNull(next); invalidate();
    }
    public void setNumberFormatter(LongFunction<Component> number) { this.number = Objects.requireNonNull(number); invalidate(); }
    public void setPreferences(UiPreferences preferences) { this.preferences = Objects.requireNonNull(preferences); invalidate(); }
    public void setInputEnabled(BooleanSupplier enabled) { inputEnabled = Objects.requireNonNull(enabled); }
    /** Null restores generated links. The caller can compose arbitrary page links and separators. */
    public void setContent(List<Item> items) {
        if (items != null && items.size() > 128) throw new IllegalArgumentException("Too many pagination entries");
        content = items == null ? null : List.copyOf(items); invalidate();
    }
    public void cancelInteraction() { pressed = -1; for (var cell : cells) if (cell.button() != null) cell.button().setHeld(false); }
    private void invalidate() { cancelInteraction(); dirty = true; }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (!focused) cancelInteraction(); }
    private boolean interactive() { return active && visible && inputEnabled.getAsBoolean() && Minecraft.getInstance().isWindowActive(); }
    private boolean rtl() { return direction == Direction.RTL || direction == Direction.AUTO && Language.getInstance().isDefaultRightToLeft(); }
    private List<Item> generated(int siblings, int boundaries) {
        var items = new ArrayList<Item>();
        if (mode != Mode.SIMPLE) items.add(Item.previous(null));
        if (mode != Mode.ICONS_ONLY) for (var item : PaginationRange.items(state.pageCount(), state.page(), siblings, boundaries))
            items.add(item.ellipsis() ? Item.ellipsis() : Item.link(item.page()));
        if (mode != Mode.SIMPLE) items.add(Item.next(null));
        return items;
    }
    private Component label(Item item) {
        if (item.text() != null) return item.text();
        return switch (item.kind()) {
            case PREVIOUS -> previous; case NEXT -> next;
            case ELLIPSIS -> CommonComponents.ELLIPSIS;
            case PAGE -> Objects.requireNonNull(number.apply(item.page()));
        };
    }
    private int width(Item item, boolean labels) {
        return switch (item.kind()) {
            case PREVIOUS, NEXT -> labels ? Math.max(56, font.width(label(item)) + 32) : HEIGHT;
            default -> Math.max(HEIGHT, font.width(label(item)) + 12);
        };
    }
    private int totalWidth(List<Item> items, boolean labels) {
        return items.stream().mapToInt(item -> width(item, labels)).sum() + Math.max(0, items.size() - 1) * GAP;
    }
    private boolean enabled(Item item) {
        return switch (item.kind()) {
            case PREVIOUS -> state.hasPrevious(); case NEXT -> state.hasNext();
            case PAGE -> item.page() <= state.pageCount(); case ELLIPSIS -> false;
        };
    }
    private void layout() {
        boolean rtl = rtl(), labels = mode == Mode.STANDARD;
        var items = content == null ? generated(1, 1) : content;
        if (totalWidth(items, labels) > getWidth()) labels = false;
        if (content == null && totalWidth(items, labels) > getWidth()) items = generated(1, 0);
        if (content == null && totalWidth(items, labels) > getWidth()) items = generated(0, 0);
        if (content == null && totalWidth(items, labels) > getWidth() && mode != Mode.SIMPLE)
            items = List.of(Item.previous(null), Item.next(null));
        if (totalWidth(items, labels) > getWidth() || getHeight() < HEIGHT) items = List.of();
        if (rtl) { items = new ArrayList<>(items); Collections.reverse(items); }
        var bounds = new Bounds(getX(), getY(), getWidth(), getHeight());
        if (!dirty && items.equals(lastItems) && bounds.equals(lastBounds) && rtl == lastRtl && labels == lastLabels
                && lastPage == state.page() && lastCount == state.pageCount()
                && lastTotal == state.totalItems() && lastPageSize == state.pageSize()) return;
        Item focusItem = focused >= 0 && focused < cells.size() ? cells.get(focused).item() : null;
        cancelInteraction();
        dirty = false; lastItems = List.copyOf(items); lastBounds = bounds; lastRtl = rtl; lastLabels = labels;
        lastPage = state.page(); lastCount = state.pageCount();
        lastTotal = state.totalItems(); lastPageSize = state.pageSize();
        var result = new ArrayList<Cell>();
        int x = getX() + (getWidth() - totalWidth(items, labels)) / 2, y = getY() + (getHeight() - HEIGHT) / 2;
        for (var item : items) {
            int w = width(item, labels);
            var area = new Bounds(x, y, w, HEIGHT);
            UiButton button = null;
            if (item.kind() != Kind.ELLIPSIS) {
                button = new UiButton(x, y, w, HEIGHT, label(item), ignored -> {}, preferences, false);
                if (item.kind() == Kind.PAGE) button.setPreview(Component.translatable(KEY + "go_to", label(item)));
                if (item.kind() == Kind.PREVIOUS || item.kind() == Kind.NEXT) {
                    var icon = (item.kind() == Kind.PREVIOUS) != rtl ? GraphicIcon.PREVIOUS : GraphicIcon.NEXT;
                    if (!labels) button.setIcon(icon);
                    else if ((item.kind() == Kind.PREVIOUS) != rtl) button.setLeadingIcon(icon);
                    else button.setTrailingIcon(icon);
                }
            }
            result.add(new Cell(item, area, button, label(item))); x += w + GAP;
        }
        cells = List.copyOf(result);
        focused = -1;
        for (int i = 0; i < cells.size(); i++) if (enabled(cells.get(i).item()) &&
                (cells.get(i).item().equals(focusItem) || focused < 0)) focused = i;
    }
    @Override List<Cell> cells() { return cells; }
    @Override void prepare(int mx, int my) {
        layout();
        if (!interactive()) cancelInteraction();
        int hit = hit(mx, my);
        if (pressed >= 0 && hit != pressed) cancelInteraction();
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i); var button = cell.button();
            if (button == null) continue;
            button.active = interactive() && enabled(cell.item());
            button.setSelected(cell.item().kind() == Kind.PAGE && cell.item().page() == state.page());
            button.setFocused(isFocused() && focused == i);
            button.setHeld(pressed == i);
        }
    }
    private int hit(double x, double y) {
        if (!interactive()) return -1;
        for (int i = 0; i < cells.size(); i++) if (enabled(cells.get(i).item()) && cells.get(i).area().contains(x, y)) return i;
        return -1;
    }
    @Override public boolean isMouseOver(double x, double y) { layout(); return hit(x, y) >= 0; }
    @Override boolean pointerDown(double x, double y) {
        layout(); int hit = hit(x, y);
        if (hit < 0) return false;
        pressed = focused = hit; return true;
    }
    @Override boolean pointerDrag(double x, double y) {
        if (pressed < 0) return false;
        if (hit(x, y) != pressed) cancelInteraction(); return true;
    }
    @Override boolean pointerUp(double x, double y) {
        layout();
        int index = pressed;
        if (index < 0) return false;
        cancelInteraction();
        if (hit(x, y) == index) activate(index);
        return true;
    }
    private void activate(int index) {
        if (index < 0 || index >= cells.size() || !enabled(cells.get(index).item())) return;
        var item = cells.get(index).item();
        boolean changed = switch (item.kind()) {
            case PREVIOUS -> state.previous(); case NEXT -> state.next();
            case PAGE -> state.select(item.page()); case ELLIPSIS -> false;
        };
        if (changed) { cancelInteraction(); onChange.accept(state.page()); }
    }
    @Override boolean key(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE && pressed >= 0) { cancelInteraction(); return true; }
        if (!interactive() || !isFocused()) return false;
        layout();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            activate(focused); return true;
        }
        int direction = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
        boolean edge = key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END;
        if (direction == 0 && !edge) return false;
        cancelInteraction();
        if (edge) { direction = key == GLFW.GLFW_KEY_HOME ? 1 : -1; focused = direction > 0 ? -1 : cells.size(); }
        for (int i = focused + direction; i >= 0 && i < cells.size(); i += direction)
            if (enabled(cells.get(i).item())) { focused = i; break; }
        return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        layout();
        output.add(NarratedElementType.TITLE, Component.translatable(KEY + "narration", getMessage(),
                number.apply(state.page()), number.apply(state.pageCount())));
        if (focused >= 0 && focused < cells.size()) output.add(NarratedElementType.HINT,
                label(cells.get(focused).item()));
        output.add(NarratedElementType.USAGE, Component.translatable(KEY + "usage"));
    }
}
