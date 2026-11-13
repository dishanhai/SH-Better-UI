// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmoothVerticalScrollTest {
    @Test void repeatedWheelInputRetargetsWithoutJumping() {
        var scroll = new VerticalScroll();
        scroll.resize(100, 400);
        var motion = new SmoothVerticalScroll(scroll);

        assertTrue(motion.scrollBy(30, 1000, false));
        assertEquals(0, scroll.offset());
        assertTrue(motion.advance(1016, false));
        double first = scroll.offset();
        assertTrue(first > 0 && first < 30);
        assertTrue(motion.scrollBy(30, 1016, false));
        assertEquals(first, scroll.offset());
        for (long now = 1032; now < 2000; now += 16) motion.advance(now, false);
        assertEquals(60, scroll.offset(), 0.1);
    }

    @Test void reducedMotionAndDirectManipulationAreImmediate() {
        var scroll = new VerticalScroll();
        scroll.resize(100, 400);
        var motion = new SmoothVerticalScroll(scroll);
        motion.scrollBy(50, 1000, false);
        motion.advance(1016, false);
        motion.advance(1032, true);
        assertEquals(50, scroll.offset());
        motion.scrollBy(30, 1040, true);
        assertEquals(80, scroll.offset());
        assertTrue(scroll.beginDrag(80, 100));
        motion.synchronize();
        double dragged = scroll.offset();
        motion.advance(1100, false);
        assertEquals(dragged, scroll.offset());
        scroll.cancelDrag();
        motion.synchronize();
        assertEquals(80, scroll.offset());
    }

    @Test void resizeAndInvalidInputStayWithinBounds() {
        var scroll = new VerticalScroll();
        scroll.resize(100, 400);
        var motion = new SmoothVerticalScroll(scroll);
        assertFalse(motion.scrollBy(Double.NaN, 0, false));
        motion.scrollBy(999, 0, false);
        scroll.resize(100, 130);
        for (long now = 16; now < 1000; now += 16) {
            motion.advance(now, false);
            assertTrue(scroll.offset() >= 0 && scroll.offset() <= 30);
        }
        assertEquals(30, scroll.offset(), 0.1);
    }
}
