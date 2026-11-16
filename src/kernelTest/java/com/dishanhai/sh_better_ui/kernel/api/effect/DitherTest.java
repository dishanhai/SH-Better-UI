// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.effect;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DitherTest {
    @Test void bayerAndTransparencyMatchReference() {
        var o = new Dither.Options(1, Dither.Mode.BAYER, Dither.ColorMode.GRAYSCALE, false, 1,
                0xff000000, 0xffffffff, List.of(0xff000000, 0xffffffff), 0, 1, 0,
                Dither.Fit.COVER, 0, false, .02f);
        int[] image = new int[16];
        java.util.Arrays.fill(image, 0xff7f7f7f);
        image[15] = 0;
        int[] result = Dither.apply(image, 4, 4, o, 0);
        assertEquals(0xffffffff, result[0]);
        assertEquals(0xff000000, result[1]);
        assertEquals(0xff000000, result[4]);
        assertEquals(0, result[15]);
    }

    @Test void pixelCellsAndCustomColorsAreStable() {
        var o = new Dither.Options(2, Dither.Mode.BAYER, Dither.ColorMode.DUOTONE, true, 1,
                0xff123456, 0xffabcdef, List.of(0xff000000, 0xffffffff), 0, 1, 0,
                Dither.Fit.COVER, .5f, false, 0);
        int[] result = Dither.apply(new int[]{0xff000000, 0xffffffff, 0xffffffff, 0xffffffff}, 2, 2, o, 0);
        assertArrayEquals(new int[]{0xffedcba9, 0xffedcba9, 0xffedcba9, 0xffedcba9}, result);
    }
}
