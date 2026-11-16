package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrystalDriftFieldTest {
    private static CrystalDriftProfile profile(int count, float speed, CrystalDriftProfile.Placement placement,
                                               boolean interaction, boolean pulse, boolean gather) {
        var p = CrystalDriftProfile.DEFAULT;
        return new CrystalDriftProfile(count, p.size(), speed, p.spread(), p.opacity(), p.glow(), p.parallax(),
                placement, p.material(), p.color(), p.accentColor(), p.backgroundColor(), interaction, pulse, gather);
    }
    private static void update(CrystalDriftField f, long now, CrystalDriftProfile p) {
        f.update(now, p, false, .5f, .5f, false, false);
    }
    @Test void defaultsAndClampBounds() {
        var p = CrystalDriftProfile.DEFAULT;
        assertEquals(96, p.count()); assertEquals(.4f, p.speed()); assertEquals(.35f, p.glow());
        var low = new CrystalDriftProfile(-1, -1, -1, -1, -1, -1, -1, p.placement(), p.material(), 1, 2, 3, true, true, true);
        assertEquals(16, low.count()); assertEquals(.25f, low.size()); assertEquals(.2f, low.spread());
        assertEquals(0f, low.opacity()); assertEquals(0f, low.glow()); assertEquals(0f, low.parallax());
        var high = new CrystalDriftProfile(999, 9, 9, 9, 9, 9, 9, p.placement(), p.material(), 1, 2, 3, true, true, true);
        assertEquals(320, high.count()); assertEquals(3f, high.size()); assertEquals(2f, high.speed());
        assertEquals(2f, high.spread()); assertEquals(1f, high.opacity());
    }
    @Test void rejectsNonFiniteAndMissingEnums() {
        var p = CrystalDriftProfile.DEFAULT;
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            for (int index = 0; index < 6; index++) {
                float[] a = {1, 1, 1, 1, 1, 1}; a[index] = bad;
                assertThrows(IllegalArgumentException.class, () -> new CrystalDriftProfile(96, a[0], a[1], a[2], a[3], a[4], a[5],
                        p.placement(), p.material(), 0, 0, 0, true, true, true));
            }
        }
        assertThrows(NullPointerException.class, () -> profile(96, 1, null, true, true, true));
    }
    @Test void sameInputsProduceSameFieldAndArraysAreReused() {
        var a = new CrystalDriftField(); var b = new CrystalDriftField(); var p = CrystalDriftProfile.DEFAULT;
        for (long t = 0; t < 1000; t += 20) { update(a, t, p); update(b, t, p); }
        assertArrayEquals(a.x, b.x); assertArrayEquals(a.y, b.y); assertArrayEquals(a.angle, b.angle);
        var xs = a.x; update(a, 1020, p); assertSame(xs, a.x);
        assertNotEquals(a.angle[0], a.angle[1]);
    }
    @Test void placementsAreDistinctBoundedAndDepthSorted() {
        for (var placement : CrystalDriftProfile.Placement.values()) {
            var f = new CrystalDriftField(); update(f, 0, profile(320, .4f, placement, true, true, true));
            float sum = 0;
            for (int i = 0; i < f.count(); i++) {
                assertTrue(f.x[i] >= 0 && f.x[i] <= 1 && f.y[i] >= 0 && f.y[i] <= 1);
                assertTrue(Float.isFinite(f.angle[i]) && f.size[i] >= 0 && f.size[i] <= 1);
                if (i > 0) assertTrue(f.depth[i] >= f.depth[i - 1]);
                sum += f.x[i];
            }
            float mean = sum / f.count();
            if (placement == CrystalDriftProfile.Placement.LEFT) assertTrue(mean < .3f);
            if (placement == CrystalDriftProfile.Placement.RIGHT) assertTrue(mean > .7f);
            if (placement == CrystalDriftProfile.Placement.CENTER) assertTrue(mean > .4f && mean < .6f);
        }
    }
    @Test void stalledTimeIsCappedAndBackwardTimeDoesNotAdvance() {
        var a = new CrystalDriftField(); var b = new CrystalDriftField(); var p = CrystalDriftProfile.DEFAULT;
        update(a, 100, p); update(b, 100, p); update(a, 150, p); update(b, Long.MAX_VALUE, p);
        assertArrayEquals(a.x, b.x);
        float[] frozen = a.x.clone(); update(a, Long.MIN_VALUE, p); assertArrayEquals(frozen, a.x);
        update(a, Long.MAX_VALUE, p); for (float x : a.x) assertTrue(Float.isFinite(x));
    }
    @Test void reducedMotionFreezesPulseAndResumeDoesNotJump() {
        var f = new CrystalDriftField(); var p = CrystalDriftProfile.DEFAULT;
        update(f, 0, p); update(f, 20, p); var xs = f.x.clone(); var angles = f.angle.clone();
        f.pulse(.5f, .5f); f.update(1000, p, true, .1f, .1f, true, true);
        f.update(2000, p, true, .8f, .8f, true, true);
        assertArrayEquals(xs, f.x); assertArrayEquals(angles, f.angle);
        for (float flash : f.flash) assertEquals(0, flash);
        f.update(3000, p, false, .8f, .8f, true, true); assertArrayEquals(xs, f.x);
    }
    @Test void pulseRespectsPreferencesAndDecays() {
        var f = new CrystalDriftField(); var p = CrystalDriftProfile.DEFAULT;
        update(f, 0, p); f.pulse(f.x[0], f.y[0]); f.update(10, p, false, .5f, .5f, true, false);
        assertTrue(f.flash[0] > .9f);
        for (long t = 60; t < 1500; t += 50) update(f, t, p);
        assertEquals(0, f.flash[0]);
        f.pulse(.5f, .5f); f.update(1500, profile(96, .4f, p.placement(), true, false, true), false, .5f, .5f, true, false);
        for (float flash : f.flash) assertEquals(0, flash);
    }
    @Test void longPressGatherWaitsAndInvalidPointersStayFinite() {
        var p = profile(96, 0, CrystalDriftProfile.Placement.FULL, true, true, true);
        var a = new CrystalDriftField(); var b = new CrystalDriftField();
        for (long t = 0; t <= 250; t += 50) {
            a.update(t, p, false, .5f, .5f, true, true); b.update(t, p, false, .5f, .5f, true, false);
        }
        assertArrayEquals(a.x, b.x);
        a.update(350, p, false, .5f, .5f, true, true);
        assertTrue(Math.abs(a.x[0] - .5f) < Math.abs(b.x[0] - .5f));
        a.update(400, p, false, Float.NaN, Float.POSITIVE_INFINITY, true, true);
        for (float x : a.x) assertTrue(Float.isFinite(x));
    }
    @Test void lifecycleAndCountChangesAreSafe() {
        var f = new CrystalDriftField(); var p = CrystalDriftProfile.DEFAULT;
        update(f, 0, p); update(f, 50, p); float[] xs = f.x.clone();
        f.suspend(); update(f, 999999, p); assertArrayEquals(xs, f.x);
        update(f, 1000000, profile(320, .4f, p.placement(), true, true, true)); assertEquals(320, f.count());
        update(f, 1000050, profile(16, .4f, p.placement(), true, true, true)); assertEquals(16, f.count());
    }
    @Test void sizeSeedsAreNormalizedAndIndependentOfProfileScale() {
        var a = new CrystalDriftField(); var b = new CrystalDriftField(); var p = CrystalDriftProfile.DEFAULT;
        var large = new CrystalDriftProfile(p.count(), 3f, p.speed(), p.spread(), p.opacity(), p.glow(),
                p.parallax(), p.placement(), p.material(), p.color(), p.accentColor(), p.backgroundColor(),
                p.mouseInteraction(), p.pulseOnClick(), p.holdToGather());
        update(a, 0, p); update(b, 0, large);
        assertArrayEquals(a.size, b.size);
        for (float seed : a.size) assertTrue(seed >= 0f && seed <= 1f);
    }
    @Test void suspendClearsInteractionStateWithoutResettingAnimation() {
        var active = new CrystalDriftField(); var neutral = new CrystalDriftField();
        var p = CrystalDriftProfile.DEFAULT;
        for (long t = 0; t <= 1000; t += 50) {
            active.update(t, p, false, .1f, .2f, true, true);
            update(neutral, t, p);
        }
        active.pulse(active.x[0], active.y[0]);
        active.update(1000, p, false, .1f, .2f, true, true);
        assertTrue(active.flash[0] > .9f);
        assertNotEquals(neutral.x[0], active.x[0]);
        active.pulse(.5f, .5f);
        active.suspend();
        for (float flash : active.flash) assertEquals(0f, flash);
        active.update(999999, p, true, .1f, .2f, true, true);
        assertArrayEquals(neutral.x, active.x);
        assertArrayEquals(neutral.y, active.y);
        assertArrayEquals(neutral.angle, active.angle);
        for (float flash : active.flash) assertEquals(0f, flash);
    }

}
