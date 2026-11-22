package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.render.LegacyGuiBackend;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 山海：一次绘制共用的图形、GUI 区域、鼠标、偏好和时钟快照。 */
public record UiFrame(GuiGraphics graphics, Bounds viewport, UiMouseState mouse,
                      UiPreferences preferences, long nowMs, float partialTick) implements UiCanvas {
    /** Returns this frame through the common drawing contract. */
    public UiCanvas canvas() { return this; }

    @Override
    public void fill(Bounds bounds, int argb) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), argb);
    }

    @Override
    public void icon(GraphicIcon icon, int x, int y, int scale, int argb) {
        icon.draw(graphics, x, y, scale, argb, preferences);
    }

    @Override
    public void clipped(Bounds bounds, Runnable content) {
        if (bounds.width() == 0 || bounds.height() == 0) return;
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        try {
            content.run();
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public UiGlassPanel.Paint glass(UiGlassPanel panel) {
        if (panel.empty()) return UiGlassPanel.Paint.EMPTY;
        var area = panel.bounds();
        if (LegacyGuiBackend.isNonOpenGlWindow()) {
            return UiCanvas.super.glass(panel);
        }
        var optics = GlassMaterial.preset(panel.variant()).optics();
        var style = switch (panel.variant()) {
            case CLEAR -> GlassStyle.LIQUID_CLEAR;
            case REGULAR -> GlassStyle.LIQUID_REGULAR;
        };
        boolean optical = preferences.useBackdropBlur() && ShBetterUiApi.blur().tryBackdrop(graphics,
                new GuiRect(area.x(), area.y(), area.width(), area.height()), 1, style, optics.blurRadius(), panel.radius());
        RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), panel.radius(), 1,
                panel.edge(preferences), optical ? 0 : panel.fallbackFill(preferences));
        return optical ? UiGlassPanel.Paint.OPTICAL : UiGlassPanel.Paint.SOLID;
    }

    @Override
    public void placed(AlignmentLayout.View view, Runnable content) {
        ShBetterUiApi.layout().draw(graphics, view, ignored -> content.run());
    }

    /** Draws from the given top-left GUI position, scaling relative to the native font size. */
    @Override
    public void drawScaledText(Font font, Component text, int x, int y, int color, float scale) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1);
            graphics.drawString(font, text, 0, 0, color, false);
        } finally {
            pose.popPose();
        }
    }
}
