// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

abstract class UiAnimatedListPlatform extends AbstractWidget {
    UiAnimatedListPlatform(Bounds area, Component label) { super(area.x(), area.y(), area.width(), area.height(), label); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return event.button() == 0 && pointerDown(event.x(), event.y()); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return event.button() == 0 && pointerDrag(event.x(), event.y()); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return event.button() == 0 && pointerUp(event.x(), event.y()); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double delta) { return wheel(x, y, delta); }
    @Override public boolean keyPressed(KeyEvent event) { return key(event.key(), event.modifiers()); }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
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
                if ((ink >>> 24) >= 4) graphics.text(font, text, x, y, ink, false);
            }
            public void icon(GraphicIcon icon, int x, int y, int value, UiPreferences prefs) { icon.draw(graphics, x, y, 1, color(value), prefs); }
            public void push(float x, float y, float scale) {
                graphics.pose().pushMatrix(); graphics.pose().translate(x, y); graphics.pose().scale(scale);
            }
            public void pop() { graphics.pose().popMatrix(); }
            public void clip(int x, int y, int right, int bottom) { graphics.enableScissor(x, y, right, bottom); }
            public void unclip() { graphics.disableScissor(); }
            public void plate(Bounds area, float radius, int edge, int fill, UiPreferences prefs) {
                if (prefs.useBackdropBlur() && com.dishanhai.sh_better_ui.client.api.ShBetterUiApi.blur().prepareBackdrop(graphics)
                        && com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface.drawPrepared(graphics,
                        area.x(), area.y(), area.width(), area.height(), radius, fill, edge,
                        com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.preset(
                        com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant.REGULAR).optics(), prefs, 1)) return;
                RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), radius, 1, edge, fill | 0xFF000000);
            }
        }, mouseX, mouseY);
    }

    abstract boolean pointerDown(double x, double y);
    net.minecraft.client.gui.screens.Screen currentScreen() { return net.minecraft.client.Minecraft.getInstance().gui.screen(); }
    void narrate(Component message) { net.minecraft.client.Minecraft.getInstance().getNarrator().saySystemNow(message); }
    abstract boolean pointerDrag(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean wheel(double x, double y, double delta);
    abstract boolean key(int key, int modifiers);
    abstract void paint(UiAnimatedList.Canvas canvas, int mouseX, int mouseY);
}
