package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmbientBackdropTest {
    @Test void finiteParametersAreClampedAndColorsPreserved() {
        var low = new AmbientBackdropProfile(-1, -1, -1, -1, 1, 2, 3);
        assertEquals(0, low.speed());
        assertEquals(.25f, low.scale());
        assertEquals(0, low.intensity());
        assertEquals(0, low.opacity());
        assertEquals(1, low.primaryColor());
        assertEquals(2, low.secondaryColor());
        assertEquals(3, low.backgroundColor());
        var high = new AmbientBackdropProfile(9, 9, 9, 9, 1, 2, 3);
        assertEquals(2, high.speed());
        assertEquals(4, high.scale());
        assertEquals(1, high.intensity());
        assertEquals(1, high.opacity());
    }

    @Test void nonFiniteParametersAreRejected() {
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new AmbientBackdropProfile(bad, 1, 1, 1, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new AmbientBackdropProfile(1, bad, 1, 1, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new AmbientBackdropProfile(1, 1, bad, 1, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new AmbientBackdropProfile(1, 1, 1, bad, 0, 0, 0));
        }
    }

    @Test void presetsHaveDistinctPalettesAndConservativeDefaults() {
        for (var preset : new AmbientBackdropProfile[]{AmbientBackdropProfile.AURORA,
                AmbientBackdropProfile.SILK, AmbientBackdropProfile.IRIDESCENT}) {
            assertEquals(.25f, preset.speed());
            assertEquals(1f, preset.scale());
            assertEquals(.65f, preset.intensity());
            assertEquals(1f, preset.opacity());
        }
        assertNotEquals(AmbientBackdropProfile.AURORA, AmbientBackdropProfile.SILK);
        assertNotEquals(AmbientBackdropProfile.SILK, AmbientBackdropProfile.IRIDESCENT);
        assertNotEquals(AmbientBackdropProfile.AURORA, AmbientBackdropProfile.IRIDESCENT);
    }

    @Test void firstFrameStartsAtRestAndLongFramesAreCapped() {
        var clock = new AmbientBackdropClock();
        assertEquals(0f, clock.advance(1000, false, 1));
        assertEquals(.025f, clock.advance(1025, false, 1), .00001f);
        assertEquals(.075f, clock.advance(100000, false, 1), .00001f);
        assertEquals(.075f, clock.advance(100000, false, 1), .00001f);
    }

    @Test void reducedMotionFreezesAndResumeDoesNotCatchUp() {
        var clock = new AmbientBackdropClock();
        clock.advance(0, false, 1);
        float phase = clock.advance(20, false, 1);
        assertEquals(phase, clock.advance(40, true, 1));
        assertEquals(phase, clock.advance(100000, true, 1));
        assertEquals(phase, clock.advance(200000, false, 1));
        assertEquals(phase + .02f, clock.advance(200020, false, 1), .00001f);
    }

    @Test void reversedAndExtremeTimestampsRemainFinite() {
        var clock = new AmbientBackdropClock();
        clock.advance(100, false, 1);
        assertEquals(0f, clock.advance(0, false, 1));
        assertEquals(.02f, clock.advance(20, false, 1), .00001f);
        clock.advance(Long.MIN_VALUE, false, 1);
        assertEquals(.07f, clock.advance(Long.MAX_VALUE, false, 1), .00001f);
    }

    @Test void instancesAndZeroSpeedAreIndependent() {
        var a = new AmbientBackdropClock();
        var b = new AmbientBackdropClock();
        a.advance(0, false, 1);
        b.advance(0, false, 1);
        assertEquals(.05f, a.advance(50, false, 1), .00001f);
        assertEquals(0f, b.advance(50, false, 0));
        assertEquals(0f, b.advance(100, false, Float.NaN));
        assertEquals(0f, b.advance(150, false, -1));
    }

    @Test void phaseStaysBoundedOverManyTurns() {
        var clock = new AmbientBackdropClock();
        clock.advance(0, false, 2);
        for (int i = 1; i <= 100000; i++) {
            float phase = clock.advance(i * 50L, false, 2);
            assertTrue(Float.isFinite(phase) && phase >= 0 && phase < Math.PI * 2);
        }
    }
    @Test void millisecondDeltasSurviveExtremeTimestampMagnitude() {
        var clock = new AmbientBackdropClock();
        clock.advance(Long.MAX_VALUE - 10, false, 1);
        assertEquals(.001f, clock.advance(Long.MAX_VALUE - 9, false, 1), .000001f);
    }

    @Test void firstReducedMotionFrameAndRepeatedTogglesNeverAccumulateTime() {
        var clock = new AmbientBackdropClock();
        assertEquals(0f, clock.advance(0, true, 1));
        assertEquals(0f, clock.advance(1000, true, 1));
        assertEquals(0f, clock.advance(2000, false, 1));
        assertEquals(.02f, clock.advance(2020, false, 1), .000001f);
        assertEquals(.02f, clock.advance(2040, true, 1), .000001f);
        assertEquals(.02f, clock.advance(2060, false, 1), .000001f);
    }

    @Test void phaseWrapsAtOneTurnAndInvalidSpeedDoesNotPoisonClock() {
        var clock = new AmbientBackdropClock();
        clock.advance(0, false, 2);
        for (int i = 1; i <= 63; i++) clock.advance(i * 50L, false, 2);
        float expected = (float) (6.3 - Math.PI * 2);
        assertEquals(expected, clock.advance(3150, false, 2), .000001f);
        assertEquals(expected, clock.advance(3200, false, Float.POSITIVE_INFINITY), .000001f);
        assertEquals(expected + .1f, clock.advance(3250, false, 100), .000001f);
    }

    @Test void suspendPreservesPhaseWithoutResumeCatchUp() {
        var clock = new AmbientBackdropClock();
        clock.advance(0, false, 1);
        float phase = clock.advance(20, false, 1);
        clock.suspend();
        assertEquals(phase, clock.advance(100000, false, 1));
        assertEquals(phase + .02f, clock.advance(100020, false, 1), .000001f);
    }

}
