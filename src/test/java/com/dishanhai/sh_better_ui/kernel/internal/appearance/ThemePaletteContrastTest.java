package com.dishanhai.sh_better_ui.kernel.internal.appearance;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThemePaletteContrastTest {
    @Test void allBuiltinPalettesUseOpaqueCompositedBackdropWithoutDiscardingThemeAlpha() {
        try (var manager = new ThemeManager()) {
            boolean sawTranslucent = false;
            for (var theme : manager.registry().themes().values()) {
                manager.preferences(manager.preferences().withReducedMotion(true).withFixedTheme(theme.id()));
                var palette = UiTheme.fromTokens(manager.snapshot());
                int originalBackground = palette.background(), originalSurface = palette.surface();
                sawTranslucent |= originalBackground >>> 24 != 255;
                int backdrop = ThemePaletteScreen.sampleBackdrop(palette);
                assertEquals(255, backdrop >>> 24, theme.id());
                assertEquals(ContrastPolicy.composite(originalSurface,
                        ContrastPolicy.composite(originalBackground, ThemePaletteScreen.sampleCanvas(palette))), backdrop);
                int text = assertDoesNotThrow(() -> ThemePaletteScreen.sampleActionText(palette), theme.id());
                assertTrue(ContrastPolicy.ratio(text, palette.accent(), backdrop) >= 4.5, theme.id());
                assertEquals(originalBackground, palette.background());
                assertEquals(originalSurface, palette.surface());
            }
            assertTrue(sawTranslucent, "Regression must include actual transparent registered themes");
        }
    }

    @Test void translucentActionAndTextAlsoResolveAgainstActualLayerComposition() {
        for (boolean light : new boolean[]{false, true}) {
            for (int alpha : new int[]{0, 24, 128, 255}) {
                var palette = new UiTheme(light, alpha << 24 | 0x3569ab,
                        (255 - alpha) << 24 | 0x907054, 0x80808080,
                        0x40707070, alpha << 24 | 0x607080, 31);
                int backdrop = ThemePaletteScreen.sampleBackdrop(palette);
                int text = ThemePaletteScreen.sampleActionText(palette);
                assertEquals(255, backdrop >>> 24);
                assertTrue(ContrastPolicy.ratio(text, palette.accent(), backdrop) >= 4.5);
                assertEquals(alpha, palette.background() >>> 24);
            }
        }
    }
}
