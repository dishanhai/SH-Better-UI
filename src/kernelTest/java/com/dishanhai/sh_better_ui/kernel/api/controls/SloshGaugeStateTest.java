// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SloshGaugeStateTest {
    @Test
    void pressAwayFromMarkerDragsAndReleasesOutsideAtBoundary() {
        var gauge = new SloshGaugeState(60, SloshGaugeState.Motion.DEFAULT);
        assertTrue(gauge.press(20, 200));
        assertEquals(90, gauge.value());
        assertTrue(gauge.drag(150, 200));
        assertEquals(25, gauge.value());
        assertTrue(gauge.release(-100, 200));
        assertEquals(100, gauge.value());
        assertFalse(gauge.dragging());
        assertFalse(gauge.drag(200, 200));
    }

    @Test
    void grabbingMarkerPreservesOffsetAndCancelRestoresStart() {
        var gauge = new SloshGaugeState(60, SloshGaugeState.Motion.DEFAULT);
        assertTrue(gauge.press(84, 200));
        assertEquals(60, gauge.value());
        assertTrue(gauge.drag(104, 200));
        assertEquals(50, gauge.value());
        assertTrue(gauge.cancel());
        assertEquals(60, gauge.value());
        assertFalse(gauge.cancel());
    }

    @Test
    void liquidLeansAndSettlesAfterLevelChange() {
        var gauge = new SloshGaugeState(25, SloshGaugeState.Motion.DEFAULT);
        gauge.setValue(70);
        var first = gauge.sample(1, false);
        assertTrue(first.level() > 25 && first.level() < 70);
        assertTrue(first.topAt(0, 88, 180) > first.topAt(88, 88, 180));
        var settled = first;
        for (int frame = 1; frame <= 240; frame++) {
            settled = gauge.sample(1 + frame * 16_666_667L, false);
            assertTrue(settled.level() >= 0 && settled.level() <= 100);
        }
        assertEquals(new SloshGaugeState.Surface(70, 0), settled);
    }

    @Test
    void reducedMotionAndZeroViscositySnapToFlatClampedLevel() {
        var gauge = new SloshGaugeState(60, SloshGaugeState.Motion.DEFAULT);
        gauge.setValue(120);
        assertEquals(new SloshGaugeState.Surface(100, 0), gauge.sample(1, true));
        var rigid = new SloshGaugeState(60, new SloshGaugeState.Motion(0, 1, 1));
        rigid.setValue(-1);
        assertEquals(new SloshGaugeState.Surface(0, 0), rigid.sample(1, false));
    }
}
