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
    private Runnable focusOnWheel = () -> {};
    private boolean pointerFocusPending;
    OnlineMusicScrollRegion(int x, int y, int width, int height, Component label, IntConsumer changed) {
        super(x, y, width, height, label);
        this.changed = java.util.Objects.requireNonNull(changed);
    }
    void setOnBrowse(Runnable focus) { focusOnWheel = java.util.Objects.requireNonNull(focus); }
    boolean dragging() { return state.dragging(); }
    void setStep(int step) { state.setStep(step); }
    void configure(int offset, int total, int visibleRows) { state.configure(offset, total, visibleRows); active = total > visibleRows && visibleRows > 0; }
    void setBounds(Bounds bounds) {
        if (getX() != bounds.x() || getY() != bounds.y() || width != bounds.width() || height != bounds.height()) cancelDrag();
        setX(bounds.x()); setY(bounds.y()); width = Math.max(0, bounds.width()); height = Math.max(0, bounds.height());
    }
    @Override public boolean cancelInteraction() {
        if (!state.dragging()) return false;
        cancelDrag(); return true;
    }
    @Override public boolean releasePointer(int button) {
        if (button != 0 || !state.dragging()) return false;
        pointerFocusPending = false; state.endDrag(); return true;
    }
    void cancelDrag() {
        pointerFocusPending = false;
        int old = state.offset(); state.cancelDrag(); notifyChanged(old);
    }
    private void notifyChanged(int previous) { if (previous != state.offset()) changed.accept(state.offset()); }
    private boolean usable() { return visible && active && width > 0 && height > 0 && state.maximum() > 0; }
    @Override public boolean isMouseOver(double x, double y) {
        return visible && active && x >= getX() && x < getX() + width && y >= getY() && y < getY() + height;
    }
    public boolean mouseScrolled(double x, double y, double amount) {
        if (!usable() || !isMouseOver(x, y)) return false;
        int old = state.offset(); boolean consumed = state.wheel(amount);
        if (consumed) focusOnWheel.run();
        notifyChanged(old); return consumed;
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return mouseScrolled(x, y, vertical); }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y(); int button = event.button();
        if (!usable() || button != 0 || !isMouseOver(x, y) || x < getX() + width - RAIL_WIDTH) return false;
        pointerFocusPending = true;
        int old = state.offset(); boolean consumed = state.beginDrag(y - getY(), height); notifyChanged(old); return consumed;
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double x = event.x(), y = event.y(); int button = event.button();
        if (!usable()) { cancelDrag(); return false; }
        if (button != 0 || !state.dragging()) return false;
        int old = state.offset(); state.drag(y - getY(), height); notifyChanged(old); return true;
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        int button = event.button();
        return releasePointer(button);
    }
    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) pointerFocusPending = false;
        else if (!pointerFocusPending) cancelDrag();
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
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
        int old = state.offset(); state.moveTo(target); notifyChanged(old); return old != state.offset();
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.control.scroll.position",
                state.maximum() == 0 ? 0 : (int)Math.round(100d * state.offset() / state.maximum())));
    }
    @Override protected void extractWidgetRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!usable()) { cancelDrag(); return; }
        var theme = UiTheme.current();
        int x = getX() + width - RAIL_WIDTH, y = getY(), top = y + state.thumbTop(height);
        graphics.fill(x + 3, y, x + 5, y + height, theme.border());
        graphics.fill(x + 1, top, x + 7, top + state.thumbHeight(height),
                state.dragging() || isFocused() || isMouseOver(mouseX, mouseY) && mouseX >= x ? theme.accent() : theme.text());
    }
}
