package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicHudLayoutTest {
    @Test void fullCardHasCoverTimelineAndLyricsWithoutOverlap() {
        var l = MusicHudLayout.arrange(new Bounds(12, 20, 230, 112), 9);
        assertTrue(l.visible());
        assertTrue(l.metadata().x() > l.cover().right());
        assertTrue(l.progress().y() > l.cover().bottom());
        assertTrue(l.lyrics().y() > l.progress().bottom() + 9);
        for (var child : new Bounds[]{l.cover(), l.metadata(), l.progress(), l.lyrics()}) {
            assertTrue(child.x() >= l.panel().x());
            assertTrue(child.y() >= l.panel().y());
            assertTrue(child.right() <= l.panel().right());
            assertTrue(child.bottom() <= l.panel().bottom());
        }
    }
    @Test void compactCardOmitsSectionsThatDoNotFit() {
        var l = MusicHudLayout.arrange(new Bounds(0, 0, 230, 56), 9);
        assertTrue(l.visible());
        assertEquals(0, l.progress().width());
        assertEquals(0, l.lyrics().height());
        assertFalse(MusicHudLayout.arrange(new Bounds(0, 0, 80, 20), 9).visible());
    }
    @Test void normalizedPositionUsesRemainingRoomAndSurvivesResize() {
        assertEquals(new Bounds(270, 188, 230, 112), MusicHudLayout.anchored(500, 300, 230, 112, 1, 1));
        assertEquals(new Bounds(0, 0, 100, 60), MusicHudLayout.anchored(100, 60, 230, 112, 1, 1));
        assertEquals(new Bounds(50, 30, 100, 40), MusicHudLayout.anchored(200, 100, 100, 40, Double.NaN, Double.POSITIVE_INFINITY));
        assertEquals(new Bounds(0, 60, 100, 40), MusicHudLayout.anchored(200, 100, 100, 40, -3, 2));
    }
    @Test void unknownTimelineNeverPretendsToHaveProgress() {
        assertEquals(0, MusicHudLayout.progress(-1, 120));
        assertEquals(0, MusicHudLayout.progress(20, 0));
        assertEquals(.5, MusicHudLayout.progress(50, 100));
        assertEquals(1, MusicHudLayout.progress(150, 100));
    }
}
