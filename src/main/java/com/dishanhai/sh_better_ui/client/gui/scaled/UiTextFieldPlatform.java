// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

abstract class UiTextFieldPlatform extends AbstractWidget {
    protected final Font font;
    private long lastClick;
    private double lastX, lastY;
    private char highSurrogate;

    UiTextFieldPlatform(Font font, Bounds bounds, Component label) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), label);
        this.font = font;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 1 && visible && isActive() && isMouseOver(x, y) && clearOnRightClick()) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override
    public void onClick(double x, double y) {
        long now = System.nanoTime() / 1_000_000;
        boolean doubleClick = lastClick != 0 && now - lastClick < 300 && Math.hypot(x - lastX, y - lastY) < 4;
        lastClick = doubleClick ? 0 : now;
        lastX = x;
        lastY = y;
        pointerDown(x, Screen.hasShiftDown(), doubleClick);
    }

    @Override protected void onDrag(double x, double y, double dx, double dy) { pointerDrag(x); }
    @Override public void onRelease(double x, double y) { pointerUp(); }
    @Override public void playDownSound(SoundManager soundManager) {}

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            highSurrogate = 0;
            lastClick = 0;
        }
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!isFocused() || !isActive()) return false;
        if (Character.isHighSurrogate(character)) {
            highSurrogate = character;
            return true;
        }
        char previous = highSurrogate;
        highSurrogate = 0;
        if (Character.isLowSurrogate(character)) {
            return previous != 0 && input(new String(new char[]{previous, character}));
        }
        return !Character.isISOControl(character) && input(String.valueOf(character));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return key(key, Screen.hasControlDown(), (modifiers & GLFW.GLFW_MOD_SHIFT) != 0,
                (modifiers & GLFW.GLFW_MOD_ALT) != 0);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        paint(new Canvas() {
            public void box(int x, int y, int w, int h, int radius, int stroke, int edge, int fill) {
                RoundedCorners.draw(graphics, x, y, w, h, radius, stroke, edge, fill);
            }
            public void fill(int x, int y, int right, int bottom, int color) {
                graphics.fill(x, y, right, bottom, color);
            }
            public void text(FormattedCharSequence text, int x, int y, int color) {
                graphics.drawString(font, text, x, y, color, false);
            }
            public void clip(int x, int y, int right, int bottom) { graphics.enableScissor(x, y, right, bottom); }
            public void unclip() { graphics.disableScissor(); }
        });
    }

    void tooltipDelay(int millis) { PlatformUiApi.setTooltipDelay(this, millis); }
    boolean composing() { return false; }
    void textInputArea(int x, int y) {}
    abstract void pointerDown(double x, boolean extend, boolean doubleClick);
    abstract boolean clearOnRightClick();
    abstract void pointerDrag(double x);
    abstract void pointerUp();
    abstract boolean key(int key, boolean shortcut, boolean shift, boolean alt);
    abstract boolean input(String text);
    abstract void paint(Canvas canvas);

    interface Canvas {
        void box(int x, int y, int w, int h, int radius, int stroke, int edge, int fill);
        void fill(int x, int y, int right, int bottom, int color);
        void text(FormattedCharSequence text, int x, int y, int color);
        void clip(int x, int y, int right, int bottom);
        void unclip();
    }
}
