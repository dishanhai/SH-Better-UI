// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
import org.lwjgl.glfw.GLFW;

/** Register before row buttons: wheel covers the canvas, while clicks pass through except the rail. */
final class OnlineMusicScrollRegion extends AbstractWidget implements com.dishanhai.sh_better_ui.client.gui.scaled.UiCancelableInteraction {
    private static final int RAIL_WIDTH = 8;
    private final ScrollRegionState state = new ScrollRegionState();
    private final IntConsumer changed;
    private IntConsumer shifted = value -> {};
    private int pitch, lastShift;
    private boolean forwardIntent;
    private final java.util.function.LongSupplier clock;
    private final java.util.function.BooleanSupplier reducedMotion;
    private Runnable focusOnWheel = () -> {};
    private boolean pointerFocusPending;
    private java.util.function.BooleanSupplier more = () -> false;
    private Runnable loadMore = () -> {};
    void onEnd(java.util.function.BooleanSupplier available, Runnable action) {
        more = java.util.Objects.requireNonNull(available);
        loadMore = java.util.Objects.requireNonNull(action);
    }
    void pixelRows(int pitch, IntConsumer shifted) {
        this.pitch = Math.max(1, pitch);
        this.shifted = java.util.Objects.requireNonNull(shifted);
        state.pixelRows();
    }
    private void shiftRows() {
        if (pitch == 0) return;
        int shift = state.shift(pitch);
        if (shift != lastShift) { lastShift = shift; shifted.accept(shift); }
    }
    int rowShift() { return lastShift; }
    private boolean requestMore() {
        if (!more.getAsBoolean()) return false;
        loadMore.run();
        return true;
    }

    OnlineMusicScrollRegion(int x, int y, int width, int height, Component label, IntConsumer changed) {
        this(x, y, width, height, label, changed, () -> System.nanoTime() / 1_000_000L,
                () -> com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences.configured().reducedMotion());
    }
    OnlineMusicScrollRegion(int x, int y, int width, int height, Component label, IntConsumer changed,
                            java.util.function.LongSupplier clock, java.util.function.BooleanSupplier reducedMotion) {
        super(x, y, width, height, label);
        this.clock = clock;
        this.reducedMotion = reducedMotion;
        this.changed = java.util.Objects.requireNonNull(changed);
    }
    void setOnBrowse(Runnable focus) { focusOnWheel = java.util.Objects.requireNonNull(focus); }
    boolean dragging() { return state.dragging(); }
    void setStep(int step) { state.setStep(step); }
    void configure(int offset, int total, int visibleRows) {
        state.configure(offset, total, visibleRows);
        active = visibleRows > 0 && (total > visibleRows || more.getAsBoolean());
        shiftRows();
    }
    void setBounds(Bounds bounds) {
        if (getX() != bounds.x() || getY() != bounds.y() || width != bounds.width() || height != bounds.height()) cancelDrag();
        setX(bounds.x()); setY(bounds.y()); width = Math.max(0, bounds.width()); height = Math.max(0, bounds.height());
    }
    @Override public boolean cancelInteraction() {
        boolean drag = state.dragging();
        cancelDrag();
        return drag;
    }
    @Override public boolean releasePointer(int button) {
        if (button != 0 || !state.dragging()) return false;
        pointerFocusPending = false;
        state.endDrag();
        if (state.atForwardLimit()) requestMore();
        return true;
    }
    void cancelDrag() {
        pointerFocusPending = false;
        forwardIntent = false;
        state.stopMotion();
        int old = state.offset(); state.cancelDrag(); notifyChanged(old);
    }
    private void notifyChanged(int previous) {
        if (previous != state.offset()) changed.accept(state.offset());
        shiftRows();
    }
    private boolean usable() { return visible && active && width > 0 && height > 0; }
    @Override public boolean isMouseOver(double x, double y) {
        return visible && active && x >= getX() && x < getX() + width && y >= getY() && y < getY() + height;
    }
    public boolean mouseScrolled(double x, double y, double amount) {
        if (!usable() || !isMouseOver(x, y)) return false;
        int old = state.offset();
        if (!Double.isFinite(amount) || amount == 0 || state.dragging()) return false;
        boolean consumed = state.wheel(amount, clock.getAsLong(), reducedMotion.getAsBoolean());
        forwardIntent = amount < 0 && more.getAsBoolean();
        if (consumed && !isFocused()) focusOnWheel.run();
        notifyChanged(old);
        if (forwardIntent && state.atForwardLimit()) {
            forwardIntent = false;
            if (requestMore()) { if (!isFocused()) focusOnWheel.run(); return true; }
        }
        return consumed;
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return mouseScrolled(x, y, vertical); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!usable() || state.maximum() == 0 || button != 0 || !isMouseOver(x, y) || x < getX() + width - RAIL_WIDTH) return false;
        pointerFocusPending = true;
        forwardIntent = false;
        int old = state.offset(); boolean consumed = state.beginDrag(y - getY(), height); notifyChanged(old); return consumed;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!usable()) { cancelDrag(); return false; }
        if (button != 0 || !state.dragging()) return false;
        int old = state.offset(); state.drag(y - getY(), height); notifyChanged(old); return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return releasePointer(button);
    }
    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) pointerFocusPending = false;
        else if (!pointerFocusPending) cancelDrag();
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!isFocused() || !usable()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE && state.dragging()) { cancelDrag(); return true; }
        long target = switch (key) {
            case GLFW.GLFW_KEY_UP -> (long)state.offset() - state.step();
            case GLFW.GLFW_KEY_DOWN -> (long)state.offset() + state.step();
            case GLFW.GLFW_KEY_PAGE_UP -> (long)state.offset() - state.page();
            case GLFW.GLFW_KEY_PAGE_DOWN -> (long)state.offset() + state.page();
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> state.maximum();
            default -> Long.MIN_VALUE;
        };
        if (target == Long.MIN_VALUE) return false;
        forwardIntent = false;
        int old = state.offset(); state.moveTo(target); notifyChanged(old);
        if (target >= state.maximum() && (key == GLFW.GLFW_KEY_END || key == GLFW.GLFW_KEY_DOWN
                || key == GLFW.GLFW_KEY_PAGE_DOWN) && requestMore()) return true;
        return old != state.offset();
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.control.scroll.position",
                state.maximum() == 0 ? 0 : (int)Math.round(100d * state.offset() / state.maximum())));
    }
    void tick() {
        if (!usable()) { cancelDrag(); return; }
        int offset = state.offset();
        state.advance(clock.getAsLong(), reducedMotion.getAsBoolean());
        notifyChanged(offset);
        if (forwardIntent && state.atForwardLimit()) {
            forwardIntent = false;
            requestMore();
        }
    }
    @Override protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!usable()) return;
        if (state.maximum() == 0) return;
        var theme = UiTheme.current();
        int x = getX() + width - RAIL_WIDTH, y = getY(), top = y + state.thumbTop(height);
        graphics.fill(x + 3, y, x + 5, y + height, theme.border());
        graphics.fill(x + 1, top, x + 7, top + state.thumbHeight(height),
                state.dragging() || isFocused() || isMouseOver(mouseX, mouseY) && mouseX >= x ? theme.accent() : theme.text());
    }
}
