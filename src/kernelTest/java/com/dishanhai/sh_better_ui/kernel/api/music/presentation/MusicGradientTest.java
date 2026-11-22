// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicGradientTest {
    @Test
    void everyPresetMovesLoopsAndHasADistinctStaticPalette() {
        var periods = Map.of(MusicGradient.RAINBOW, 36_000, MusicGradient.AURORA, 32_000,
                MusicGradient.AFTERGLOW, 44_000, MusicGradient.MOONLIT_TIDE, 52_000);
        var staticPalettes = new HashSet<ArtworkPalette>();
        periods.forEach((gradient, period) -> {
            var start = gradient.sample(0, false, false);
            assertNotEquals(start, gradient.sample(period / 3, false, false), gradient.name());
            assertEquals(start, gradient.sample(period, false, false), gradient.name());
            assertEquals(start, gradient.sample(period - 1, false, false), gradient.name());
            assertEquals(start, gradient.sample(-period, false, false), gradient.name());
            assertEquals(start, gradient.sample(period / 3, true, false), gradient.name());
            assertTrue(staticPalettes.add(start), gradient.name());
        });
    }

    @Test
    void eachColorCycleKeepsLightForegroundReadable() {
        double foreground = ArtworkPalette.luminance(0xffb9d4d5);
        for (var gradient : MusicGradient.values()) {
            for (int time = 0; time < 52_000; time += 500) {
                var palette = gradient.sample(time, false, false);
                for (int color : new int[]{palette.topLeft(), palette.topRight(), palette.bottomLeft(), palette.bottomRight()}) {
                    assertTrue((foreground + .05) / (ArtworkPalette.luminance(color) + .05) >= 4.5,
                            gradient.name());
                }
            }
        }
    }
}
