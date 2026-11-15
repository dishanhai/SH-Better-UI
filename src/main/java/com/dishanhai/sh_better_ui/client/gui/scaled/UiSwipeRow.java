// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.controls.SwipeRowState;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** A native UUK row with a mirrored action drawer. Mount as a widget and call tick() from the host screen. */
public final class UiSwipeRow extends UiSwipeRowPlatform {
    private static final String KEY = "sh_better_ui.swipe_row.";
    private static final int GRAB_SLOP = 6;

    /** The outermost action is primary and always collapses the row. */
    public record Action(String id, Component label, GraphicIcon icon, Integer color, boolean dismiss, Runnable onSelect) {
        public Action {
            Objects.requireNonNull(id);
            Objects.requireNonNull(label);
        }

        public Action(String id, Component label, GraphicIcon icon, boolean dismiss) {
            this(id, label, icon, null, dismiss, null);
        }
    }

    /** Null colors follow the theme. The bounds supply the resting height. */
    public record Style(float radius, Integer actionColor, Integer drawerColor, Integer rowColor, Integer textColor) {
        public static final Style DEFAULT = new Style(16, null, null, null, null);

        public Style {
            if (!(radius >= 0) || !Float.isFinite(radius)) throw new IllegalArgumentException("radius");
        }
    }

    public record Callbacks(Consumer<Boolean> onOpenChange, Consumer<Action> onAction, Consumer<Action> onCommit) {
        public static final Callbacks NONE = new Callbacks(null, null, null);
    }

    private final List<Action> actions;
    private final SwipeRowState.Options options;
    private final SwipeRowState state;
    private final SwipeGesture pointer = new SwipeGesture(GRAB_SLOP);
    private final Style style;
    private final Callbacks callbacks;
    private UiPreferences preferences;
    private int pressedAction = -1;
    private int focusedAction = -1;
    private int lastWidth;
    private int activationKey = -1;
    private double grabOffset;
    private boolean verticalGesture;
    private boolean disabled;

    public UiSwipeRow(Bounds area, Component content, List<Action> actions, SwipeRowState.Options options,
                      Style style, UiPreferences preferences, Callbacks callbacks) {
        super(area, content);
        this.actions = List.copyOf(actions);
        this.options = Objects.requireNonNull(options);
        this.style = Objects.requireNonNull(style);
        this.preferences = Objects.requireNonNull(preferences);
        this.callbacks = Objects.requireNonNull(callbacks);
        state = new SwipeRowState(options, this.actions.size());
        lastWidth = area.width();
        state.resize(lastWidth);
    }

    public boolean isOpen() { return state.open(); }
    public boolean isCollapsed() { return state.collapsed(); }

    /** Current occupied height for hosts that reflow their rows while an action collapses. */
    public int layoutHeight() {
        return (int) Math.ceil(getHeight() * state.heightFraction(now(), preferences.reducedMotion()));
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    /** Applies an external open state without calling onOpenChange. */
    public void setOpen(boolean open) {
        cancelPress();
        state.setOpen(open, now());
        normalizeFocus();
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        active = !disabled && !isCollapsed();
        if (disabled) cancelPress();
    }

    /** Restores a collapsed row. The owner may also remove the row when onCommit runs. */
    public void reset() {
        cancelPress();
        state.reset();
        focusedAction = -1;
        active = !disabled;
        visible = true;
    }

    public boolean cancelPress() {
        if (!pointer.active()) return false;
        pointer.cancel();
        pressedAction = -1;
        state.cancel(now());
        return true;
    }

    private boolean ready() {
        return active && visible && getWidth() > 0 && getHeight() > 0 && state.pendingAction() < 0
                && Minecraft.getInstance().isWindowActive();
    }

    public void tick() {
        if (stateWidthChanged() || !ready()) cancelPress();
        if (!isFocused() || !Minecraft.getInstance().isWindowActive()) activationKey = -1;
        int committed = state.advance(now(), preferences.reducedMotion());
        if (committed < 0) return;
        active = false;
        visible = false;
        var action = actions.get(committed);
        if (callbacks.onCommit() != null) callbacks.onCommit().accept(action);
        if (action.onSelect() != null) action.onSelect().run();
    }

    private boolean stateWidthChanged() {
        int width = getWidth();
        if (width == lastWidth) return false;
        state.resize(width);
        lastWidth = width;
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            cancelPress();
            activationKey = -1;
        }
    }

    @Override
    boolean pointerDown(double x, double y) {
        if (!ready() || !contains(x, y)) return false;
        if (stateWidthChanged()) cancelPress();
        long time = now();
        state.advance(time, preferences.reducedMotion());
        if (!pointer.begin(0, x, y, time)) return false;
        pressedAction = actionAt(x);
        verticalGesture = false;
        return true;
    }

