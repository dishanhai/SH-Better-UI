// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicAppearanceStore.Preference;
import java.nio.file.Path;

/** Resolves the music material independently of the user’s global palette. */
final class MusicAppearance {
    private static final String OVERRIDE = System.getProperty("shbui.music.theme", "");

    private static final class Stored {
        static final MusicAppearanceStore VALUE = new MusicAppearanceStore(
                Path.of("config", "sh_better_ui", "music-appearance.txt"));
    }

    private MusicAppearance() {}

    static boolean locked() { return locked(OVERRIDE); }
    static boolean locked(String override) { return "classic".equalsIgnoreCase(override); }
    static Preference current() { return locked() ? Preference.CLASSIC : Stored.VALUE.value(); }
    static boolean select(Preference preference) { return !locked() && Stored.VALUE.select(preference); }

    static boolean liquidGlass(ThemeSettings.Mode mode, boolean glassPalette) {
        return liquidGlass(OVERRIDE, current(), mode, glassPalette);
    }

    static boolean liquidGlass(String override, ThemeSettings.Mode mode, boolean glassPalette) {
        return liquidGlass(override, Preference.AUTOMATIC, mode, glassPalette);
    }

    static boolean liquidGlass(String override, Preference preference, ThemeSettings.Mode mode, boolean glassPalette) {
        if (locked(override)) return false;
        return switch (preference) {
            case CLASSIC, RAINBOW -> false;
            case LIQUID_GLASS -> true;
            case AUTOMATIC -> mode == ThemeSettings.Mode.DARK || mode.fixedThemeId() != null || glassPalette;
        };
    }
}
