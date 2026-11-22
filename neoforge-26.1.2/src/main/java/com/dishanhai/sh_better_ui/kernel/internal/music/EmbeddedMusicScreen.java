// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import com.dishanhai.sh_better_ui.client.gui.scaled.NativeAppearanceBackground;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.muoniumplayer.api.features.ExtensionScreen;
import com.muoniumplayer.core.settings.PlayerAppearance;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Extracts the UUK background below the player's Skija composite. */
public abstract class EmbeddedMusicScreen extends ExtensionScreen {
    private boolean themeBackdropVisible;

    public final boolean hasThemeBackdrop() {
        return themeBackdropVisible;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var preferences = UiPreferences.configured();
        var theme = ControlPanelSettings.current().theme();
        String effect = theme.backgroundEffect();
        themeBackdropVisible = PlayerAppearance.useShbuiTheme() && !preferences.highContrast()
                && !EmbeddedMusicGradient.active() && NativeAppearanceBackground.supported(effect)
                && !NativeAppearanceBackground.isBackdrop(effect);
        if (themeBackdropVisible) {
            NativeAppearanceBackground.draw(graphics, width, height, theme, preferences, System.nanoTime() / 1_000_000L);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        themeBackdropVisible = false;
        super.removed();
    }
}
