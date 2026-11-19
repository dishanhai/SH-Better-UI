// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicMetadataRevealTest {
    @Test void initialAndNoTrackStayStill() {
        var reveal = new MusicMetadataReveal();
        assertEquals(1f, reveal.sample(null, 0, false).scale());
        assertEquals(1f, reveal.sample("qq:a", 10, false).scale());
        assertEquals(1f, reveal.sample(null, 20, false).opacity());
    }
    @Test void realTrackChangesSettleInBounds() {
        var reveal = new MusicMetadataReveal();
        reveal.sample("qq:a", 0, false);
        var start = reveal.sample("qq:b", 10, false);
        assertEquals(.975f, start.scale(), .0001f);
        assertEquals(.82f, start.opacity(), .0001f);
        var middle = reveal.sample("qq:b", 150, false);
        assertTrue(middle.scale() > start.scale() && middle.scale() < 1f);
        assertEquals(1f, reveal.sample("qq:b", 290, false).scale());
        assertEquals(1f, reveal.sample("qq:b", 2000, false).opacity());
    }
    @Test void interruptionContinuesCurrentTransformAndOnlyLatestSettles() {
        var reveal = new MusicMetadataReveal();
        reveal.sample("qq:a", 0, false);
        reveal.sample("qq:b", 10, false);
        var before = reveal.sample("qq:b", 110, false);
        assertEquals(before, reveal.sample("netease:b", 110, false));
        assertEquals(1f, reveal.sample("netease:b", 390, false).scale());
    }
    @Test void reducedMotionTerminatesRatherThanResumes() {
        var reveal = new MusicMetadataReveal();
        reveal.sample("qq:a", 0, false);
        reveal.sample("qq:b", 10, false);
        assertEquals(1f, reveal.sample("qq:b", 20, true).scale());
        assertEquals(1f, reveal.sample("qq:b", 30, false).scale());
        assertEquals(1f, reveal.sample("qq:c", 40, true).opacity());
    }
    @Test void backwardClockCannotOvershoot() {
        var reveal = new MusicMetadataReveal();
        reveal.sample("a", 0, false);
        var start = reveal.sample("b", 100, false);
        assertEquals(start, reveal.sample("b", 90, false));
    }
}
