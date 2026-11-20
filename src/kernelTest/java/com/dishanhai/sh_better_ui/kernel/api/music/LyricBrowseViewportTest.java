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
    @Test void idleReturnUsesLastInteractionAndWaitsForTransportRelease() {
        var viewport = new LyricBrowseViewport();
        viewport.input(1000);
        assertFalse(viewport.shouldFollow(4599,true,true));
        viewport.input(4500);
        assertFalse(viewport.shouldFollow(8099,true,true));
        viewport.playback(true,true,80);
        assertFalse(viewport.shouldFollow(9000,true,true));
        viewport.playback(false,true,260);
        viewport.input(9000);
        assertFalse(viewport.shouldFollow(12599,true,true));
        assertTrue(viewport.shouldFollow(12600,true,true));
        assertFalse(viewport.shouldFollow(12600,true,false));
        assertFalse(viewport.shouldFollow(12600,false,true));
        viewport.clear();
        assertFalse(viewport.shouldFollow(13000,true,true));
    }
}
