package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicIslandCapsuleStateTest {
    @Test void playbackPauseTimeoutAndResumeAreBasedOnActualSession() {
        var state = new MusicIslandCapsuleState();
        state.observe(null, false, 10); assertFalse(state.active());
        state.observe("a", true, 20); assertTrue(state.active());
        state.observe("a", false, 30); assertTrue(state.active());
        state.observe("a", false, 30_029); assertTrue(state.active());
        state.observe("a", false, 30_030); assertFalse(state.active());
        state.observe("a", true, 30_031); assertTrue(state.active());
        state.observe(null, false, 30_032); assertFalse(state.active());
    }
    @Test void switchResetAndClockRewindNeverReuseOldPauseDeadline() {
        var state = new MusicIslandCapsuleState();
        state.observe("a", false, 0);
        state.observe("a", false, 31_000); assertFalse(state.active());
        state.observe("b", false, 31_001); assertTrue(state.active());
        state.observe("b", false, 1); assertTrue(state.active());
        state.observe("b", false, 30_001); assertFalse(state.active());
        state.reset(); assertFalse(state.active());
        state.observe("b", false, 40_000); assertTrue(state.active());
    }
    @Test void timelineNeverProducesNanOverflowOrNegativeFill() {
        assertEquals(0, MusicIslandCapsuleState.progress(-1, 100));
        assertEquals(0, MusicIslandCapsuleState.progress(100, 0));
        assertEquals(0, MusicIslandCapsuleState.progress(100, -1));
        assertEquals(.5, MusicIslandCapsuleState.progress(50, 100));
        assertEquals(1, MusicIslandCapsuleState.progress(Long.MAX_VALUE, 100));
    }
}
