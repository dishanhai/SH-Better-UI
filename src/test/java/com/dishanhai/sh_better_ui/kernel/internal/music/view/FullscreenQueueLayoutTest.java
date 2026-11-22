// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FullscreenQueueLayoutTest {
    @Test
    void compactColumnKeepsModeButtonsAndRoomForQueueContent() {
        var layout = layout(94, 100, false, 0);

        assertNotNull(layout.modes());
        assertEquals(0, layout.headerHeight());
        assertEquals(16, (layout.modes().width() - layout.modeGap() * 4) / 5);
        assertTrue(layout.viewport().y() > layout.modes().bottom());
        assertTrue(layout.viewport().height() >= 36);
    }

    @Test
    void shortColumnFitsReviewAndNotesEvenWhenTheirLabelsCannotShareARow() {
        var layout = layout(110, 115, true, 90);

        assertNotNull(layout.modes());
        assertNotNull(layout.review());
        assertEquals(layout.width(), layout.review().width());
        assertEquals(0, layout.notesTitleWidth());
        assertTrue(layout.viewport().y() > layout.review().bottom());
        assertTrue(layout.viewport().height() >= 18);
    }

    @Test
    void tallColumnShowsCoverAndKeepsModeTargetsStableWhenSwitchingViews() {
        var queue = layout(240, 210, false, 0);
        var notes = layout(240, 210, true, 90);

        assertEquals(46, notes.headerHeight());
        assertEquals(queue.modes(), notes.modes());
        assertTrue(notes.notesTitleWidth() >= 90);
        assertTrue(notes.viewport().height() >= 36);
        assertEquals(118, notes.viewport().right() - notes.review().x());
    }

    @Test
    void veryShortColumnStillShowsNotes() {
        var layout = layout(90, 42, true, 90);

        assertNull(layout.review());
        assertNull(layout.modes());
        assertEquals(30, layout.viewport().height());
    }

    private static FullscreenQueueLayout layout(double width, double height, boolean notes, double titleWidth) {
        return FullscreenQueueLayout.of(new Box(30, 50, width, height), 5, 36, 18, true, notes, titleWidth, 118);
    }
}
