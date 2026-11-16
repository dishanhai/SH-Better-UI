// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native UUK screen with a background pass, a clear foreground pass, and registered widgets. */
public abstract class LayeredScreen extends Screen {
    protected LayeredScreen(Component title) { super(title); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                && getFocused() instanceof com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots slots
                && slots.cancelDrag()) { setDragging(false); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && getFocused() instanceof com.dishanhai.sh_better_ui.client.gui.scaled.UiIngredientSlots slots
                && slots.consumeCancelledRelease()) return true;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackdrop(graphics, mouseX, mouseY, partialTick);
        renderForeground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** NeoForge 1.21.1 calls this from Screen.render just before widgets. Backdrop already ran above. */
    @SuppressWarnings("unused")
    public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    protected void renderBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    protected abstract void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
}