    @Override
    boolean pointerDragged(double x, double y) {
        long time = now();
        if (!pointer.active()) return false;
        if (ready() && pointer.move(0, x, y, time)) updateDrag(pointer.sample(time));
        else cancelPress();
        return true;
    }

    @Override
    boolean pointerUp(double x, double y) {
        if (!pointer.active()) return false;
        if (!ready()) return cancelPress();
        long time = now();
        var sample = pointer.release(0, x, y, time);
        int action = pressedAction;
        pressedAction = -1;
        if (sample == null) {
            state.cancel(time);
            return true;
        }
        updateDrag(sample);
        boolean wasOpen = state.open();
        if (state.dragging()) state.release(sample.velocityX(), time);
        else if (!sample.moved() && contains(x, y) && action == actionAt(x)) {
            if (action >= 0) activate(action);
            else changeOpen(!wasOpen);
            return true;
        }
        reportOpen(wasOpen);
        return true;
    }

    private void updateDrag(SwipeGesture.Sample sample) {
        if (!sample.moved() || verticalGesture) return;
        double dx = sample.offsetX();
        if (!state.dragging()) {
            if (Math.abs(sample.offsetY()) > Math.abs(dx)) {
                verticalGesture = true;
                return;
            }
            if (Math.abs(dx) < GRAB_SLOP) return;
            pressedAction = -1;
            grabOffset = Math.copySign(GRAB_SLOP, dx);
            state.begin();
        }
        state.drag(dx - grabOffset);
    }

