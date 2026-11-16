package com.dishanhai.sh_better_ui.client.gui.text;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarqueeMotionTest {
    private MarqueeMotion.Frame at(MarqueeMotion m, long time) {
        return m.sample("title", 100, 50, time, false, false);
    }
    @Test void holdsThenLoopsWithoutFrameRateDependence() {
        var slow = new MarqueeMotion(20, 1000, 20);
        var fast = new MarqueeMotion(20, 1000, 20);
        at(slow, 0); at(fast, 0);
        for (int t = 10; t <= 3000; t += 10) at(fast, t);
        assertEquals(at(fast, 3000), at(slow, 3000));
        assertEquals(-40, at(slow, 3000).offset(), 0.0001);
        assertEquals(80, at(slow, 3000).repeatOffset(), 0.0001);
        assertEquals(0, at(slow, 7000).offset());
    }
    @Test void pauseDoesNotAccumulateHiddenTime() {
        var m = new MarqueeMotion(20, 1000, 20); at(m, 0); at(m, 2000);
        assertEquals(-20, m.sample("title", 100, 50, 20000, true, false).offset());
        assertEquals(-40, at(m, 21000).offset());
    }
    @Test void changedContentOrWidthRestartsHold() {
        var m = new MarqueeMotion(20, 1000, 20); at(m, 0); at(m, 2000);
        assertEquals(0, m.sample("new", 100, 50, 2001, false, false).offset());
        m.sample("new", 100, 50, 4001, false, false);
        assertEquals(0, m.sample("new", 100, 60, 4002, false, false).offset());
    }
    @Test void reducedMotionAndFitAreStatic() {
        var m = new MarqueeMotion(); at(m, 0); at(m, 5000);
        var frame = m.sample("title", 100, 50, 6000, false, true);
        assertTrue(frame.overflowing()); assertFalse(frame.moving()); assertEquals(0, frame.offset());
        assertFalse(m.sample("title", 100, 100, 7000, false, false).overflowing());
    }
    @Test void invalidGeometryAndBackwardsClockAreSafe() {
        var m = new MarqueeMotion(); at(m, 10); at(m, 5000);
        assertEquals(0, at(m, 9).offset());
        assertFalse(m.sample("x", Double.NaN, 50, 10, false, false).overflowing());
        assertFalse(m.sample("x", 100, -1, 11, false, false).overflowing());
        assertEquals(0, at(m, 100000).offset());
    }
    @Test void explicitResetRestoresHold() {
        var m = new MarqueeMotion(); at(m, 0); at(m, 5000); m.reset();
        assertEquals(0, at(m, 6000).offset());
    }
    @Test void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new MarqueeMotion(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MarqueeMotion(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MarqueeMotion(1, 1, Double.NaN));
    }
}
