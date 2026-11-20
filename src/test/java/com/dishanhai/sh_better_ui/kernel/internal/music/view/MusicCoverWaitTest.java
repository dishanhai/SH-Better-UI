// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import org.junit.jupiter.api.Test;
import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicCoverWait.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicCoverWaitTest {
    @Test void uncachedCoverUsesDelayedShimmerAndFastRequestsNeverFlash() {
        var wait = new MusicCoverWait();
        assertEquals(Mode.NONE, wait.sample(true, false, 1000, false).mode());
        assertEquals(0f, wait.sample(true, false, 1100, false).opacity());
        assertEquals(Mode.NONE, wait.sample(false, false, 1101, false).mode());
        wait.sample(true, false, 1200, false);
        assertEquals(0f, wait.sample(true, false, 1200 + DELAY_MS, false).opacity());
        var shown = wait.sample(true, false, 1200 + DELAY_MS + ENTER_MS, false);
        assertEquals(Mode.SKELETON, shown.mode());
        assertEquals(1f, shown.opacity());
        assertTrue(shown.phaseMs() > 0);
    }

    @Test void completionFreezesTheCueAndFadesItToIdleInBothModes() {
        for (boolean hasCover : new boolean[]{false, true}) {
            var wait = new MusicCoverWait();
            wait.sample(true, hasCover, 1000, false);
            wait.sample(true, hasCover, 1000 + DELAY_MS, false);
            long turn = 1000 + DELAY_MS + ENTER_MS + 10;
            var shown = wait.sample(true, hasCover, turn, false);
            var exit = wait.sample(false, hasCover, turn + 1, false);
            assertEquals(hasCover ? Mode.BADGE : Mode.SKELETON, exit.mode());
            assertEquals(shown.phaseMs(), exit.phaseMs());
            var faded = wait.sample(false, hasCover, turn + 55, false);
            assertTrue(faded.opacity() > 0 && faded.opacity() < shown.opacity());
            assertEquals(shown.phaseMs(), faded.phaseMs());
            assertEquals(Mode.NONE, wait.sample(false, hasCover, turn + EXIT_MS + 1, false).mode());
            assertEquals(0f, wait.sample(false, hasCover, turn + 2000, false).opacity());
        }
    }

    @Test void reducedMotionShowsAStaticCueThenRemovesItImmediately() {
        var wait = new MusicCoverWait();
        wait.sample(true, false, 1000, true);
        var shown = wait.sample(true, false, 1000 + DELAY_MS, true);
        assertEquals(Mode.SKELETON, shown.mode());
        assertEquals(1f, shown.opacity());
        assertEquals(shown, wait.sample(true, false, 3000, true));
        assertEquals(Mode.NONE, wait.sample(false, false, 3001, true).mode());
    }
}
