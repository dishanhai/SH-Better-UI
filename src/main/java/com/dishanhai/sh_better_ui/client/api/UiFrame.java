package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
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
