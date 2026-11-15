// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.input;

import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture.Input.KEYBOARD;
import static com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture.Input.POINTER;
import static com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture.Result.CONFIRMED;
import static com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture.Result.NONE;
import static com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture.Result.TAP;
import static org.junit.jupiter.api.Assertions.*;

class HoldGestureTest {
    private final HoldGesture gesture = new HoldGesture(new HoldGesture.Timing(1000, 200, 300));

    @Test
    void confirmsAtThresholdOnceAndThenResets() {
        assertTrue(gesture.begin(POINTER, 0));
        assertFalse(gesture.begin(KEYBOARD, 500));
        assertEquals(0.5f, gesture.progress(500));
        assertEquals(NONE, gesture.advance(999));
        assertEquals(CONFIRMED, gesture.advance(1000));
        assertEquals(NONE, gesture.release(POINTER, 1001));
        assertEquals(NONE, gesture.advance(1200));
        assertTrue(gesture.completed());
        assertFalse(gesture.begin(KEYBOARD, 1299));
        assertEquals(NONE, gesture.advance(1300));
        assertFalse(gesture.completed());
        assertEquals(0.125f, gesture.progress(1400), 0.0001f);
    }

    @Test
    void tapRequiresMatchingEarlyReleaseAndCancelNeverTaps() {
        assertTrue(gesture.begin(KEYBOARD, 0));
        assertEquals(NONE, gesture.release(POINTER, 50));
        assertEquals(TAP, gesture.release(KEYBOARD, 249));
        assertTrue(gesture.progress(250) > 0);
        assertTrue(gesture.begin(POINTER, 250));
        assertEquals(0f, gesture.progress(250));
        assertEquals(NONE, gesture.release(POINTER, 500));

        assertTrue(gesture.begin(POINTER, 700));
        assertTrue(gesture.cancel(1800));
        assertEquals(NONE, gesture.advance(1800));
        assertEquals(NONE, gesture.release(POINTER, 1810));
        assertFalse(gesture.completed());
    }

    @Test
    void releaseCanCompleteBetweenFramesAndDoneCanBeLatched() {
        var latched = new HoldGesture(new HoldGesture.Timing(400, 0, 0));
        assertTrue(latched.begin(KEYBOARD, 50));
        assertEquals(CONFIRMED, latched.release(KEYBOARD, 450));
        assertEquals(NONE, latched.advance(5000));
        assertTrue(latched.completed());
        assertFalse(latched.begin(POINTER, 5000));
        latched.reset();
        assertTrue(latched.begin(POINTER, 5000));
        assertTrue(latched.cancel(5100));
        assertEquals(0f, latched.progress(5100));
    }
}
