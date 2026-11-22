// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.config.ThemeSettings.Mode;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.RainbowMusicPalette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicAppearanceTest {
    @Test void defaultsToGlassWithoutReplacingOtherPalettes() {
        assertTrue(MusicAppearance.liquidGlass("", Mode.DARK, false));
        assertTrue(MusicAppearance.liquidGlass("", Mode.LIQUID_GLASS, true));
        assertTrue(MusicAppearance.liquidGlass("", Mode.HUD_GLASS, true));
        assertTrue(MusicAppearance.liquidGlass("", Mode.CUSTOM, true));
        assertFalse(MusicAppearance.liquidGlass("", Mode.CUSTOM, false));
        assertFalse(MusicAppearance.liquidGlass("", Mode.LIGHT, false));
    }

    @Test void classicIsAnExplicitOptOutEvenForANativeGlassPalette() {
        assertFalse(MusicAppearance.liquidGlass("classic", Mode.DARK, false));
        assertFalse(MusicAppearance.liquidGlass("CLASSIC", Mode.LIQUID_GLASS, true));
        assertTrue(MusicAppearance.liquidGlass("typo", Mode.DARK, false));
    }

    @Test void rainbowLoopsAndRespectsReducedMotion() {
        var beginning = RainbowMusicPalette.sample(0, false, false);
        assertNotEquals(beginning, RainbowMusicPalette.sample(9_000, false, false));
        assertEquals(beginning, RainbowMusicPalette.sample(36_000, false, false));
        assertEquals(beginning, RainbowMusicPalette.sample(9_000, true, false));
        assertNotEquals(beginning, RainbowMusicPalette.sample(0, true, true));
    }
}
