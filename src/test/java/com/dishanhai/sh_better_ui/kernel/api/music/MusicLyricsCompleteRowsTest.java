package com.dishanhai.sh_better_ui.kernel.api.music;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MusicLyricsCompleteRowsTest {
    @Test void twelvePixelRowsRequireTheirEntireBoundsIncludingFractionalEdges() {
        assertTrue(MusicLyricsView.completeRowVisible(100,12,100,24));
        assertTrue(MusicLyricsView.completeRowVisible(112,12,100,24));
        assertFalse(MusicLyricsView.completeRowVisible(112.01,12,100,24));
        assertFalse(MusicLyricsView.completeRowVisible(99.99,12,100,24));
        assertFalse(MusicLyricsView.completeRowVisible(112,12,100,23));
        assertTrue(MusicLyricsView.completeRowVisible(100,12,100,23));
        assertFalse(MusicLyricsView.completeRowVisible(100,12,100,11));
        assertFalse(MusicLyricsView.completeRowVisible(Double.NaN,12,100,24));
        assertFalse(MusicLyricsView.completeRowVisible(100,Double.POSITIVE_INFINITY,100,24));
    }
}
