// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import org.junit.jupiter.api.Test;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import static org.junit.jupiter.api.Assertions.*;

class GlassSegmentStyleTest {
    @Test void opticalSurfacesShareThePresentedThumbGeometry() {
        var draws = GlassSegmentStyle.surfaces(20, 30, 180, 28, 42, 131, 0xffffffff, 0xff000000);
        assertEquals(2, draws.size());
        var track = draws.get(0);
        var thumb = draws.get(1);
        assertEquals(20, track.region().x());
        assertEquals(180, track.region().width());
        assertEquals(42, thumb.region().x());
        assertEquals(89, thumb.region().width());
        assertEquals(32, thumb.region().y());
        assertEquals(24, thumb.region().height());
        assertEquals(thumb.region().width(), thumb.foregroundBounds().width());
        assertEquals(GlassDraw.Profile.SNELL, thumb.profile());
        assertTrue(thumb.material().optics().refraction() > 0);
        assertEquals(GlassMaterial.Foreground.LIGHT, track.material().foreground());
        assertEquals(GlassMaterial.Foreground.DARK, thumb.material().foreground());
    }

    @Test void emptyThumbDoesNotAllocateInvalidOpticalRegion() {
        assertEquals(1, GlassSegmentStyle.surfaces(0, 0, 20, 14, 2, 2, -1, -1).size());
    }

    @Test void fallbackIsOpaqueAndOpticalTintKeepsTheSelectionColor() {
        int tint = GlassSegmentStyle.tint(0xff2266cc, 0xffffffff, true);
        assertEquals(0x2266cc, tint & 0xffffff);
        assertTrue((tint >>> 24) < 255);
        assertEquals(0xff2266cc, GlassSegmentStyle.tint(0x332266cc, 0xffffffff, false));
    }

    @Test void scrimProtectsBlackAndWhiteInkAgainstBothSceneExtremes() {
        assertSafeScrim(0xff20252c, 0xffffffff);
        assertSafeScrim(0xffe4e8ee, 0xff000000);
    }

    @Test void customMiddleGrayAndAccentUseTheirActualReadableInk() {
        for (int fill : new int[]{0xff777777, 0xff888888, 0xff667788, 0xff2266cc}) {
            for (int preferred : new int[]{0xffffffff, 0xff000000, 0xff999999}) {
                int ink = ContrastPolicy.readable(preferred, fill, fill, ContrastPolicy.TEXT_RATIO);
                assertSafeScrim(fill, ink);
            }
        }
    }

    private static void assertSafeScrim(int fill, int ink) {
        int scrim = GlassSegmentStyle.tint(fill, ink, true);
        assertEquals(fill & 0xffffff, scrim & 0xffffff);
        assertTrue((scrim >>> 24) >= 184);
        assertTrue(ContrastPolicy.ratio(ink, scrim, 0xff000000) >= ContrastPolicy.TEXT_RATIO);
        assertTrue(ContrastPolicy.ratio(ink, scrim, 0xffffffff) >= ContrastPolicy.TEXT_RATIO);
        for (int channel = 0; channel <= 255; channel++) {
            int scene = 0xff000000 | channel << 16 | channel << 8 | channel;
            assertTrue(ContrastPolicy.ratio(ink, scrim, scene) >= ContrastPolicy.TEXT_RATIO);
        }
        if ((scrim >>> 24) > 184) {
            int previous = (scrim & 0xffffff) | ((scrim >>> 24) - 1) << 24;
            boolean fails = false;
            for (int channel = 0; channel <= 255; channel++) {
                int scene = 0xff000000 | channel << 16 | channel << 8 | channel;
                fails |= ContrastPolicy.ratio(ink, previous, scene) < ContrastPolicy.TEXT_RATIO;
            }
            assertTrue(fails);
        }
        assertEquals(fill | 0xff000000, GlassSegmentStyle.tint(fill, ink, false));
    }

    @Test void perControlCacheTracksThemeAndInkChangesWithoutChangingFallback() {
        var cache = new GlassSegmentStyle.Scrim();
        int dark = cache.tint(0xff20252c, 0xffffffff, true);
        assertEquals(dark, cache.tint(0xff20252c, 0xffffffff, true));
        assertEquals(0xff20252c, cache.tint(0xff20252c, 0xffffffff, false));
        assertEquals(dark, cache.tint(0xff20252c, 0xffffffff, true));
        int light = cache.tint(0xffe4e8ee, 0xff000000, true);
        assertEquals(GlassSegmentStyle.tint(0xffe4e8ee, 0xff000000, true), light);
        assertNotEquals(dark, light);
        assertEquals(GlassSegmentStyle.tint(0xff777777, 0xff000000, true),
                cache.tint(0xff777777, 0xff000000, true));
    }

    @Test void middleGrayForegroundIsProtectedAcrossTheWholeSceneInterval() {
        // Mid-gray can contrast with both black and white in isolation; no intermediate
        // background may cross its luminance after the selected scrim is composited.
        assertSafeScrim(0xff000000, 0xff757575);
        assertSafeScrim(0xffffffff, 0xff757575);
    }
}
