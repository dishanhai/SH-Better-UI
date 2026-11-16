package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.theme.MuoniumThemes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegisteredThemeBackgroundTest {
    @Test void customCrystalUsesTheBackgroundChosenInItsConfigurationPage() {
        var settings = ThemeSettings.DEFAULT.withRegisteredTheme(MuoniumThemes.CRYSTAL)
                .withBackgroundEffect("sh_better_ui:muonium_fluid").withHudBackgroundEffect("laserFlow");
        assertTrue(UiTheme.resolve(settings).hudGlass());
        assertEquals("sh_better_ui:muonium_fluid", UiDotBackground.configuredEffect(settings, true));
        assertEquals("laserFlow", settings.hudBackgroundEffect());
    }
    @Test void explicitHudModeKeepsItsIndependentBackgroundSetting() {
        var settings = ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.HUD_GLASS)
                .withBackgroundEffect("dotMatrix").withHudBackgroundEffect("laserFlow");
        assertEquals("laserFlow", UiDotBackground.configuredEffect(settings, true));
        assertEquals("", UiDotBackground.configuredEffect(settings.withHudBackgroundEffect(""), true));
    }
    @Test void switchingMaterialDoesNotChangeCustomBackgroundScope() {
        var settings = ThemeSettings.DEFAULT.withRegisteredTheme(MuoniumThemes.CRYSTAL)
                .withBackgroundEffect("sh_better_ui:image");
        var opaque = settings.withRegisteredTheme(MuoniumThemes.PORCELAIN_INK);
        assertEquals(UiDotBackground.configuredEffect(settings, true), UiDotBackground.configuredEffect(opaque, false));
    }
}
