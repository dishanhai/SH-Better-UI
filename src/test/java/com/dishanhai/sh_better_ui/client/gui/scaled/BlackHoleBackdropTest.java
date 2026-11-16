package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackHoleBackdropTest {
    private static BlackHoleBackdropProfile profile(float[] f) {
        return new BlackHoleBackdropProfile(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], 0x80556677, 0xA0112233);
    }
    @Test void defaultsAreMonochromeAndConservative() {
        var p = BlackHoleBackdropProfile.DEFAULT;
        for (int color : new int[]{p.color(), p.backgroundColor()}) {
            assertEquals(color & 255, color >> 8 & 255);
            assertEquals(color & 255, color >> 16 & 255);
        }
        assertEquals(.18f, p.speed()); assertEquals(1f, p.size()); assertEquals(.16f, p.diskWidth());
    }
    @Test void everyFiniteParameterClampsAndArgbIsPreserved() throws Exception {
        float[] lows = {0, .5f, -.7f, .05f, 0, 0, 0, 0};
        float[] highs = {2, 1.8f, .7f, .35f, 1.5f, 2, 3, 1};
        float[] input = new float[8]; java.util.Arrays.fill(input, -100);
        var low = profile(input); java.util.Arrays.fill(input, 100); var high = profile(input);
        var fields = BlackHoleBackdropProfile.class.getRecordComponents();
        for (int i = 0; i < 8; i++) {
            assertEquals(lows[i], fields[i].getAccessor().invoke(low));
            assertEquals(highs[i], fields[i].getAccessor().invoke(high));
        }
        assertEquals(0x80556677, low.color()); assertEquals(0xA0112233, low.backgroundColor());
    }
    @Test void everyNonFiniteParameterIsRejected() {
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            for (int i = 0; i < 8; i++) {
                float[] values = {.18f, 1, .08f, .16f, 1, 1, .55f, 1}; values[i] = bad;
                assertThrows(IllegalArgumentException.class, () -> profile(values));
            }
        }
    }
    @Test void reusedClockFreezesAndSuspendsWithoutCatchingUp() {
        var clock = new AmbientBackdropClock();
        clock.advance(0, false, .18f); float phase = clock.advance(50, false, .18f);
        assertEquals(phase, clock.advance(10000, true, .18f));
        assertEquals(phase, clock.advance(20000, false, .18f));
        clock.suspend(); assertEquals(phase, clock.advance(30000, false, .18f));
        assertTrue(clock.advance(30050, false, .18f) > phase);
    }
    @Test void physicalDefaultsAndLegacyConstructorRemainCompatible() {
        var legacy = profile(new float[]{.18f, 1, .08f, .16f, 1, 1, .55f, 1});
        assertEquals(.25f, legacy.inclination()); assertEquals(0f, legacy.outline());
        assertEquals(18f, legacy.diskOuterRadius());
        assertEquals(BlackHoleBackdropProfile.Quality.BALANCED, legacy.quality());
        assertEquals(.55f, legacy.glow());
        assertEquals(1.2f, BlackHoleBackdropProfile.DEFAULT.glow());
        assertEquals(0f, BlackHoleBackdropProfile.DEFAULT.outline());
    }
    @Test void addedPhysicalParametersRejectNonfiniteAndClamp() {
        var low = physical(-1, -1, 0, BlackHoleBackdropProfile.Quality.LOW);
        var high = physical(9, 9, 99, BlackHoleBackdropProfile.Quality.HIGH);
        assertEquals(.1f, low.inclination()); assertEquals(0f, low.outline()); assertEquals(6.5f, low.diskOuterRadius());
        assertEquals(1.4f, high.inclination()); assertEquals(1f, high.outline()); assertEquals(24f, high.diskOuterRadius());
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> physical(bad, 0, 18, BlackHoleBackdropProfile.Quality.LOW));
            assertThrows(IllegalArgumentException.class, () -> physical(.25f, bad, 18, BlackHoleBackdropProfile.Quality.LOW));
            assertThrows(IllegalArgumentException.class, () -> physical(.25f, 0, bad, BlackHoleBackdropProfile.Quality.LOW));
        }
        assertThrows(NullPointerException.class, () -> physical(.25f, 0, 18, null));
    }
    @Test void qualityHasDiscreteIncreasingBudgets() {
        var low = BlackHoleBackdropProfile.Quality.LOW;
        var balanced = BlackHoleBackdropProfile.Quality.BALANCED;
        var high = BlackHoleBackdropProfile.Quality.HIGH;
        assertEquals(192, low.steps()); assertEquals(384, low.resolution()); assertEquals(.04f, low.stepSize());
        assertEquals(320, balanced.steps()); assertEquals(768, balanced.resolution()); assertEquals(.025f, balanced.stepSize());
        assertEquals(512, high.steps()); assertEquals(1024, high.resolution()); assertEquals(.016f, high.stepSize());
    }
    private static BlackHoleBackdropProfile physical(float inclination, float outline, float radius,
                                                     BlackHoleBackdropProfile.Quality quality) {
        return new BlackHoleBackdropProfile(.18f, 1, .08f, .16f, 1, 1, 1.2f, 1, 0xFFFFFFFF, 0xFF080808,
                inclination, outline, quality, radius);
    }

}
