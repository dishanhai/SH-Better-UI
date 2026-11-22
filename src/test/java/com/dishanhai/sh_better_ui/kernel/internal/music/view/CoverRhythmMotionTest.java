package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverRhythmMotionTest {
    @Test void silenceAndUnavailableAudioOnlyMoveTheAmbientBackground() {
        var motion = new CoverRhythmMotion();
        for (int ms = 0; ms < 2000; ms += 16) {
            var frame = motion.sample(.016, 1, ms, true, false, Float.NaN, 0f);
            assertEquals(0, frame.getPulse());
            assertEquals(0, frame.getEnergy());
            assertEquals(0, frame.getDeformation());
        }
    }

    @Test void idleDriftIsVisibleButMuchSlowerThanPlaybackDrift() {
        var idle = new CoverRhythmMotion();
        var playing = new CoverRhythmMotion();
        idle.sample(.1, 1, 0, false, false, 0, 0);
        playing.sample(.1, 1, 0, true, false, 0, 0);
        double idlePhase = 0;
        double playingPhase = 0;
        for (int i = 1; i <= 10; i++) {
            idlePhase = idle.sample(.1, 1, 0, false, false, 0, 0).getPhase();
            playingPhase = playing.sample(.1, 1, 100L * i, true, false, 0, 0).getPhase();
        }
        assertTrue(idlePhase > 0);
        assertTrue(idlePhase < playingPhase / 3);
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
        var idle = motion.sample(.1, 1, 16, false, false, 1, 1);
        assertTrue(idle.getPhase() > after.getPhase());
        assertTrue(idle.getPhase() - after.getPhase() < .01);
    }

    @Test void seekTrackChangeAndStaticModeClearPreviouslyAccumulatedMotion() {
        var motion = new CoverRhythmMotion();
        motion.sample(.016, 1, 0, true, false, 0, 0);
        assertTrue(motion.sample(.05, 1, 50, true, false, 1, 1).getPulse() > 0);
        assertEquals(0, motion.sample(.016, 1, 10_000, true, false, 1, 1).getPulse());
        assertEquals(0, motion.sample(.1, 1, 10_100, true, false, 1, 1).getPulse());
        assertTrue(motion.sample(.1, 1, 10_200, true, false, 1, 1).getPulse() > 0);
        assertEquals(0, motion.sample(.016, 2, 0, true, false, 1, 1).getEnergy());
        assertTrue(motion.sample(.016, 2, 16, true, false, 1, 1).getEnergy() > 0);
        assertEquals(0, motion.sample(.016, 2, 32, true, true, 1, 1).getEnergy());
        assertEquals(0, motion.sample(.016, 2, 48, true, false, 0, 0).getPulse());
    }

    @Test void explicitShortSeekDropsThePreviousAudioEnvelope() {
        var motion = new CoverRhythmMotion();
        motion.sample(.1, 1, 0, true, false, 0, 0, 0, 0);
        var before = motion.sample(.1, 1, 100, true, false, 1, 1, 1, 0);
        assertTrue(before.getDeformation() > 0);

        var seek = motion.sample(.016, 1, 160, true, false, 1, 1, 1, 42);
        assertEquals(before.getPhase(), seek.getPhase());
        assertEquals(0, seek.getDeformation());
        assertEquals(0, motion.sample(.1, 1, 260, true, false, 1, 1, 1, 42).getPulse());
        assertTrue(motion.sample(.1, 1, 360, true, false, 1, 1, 1, 42).getPulse() > 0);
    }

    @Test void lyricScrollingAddsOnlyABoundedTransientWithoutAudio() {
        var motion = new CoverRhythmMotion();
        motion.sample(.016, 1, 3000, false, false, 0, 0);
        var scroll = motion.sample(.05, 1, 3000, false, false, 0, 0, 0, 0, -500);
        assertTrue(scroll.getDeformation() > 0 && scroll.getDeformation() <= .035);
        assertEquals(0, scroll.getPulse());
        assertTrue(motion.sample(.05, 1, 3000, false, false, 0, 0).getDeformation() < scroll.getDeformation());

        var still = motion.sample(.05, 1, 3000, false, true, 0, 0, 0, 0, 500);
        assertEquals(0, still.getDeformation());
        assertEquals(still.getPhase(), motion.sample(.05, 1, 3000, false, true, 0, 0).getPhase());
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
        for (int ms = 50; ms < 950; ms += 50) {
            weak.sample(.05, 1, ms, true, false, .05f, .2f, .08f);
            strong.sample(.05, 1, ms, true, false, .05f, .95f, .15f);
        }
        var quiet = weak.sample(.1, 1, 1000, true, false, 1, .2f, 1);
        var hit = strong.sample(.1, 1, 1000, true, false, 1, .95f, 1);
        assertEquals(0, quiet.getDeformation(), "a relative onset alone must not drive a quiet passage");
        assertTrue(hit.getDeformation() > .4 && hit.getDeformation() <= .7);
        assertTrue(hit.getPhase() > quiet.getPhase());

        var still = strong.sample(.05, 1, 1050, true, true, 1, 1, 1);
        assertEquals(0, still.getBass());
        assertEquals(0, still.getDeformation());
        assertEquals(hit.getPhase(), still.getPhase());
    }

    @Test void isolatedBassDoesNotOverrideTheLoudnessOfThePassage() {
        var motion = new CoverRhythmMotion();
        motion.sample(.016, 1, 0, true, false, 0, 0, 0);
        var accent = motion.sample(.1, 1, 100, true, false, 0, 0, 1);
        assertTrue(accent.getBass() > .5, "the bass accent reaches the motion model");
        assertEquals(0, accent.getDeformation());
    }
}
