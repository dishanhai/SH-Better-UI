package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverRhythmMotionTest {
    @Test void silenceAndUnavailableAudioNeverStartAnAnimation() {
        var motion = new CoverRhythmMotion();
        for (int ms = 0; ms < 2000; ms += 16) {
            var frame = motion.sample(.016, 1, ms, true, false, Float.NaN, 0f);
            assertEquals(0, frame.getPulse());
            assertEquals(0, frame.getEnergy());
            assertEquals(0, frame.getPhase());
        }
    }

    @Test void realOnsetsReactFasterThanEnergyAndPauseLetsBothSettle() {
        var motion = new CoverRhythmMotion();
        motion.sample(.016, 1, 0, true, false, 0, 0);
        var hit = motion.sample(.016, 1, 16, true, false, 1, 1);
        assertTrue(hit.getPulse() > hit.getEnergy());
        assertTrue(hit.getEnergy() > 0);
        var after = motion.sample(.016, 1, 16, false, false, 1, 1);
        assertTrue(after.getPulse() < hit.getPulse());
        assertTrue(after.getEnergy() < hit.getEnergy());
        assertTrue(after.getPhase() > hit.getPhase());
        for (int i = 0; i < 48; i++) after = motion.sample(.1, 1, 16, false, false, 1, 1);
        assertEquals(0, after.getPulse());
        assertEquals(0, after.getEnergy());
        assertEquals(after.getPhase(), motion.sample(.1, 1, 16, false, false, 1, 1).getPhase());
    }

    @Test void seekTrackChangeAndStaticModeClearPreviouslyAccumulatedMotion() {
        var motion = new CoverRhythmMotion();
        motion.sample(.016, 1, 0, true, false, 0, 0);
        assertTrue(motion.sample(.05, 1, 50, true, false, 1, 1).getPulse() > 0);
        assertEquals(0, motion.sample(.016, 1, 10_000, true, false, 1, 1).getPulse());
        assertTrue(motion.sample(.016, 1, 10_016, true, false, 1, 1).getPulse() > 0);
        assertEquals(0, motion.sample(.016, 2, 0, true, false, 1, 1).getEnergy());
        assertTrue(motion.sample(.016, 2, 16, true, false, 1, 1).getEnergy() > 0);
        assertEquals(0, motion.sample(.016, 2, 32, true, true, 1, 1).getEnergy());
        assertEquals(0, motion.sample(.016, 2, 48, true, false, 0, 0).getPulse());
    }

    @Test void flowKeepsItsPositionAcrossSeekSessionAndStaticChanges() {
        var motion = new CoverRhythmMotion();
        motion.sample(.016, 1, 0, true, false, 0, 0);
        double phase = motion.sample(.05, 1, 50, true, false, 1, 1).getPhase();
        assertTrue(phase > 0);
        assertEquals(phase, motion.sample(.016, 1, 10_000, true, false, 1, 1).getPhase());
        assertEquals(phase, motion.sample(.016, 2, 0, true, false, 1, 1).getPhase());
        assertEquals(phase, motion.sample(.016, 2, 16, true, true, 1, 1).getPhase());
        assertEquals(phase, motion.sample(.016, 2, 32, true, false, 1, 1).getPhase());
        assertTrue(motion.sample(.016, 2, 48, true, false, 1, 1).getPhase() > phase);
    }

    @Test void strongerRhythmMovesFurtherAndDeformsMoreAtTheSamePlaybackPosition() {
        var weak = new CoverRhythmMotion();
        var strong = new CoverRhythmMotion();
        weak.sample(.016, 1, 0, true, false, 0, 0, 0);
        strong.sample(.016, 1, 0, true, false, 0, 0, 0);
        var quiet = weak.sample(.05, 1, 50, true, false, .1f, .2f, .08f);
        var hit = strong.sample(.05, 1, 50, true, false, .85f, .8f, .75f);
        assertTrue(hit.getBass() > quiet.getBass());
        assertTrue(hit.getDeformation() > quiet.getDeformation() * 2);
        assertTrue(hit.getPhase() > quiet.getPhase() * 2);
        assertTrue(hit.getDeformation() <= 1);

        var still = strong.sample(.05, 1, 100, true, true, 1, 1, 1);
        assertEquals(0, still.getBass());
        assertEquals(0, still.getDeformation());
        assertEquals(hit.getPhase(), still.getPhase());
    }

    @Test void isolatedBassOnlyAddsASmallSwellAndPhaseAdvance() {
        var motion = new CoverRhythmMotion();
        motion.sample(.016, 1, 0, true, false, 0, 0, 0);
        var accent = motion.sample(.1, 1, 100, true, false, 0, 0, 1);
        assertTrue(accent.getBass() > .5, "the bass accent reaches the motion model");
        assertTrue(accent.getDeformation() > 0 && accent.getDeformation() <= .12);
        assertTrue(accent.getPhase() > 0 && accent.getPhase() <= .02);
    }
}
