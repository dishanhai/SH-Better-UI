// Author: dishanhai
package com.dishanhai.sh_better_ui.client.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicOpenShortcutTest {
    @Test void explicitPressOpensOnceAndHeldRepeatAndReleaseDoNotReopen() {
        var key = new MusicOpenShortcut();
        key.offer(true,true,true);
        key.offer(true,true,true);
        assertTrue(key.take(true));
        assertFalse(key.take(true));
        key.offer(false,true,true);
        key.offer(false,true,true);
        assertFalse(key.take(true));
        key.offer(true,true,true);
        assertTrue(key.take(true));
    }

    @Test void unboundAndBlockedInputAreNotSavedForLater() {
        var key = new MusicOpenShortcut();
        key.offer(true,false,true);
        assertFalse(key.take(true));
        key.offer(true,true,false);
        assertFalse(key.take(true));
        key.offer(true,true,true);
        assertFalse(key.take(false));
        assertFalse(key.take(true));
    }
    @Test void mouseRequiresUncancelledPostAndDoesNotJumpFromAClosingScreen() {
        var key = new MusicOpenShortcut();
        key.prepareMouse(true,true,true); // Cancelled: no post, no queued navigation.
        assertFalse(key.take(true));
        key.prepareMouse(true,true,false);
        key.finishMouse(true); // The press itself closed a screen.
        assertFalse(key.take(true));
        key.prepareMouse(true,true,true);
        key.finishMouse(true);
        assertTrue(key.take(true));
        assertFalse(key.take(true));
    }
}
