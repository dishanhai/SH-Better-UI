// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFitTest {
    @Test
    void keepsRequestedSizeAndSnapsOnlyAnAutomaticReduction() {
        var scale = new TextFit.Scale(1.75f, 1, 2);
        assertEquals(1.75f, scale.within(2));
        assertEquals(1.5f, scale.within(1.74));
        assertEquals(1f, scale.within(1.49));
        assertEquals(0f, scale.within(0.99));
    }

    @Test
    void neverRoundsPastTheAvailableSizeOrUnderTheRequestedMinimum() {
        var scale = new TextFit.Scale(2, 1.25f, 2);
        float chosen = scale.within(1.4);
        assertTrue(chosen >= 1.25 && chosen <= 1.4);
        assertThrows(IllegalArgumentException.class, () -> new TextFit.Scale(1, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> scale.within(Double.NaN));
    }

    @Test
    void reservesSpaceForNoticeAndRestoresVisualOrder() {
        var rows = List.of("world", "player", "clock", "xyz");
        var fit = TextFit.selectRows(rows, row -> row.equals("xyz") ? 0 : 1,
                measured -> measured.size() <= 3, "notice");
        assertEquals(List.of("world", "xyz"), fit.visible());
        assertEquals(2, fit.omitted());
        var wide = TextFit.selectRows(rows, row -> 0, measured -> measured.size() <= 4, "notice");
        assertEquals(rows, wide.visible());
        assertEquals(0, wide.omitted());
    }

    @Test
    void skipsOversizedRowsAndHandlesDuplicateContent() {
        var rows = List.of("a", "oversized", "b", "a");
        var fit = TextFit.selectRows(rows, row -> row.equals("oversized") ? 0 : 1,
                measured -> measured.size() <= 4 && !measured.contains("oversized"), "notice");
        assertEquals(List.of("a", "b", "a"), fit.visible());
        assertEquals(1, fit.omitted());
        var empty = TextFit.selectRows(rows, row -> 0, measured -> measured.isEmpty(), "notice");
        assertTrue(empty.visible().isEmpty());
        assertEquals(4, empty.omitted());
    }
}
