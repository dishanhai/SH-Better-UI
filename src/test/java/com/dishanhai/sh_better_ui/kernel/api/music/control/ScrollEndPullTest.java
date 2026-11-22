// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScrollEndPullTest {
    @Test void countsOnlyThePartOfTheGestureBeyondTheLastRow() {
        var pull = new ScrollEndPull();
        assertEquals(93, pull.drag(80, 100, 13));
        assertEquals(0f, pull.progress());
        assertEquals(100, pull.drag(93, 100, 27));
        assertTrue(pull.progress() > 0 && pull.progress() < 1);
        assertEquals(100, pull.drag(100, 100, 16));
        assertEquals(1f, pull.progress());
        assertTrue(pull.release(true, 100));
        assertFalse(pull.release(true, 101));
        assertEquals(0f, pull.reveal(101, true));
        assertEquals(0f, pull.reveal(300, false));
    }

    @Test void reversalAndReleaseOutsideTheViewportDoNotLoad() {
        var pull = new ScrollEndPull();
        pull.drag(100, 100, 40);
        assertEquals(100, pull.drag(100, 100, -5));
        assertFalse(pull.release(true, 100));
        pull.drag(100, 100, 40);
        assertFalse(pull.release(false, 200));
        assertTrue(pull.reveal(240, false) > 0);
        pull.reset();
        assertEquals(0f, pull.reveal(240, false));
    }

    @Test void canLoadFromAListShorterThanTheViewport() {
        var pull = new ScrollEndPull();
        assertEquals(0, pull.drag(0, 0, 40));
        assertTrue(pull.release(true, 100));
    }
}
