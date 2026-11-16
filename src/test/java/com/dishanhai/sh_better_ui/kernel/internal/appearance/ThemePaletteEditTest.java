package com.dishanhai.sh_better_ui.kernel.internal.appearance;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThemePaletteEditTest {
    private static final ThemeSettings ORIGINAL = ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM);
    @Test void previewCancellationPreservesEmptyInheritanceExactly() {
        for (int i = 0; i < 5; i++) {
            var edit = new ThemePaletteEdit(ORIGINAL, i);
            edit.preview(0x12345678);
            assertEquals("", edit.value());
            assertEquals("#345678", ThemePaletteEdit.values(edit.previewTheme())[i]);
            edit.cancelPreview();
            assertEquals(ORIGINAL, edit.previewTheme());
        }
    }
    @Test void validHexAndRgbCommitAreIsolatedUntilCallerAccepts() {
        var edit = new ThemePaletteEdit(ORIGINAL, 4);
        assertTrue(edit.text("aBcDeF")); assertEquals("#ABCDEF", edit.value());
        assertFalse(edit.text("#12345")); assertEquals("#ABCDEF", edit.value());
        edit.preview(0x00112233); assertEquals("#ABCDEF", edit.value());
        edit.commit(0x00112233); assertEquals("#112233", edit.value());
        assertEquals("", ORIGINAL.accent());
        edit.inherit(); assertEquals("", edit.value()); assertEquals(ORIGINAL, edit.previewTheme());
    }
    @Test void editingOneFieldPreservesBaseEffectsAndOtherColors() {
        var theme = ORIGINAL.withPalette(ThemeSettings.Base.LIGHT, "123456", "234567", "345678", "456789", "567890")
                .withBackgroundEffect("blackHole");
        for (int i = 0; i < 5; i++) {
            var edit = new ThemePaletteEdit(theme, i); edit.commit(0xffabcdef);
            var next = edit.previewTheme();
            assertEquals(theme.customBase(), next.customBase());
            assertEquals(theme.effects(), next.effects());
            assertEquals(theme.backgroundEffect(), next.backgroundEffect());
            for (int j = 0; j < 5; j++) assertEquals(i == j ? "#ABCDEF" : ThemePaletteEdit.values(theme)[j], ThemePaletteEdit.values(next)[j]);
        }
    }
    @Test void openingBuiltInModeKeepsOldCustomPaletteArchivedAndUsesActualTheme() {
        var custom = ORIGINAL.withPalette(ThemeSettings.Base.LIGHT, "123456", "", "", "", "ABCDEF");
        for (var mode : new ThemeSettings.Mode[]{ThemeSettings.Mode.DARK, ThemeSettings.Mode.LIGHT,
                ThemeSettings.Mode.LIQUID_GLASS, ThemeSettings.Mode.HUD_GLASS}) {
            var seed = ThemeCustomizationScreen.initialDraft(custom.withMode(mode));
            assertEquals(ThemeSettings.Mode.CUSTOM, seed.mode());
            assertFalse(seed.registeredThemeId().isEmpty());
            assertEquals("", seed.background());
            var restored = seed.withRegisteredTheme("");
            assertEquals(custom.background(), restored.background());
            assertEquals(custom.accent(), restored.accent());
            assertEquals(custom.customBase(), restored.customBase());
        }
        assertSame(custom, ThemeCustomizationScreen.initialDraft(custom));
    }

    @Test void registeredThemeInheritanceIsNotFlattenedToRgb() {
        var selected = ORIGINAL.withRegisteredTheme(com.dishanhai.sh_better_ui.theme.MuoniumThemes.AURORA_FROST);
        var edit = new ThemePaletteEdit(selected, 1);
        edit.preview(0xff654321); edit.cancelPreview();
        assertEquals(selected, edit.previewTheme());
        edit.commit(0xff123456); edit.inherit();
        assertEquals("", edit.value());
        assertEquals(selected.registeredThemeId(), edit.previewTheme().registeredThemeId());
        assertEquals(selected.paletteArchive(), edit.previewTheme().paletteArchive());
    }

    @Test void legacyCustomDynamicSeedUsesActiveThemeWithoutMutatingInputOrLosingCustom() {
        var a = com.dishanhai.sh_better_ui.theme.MuoniumThemes.AURORA_FROST;
        var b = com.dishanhai.sh_better_ui.theme.MuoniumThemes.CRYSTAL;
        var custom = ORIGINAL.withPalette(ThemeSettings.Base.LIGHT, "112233", "", "", "", "445566");
        var settings = com.dishanhai.sh_better_ui.config.ClientUiSettings.DEFAULT.withTheme(custom)
                .withDynamicTheme(new com.dishanhai.sh_better_ui.config.DynamicThemeSettings(a, 100, false));
        try (var manager = new com.dishanhai.sh_better_ui.theme.ThemeManager()) {
            java.util.function.Predicate<String> exists = id -> manager.registry().find(id).isPresent();
            var seed = ThemeCustomizationScreen.initialDraft(settings, exists);
            assertEquals(a, seed.registeredThemeId()); assertEquals("", seed.background());
            assertSame(custom, settings.theme()); assertEquals("#112233", settings.theme().background());
            assertEquals(custom.background(), seed.withRegisteredTheme("").background());
            var registered = settings.withTheme(custom.withRegisteredTheme(b));
            assertEquals(b, ThemeCustomizationScreen.initialDraft(registered, exists).registeredThemeId());
            for (var mode : new ThemeSettings.Mode[]{ThemeSettings.Mode.DARK, ThemeSettings.Mode.LIGHT}) {
                var dormant = registered.theme().withPalette(ThemeSettings.Base.DARK, "AABBCC", "", "", "", "")
                        .withMode(mode);
                var active = ThemeCustomizationScreen.initialDraft(settings.withTheme(dormant), exists);
                assertEquals(a, active.registeredThemeId());
                assertEquals("#AABBCC", active.withRegisteredTheme(b).background());
                assertEquals(b, dormant.registeredThemeId());
                assertEquals(mode, dormant.mode());
            }
            var glass = settings.withTheme(custom.withMode(ThemeSettings.Mode.LIQUID_GLASS));
            assertEquals(com.dishanhai.sh_better_ui.theme.BuiltinThemes.LIQUID_GLASS,
                    ThemeCustomizationScreen.initialDraft(glass, exists).registeredThemeId());
            assertSame(custom, ThemeCustomizationScreen.initialDraft(settings, id -> false));
        }
    }

}