    @Override
    boolean key(int key) {
        if (!isFocused() || !ready()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (cancelPress()) return true;
            if (!isOpen()) return false;
            changeOpen(false);
            return true;
        }
        if (pointer.active()) return false;
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            boolean opening = (key == GLFW.GLFW_KEY_LEFT) == (options.direction() == SwipeRowState.Direction.LEFT);
            changeOpen(opening);
            return true;
        }
        if (isOpen() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            int step = key == GLFW.GLFW_KEY_DOWN ? 1 : -1;
            focusedAction = focusedAction < 0
                    ? (step > 0 ? 0 : actions.size() - 1)
                    : Math.floorMod(focusedAction + step, actions.size());
            return true;
        }
        if (key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER && key != GLFW.GLFW_KEY_SPACE) return false;
        if (activationKey >= 0) return true;
        activationKey = key;
        if (isOpen() && focusedAction >= 0) activate(focusedAction);
        else changeOpen(!isOpen());
        return true;
    }

    @Override
    boolean releaseKey(int key) {
        if (activationKey != key) return false;
        activationKey = -1;
        return true;
    }

    private void activate(int index) {
        var action = actions.get(index);
        boolean wasOpen = state.open();
        state.select(index, action.dismiss(), now());
        reportOpen(wasOpen);
        if (callbacks.onAction() != null) callbacks.onAction().accept(action);
        if (index > 0 && !action.dismiss() && action.onSelect() != null) action.onSelect().run();
    }

    private void changeOpen(boolean open) {
        boolean wasOpen = state.open();
        if (open != wasOpen) state.setOpen(open, now());
        reportOpen(wasOpen);
    }

    private void reportOpen(boolean wasOpen) {
        normalizeFocus();
        if (wasOpen != state.open() && callbacks.onOpenChange() != null) callbacks.onOpenChange().accept(state.open());
    }

    private void normalizeFocus() {
        if (!isOpen()) focusedAction = -1;
    }

    private boolean contains(double x, double y) {
        return x >= getX() && x < getX() + getWidth() && y >= getY() && y < getY() + getHeight();
    }

    private double outerDistance(double x) {
        return options.direction() == SwipeRowState.Direction.LEFT ? getX() + getWidth() - x : x - getX();
    }

    private int actionAt(double x) {
        double distance = outerDistance(x);
        if (!isOpen() || distance < 0 || distance >= Math.min(state.offset(), state.drawerWidth())) return -1;
        return Math.min(actions.size() - 1, (int) (distance / state.actionWidth()));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (state.armed()) output.add(NarratedElementType.USAGE, Component.translatable(KEY + "release", actions.get(0).label()));
        else if (isOpen()) {
            output.add(NarratedElementType.POSITION, focusedAction < 0
                    ? Component.translatable(KEY + "revealed", actions.size()) : actions.get(focusedAction).label());
            output.add(NarratedElementType.USAGE, Component.translatable(KEY + "actions_hint"));
        } else output.add(NarratedElementType.USAGE, Component.translatable(KEY + "open_hint"));
    }

    @Override
    void paint(Canvas canvas, int mouseX, int mouseY) {
        if (stateWidthChanged() || !ready()) cancelPress();
        int x = getX(), y = getY(), width = getWidth();
        long time = now();
        if (state.pendingAction() < 0) state.advance(time, preferences.reducedMotion());
        int height = layoutHeight();
        if (width <= 0 || height <= 0) return;
        int exposed = (int) Math.round(Math.max(0, Math.min(width, state.visualOffset(time, preferences.reducedMotion()))));
        boolean left = options.direction() == SwipeRowState.Direction.LEFT;
        float radius = Math.min(style.radius(), Math.min(width, height) / 2f);
        var theme = UiTheme.current();
        int row = color(style.rowColor(), theme.surface());
        int drawer = color(style.drawerColor(), theme.background());
        int text = color(style.textColor(), theme.text());
        int accent = color(style.actionColor(), theme.accent());
        boolean primaryFill = state.armed() || state.pendingAction() == 0;
        int surfaceX = x + options.direction().sign() * exposed;
        int edge = isFocused() || pointer.active() || preferences.highContrast() ? readable(text, row) : theme.border();

        canvas.clip(x, y, x + width, y + height);
        try {
            if (exposed > 0) {
                canvas.box(x, y, width, height, radius, 0, drawer, drawer);
                int hover = contains(mouseX, mouseY) ? actionAt(mouseX) : -1;
                for (int i = 0; i < (primaryFill ? 1 : actions.size()); i++) {
                    double span = primaryFill ? exposed : state.actionWidth() * (i + 1);
                    int end = (int) Math.round(Math.min(exposed, span));
                    int start = primaryFill ? 0 : (int) Math.round(state.actionWidth() * i);
                    if (end <= start) break;
                    int cellX = left ? x + width - end : x + start;
                    int fill = i == 0 ? accent : color(actions.get(i).color(), drawer);
                    int ink = readable(text, fill);
                    boolean selected = isFocused() && focusedAction == i || i == hover || pressedAction == i;
                    canvas.clip(cellX, y, cellX + end - start, y + height);
                    try {
                        canvas.box(x, y, width, height, radius, 0, fill, fill);
                        if (selected && end - start > 4 && height > 4) canvas.box(cellX + 2, y + 2, end - start - 4, height - 4,
                                Math.max(0, radius - 2), 1, ink, fill);
                        var action = actions.get(i);
                        var label = state.armed() ? Component.translatable(KEY + "release", action.label()) : action.label();
                        int labelWidth = primaryFill ? end - start : (int) state.actionWidth();
                        drawLabel(canvas, left ? x + width - start - labelWidth : cellX, y, labelWidth, height,
                                label, action.icon(), ink);
                    } finally {
                        canvas.unclip();
                    }
                }
            }
            if (exposed < width) {
                canvas.box(surfaceX, y, width, height, radius, isFocused() || pointer.active() ? 2 : 1, edge, row);
                drawLabel(canvas, surfaceX, y, width, height, getMessage(), null, readable(text, row));
                int grip = left ? surfaceX + width - 10 : surfaceX + 8;
                for (int i = -1; i <= 1 && height > 12; i++) {
                    canvas.box(grip, y + height / 2f + i * 3 - 1, 2, 2, 1, 0, edge, edge);
                }
            }
        } finally {
            canvas.unclip();
        }
    }

    private void drawLabel(Canvas canvas, int x, int y, int width, int height,
                           Component label, GraphicIcon icon, int ink) {
        var font = Minecraft.getInstance().font;
        int available = width - 16;
        if (available <= 0 || height < font.lineHeight + 2) return;
        int size = GraphicIcon.PIXELS;
        boolean iconFits = icon != null && available >= size + 4 + font.width(label) && height >= size + 2;
        int iconSpace = iconFits ? size + 4 : 0;
        FormattedText fitted = label;
        if (font.width(label) > available) {
            int ellipsis = font.width(CommonComponents.ELLIPSIS);
            if (available < ellipsis) return;
            fitted = FormattedText.composite(font.substrByWidth(label, available - ellipsis), CommonComponents.ELLIPSIS);
        }
        var glyphs = Language.getInstance().getVisualOrder(fitted);
        int left = x + (width - iconSpace - font.width(glyphs)) / 2;
        if (iconFits) canvas.icon(icon, left, y + (height - size) / 2, ink, preferences);
        canvas.text(font, glyphs, left + iconSpace, y + (height - font.lineHeight) / 2, ink);
    }

    private int readable(int ink, int fill) {
        int color = ContrastPolicy.readable(ink, fill, fill,
                preferences.highContrast() ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO);
        return active ? color : (color & 0x00ffffff) | 0x99000000;
    }

    private static int color(Integer override, int fallback) { return (override == null ? fallback : override) | 0xff000000; }
    private static long now() { return System.nanoTime() / 1_000_000; }
}
