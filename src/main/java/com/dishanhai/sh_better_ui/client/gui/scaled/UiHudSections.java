// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

/** Draws section dividers in a compact HUD panel's existing row gaps. */
public final class UiHudSections {
    private UiHudSections() {}

    public static void drawDividers(GuiGraphics graphics, Font font, int x, int y, float scale,
                                    int panelWidth, List<Integer> boundaries, boolean highContrast, int textColor) {
        if (boundaries.isEmpty()) return;
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1);
            int color = (highContrast ? 0xB0000000 : 0x60000000) | (textColor & 0xFFFFFF);
            int contentHeight = Math.max(8, font.lineHeight);
            int rowHeight = contentHeight + 3;
            for (int boundary : boundaries) {
                int lineY = 6 + contentHeight + 2 + (boundary - 1) * rowHeight;
                graphics.fill(6, lineY, panelWidth - 6, lineY + 1, color);
            }
        } finally { pose.popPose(); }
    }
}
