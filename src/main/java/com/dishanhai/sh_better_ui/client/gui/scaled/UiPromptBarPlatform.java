// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;

import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Bridges native rendering, text editing and input for the shared PromptBar. */
abstract class UiPromptBarPlatform extends AbstractWidget implements ContainerEventHandler {
    private GuiEventListener focus;
    private GuiEventListener pressed;
    private GuiEventListener pendingFocusLoss;

    UiPromptBarPlatform(Bounds area, Component label) {
        super(area.x(), area.y(), area.width(), area.height(), label);
    }

    static MultiLineEditBox editor(Font font, int width, Component placeholder, Component label) {
        return new MultiLineEditBox(font, 0, 0, width, 17, placeholder, label) {
            @Override
            protected void renderBackground(GuiGraphics graphics) {}

            @Override
            protected void renderDecorations(GuiGraphics graphics) {
                // The composer supplies its own border and omits the character counter.
            }
        };
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
    public boolean mouseClicked(double x, double y, int button) {
        if (!isActive() || button != 0) return false;
        pointerAt(x, y);
        for (var child : children()) {
            if (!child.isMouseOver(x, y)) continue;
            setFocused(child);
            pressed = child;
            if (child.mouseClicked(x, y, button)) {
                return true;
            }
            pressed = null;
        }
        if (!isMouseOver(x, y)) return false;
        setFocused(children().get(0));
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return pressed != null && pressed.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (pressed == null || button != 0) return false;
        var target = pressed;
        pressed = null;
        target.mouseReleased(x, y, button);
        afterRelease(target, x, y);
        return true;
    }

    // Minecraft 1.20.1 passes only vertical scroll, while 1.21.1 passes both axes.
    public boolean mouseScrolled(double x, double y, double dy) {
        return mouseScrolled(x, y, 0, dy);
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return isActive() && (handleScroll(x, y, dy)
                || getChildAt(x, y).filter(child -> PlatformUiApi.scrollWidget(child, x, y, dx, dy)).isPresent());
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return isActive() && (handleKey(key, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0)
                || ContainerEventHandler.super.keyPressed(key, scan, modifiers));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
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
                graphics.drawString(font, text, x, y, color, false);
            }
        };
        paint(canvas, mouseX, mouseY);
        for (var child : children()) child.render(graphics, mouseX, mouseY, tick);
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
