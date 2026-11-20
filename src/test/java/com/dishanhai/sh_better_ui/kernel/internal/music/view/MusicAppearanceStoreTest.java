// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.config.ThemeSettings.Mode;
import com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicAppearanceStore.Preference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MusicAppearanceStoreTest {
    @TempDir Path dir;

    @Test void userChoicesSurviveReopenAndRemainReversible() {
        var file = dir.resolve("config/sh_better_ui/music-appearance.txt");
        var store = new MusicAppearanceStore(file);
        assertEquals(Preference.AUTOMATIC, store.value());
        for (var expected : new Preference[]{Preference.CLASSIC, Preference.LIQUID_GLASS, Preference.AUTOMATIC}) {
            assertEquals(expected, store.value().next());
            assertTrue(store.select(expected));
            assertEquals(expected, store.value());
            assertEquals(expected, new MusicAppearanceStore(file).value());
        }
    }

    @Test void aFailedSaveKeepsTheOldMaterial() throws Exception {
        var file = Files.createDirectory(dir.resolve("unwritable-as-file"));
        Files.writeString(file.resolve("keep"), "original");
        var store = new MusicAppearanceStore(file);
        assertFalse(store.select(Preference.CLASSIC));
        assertEquals(Preference.AUTOMATIC, store.value());
        assertEquals("original", Files.readString(file.resolve("keep")));
        try (var paths = Files.list(dir)) {
            assertEquals(1, paths.count(), "temporary file is cleaned up");
        }
    }

    @Test void invalidOrOversizeSettingsFallBackToAutomatic() throws Exception {
        var file = dir.resolve("appearance");
        Files.writeString(file, "obsolete\n");
        assertEquals(Preference.AUTOMATIC, new MusicAppearanceStore(file).value());
        Files.writeString(file, "classic" + " ".repeat(100));
        assertEquals(Preference.AUTOMATIC, new MusicAppearanceStore(file).value());
    }

    @Test void automaticInheritsThePaletteButExplicitMaterialsAndLaunchLockAreIndependent() {
        assertFalse(MusicAppearance.liquidGlass("", Preference.AUTOMATIC, Mode.LIGHT, false));
        assertTrue(MusicAppearance.liquidGlass("", Preference.LIQUID_GLASS, Mode.LIGHT, false));
        assertFalse(MusicAppearance.liquidGlass("", Preference.CLASSIC, Mode.DARK, true));
        assertFalse(MusicAppearance.liquidGlass("CLASSIC", Preference.LIQUID_GLASS, Mode.LIGHT, false));
        assertFalse(MusicAppearance.locked("mistyped"));
    }
}
