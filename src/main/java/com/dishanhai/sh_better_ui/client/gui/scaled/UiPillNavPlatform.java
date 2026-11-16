// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

abstract class UiPillNavPlatform extends AbstractWidget {
    private Component tooltipLabel;
    UiPillNavPlatform(Bounds area, Component name) { super(area.x(), area.y(), area.width(), area.height(), name); }
    void tooltip(Component label, int delay) {
        if (java.util.Objects.equals(tooltipLabel, label)) return;
        tooltipLabel = label;
        setTooltip(label == null ? null : Tooltip.create(label));
        PlatformUiApi.setTooltipDelay(this, delay);
    }
    @Override public boolean mouseClicked(double x, double y, int button) { return button == 0 && pointerDown(x, y); }
    @Override public boolean mouseReleased(double x, double y, int button) { return button == 0 && pointerUp(x, y); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return button == 0 && pointerDrag(x, y);
    }
    public boolean mouseScrolled(double x, double y, double delta) { return scroll(x, y, delta); }
    public boolean mouseScrolled(double x, double y, double dx, double dy) { return scroll(x, y, dy); }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) { return key(key); }
    @Override protected void renderWidget(GuiGraphics graphics, int mx, int my, float partialTick) {
        paint(new PillNavCanvas() {
            public void box(float x, float y, float w, float h, float radius, int stroke, int edge, int fill) {
                RoundedCorners.draw(graphics, x, y, w, h, radius, stroke, edge, fill);
            }
            public void mesh(float x, float y, FilledGeometry<Local> mesh, int top, int bottom, float height) {
                var sink = CapsuleRenderer.colorVertices(graphics, x, y);
                for (var triangle : mesh.triangles()) {
                    vertex(triangle.a(), sink, top, bottom, height); vertex(triangle.c(), sink, top, bottom, height);
                    vertex(triangle.b(), sink, top, bottom, height); vertex(triangle.b(), sink, top, bottom, height);
                }
            }
            public void clip(int x, int y, int right, int bottom, Runnable draw) {
                graphics.enableScissor(x, y, right, bottom);
                try { draw.run(); } finally { graphics.disableScissor(); }
            }
            public void text(Font font, FormattedCharSequence text, float x, float y, float scale, int ink) {
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(x, y, 0); graphics.pose().scale(scale, scale, 1);
                    graphics.drawString(font, text, 0, 0, ink, false);
                } finally { graphics.pose().popPose(); }
            }
            public void icon(GraphicIcon icon, float x, float y, float size, float rotation, int ink, UiPreferences prefs) {
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(x + size / 2, y + size / 2, 0);
                    graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
                    float scale = size / GraphicIcon.PIXELS;
                    graphics.pose().scale(scale, scale, 1);
                    icon.draw(graphics, -GraphicIcon.PIXELS / 2, -GraphicIcon.PIXELS / 2, 1, ink, prefs);
                } finally { graphics.pose().popPose(); }
            }
            public boolean glass(int x, int y, int width, int height, float radius, int tint, UiPreferences prefs) {
                return prefs.useBackdropBlur() && GlassBlur.tryBackdrop(graphics, new GuiRect(x, y, width, height),
                        1f, GlassStyle.CLEAR, 20f, radius);
            }
        }, mx, my);
    }
    private static void vertex(com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point<Local> p,
                               CapsuleMesh.VertexSink sink, int top, int bottom, float height) {
        sink.vertex((float) p.x(), (float) p.y(), UiPillNavPaint.mix(top, bottom, (float) p.y() / height));
    }
    abstract boolean pointerDown(double x, double y);
    abstract boolean pointerUp(double x, double y);
    abstract boolean pointerDrag(double x, double y);
    abstract boolean scroll(double x, double y, double delta);
    abstract boolean key(int key);
    abstract void paint(PillNavCanvas canvas, int mx, int my);
}
