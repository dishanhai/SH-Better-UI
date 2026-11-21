package com.muoniumplayer.core.screens.ncm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppleLyricEffectsTest {
    @Test
    void onlyFutureUnfocusedLinesAreBlurred() {
        assertEquals(0f, AppleLyricEffects.upcomingBlur(false, 900, 0, 0));
        assertEquals(0f, AppleLyricEffects.upcomingBlur(true, 0, 0, 0));
        assertEquals(0f, AppleLyricEffects.upcomingBlur(true, -250, 0, 0));
        assertEquals(0f, AppleLyricEffects.upcomingBlur(true, 900, 1, 0));
        assertEquals(0f, AppleLyricEffects.upcomingBlur(true, 900, 0, 1));
        assertEquals(1f, AppleLyricEffects.upcomingBlur(true, 900, 0, 0));
        assertEquals(.5f, AppleLyricEffects.upcomingBlur(true, 240, 0, 0));
        assertTrue(AppleLyricEffects.upcomingBlur(true, 120, 0, 0)
                < AppleLyricEffects.upcomingBlur(true, 240, 0, 0));
    }

    @Test
    void coverPaletteStaysDarkAndRetainsNeutralOrWarmTones() {
        int white = AppleLyricEffects.backdropColor(new int[]{0xffffff}, 2);
        assertEquals(0xff616161, white);
        int warm = AppleLyricEffects.backdropColor(new int[]{0xe07321}, 0);
        assertTrue(((warm >> 16) & 255) > (warm & 255));
        assertNotEquals(AppleLyricEffects.backdropColor(null, 0), AppleLyricEffects.backdropColor(null, 1));
        assertEquals(0xff405060, AppleLyricEffects.mix(0xff204060, 0xff606060, .5));
    }
}
