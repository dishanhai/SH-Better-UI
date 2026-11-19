// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import static com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl.Glyph.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class TransportGlyphMotionTest {
    @Test void playPauseKeepsTheFirstFrameAndSettlesWithoutRestartingOnRepaints() {
        var motion = new TransportGlyphMotion();
        assertEquals(1f, motion.sample(PLAY, 1000, false));
        assertEquals(0f, motion.sample(PAUSE, 1010, false));
        float early = motion.sample(PAUSE, 1030, false);
        assertTrue(early > 0f && early < .5f);
        assertEquals(.5f, motion.sample(PAUSE, 1080, false), .001f);
        assertEquals(1f, motion.sample(PAUSE, 1150, false));
    }

    @Test void rapidReversalKeepsTheCurrentlyPaintedMixAndChangesDirection() {
        var motion = new TransportGlyphMotion();
        motion.sample(STOP, 0, false);
        motion.sample(CANCEL, 5, false);
        float cancel = motion.sample(CANCEL, 55, false);
        float stop = motion.sample(STOP, 55, false);
        assertEquals(1f - cancel, stop, .001f);
        assertEquals(stop, motion.sample(STOP, 55, false));
        assertTrue(motion.sample(STOP, 95, false) > stop);
        assertEquals(1f, motion.sample(STOP, 195, false));
    }

    @Test void reducedMotionOrDisabledPresentationImmediatelyDiscardsTheBlend() {
        var motion = new TransportGlyphMotion();
        motion.sample(PLAY, 0, false);
        motion.sample(PAUSE, 5, false);
        assertEquals(1f, motion.sample(PAUSE, 30, true));
        assertEquals(1f, motion.sample(PAUSE, 35, false));
        assertEquals(1f, motion.sample(PLAY, 40, true));
    }

    @Test void unpaintedGapReversedClockAndUnrelatedGlyphHaveNoOutgoingIcon() {
        var motion = new TransportGlyphMotion();
        motion.sample(STOP, 700, false);
        assertEquals(1f, motion.sample(CANCEL, 1200, false));
        assertEquals(1f, motion.sample(STOP, 1199, false));
        assertEquals(1f, motion.sample(LYRICS, 1200, false));
        assertEquals(1f, motion.sample(CANCEL, 1201, false));
        assertEquals(LYRICS, TransportGlyphMotion.pairedWith(LYRICS));
    }
}
