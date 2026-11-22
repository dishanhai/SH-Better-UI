// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.muoniumplayer.core.screens.ncm.NCMTheme;
import com.muoniumplayer.render.skija.SkijaShapes;

/** Skia bridge for the shared music palette, used by the embedded canvas and its preview. */
public final class EmbeddedMusicGradient {
    public static boolean active() {
        return NCMTheme.getCurrent().gradient() != null
                && !UiPreferences.configured().highContrast();
    }

    public static void paint(NCMTheme.ThemePreset preset, double x, double y,
                             double width, double height, double radius, float alpha) {
        var gradient = preset.gradient();
        if (gradient == null || width <= 0 || height <= 0 || alpha <= 0) return;
        var preferences = UiPreferences.configured();
        var palette = gradient.sample(System.nanoTime() / 1_000_000L,
                preferences.reducedMotion() || preferences.highContrast(), false);
        int opacity = Math.round(alpha * 255) << 24;
        SkijaShapes.roundedRectGradient(x, y, width, height, radius,
                opacity | (palette.color(0, .25) & 0xffffff), opacity | (palette.color(1, .25) & 0xffffff), true);
        SkijaShapes.roundedRectGradient(x, y, width, height, radius, palette.topLeft() & 0xffffff,
                (Math.round(alpha * 178) << 24) | (palette.color(.68, 1) & 0xffffff), false);
    }

    private EmbeddedMusicGradient() {}
}
