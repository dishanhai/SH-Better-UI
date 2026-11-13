// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

/** Draws section dividers in a compact HUD panel's existing row gaps. */
public final class UiHudSections {
    private UiHudSections() {}

    public static void drawDividers(GuiGraphicsExtractor graphics, Font font, int x, int y, float scale,
                                    int panelWidth, List<Integer> boundaries, boolean highContrast, int textColor) {
        if (boundaries.isEmpty()) return;
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).scale(scale);
            int color = (highContrast ? 0xB0000000 : 0x60000000) | (textColor & 0xFFFFFF);
            int rowHeight = font.lineHeight + 3;
            int contentHeight = Math.max(8, font.lineHeight);
            int gap = rowHeight - contentHeight;
            if (gap <= 0) return;
            for (int boundary : boundaries) {
                int lineY = 7 + contentHeight + (gap - 1) / 2 + (boundary - 1) * rowHeight;
                graphics.fill(6, lineY, panelWidth - 6, lineY + 1, color);
            }
        } finally { pose.popMatrix(); }
    }
}
