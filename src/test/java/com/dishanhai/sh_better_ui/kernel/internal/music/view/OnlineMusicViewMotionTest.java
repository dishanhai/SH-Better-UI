// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OnlineMusicViewMotionTest {
    @Test void playingChangesSmoothlyAndCanReverseWithoutJump() {
        var m = new OnlineMusicViewMotion();
        assertEquals(0f, m.sample(0, false, true).activity());
        float rising = m.sample(60, false, true).activity();
        assertTrue(rising > 0 && rising < 1);
        assertEquals(rising, m.sample(60, false, false).activity());
        assertTrue(m.sample(120, false, false).activity() < rising);
    }
    @Test void repeatedPulsesDoNotJumpAndEventuallySettle() {
        var m = new OnlineMusicViewMotion(); m.pulse(0);
        float prior = m.sample(70, false, false).emphasis();
        assertTrue(prior > 0 && prior < 1);
        m.pulse(70); assertEquals(prior, m.sample(70, false, false).emphasis());
        for (int t = 100; t <= 5000; t += 100) m.sample(t, false, false);
        assertEquals(0f, m.sample(5000, false, false).emphasis());
    }
    @Test void backwardsClockDoesNotAnimateTwice() {
        var m = new OnlineMusicViewMotion(); m.pulse(100);
        var frame = m.sample(200, false, true);
        assertEquals(frame, m.sample(150, false, true));
        assertEquals(frame, m.sample(200, false, true));
    }
    @Test void reducedMotionSkipsPulseAndResolvesPlaybackImmediately() {
        var m = new OnlineMusicViewMotion(); m.pulse(0);
        assertEquals(new OnlineMusicViewMotion.Frame(1, 0), m.sample(20, true, true));
        assertEquals(new OnlineMusicViewMotion.Frame(1, 0), m.sample(80, false, true));
        assertEquals(new OnlineMusicViewMotion.Frame(0, 0), m.sample(90, true, false));
    }
    @Test void closedMotionCannotRestart() {
        var m = new OnlineMusicViewMotion(); m.sample(0, false, true); m.pulse(1); m.close(); m.close();
        m.pulse(400); assertEquals(new OnlineMusicViewMotion.Frame(0, 0), m.sample(500, false, true));
    }
    @Test void adjacentLargeTimestampsStillAdvance() {
        var m = new OnlineMusicViewMotion(); m.sample(Long.MAX_VALUE - 1, false, true);
        assertTrue(m.sample(Long.MAX_VALUE, false, true).activity() > 0);
    }
    @Test void extremeClocksRemainFiniteAndBounded() {
        var m = new OnlineMusicViewMotion(); m.sample(Long.MIN_VALUE, false, true); m.pulse(Long.MIN_VALUE);
        var f = m.sample(Long.MAX_VALUE, false, true);
        assertTrue(Float.isFinite(f.activity()) && f.activity() >= 0 && f.activity() <= 1);
        assertTrue(Float.isFinite(f.emphasis()) && f.emphasis() >= 0 && f.emphasis() <= 1);
    }
}
