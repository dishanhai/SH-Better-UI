// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.motion;

import com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader.Pattern;
import com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader.Preset;
import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class LatticeLoaderTest {
    @Test
    void timerFreezesAcrossTerminalUpdatesAndRestarts() {
        var loader = new LatticeLoader(100);
        assertEquals(350, loader.elapsedMs(450));
        loader.setStatus(DONE, 475);
        assertEquals(375, loader.elapsedMs(1_300));
        assertEquals(375, loader.frame(1_300, false).waveMs());
        loader.setStatus(ERROR, 1_400);
        assertEquals(375, loader.elapsedMs(1_600));
        loader.restart(1_700);
        assertEquals(WORKING, loader.status());
        assertEquals(0, loader.elapsedMs(1_700));
        assertEquals(25, loader.elapsedMs(1_725));
        loader.restart(1_800);
        assertEquals(0, loader.elapsedMs(1_800));
    }

    @Test
    void quickTerminalChangeStartsFromTheVisibleBlend() {
        var loader = new LatticeLoader(0);
        loader.setStatus(DONE, 400);
        var midway = loader.frame(450, false);
        assertTrue(midway.opacity(DONE) > 0 && midway.opacity(DONE) < 1);
        loader.setStatus(ERROR, 450);
        var interrupted = loader.frame(450, false);
        assertEquals(midway.opacity(WORKING), interrupted.opacity(WORKING));
        assertEquals(midway.opacity(DONE), interrupted.opacity(DONE));
        assertEquals(0, interrupted.opacity(ERROR));
        assertEquals(1, loader.frame(650, false).opacity(ERROR));
    }

    @Test
    void waveHonorsDelaysAndHolesAndReducedMotionIsStatic() {
        var loader = new LatticeLoader(0);
        var orbit = Preset.ORBIT.pattern(3);
        var start = loader.frame(100, false);
        assertTrue(start.wave(orbit, 0, 90) > 0);
        assertEquals(0, start.wave(orbit, 1, 90));
        assertEquals(0, start.wave(orbit, 4, 90));
        var reduced = loader.frame(650, true);
        assertEquals(reduced.wave(orbit, 0, 90), reduced.wave(orbit, 1, 90));
        assertEquals(0, reduced.wave(orbit, 4, 90));
        loader.setStatus(ERROR, 650);
        var end = loader.frame(650, true);
        assertEquals(1, end.opacity(ERROR));
        assertEquals(1, end.markScale(ERROR));
        assertTrue(end.marked(ERROR, 3, 4));
        assertFalse(end.marked(ERROR, 3, 1));
        assertTrue(end.marked(DONE, 4, 13));
    }

    @Test
    void customPatternCopiesDelaysAndFallbackFitsTheGrid() {
        double[] delays = {0, 1.5, 2, 3, -1, 5, 6, 7, 8};
        var pattern = Pattern.custom(3, delays);
        delays[1] = 99;
        assertEquals(1.5, pattern.delay(1));
        assertEquals(-1, pattern.delay(4));
        assertSame(Preset.ORBIT.pattern(3), Preset.SWEEP.pattern(3));
        assertSame(Preset.SWEEP.pattern(4), Preset.ARROW.pattern(4));
        assertThrows(IllegalArgumentException.class, () -> Pattern.custom(4, delays));
    }
}
