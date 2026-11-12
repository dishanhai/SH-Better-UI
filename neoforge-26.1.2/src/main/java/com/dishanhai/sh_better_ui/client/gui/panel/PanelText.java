// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class PanelText {
    private PanelText() {}

    static void drawLine(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y,
                         int width, int color, int mouseX, int mouseY) {
        boolean clipped = font.width(text) > width;
        var visual = text.getVisualOrderText();
        if (clipped) {
            visual = width >= font.width(CommonComponents.ELLIPSIS) ? ComponentRenderUtils.clipText(text, font, width)
                    : Language.getInstance().getVisualOrder(font.substrByWidth(text, width));
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + font.lineHeight) {
                int tooltipWidth = Math.clamp(graphics.guiWidth() - 12, 1, 170);
                graphics.setTooltipForNextFrame(font.split(text, tooltipWidth), mouseX, mouseY);
            }
        }
        graphics.text(font, visual, x, y, color, false);
    }
}
