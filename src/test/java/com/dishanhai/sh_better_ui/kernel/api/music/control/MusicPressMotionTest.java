// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicPressMotionTest {
    @Test void quickActivationsRestartTheReturnWithoutLoweringVisibleCompression() {
        var motion = new MusicPressMotion();
        motion.sample(true, 100, false);
        assertEquals(1f, motion.sample(true, 160, false));
        motion.pulse(160, false);
        assertEquals(1f, motion.sample(false, 160, false));
        assertTrue(motion.sample(false, 280, false) < .38f);
        motion.pulse(280, false);
        assertEquals(.38f, motion.sample(false, 280, false));
        motion.pulse(290, true);
        assertEquals(0f, motion.sample(false, 290, false));
    }

    @Test void fastTapBetweenFramesLeavesATailThenSettles() {
        var motion = new MusicPressMotion();
        assertTrue(motion.sample(true, 100, false) > 0);
        motion.sample(false, 104, false);
        float firstFrame = motion.sample(false, 120, false);
        assertTrue(firstFrame > 0 && firstFrame < 1);
        assertEquals(0f, motion.sample(false, 249, false));
    }

    @Test void repeatedPressRetargetsFromVisibleCompression() {
        var motion = new MusicPressMotion();
        motion.sample(true, 100, false);
        assertEquals(1f, motion.sample(true, 160, false));
        motion.sample(false, 161, false);
        float recovering = motion.sample(false, 204, false);
        assertTrue(recovering > 0 && recovering < 1);
        assertEquals(recovering, motion.sample(true, 204, false), .0001f);
        assertEquals(1f, motion.sample(true, 264, false));
    }

    @Test void reducedMotionAndDisableClearAnyTail() {
        var motion = new MusicPressMotion();
        motion.sample(true, 100, false);
        assertEquals(0f, motion.sample(true, 125, true));
        assertEquals(0f, motion.sample(false, 130, false));
        motion.sample(true, 200, false);
        motion.reset();
        assertEquals(0f, motion.sample(false, 201, false));
    }
}
