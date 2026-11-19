package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnlineCoverRevealTest {
    @Test void currentArtworkEasesInWithoutRestartingEachFrame() {
        try (var reveal = new OnlineCoverReveal()) {
            assertEquals(0f, reveal.sample(5, false, 100, false));
            assertEquals(0f, reveal.sample(5, true, 500, false));
            assertEquals(.5f, reveal.sample(5, true, 590, false), .001f);
            assertEquals(.5f, reveal.sample(5, true, 590, false), .001f);
            assertEquals(1f, reveal.sample(5, true, 680, false));
            assertEquals(1f, reveal.sample(5, true, 690, false));
        }
    }

    @Test void replacementAndMissingImagesNeverInheritOldOpacity() {
        try (var reveal = new OnlineCoverReveal()) {
            reveal.sample(5, true, 100, false);
            assertTrue(reveal.sample(5, true, 180, false) > 0f);
            assertEquals(0f, reveal.sample(6, true, 181, false));
            assertTrue(reveal.sample(6, true, 271, false) > 0f);
            assertEquals(0f, reveal.sample(6, false, 272, false));
            assertEquals(0f, reveal.sample(6, true, 300, false));
        }
    }

    @Test void reducedMotionOrHighContrastSettlesAndDoesNotReplay() {
        try (var reveal = new OnlineCoverReveal()) {
            reveal.sample(5, true, 100, false);
            assertEquals(1f, reveal.sample(5, true, 130, true));
            assertEquals(1f, reveal.sample(5, true, 150, false));
            assertEquals(1f, reveal.sample(6, true, 160, true));
        }
    }
}
