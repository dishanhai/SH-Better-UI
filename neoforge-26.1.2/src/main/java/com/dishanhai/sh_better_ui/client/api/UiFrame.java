// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** One native GUI extraction snapshot; coordinates use GUI logical pixels. */
public record UiFrame(GuiGraphicsExtractor graphics, Bounds viewport, int mouseX, int mouseY,
                      UiPreferences preferences, long nowMs, float partialTick) {
    /** Draws from the given top-left GUI position, scaling relative to the native font size. */
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
