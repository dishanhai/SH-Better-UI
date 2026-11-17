// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Native HUD timeline renderer; callers own placement, colours, text and state. */
public final class TimelineBarRenderer {
    private TimelineBarRenderer() {}

    public static void draw(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                            double fraction, boolean reversed, int background, int fill,
                            Component label, int textColor) {
        Objects.requireNonNull(graphics, "graphics");
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, background);
        int fillWidth = TimelineBarModel.fillWidth(width, fraction);
        if (fillWidth > 0) {
            int start = TimelineBarModel.fillStart(x, width, fraction, reversed);
            graphics.fill(start, y, start + fillWidth, y + height, fill);
        }
        if (font != null && label != null) {
            graphics.drawCenteredString(font, label, x + width / 2, y - font.lineHeight - 1, textColor);
        }
    }
}
