// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;

public final class WelcomeShortcutNoticeRenderer {
    private WelcomeShortcutNoticeRenderer() {}

    public static void draw(GuiGraphicsExtractor graphics, Screen screen, double mouseX, double mouseY) {
        if (!WelcomeShortcutNotice.pending()) return;
        var minecraft = Minecraft.getInstance();
        if (!visibleIn(minecraft, screen)) return;
        var window = minecraft.getWindow();
        WelcomeShortcutNotice.draw(minecraft.font, window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                screen, mouseX, mouseY, UiPreferences.configured().highContrast(),
                (x, y, width, height, radius, stroke, border, fill) ->
                        RoundedCorners.draw(graphics, x, y, width, height, radius, stroke, border, fill),
                graphics::fill, graphics::text, minecraft.getNarrator()::saySystemNow);
    }

    static boolean visibleIn(Minecraft client, Screen screen) {
        return screen == NativePort.screen(client) && client.level != null && !NativePort.hasOverlay(client)
                && !(screen instanceof LevelLoadingScreen);
    }
}
