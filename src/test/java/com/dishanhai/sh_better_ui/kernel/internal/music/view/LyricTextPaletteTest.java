// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LyricTextPaletteTest {
    @Test void distantTextNeverLosesAlphaAndOpaqueThemesMeetContrast() {
        for (int bg : new int[]{0xff151c26, 0xfff8fafc, 0xff777777}) {
            for (int ink : new int[]{0xffeeeeee, 0xff172334, 0x11888888}) {
                var p = LyricTextPalette.sample(ink, 0x229acdff, bg, 9, false);
                assertEquals(255, p.text() >>> 24); assertEquals(255, p.active() >>> 24);
                assertTrue(LyricTextPalette.contrast(p.text(), bg) >= 4.5);
                assertTrue(LyricTextPalette.contrast(p.active(), bg) >= 4.5);
                assertEquals(0, p.protection());
            }
        }
    }
    @Test void highContrastCanProtectAnOtherwiseImpossibleMidGrayBackground() {
        var p = LyricTextPalette.sample(0xffeeeeee, 0xff888888, 0xff777777, 2, true);
        assertEquals(255, p.protection() >>> 24);
        assertTrue(LyricTextPalette.contrast(p.text(), p.protection()) >= 7);
        assertTrue(LyricTextPalette.contrast(p.active(), p.protection()) >= 7);
    }
    @Test void transparentThemesProtectAgainstEitherUnknownBackdropExtreme() {
        for (int ink : new int[]{0xffeeeeee, 0xff172334}) {
            for (boolean high : new boolean[]{false, true}) {
                var p = LyricTextPalette.sample(ink, 0xff8899aa, 0x00151c26, 7, high);
                assertTrue(p.shadow()); assertTrue(p.protection() >>> 24 >= 224);
                for (int world : new int[]{0xff000000, 0xffffffff}) {
                    int local = LyricTextPalette.over(p.protection(), world);
                    assertTrue(LyricTextPalette.contrast(p.text(), local) >= (high ? 7 : 4.5));
                    assertTrue(LyricTextPalette.contrast(p.active(), local) >= (high ? 7 : 4.5));
                }
            }
        }
    }
}
