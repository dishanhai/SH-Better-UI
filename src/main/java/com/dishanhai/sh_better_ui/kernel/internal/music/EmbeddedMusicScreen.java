// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.muoniumplayer.api.features.ExtensionScreen;
import com.muoniumplayer.core.settings.PlayerAppearance;
import net.minecraft.client.gui.GuiGraphics;

/** Composes the UUK background before the player's Skija layer. */
public abstract class EmbeddedMusicScreen extends ExtensionScreen {
    private final UiDotBackground themeBackground = new UiDotBackground();
    private boolean themeBackdropVisible;

    public final boolean hasThemeBackdrop() {
        return themeBackdropVisible;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        var preferences = UiPreferences.configured();
        var theme = ThemeEffects.settings().theme();
        themeBackdropVisible = PlayerAppearance.useShbuiTheme() && !preferences.highContrast()
                && !EmbeddedMusicGradient.active() && theme.mode() == ThemeSettings.Mode.CUSTOM
                && ShBetterUiApi.backgrounds().find(theme.backgroundEffect()).isPresent();
        if (themeBackdropVisible) {
            themeBackground.renderThemed(this, graphics, width, height, preferences, false);
            graphics.flush();
        } else {
            themeBackground.reset();
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        themeBackdropVisible = false;
        themeBackground.reset();
        super.removed();
    }
}
