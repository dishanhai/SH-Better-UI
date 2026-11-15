// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;


import java.util.List;

/** Bridges native rendering, text editing and input for the shared PromptBar. */
abstract class UiPromptBarPlatform extends AbstractWidget implements ContainerEventHandler {
    private GuiEventListener focus;
    private GuiEventListener pressed;
    private GuiEventListener pendingFocusLoss;
    private boolean composing;

    UiPromptBarPlatform(Bounds area, Component label) {
        super(area.x(), area.y(), area.width(), area.height(), label);
    }

    static MultiLineEditBox editor(Font font, int width, Component placeholder, Component label) {
        return MultiLineEditBox.builder().setPlaceholder(placeholder).setShowDecorations(false)
                .setShowBackground(false).build(font, width, 17, label);
    }

    @Override public abstract List<AbstractWidget> children();
    @Override public GuiEventListener getFocused() { return focus; }
    @Override public boolean isFocused() { return focus != null; }
    @Override public boolean isDragging() { return pressed != null; }
    @Override public void setDragging(boolean dragging) { if (!dragging) pressed = null; }
    final boolean pressed(GuiEventListener child) { return pressed == child; }

    @Override
    public void setFocused(GuiEventListener next) {
        if (next == focus && pendingFocusLoss == null) return;
        var previous = focus == null ? pendingFocusLoss : focus;
        pendingFocusLoss = null;
        focus = next;
        if (previous != next) composing = false;
        if (previous != null && previous != next) previous.setFocused(false);
        if (next != null && next != previous) next.setFocused(true);
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused) {
            if (focus == null) setFocused(pendingFocusLoss == null ? children().get(0) : pendingFocusLoss);
        } else if (focus != null) {
            // Some Screen implementations clear and immediately restore the same widget after a click.
            pendingFocusLoss = focus;
            focus = null;
        }
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return isActive() ? ContainerEventHandler.super.nextFocusPath(event) : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || event.button() != 0) return false;
        double x = event.x(), y = event.y();
        pointerAt(x, y);
        for (var child : children()) {
            if (!child.isMouseOver(x, y)) continue;
            setFocused(child);
            pressed = child;
            if (child.mouseClicked(event, doubleClick)) {
                return true;
            }
            pressed = null;
        }
        if (!isMouseOver(x, y)) return false;
        setFocused(children().get(0));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return pressed != null && pressed.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressed == null || event.button() != 0) return false;
        var target = pressed;
        pressed = null;
        target.mouseReleased(event);
        afterRelease(target, event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return isActive() && (handleScroll(x, y, dy) || ContainerEventHandler.super.mouseScrolled(x, y, dx, dy));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (composing && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER
                || event.key() == GLFW.GLFW_KEY_TAB)) return true;
        return isActive() && (handleKey(event.key(), event.hasShiftDown())
                || ContainerEventHandler.super.keyPressed(event));
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        composing = focus instanceof MultiLineEditBox && event != null;
        return ContainerEventHandler.super.preeditUpdated(event);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        if (pendingFocusLoss != null) {
            setFocused((GuiEventListener) null);
            pressed = null;
            pointerAt(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        }
        Canvas canvas = new Canvas() {
            public void box(int x, int y, int w, int h, int radius, int edge, int fill) {
                RoundedCorners.draw(graphics, x, y, w, h, radius, 1, edge, fill);
            }

            public void dot(int x, int y, int size, int color) {
                graphics.fill(x, y, x + size, y + size, color);
            }

            public void fill(int x, int y, int right, int bottom, int color) {
                graphics.fill(x, y, right, bottom, color);
            }

            public void text(Font font, Component text, int x, int y, int color) {
                graphics.text(font, text, x, y, color, false);
            }
        };
        paint(canvas, mouseX, mouseY);
        for (var child : children()) child.extractRenderState(graphics, mouseX, mouseY, tick);
        paintForeground(canvas);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        Component label = focusedLabel();
        if (label != null) output.nest().add(NarratedElementType.TITLE, label);
        else if (focus instanceof AbstractWidget widget) widget.updateNarration(output.nest());
    }

    abstract boolean handleKey(int key, boolean shift);
    abstract boolean handleScroll(double x, double y, double amount);
    abstract void pointerAt(double x, double y);
    abstract void afterRelease(GuiEventListener target, double x, double y);
    abstract void paint(Canvas canvas, int mouseX, int mouseY);
    abstract void paintForeground(Canvas canvas);
    abstract Component focusedLabel();

    interface Canvas {
        void box(int x, int y, int w, int h, int radius, int edge, int fill);
        void dot(int x, int y, int size, int color);
        void fill(int x, int y, int right, int bottom, int color);
        void text(Font font, Component text, int x, int y, int color);
    }
}
