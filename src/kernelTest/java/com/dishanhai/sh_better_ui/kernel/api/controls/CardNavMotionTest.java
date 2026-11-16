// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardNavMotionTest {
    private static final CardNavMotion.Settings SETTINGS = CardNavMotion.Settings.DEFAULT;

    @Test void reversesWithoutJumpingAndEventuallyHides() {
        var motion = new CardNavMotion(false);
        motion.setOpen(true);
        for (int i = 0; i < 10; i++) motion.advance(i * 16, SETTINGS, false);
        double progress = motion.progress();
        motion.setOpen(false);
        assertEquals(progress, motion.progress());
        assertTrue(motion.shown());
        for (int i = 10; i < 500; i++) motion.advance(i * 16, SETTINGS, false);
        assertEquals(0, motion.progress());
        assertFalse(motion.shown());
    }

    @Test void reducedMotionFinishesImmediatelyEvenAfterAReverse() {
        var motion = new CardNavMotion(false);
        motion.setOpen(true);
        motion.advance(0, SETTINGS, true);
        assertEquals(1, motion.progress());
        motion.setOpen(false);
        motion.advance(16, SETTINGS, true);
        assertFalse(motion.shown());
    }

    @Test void largeCardCountsRemainStaggeredAndAllReachTheirFinalPose() {
        var motion = new CardNavMotion(false);
        motion.setOpen(true);
        motion.advance(0, SETTINGS, false);
        assertTrue(motion.card(0, 50, SETTINGS).opacity() > motion.card(49, 50, SETTINGS).opacity());
        motion.advance(16, SETTINGS, true);
        for (var effect : CardNavMotion.Effect.values()) {
            var settings = new CardNavMotion.Settings(effect, .3, 1, 3);
            for (int i = 0; i < 50; i++) {
                var card = motion.card(i, 50, settings);
                assertEquals(1, card.opacity());
                assertEquals(1, card.scale());
                assertEquals(0, card.translateY(), 1e-12);
                assertEquals(0, card.rotateX(), 1e-12);
                assertEquals(0, card.blur());
            }
        }
    }

    @Test void maxBounceAndSlowFramesRemainFiniteAndSettle() {
        var settings = new CardNavMotion.Settings(CardNavMotion.Effect.UNFOLD, .12, 1, 3);
        var motion = new CardNavMotion(false);
        motion.setOpen(true);
        for (int i = 0; i < 500; i++) {
            motion.advance(i * 500L, settings, false);
            assertTrue(Double.isFinite(motion.progress()));
            assertTrue(motion.progress() < 2);
        }
        assertEquals(1, motion.progress());
    }
}
