// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.spatial.GuiGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

abstract class UiPillNavPlatform extends AbstractWidget {
    private Component tooltipLabel;
    private static final GlassOptics OPTICS = new GlassOptics(.8f, 0, 0, .2f, .07f, 1, 20);
    UiPillNavPlatform(Bounds area, Component name) { super(area.x(), area.y(), area.width(), area.height(), name); }
    void tooltip(Component label, int delay) {
        if (java.util.Objects.equals(tooltipLabel, label)) return;
        tooltipLabel = label;
        setTooltip(label == null ? null : Tooltip.create(label, null));
        setTooltipDelay(java.time.Duration.ofMillis(delay));
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return isValidClickButton(event.buttonInfo()) && pointerDown(event.x(), event.y());
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return isValidClickButton(event.buttonInfo()) && pointerUp(event.x(), event.y());
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return isValidClickButton(event.buttonInfo()) && pointerDrag(event.x(), event.y());
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) { return scroll(x, y, dy); }
    @Override public boolean keyPressed(KeyEvent event) { return key(event.key()); }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mx, int my, float partialTick) {
        paint(new PillNavCanvas() {
            public void box(float x, float y, float w, float h, float radius, int stroke, int edge, int fill) {
                RoundedCorners.draw(graphics, x, y, w, h, radius, stroke, edge, fill);
            }
            public void mesh(float x, float y, FilledGeometry<Local> mesh, int top, int bottom, float height) {
                graphics.pose().pushMatrix();
                try {
                    graphics.pose().translate(x, y);
                    GuiGeometry.fillGradient(graphics, mesh, top, bottom, height);
                } finally { graphics.pose().popMatrix(); }
            }
            public void clip(int x, int y, int right, int bottom, Runnable draw) {
                graphics.enableScissor(x, y, right, bottom);
                try { draw.run(); } finally { graphics.disableScissor(); }
            }
            public void text(Font font, FormattedCharSequence text, float x, float y, float scale, int ink) {
                graphics.pose().pushMatrix();
                try {
                    graphics.pose().translate(x, y); graphics.pose().scale(scale, scale);
                    graphics.text(font, text, 0, 0, ink, false);
                } finally { graphics.pose().popMatrix(); }
            }
            public void icon(GraphicIcon icon, float x, float y, float size, float rotation, int ink, UiPreferences prefs) {
                graphics.pose().pushMatrix();
                try {
                    graphics.pose().translate(x + size / 2, y + size / 2);
                    graphics.pose().rotate((float) Math.toRadians(rotation));
                    float scale = size / GraphicIcon.PIXELS;
                    graphics.pose().scale(scale, scale);
                    icon.draw(graphics, -GraphicIcon.PIXELS / 2, -GraphicIcon.PIXELS / 2, 1, ink, prefs);
                } finally { graphics.pose().popMatrix(); }
            }
            public boolean glass(int x, int y, int width, int height, float radius, int tint, UiPreferences prefs) {
                // The shared painter applies the source tint once after the optical sample.
                return prefs.useBackdropBlur() && com.dishanhai.sh_better_ui.client.api.ShBetterUiApi.blur().prepareBackdrop(graphics)
                        && LiquidGlassSurface.drawPrepared(graphics, x, y, width, height, radius, 0, 0, OPTICS, prefs, 1);
            }
        }, mx, my);
    }
    abstract boolean pointerDown(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean pointerDrag(double x, double y);
    abstract boolean scroll(double x, double y, double delta);
    abstract boolean key(int key);
    abstract void paint(PillNavCanvas canvas, int mx, int my);
}
