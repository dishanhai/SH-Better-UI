// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;

/** One native GUI extraction snapshot; coordinates use GUI logical pixels. */
public record UiFrame(GuiGraphicsExtractor graphics, Bounds viewport, int mouseX, int mouseY,
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
        var optics = GlassMaterial.preset(panel.variant()).optics();
        if (preferences.useBackdropBlur() && ShBetterUiApi.blur().prepareBackdrop(graphics)
                && LiquidGlassSurface.drawPrepared(graphics, area.x(), area.y(), area.width(), area.height(),
                panel.radius(), 0, panel.edge(preferences), optics, preferences, 1)) {
            return UiGlassPanel.Paint.OPTICAL;
        }
        RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), panel.radius(), 1,
                panel.edge(preferences), panel.fallbackFill(preferences));
        return UiGlassPanel.Paint.SOLID;
    }

    @Override
    public void placed(AlignmentLayout.View view, Runnable content) {
        var clip = view.clip();
        if (clip != null && clip.empty()) return;
        var transform = view.transform();
        if (clip != null) graphics.enableScissor((int) Math.floor(clip.x()), (int) Math.floor(clip.y()),
                (int) Math.ceil(clip.right()), (int) Math.ceil(clip.bottom()));
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.mul(new Matrix3x2f((float) transform.xx(), (float) transform.yx(), (float) transform.xy(),
                    (float) transform.yy(), (float) transform.tx(), (float) transform.ty()));
            content.run();
        } finally {
            pose.popMatrix();
            if (clip != null) graphics.disableScissor();
        }
    }

    /** Draws from the given top-left GUI position, scaling relative to the native font size. */
    @Override
    public void drawScaledText(Font font, Component text, int x, int y, int color, float scale) {
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(scale);
            graphics.text(font, text, 0, 0, color, false);
        } finally {
            pose.popMatrix();
        }
    }
}
