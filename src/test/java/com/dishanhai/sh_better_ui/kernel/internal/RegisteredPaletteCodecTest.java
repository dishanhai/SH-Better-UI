package com.dishanhai.sh_better_ui.kernel.internal;
import com.dishanhai.sh_better_ui.config.*;
import com.dishanhai.sh_better_ui.theme.MuoniumThemes;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegisteredPaletteCodecTest {
    @Test void legacyJsonDefaultsAndRegisteredArchiveRoundTrip() {
        var original = ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM)
                .withPalette(ThemeSettings.Base.LIGHT, "112233", "", "", "", "abcdef");
        var next = original.withRegisteredTheme(MuoniumThemes.CRYSTAL)
                .withPalette(ThemeSettings.Base.DARK, "", "334455", "", "", "");
        var encoded = ClientSettingsCodec.CODEC.encode(ClientUiSettings.DEFAULT.withTheme(next));
        var decoded = ClientSettingsCodec.CODEC.decode(encoded).theme();
        assertEquals(next, decoded);
        var restored = decoded.withRegisteredTheme("");
        assertEquals(original.background(), restored.background()); assertEquals(original.accent(), restored.accent());
        assertEquals(original.customBase(), restored.customBase());
        var legacy = JsonParser.parseString(ClientSettingsCodec.CODEC.encode(ClientUiSettings.DEFAULT.withTheme(original))).getAsJsonObject();
        legacy.getAsJsonObject("theme").remove("registeredThemeId"); legacy.getAsJsonObject("theme").remove("paletteArchive");
        var old = ClientSettingsCodec.CODEC.decode(legacy.toString()).theme();
        assertEquals("", old.registeredThemeId()); assertEquals(ThemePaletteArchive.EMPTY, old.paletteArchive());
        assertEquals(original.background(), old.background());
    }
    @Test void archiveRejectsInvalidIdsColorsAndOversizedInput() {
        assertFalse(ThemePaletteArchive.valid("{\"bad id\":{}}"));
        assertFalse(ThemePaletteArchive.valid("x".repeat(ThemePaletteArchive.MAX_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> ThemePaletteArchive.EMPTY.with("test:a",
                new ThemePaletteArchive.Palette(ThemeSettings.Base.DARK, "#80112233", "", "", "", "")));
    }
    @Test void eachThemeKeepsItsOwnColorsAndResetDoesNotClearOtherSlots() {
        var a = ThemeSettings.DEFAULT.withRegisteredTheme(MuoniumThemes.AURORA_FROST)
                .withPalette(ThemeSettings.Base.DARK, "", "112233", "", "", "");
        var b = a.withRegisteredTheme(MuoniumThemes.CRYSTAL).withPalette(ThemeSettings.Base.DARK, "", "445566", "", "", "");
        assertEquals("#112233", b.withRegisteredTheme(MuoniumThemes.AURORA_FROST).surface());
        var resetB = b.withPalette(ThemeSettings.Base.DARK, "", "", "", "", "");
        assertEquals("#112233", resetB.withRegisteredTheme(MuoniumThemes.AURORA_FROST).surface());
        assertEquals("", resetB.withRegisteredTheme(MuoniumThemes.AURORA_FROST).withRegisteredTheme(MuoniumThemes.CRYSTAL).surface());
        assertEquals(MuoniumThemes.CRYSTAL, b.withMode(ThemeSettings.Mode.DARK).withMode(ThemeSettings.Mode.CUSTOM).registeredThemeId());
    }
    @Test void platformConfigEntriesPersistSelectionAndArchive() throws Exception {
        for (String path : new String[]{"src/main/java/com/dishanhai/sh_better_ui/ShBetterUi.java", "neoforge-1.21.1/src/main/java/com/dishanhai/sh_better_ui/ShBetterUi.java"}) {
            String source = java.nio.file.Files.readString(java.nio.file.Path.of(path));
            assertTrue(source.contains("builder.define(\"themeRegisteredId\""));
            assertTrue(source.contains("builder.define(\"themePaletteArchive\""));
            assertTrue(source.contains("new ThemePaletteArchive(paletteArchive.get())"));
            assertTrue(source.contains("updatedUi.set(\"themeRegisteredId\", theme.registeredThemeId())"));
            assertTrue(source.contains("updatedUi.set(\"themePaletteArchive\", theme.paletteArchive().json())"));
        }
    }
    @Test void browsingEmptyPalettesNeverConsumesTheArchiveLimit() {
        var settings = ThemeSettings.DEFAULT;
        for (int i = 0; i < 512; i++) settings = settings.withRegisteredTheme("test:theme_" + i);
        assertEquals(ThemePaletteArchive.EMPTY, settings.paletteArchive());
        var archive = ThemePaletteArchive.EMPTY.with("test:one",
                new ThemePaletteArchive.Palette(ThemeSettings.Base.DARK, "112233", "", "", "", ""));
        assertEquals(ThemePaletteArchive.EMPTY, archive.with("test:one", ThemePaletteArchive.Palette.empty()));
        var light = new ThemePaletteArchive.Palette(ThemeSettings.Base.LIGHT, "", "", "", "", "");
        assertEquals(light, ThemePaletteArchive.EMPTY.with("", light).get(""));
    }
}
