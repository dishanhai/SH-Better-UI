// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicArtworkStageRender;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.RainbowMusicPalette;

/** A shared music canvas and live preview driven by the same slow color cycle. */
final class MusicRainbowTheme {
    static boolean active(UiFrame ui) {
        return !ui.preferences().highContrast() && MusicAppearance.current() == MusicAppearanceStore.Preference.RAINBOW;
    }

    static boolean darkInk(int ink) {
        return ((ink >> 16 & 255) * 54 + (ink >> 8 & 255) * 183 + (ink & 255) * 19) < 128 * 256;
    }

    static void paint(UiFrame ui, Bounds box, int ink) {
        var colors = RainbowMusicPalette.sample(ui.nowMs(), ui.preferences().reducedMotion() || ui.preferences().highContrast(), darkInk(ink));
        MusicArtworkStageRender.background(ui.graphics(), box, colors, 0, box.width() < 64);
    }

    static void chrome(UiFrame ui, Bounds box, int ink) {
        RoundedCorners.draw(ui.graphics(), box.x(), box.y(), box.width(), box.height(),
                Math.min(10, Math.min(box.width(), box.height()) / 2f), 0, 0,
                darkInk(ink) ? 0x56ffffff : 0x60101924);
    }

    private MusicRainbowTheme() {}
}
