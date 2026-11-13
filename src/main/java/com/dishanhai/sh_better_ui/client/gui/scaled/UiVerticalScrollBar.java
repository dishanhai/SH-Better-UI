// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.layout.SmoothVerticalScroll;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Native scrollbar; the host clips and repositions its content on change. */
public final class UiVerticalScrollBar extends AbstractWidget {
    private final VerticalScroll scroll;
    private final Runnable changed;
    private final double step;
    private SmoothVerticalScroll motion;
    private java.util.function.BooleanSupplier reducedMotion;
    public UiVerticalScrollBar(Bounds area, Component name, VerticalScroll scroll, double step, Runnable changed) {
        super(area.x(), area.y(), area.width(), area.height(), name);
        if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("Invalid scroll step");
        this.scroll = java.util.Objects.requireNonNull(scroll);
        this.changed = java.util.Objects.requireNonNull(changed);
        this.step = step;
    }
    /** Enables frame-sampled wheel and keyboard motion while keeping drag immediate. */
    public void setSmoothScrolling(java.util.function.BooleanSupplier reducedMotion) {
        this.reducedMotion = java.util.Objects.requireNonNull(reducedMotion);
        motion = new SmoothVerticalScroll(scroll);
    }
    /** Call before drawing the host's positioned content. */
    public void advanceMotion() {
        if (motion != null && motion.advance(now(), reducedMotion.getAsBoolean())) changed.run();
    }
    /** Call after the host changes the position or size outside this scrollbar. */
    public void synchronizeMotion() { if (motion != null) motion.synchronize(); }
    private static long now() { return System.nanoTime() / 1_000_000L; }
    public boolean scrollByWheel(double delta) {
        if (!visible || !active || !Double.isFinite(delta) || delta == 0 || scroll.maxOffset() == 0) return false;
        moveBy(-delta * step);
        return true;
    }
    private void moveBy(double delta) {
        boolean moved = motion == null ? scroll.scrollBy(delta) : motion.scrollBy(delta, now(), reducedMotion.getAsBoolean());
        if (moved) changed.run();
    }
    private void move(double value) {
        boolean moved = motion == null ? scroll.scrollTo(value) : motion.scrollTo(value, now(), reducedMotion.getAsBoolean());
        if (moved) changed.run();
    }
    private void drag(double y) { if (scroll.dragTo(y - getY(), getHeight())) { synchronizeMotion(); changed.run(); } }
    private void cancel() { if (scroll.cancelDrag()) { synchronizeMotion(); changed.run(); } }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (!focused) cancel(); }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.control.scroll.position",
                scroll.maxOffset() == 0 ? 0 : (int) Math.round(100 * scroll.offset() / scroll.maxOffset())));
    }
    @Override public void onClick(double x, double y) {
        synchronizeMotion();
        if (scroll.beginDrag(y - getY(), getHeight())) changed.run();
        synchronizeMotion();
    }
    @Override protected void onDrag(double x, double y, double dx, double dy) { drag(y); }
    @Override public void onRelease(double x, double y) { scroll.endDrag(); }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!isFocused() || !active) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE && scroll.dragging()) { cancel(); return true; }
        switch (key) {
            case GLFW.GLFW_KEY_UP -> moveBy(-step);
            case GLFW.GLFW_KEY_DOWN -> moveBy(step);
            case GLFW.GLFW_KEY_PAGE_UP -> moveBy(-getHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> moveBy(getHeight());
            case GLFW.GLFW_KEY_HOME -> move(0);
            case GLFW.GLFW_KEY_END -> move(scroll.maxOffset());
            default -> { return false; }
        }
        return true;
    }
    @Override protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (scroll.maxOffset() <= 0) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        graphics.fill(x + w / 3, y, x + w - w / 3, y + h, DarkModeTheme.borderColor());
        int top = y + scroll.thumbTop(h);
        graphics.fill(x, top, x + w, top + scroll.thumbHeight(h), isFocused() ? DarkModeTheme.accentColor() : DarkModeTheme.secondaryTextColor());
    }
}
