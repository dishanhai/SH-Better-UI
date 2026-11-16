// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

abstract class UiAnimatedListPlatform extends AbstractWidget {
    UiAnimatedListPlatform(Bounds area, Component label) { super(area.x(), area.y(), area.width(), area.height(), label); }
    @Override public boolean mouseClicked(double x, double y, int button) { return button == 0 && pointerDown(x, y); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return button == 0 && pointerDrag(x, y); }
    @Override public boolean mouseReleased(double x, double y, int button) { return button == 0 && pointerUp(x, y); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double delta) { return wheel(x, y, delta); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return key(key, modifiers); }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        paint(new UiAnimatedList.Canvas() {
            private float opacity = 1;
            private int color(int value) { return AnimatedListPaint.alpha(value, opacity); }
            public void opacity(float value) { opacity = value; }
            public void box(float x, float y, float w, float h, float radius, int edge, int fill) {
                RoundedCorners.draw(graphics, x, y, w, h, radius, 1, color(edge), color(fill));
            }
            public void fill(int x, int y, int right, int bottom, int value) { graphics.fill(x, y, right, bottom, color(value)); }
            public void text(Font font, FormattedCharSequence text, int x, int y, int value) {
                int ink = color(value);
                if ((ink >>> 24) >= 4) graphics.drawString(font, text, x, y, ink, false);
            }
            public void icon(GraphicIcon icon, int x, int y, int value, UiPreferences prefs) { icon.draw(graphics, x, y, 1, color(value), prefs); }
            public void push(float x, float y, float scale) {
                graphics.pose().pushPose(); graphics.pose().translate(x, y, 0); graphics.pose().scale(scale, scale, 1);
            }
            public void pop() { graphics.pose().popPose(); }
            public void clip(int x, int y, int right, int bottom) { graphics.enableScissor(x, y, right, bottom); }
            public void unclip() { graphics.disableScissor(); }
            public void plate(Bounds area, float radius, int edge, int fill, UiPreferences prefs) {
                LiquidGlassTheme.plate(graphics, area.x(), area.y(), area.width(), area.height(), radius, edge, fill, prefs);
            }
        }, mouseX, mouseY);
    }

    abstract boolean pointerDown(double x, double y);
    net.minecraft.client.gui.screens.Screen currentScreen() { return net.minecraft.client.Minecraft.getInstance().screen; }
    void narrate(Component message) { net.minecraft.client.Minecraft.getInstance().getNarrator().say(message); }
    abstract boolean pointerDrag(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean wheel(double x, double y, double delta);
    abstract boolean key(int key, int modifiers);
    abstract void paint(UiAnimatedList.Canvas canvas, int mouseX, int mouseY);
}
