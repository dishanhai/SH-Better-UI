package com.dishanhai.sh_better_ui.kernel.api.music;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricBrowseViewportTest {
    @Test void scrubCanTemporarilySteerButLeavesTheManualBookmarkAndFocusIntact() {
        var viewport = new LyricBrowseViewport();
        assertFalse(viewport.steering(true));
        assertEquals(97.25, viewport.playback(true, true, 97.25));
        assertTrue(viewport.steering(true));
        assertEquals(204, viewport.playback(true, true, 204));
        assertEquals(97.25, viewport.playback(false, true, 311));
        assertFalse(viewport.steering(true));
        assertTrue(viewport.steering(false), "explicit follow always re-enables normal steering");
    }

    @Test void contentResetCannotRestoreAnOffsetFromAnOldSong() {
        var viewport = new LyricBrowseViewport();
        viewport.playback(true, true, 120);
        viewport.clear();
        assertEquals(0, viewport.playback(false, false, 0));
        assertEquals(0, viewport.playback(true, false, 0));
        assertEquals(45, viewport.playback(false, false, 45));
    }
}
