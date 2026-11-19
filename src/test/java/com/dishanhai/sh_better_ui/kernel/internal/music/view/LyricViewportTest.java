// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LyricViewportTest {
    @Test void followsSmoothlyAndReducedMotionSettles() {
        var v = new LyricViewport();
        assertEquals(0, v.sample(0, 20, 0, false, false));
        double intermediate = v.sample(1, 20, 50, false, false);
        assertTrue(intermediate > 0 && intermediate < 1);
        assertEquals(1, v.sample(1, 20, 60, true, false));
    }
    @Test void browsingIsBoundedAndTimesOut() {
        var v = new LyricViewport();
        v.scroll(-1, 0, 10, 100);
        assertEquals(0, v.sample(8, 10, 100, true, false));
        assertTrue(v.browsing(200));
        for (int i=0;i<100;i++) v.scroll(1, 8, 10, 200);
        assertEquals(9, v.sample(3, 10, 300, true, false));
        assertEquals(3, v.sample(3, 10, 5200, true, false));
        assertFalse(v.browsing(5200));
    }
    @Test void followResetAndEmptyRemainSafe() {
        var v = new LyricViewport(); v.scroll(1, 2, 10, 0);
        v.follow(); assertEquals(4, v.sample(4, 10, 1, true, false));
        assertEquals(0, v.sample(4, 0, 2, true, false));
        assertEquals(9, v.sample(100, 10, 3, false, false));
        v.reset(); assertEquals(0, v.sample(-1, 10, 4, false, false));
    }
    @Test void keyboardFocusKeepsTargetsStable() {
        var v = new LyricViewport();
        v.sample(2, 10, 0, false, false);
        assertEquals(2, v.sample(3, 10, 100, false, true));
        assertEquals(2, v.sample(9, 10, 120, true, true));
        assertEquals(2, v.sample(9, 10, 150, false, true));
        assertTrue(v.sample(3, 10, 200, false, false) > 2);
    }
    @Test void directBrowsingIsImmediateAndFollowIsContinuous() {
        var v = new LyricViewport();
        v.sample(2, 20, 0, false, false);
        v.browseTo(5, 20, 10);
        assertEquals(5, v.sample(2, 20, 11, false, false));
        v.follow();
        double prior = 5;
        for (int i=1; i<=100; i++) {
            double next = v.sample(2, 20, 11+16L*i, false, false);
            assertTrue(next <= prior && next >= 2); prior = next;
        }
        assertEquals(2, prior, .005);
    }
    @Test void stalledAndReversedClocksCannotExplodeAndResizingClampsFocus() {
        var v = new LyricViewport();
        v.sample(4, 20, 0, false, false);
        double next = v.sample(5, 20, Long.MAX_VALUE, false, false);
        assertTrue(next > 4 && next < 5);
        assertEquals(next, v.sample(5, 20, 0, false, false));
        assertEquals(1, v.sample(0, 2, 1, false, true));
        v.scroll(Integer.MAX_VALUE, Integer.MAX_VALUE, 10, 3);
        assertEquals(9, v.sample(0, 10, 4, false, false));
    }
    @Test void typographyRespectsContrastAndBounds() {
        assertEquals(1f, OnlineLyricButton.lyricScale(true, 0, true));
        assertEquals(1.12f, OnlineLyricButton.lyricScale(true, 0, false));
        assertTrue(OnlineLyricButton.lyricScale(false, Float.NaN, false) >= .9f);
        assertEquals(OnlineLyricButton.lyricScale(false, 2, false), OnlineLyricButton.lyricScale(false, -2, false));
    }
    @Test void seekPreviewOverridesBrowsingAndReturnsImmediatelyToAudibleLine() {
        var view=new LyricViewport();
        view.browseTo(8,12,0);
        assertEquals(3,view.sample(3,12,1,false,true,true));
        assertFalse(view.browsing(1));
        assertEquals(4,view.sample(4,12,2,false,false,true));
        assertEquals(2,view.sample(2,12,3,false,false,false));
        double follow=view.sample(3,12,30,false,false);
        assertTrue(follow>2&&follow<3);
    }
}
