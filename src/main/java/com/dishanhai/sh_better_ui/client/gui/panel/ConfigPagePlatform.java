// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.kernel.api.screen.LayeredScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class ConfigPagePlatform extends LayeredScreen {
    ConfigPagePlatform(Component title) { super(title); }

    @Override
    protected void renderBackdrop(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) graphics.fill(0, 0, width, height, 0xFF171F2B);
        else ShBetterUiApi.blur().background(graphics);
        graphics.fill(0, 0, width, height, 0x98080D15);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        paint(new Painter() {
            public void plate(int x, int y, int w, int h, int radius, int border, int fill) {
                RoundedCorners.draw(graphics, x, y, w, h, radius, border == 0 ? 0 : 1, border, fill);
            }
            public void text(net.minecraft.util.FormattedCharSequence text, int x, int y, int ink,
                             UiPreferences preferences, TextReadabilityPalette palette) {
                UiHudRichText.draw(graphics, font, text, x, y, ink, preferences, palette);
            }
        });
    }

    @SuppressWarnings("unused") // Forge 1.20.1
    public boolean mouseScrolled(double x, double y, double vertical) { return scroll(x, y, vertical); }

    @SuppressWarnings("unused") // NeoForge 1.21.1
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return scroll(x, y, vertical); }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return pageKey(key) || super.keyPressed(key, scanCode, modifiers);
    }

    void confirmDiscard(Component title, Component details, Component discard, Component keep,
                        Runnable confirm, Runnable resume) {
        show(new ConfirmScreen(result -> {
            if (result) confirm.run();
            else {
                show(this);
                resume.run();
            }
        }, title, details, discard, keep));
    }

    void show(Screen screen) { minecraft.setScreen(screen); }

    abstract void paint(Painter painter);
    abstract boolean scroll(double x, double y, double delta);
    abstract boolean pageKey(int key);

    interface Painter {
        void plate(int x, int y, int w, int h, int radius, int border, int fill);
        void text(net.minecraft.util.FormattedCharSequence text, int x, int y, int ink,
                  UiPreferences preferences, TextReadabilityPalette palette);
    }
}
