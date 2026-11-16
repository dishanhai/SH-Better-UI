// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.appearance;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import java.util.Locale;
import java.util.Objects;

/** One isolated RGB field edit. Empty is inheritance, not an explicit copy of its resolved color. */
final class ThemePaletteEdit {
    private final ThemeSettings original;
    private final int index;
    private String accepted, shown;
    ThemePaletteEdit(ThemeSettings original, int index) {
        this.original = Objects.requireNonNull(original);
        if (index < 0 || index > 4) throw new IllegalArgumentException("Unknown palette field");
        this.index = index;
        accepted = shown = values(original)[index];
    }
    String value() { return accepted; }
    ThemeSettings previewTheme() { return replace(original, index, shown); }
    void preview(int rgb) { shown = hex(rgb); }
    void cancelPreview() { shown = accepted; }
    void commit(int rgb) { accepted = shown = hex(rgb); }
    boolean text(String text) {
        if (!ThemeSettings.validColor(text)) return false;
        accepted = shown = values(replace(original, index, text))[index];
        return true;
    }
    void inherit() { accepted = shown = ""; }
    private static String hex(int rgb) { return String.format(Locale.ROOT, "#%06X", rgb & 0xffffff); }
    static String[] values(ThemeSettings theme) {
        return new String[]{theme.background(), theme.surface(), theme.border(), theme.text(), theme.accent()};
    }
    static ThemeSettings replace(ThemeSettings theme, int index, String value) {
        String[] colors = values(theme); colors[index] = value;
        return theme.withPalette(theme.customBase(), colors[0], colors[1], colors[2], colors[3], colors[4]);
    }
}
