// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicPlayerReviewRegressionTest {
    @Test void highContrastGlassUsesMusicInkRatherThanGlobalLightMode() {
        int darkPlate = MusicGlassFallback.fill(true, false);
        int lightPlate = MusicGlassFallback.fill(true, true);
        assertTrue(ContrastPolicy.ratio(0xfff5f5f7, darkPlate, 0xffe8eef5) >= ContrastPolicy.HIGH_CONTRAST_RATIO);
        assertTrue(ContrastPolicy.ratio(0xffb4b4ba, darkPlate, 0xffe8eef5) >= ContrastPolicy.HIGH_CONTRAST_RATIO);
        assertTrue(ContrastPolicy.ratio(0xff172334, lightPlate, 0xff131415) >= ContrastPolicy.HIGH_CONTRAST_RATIO);
        assertEquals(0xffffffff, MusicGlassFallback.edge(true, false));
        assertEquals(0xff000000, MusicGlassFallback.edge(true, true));
    }

    @Test void minimumWorkspaceFitsAllOfMuteBelowTheSlider() {
        var panel = MusicFrontendLayout.compute(new Bounds(0, 0, 208, 240),
                MusicFrontendLayout.Route.SETTINGS, false).body();
        var slider = MusicSoundLayout.slider(panel);
        var rows = MusicSoundLayout.rows(panel, 46, true);
        assertTrue(rows.y() > slider.bottom());
        assertTrue(rows.bottom() <= panel.bottom());
        assertTrue(rows.height() >= 21, "all of the mute row must share the clickable clip");
        var standard = MusicSoundLayout.rows(panel, 100, false);
        assertEquals(panel.bottom()-22, standard.bottom(), "other settings still reserve the footer");
    }
}
