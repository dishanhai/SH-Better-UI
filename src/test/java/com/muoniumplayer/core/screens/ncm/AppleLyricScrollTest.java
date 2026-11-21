package com.muoniumplayer.core.screens.ncm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppleLyricScrollTest {
    @Test
    void wheelAccumulatesNotchesAndFollowsAtTheThreeSecondDeadline() {
        var scroll = new AppleLyricScroll();
        scroll.wheel(-240, 150, 1);
        assertEquals(226, scroll.target(160, 0, 500, 2));
        scroll.wheel(120, 170, 500);
        assertEquals(188, scroll.target(180, 0, 500, AppleLyricScroll.FOLLOW_DELAY + 499));
        assertEquals(180, scroll.target(180, 0, 500, AppleLyricScroll.FOLLOW_DELAY + 500));
    }

    @Test
    void dragTakesOwnershipOnlyOnTheVerticalAxisAndHoldsUntilRelease() {
        var scroll = new AppleLyricScroll();
        scroll.press(10, 100, 8);
        assertFalse(scroll.move(11, 107, 50, 1));
        assertTrue(scroll.move(12, 88, 50, 2));
        assertTrue(scroll.move(12, 78, 57, 3));
        assertEquals(72, scroll.target(90, 0, 500, 8_000_000_000L));
        scroll.end(8_000_000_000L);
        assertFalse(scroll.ownsPress());
        assertEquals(90, scroll.target(90, 0, 500, 11_000_000_000L));

        scroll.press(10, 100, 8);
        assertFalse(scroll.move(20, 99, 70, 12_000_000_000L));
        assertFalse(scroll.move(20, 70, 70, 13_000_000_000L));
    }

    @Test
    void rebasingPreservesBrowsingAndBoundsDoNotAccumulateOverscroll() {
        var scroll = new AppleLyricScroll();
        scroll.wheel(-120, 70, 1);
        scroll.rebase(100);
        assertEquals(208, scroll.target(154, 0, 210, 2));
        scroll.wheel(-360, 208, 3);
        assertEquals(210, scroll.target(156, 0, 210, 4));
        scroll.wheel(120, 209, 5);
        assertEquals(172, scroll.target(158, 0, 210, 6));
    }

    @Test
    void firstDragAfterDeadlineUsesTheCurrentCameraRatherThanTheOldDestination() {
        var scroll = new AppleLyricScroll();
        scroll.wheel(-120, 100, 1);
        scroll.press(10, 100, 8);
        assertTrue(scroll.move(10, 90, 110, AppleLyricScroll.FOLLOW_DELAY + 1));
        assertEquals(120, scroll.target(120, 0, 500, AppleLyricScroll.FOLLOW_DELAY + 2));
    }
}
