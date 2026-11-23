// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddedMusicSliderTest {
    @Test
    void capturesOnlyAnOnTrackPressAndCommitsFinalPositionOnce() {
        var slider = new EmbeddedMusicSlider();
        slider.layout(30, 40, 100, 10);
        slider.sync("track", 20_000, 100_000, true);

        assertFalse(slider.press(55, 34));
        assertTrue(slider.release(55).isEmpty());
        assertTrue(slider.press(55, 36));
        assertEquals(25_000, slider.position());
        slider.drag(90);
        assertEquals(60_000, slider.position());
        assertEquals(100_000, slider.release(170).orElseThrow().positionMs());
        assertTrue(slider.release(55).isEmpty());

        assertTrue(slider.press(55, 44));
        assertEquals(0, slider.release(-20).orElseThrow().positionMs());
    }

    @Test
    void restoresLatestSnapshotAndRejectsStaleReleases() {
        var slider = new EmbeddedMusicSlider();
        slider.layout(30, 40, 100, 10);
        slider.sync("track", 20_000, 100_000, true);
        assertTrue(slider.press(55, 40));
        slider.drag(105);
        slider.sync("track", 21_000, 100_000, true);
        assertEquals(75_000, slider.position());
        assertTrue(slider.cancel());
        assertEquals(21_000, slider.position());
        assertTrue(slider.release(105).isEmpty());

        assertTrue(slider.press(55, 40));
        slider.sync("replacement", 2_000, 100_000, true);
        assertTrue(slider.release(105).isEmpty());
        assertEquals(2_000, slider.position());

        assertTrue(slider.press(55, 40));
        slider.sync("replacement", 3_000, 100_000, false);
        assertFalse(slider.hits(55, 40));
        assertTrue(slider.release(105).isEmpty());
        assertEquals(3_000, slider.position());
    }
}
