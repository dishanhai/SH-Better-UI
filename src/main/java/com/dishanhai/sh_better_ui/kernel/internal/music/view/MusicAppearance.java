// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.config.ThemeSettings;

/** Music defaults to glass without changing the settings of any other screen. */
final class MusicAppearance {
    private static final String OVERRIDE = System.getProperty("shbui.music.theme", "");

    private MusicAppearance() {}

    static boolean liquidGlass(ThemeSettings.Mode mode, boolean glassPalette) {
        return liquidGlass(OVERRIDE, mode, glassPalette);
    }

    static boolean liquidGlass(String override, ThemeSettings.Mode mode, boolean glassPalette) {
        if ("classic".equalsIgnoreCase(override)) return false;
        return mode == ThemeSettings.Mode.DARK || mode.fixedThemeId() != null || glassPalette;
    }
}
