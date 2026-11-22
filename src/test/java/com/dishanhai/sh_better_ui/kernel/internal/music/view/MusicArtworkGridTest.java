// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicArtworkGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicArtworkGridTest {
    @Test void adaptsColumnsAndFitsCaptionsWithoutCoveringTheRail() {
        var wide = MusicArtworkGrid.fit(720, 398);
        assertEquals(4, wide.columns());
        assertEquals(2, wide.rowCount(5));
        var narrow = MusicArtworkGrid.fit(156, 66);
        assertEquals(1, narrow.columns());
        assertEquals(1, narrow.visibleRows());
        assertEquals(66, narrow.cardHeight() + MusicArtworkGrid.TOP);
        assertTrue(wide.columns() * wide.side() + (wide.columns() - 1) * MusicArtworkGrid.GAP <= 720 - 12);
        assertEquals(0, MusicArtworkGrid.fit(156, 30).visibleRows());
    }

    @Test void portraitCropsTheCenterAndPreservesAlphaInsideTheCircle() {
        int size = MusicArtworkThumbnail.size(16, 8);
        int[] pixels = MusicArtworkThumbnail.pixels(16, 8, .5f, (x, y) -> 0x80000000 | x);
        assertEquals(8, size);
        assertEquals(0, pixels[0] >>> 24);
        assertEquals(0x80000008, pixels[4 * size + 4]);
        assertEquals(0x80000005, pixels[4 * size + 1]);
    }
}
