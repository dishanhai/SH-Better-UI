// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricReadingScrollTest {
    private final LyricReadingScroll scroll = new LyricReadingScroll();

    @Test void pageStaysAtReadLineThroughPlaybackAndReflowUntilFollow() {
        assertEquals(140, scroll.sample(100, 0, 300, 0, 40, true, 1));
        assertEquals(140, scroll.sample(200, 0, 300, 0, 0, false, 1_000_000_000L));
        assertEquals(155, scroll.sample(250, 0, 315, 15, 0, false, 2_000_000_000L));
        scroll.follow();
        assertEquals(250, scroll.sample(250, 0, 315, 0, 0, false, 2_500_000_000L));
    }

    @Test void returningToPlaybackUsesTheLastScrollTimeEvenWithAPausedPlayhead() {
        assertEquals(140, scroll.sample(100, 0, 300, 0, 40, true, 0));
        assertEquals(160, scroll.sample(100, 0, 300, 0, 20, true, 2_000_000_000L));
        assertEquals(160, scroll.sample(100, 0, 300, 0, 0, false, 4_999_999_999L));
        assertTrue(scroll.browsing());
        assertEquals(100, scroll.sample(100, 0, 300, 0, 0, false, 5_000_000_000L));
        assertFalse(scroll.browsing());
    }

    @Test void aHeldGestureKeepsTheReadingAnchorUntilIdleAgain() {
        assertEquals(140, scroll.sample(100, 0, 300, 0, 40, true, 0));
        scroll.resumeIfIdle(4_000_000_000L, true);
        assertEquals(140, scroll.sample(180, 0, 300, 0, 0, true, 4_000_000_000L));
        scroll.resumeIfIdle(7_000_000_000L, false);
        assertFalse(scroll.browsing());
        assertEquals(230, scroll.sample(230, 0, 300, 0, 0, false, 7_000_000_000L));
    }

    @Test void outwardScrollAtBoundaryDoesNotDetachAndDragStopsAtEnds() {
        assertEquals(0, scroll.sample(0, 0, 100, 0, -40, true, 0));
        assertFalse(scroll.browsing());
        assertEquals(100, scroll.sample(0, 0, 100, 0, 200, true, 1));
        assertEquals(0, scroll.sample(50, 0, 100, 0, -200, true, 2));
    }

    @Test void wheelEasesAndDirectDragStartsAtTheDisplayedPosition() {
        double visible = scroll.sample(100, 0, 300, 0, 80, false, 0);
        assertTrue(visible > 100 && visible < 180);
        assertEquals(visible + 10, scroll.sample(110, 0, 300, 0, 10, true, 16_000_000L));
    }
}
