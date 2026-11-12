// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.page.container;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.locale.Language;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class NativePageContainerScreen extends AbstractContainerScreen<NativePageMenu> {
    public NativePageContainerScreen(NativePageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 114 + menu.binding().rows() * 18;
        inventoryLabelY = imageHeight - 95;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        var colors = UiTheme.current();
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, colors.surface());
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.renderOutline(x - 1, y - 1, 18, 18, colors.border());
            graphics.fill(x, y, x + 16, y + 16, colors.background());
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int color = UiTheme.current().text();
        var clippedTitle = Language.getInstance().getVisualOrder(font.substrByWidth(title, imageWidth - 16));
        graphics.drawString(font, clippedTitle, titleLabelX, titleLabelY, color, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, color, false);
    }
}
