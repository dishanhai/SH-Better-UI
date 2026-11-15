// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native UUK screen with a background pass, a clear foreground pass, and registered widgets. */
public abstract class LayeredScreen extends Screen {
    protected LayeredScreen(Component title) { super(title); }

    @Override
    public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderBackdrop(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderForeground(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    protected void renderBackdrop(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    protected abstract void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);
}
