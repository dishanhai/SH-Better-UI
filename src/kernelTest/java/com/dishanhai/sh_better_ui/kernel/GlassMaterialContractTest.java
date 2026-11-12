// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlassMaterialContractTest {
    @Test
    void presetsPreserveTheMaterialSemanticsAndInterpolateAllOpticalParameters() {
        var regular = GlassMaterial.preset(GlassVariant.REGULAR).optics();
        var clear = GlassMaterial.preset(GlassVariant.CLEAR).optics();
        assertTrue(regular.transmission() < clear.transmission());
        assertTrue(regular.scattering() > clear.scattering());
        assertTrue(regular.blurRadius() > clear.blurRadius());
        assertSame(regular, regular.interpolate(clear, 0));
        assertSame(clear, regular.interpolate(clear, 1));
        assertEquals(new GlassOptics(.5f, 3, .5f, .5f, .5f, 4, 7),
                new GlassOptics(0, 0, 0, 1, 1, 2, 4).interpolate(new GlassOptics(1, 6, 1, 0, 0, 6, 10), .5f));
        assertThrows(IllegalArgumentException.class, () -> regular.interpolate(clear, -0.1f));
        assertThrows(IllegalArgumentException.class, () -> new GlassOptics(0, Float.NaN, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new GlassOptics(0, 0, 0, 0, 0, 0, 33));
    }

    @Test
    void opticalGeometryAndHitTestingShareTheSameCurvesAndRejectDegenerateRegions() {
        var rounded = GlassRegion.rounded(0, 0, 30, 20, 100);
        assertEquals(10, rounded.radius());
        assertFalse(rounded.contains(0, 0));
        assertTrue(rounded.contains(10, 0));
        assertFalse(rounded.contains(10, -1));
        assertTrue(GlassRegion.continuous(0, 0, 30, 20, 10).contains(3, 3));
        assertFalse(rounded.contains(2, 2));
        assertTrue(GlassRegion.ellipse(0, 0, 50, 20).contains(25, 0));
        assertFalse(GlassRegion.ellipse(0, 0, 50, 20).contains(4, 2));
        assertFalse(GlassRegion.capsule(0, 0, 20, 40).contains(0, 0));
        assertTrue(GlassRegion.capsule(0, 0, 20, 40).contains(0, 20));
        assertThrows(IllegalArgumentException.class, () -> GlassRegion.ellipse(0, 0, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> GlassRegion.capsule(Float.MAX_VALUE, 0, Float.MAX_VALUE, 20));
    }

    @Test
    void oneProgressValueDrivesGeometryHitTestingAndOpticalState() {
        var regular = GlassMaterial.preset(GlassVariant.REGULAR);
        var toOptics = new GlassOptics(1, 8, .6f, .4f, .9f, 8, 4);
        var from = new GlassDraw(GlassRegion.capsule(10, 10, 30, 12), regular);
        var to = new GlassDraw(GlassRegion.rounded(0, 4, 50, 24, 10),
                new GlassMaterial(regular.variant(), toOptics, regular.quality(), regular.foreground()), .5f, -.5f, 1);
        assertSame(from, from.interpolate(to, 0));
        assertSame(to, from.interpolate(to, 1));
        var half = from.interpolate(to, .5f);
        assertEquals(GlassRegion.rounded(5, 7, 40, 18, 8), half.region());
        assertFalse(from.region().contains(7, 16));
        assertTrue(half.region().contains(7, 16));
        assertEquals(regular.optics().interpolate(toOptics, .5f), half.material().optics());
        assertEquals(.5f, half.lightStrength());
        assertThrows(IllegalArgumentException.class, () -> from.interpolate(
                new GlassDraw(GlassRegion.ellipse(0, 0, 10, 20), regular), .5f));
    }

    @Test
    void snellForegroundBoundsFollowTheMorphAndLegacyConstructorsKeepTheirProfile() {
        var material = GlassMaterial.preset(GlassVariant.CLEAR);
        var left = GlassRegion.capsule(10, 10, 64, 30);
        var right = GlassRegion.capsule(20, 16, 84, 34);
        var start = new GlassDraw(left, material, GlassDraw.Profile.SNELL, new GlassDraw.ForegroundBounds(22, 17, 40, 12));
        var end = new GlassDraw(right, material, GlassDraw.Profile.SNELL, new GlassDraw.ForegroundBounds(40, 25, 44, 14));
        var legacy = new GlassDraw(left, material, 0, 0, 0);

        assertEquals(new GlassDraw.ForegroundBounds(31, 21, 42, 13), start.interpolate(end, .5f).foregroundBounds());
        assertEquals(GlassDraw.Profile.SNELL, start.interpolate(end, .5f).profile());
        assertEquals(GlassDraw.Profile.LEGACY, legacy.profile());
        assertNull(legacy.foregroundBounds());
        assertThrows(IllegalArgumentException.class, () -> start.interpolate(legacy, .5f));
        assertThrows(IllegalArgumentException.class, () -> new GlassDraw.ForegroundBounds(0, 0, Float.NaN, 8));
    }
}
