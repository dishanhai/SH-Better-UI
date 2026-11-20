package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicListAnchorTest {
    @Test void keepsRepeatedOccurrenceAndSubRowPositionWhenRowsMove() {
        var anchor = MusicListAnchor.capture(List.of("a", "b", "a", "c", "d"), Function.identity(), 2.375, 10);
        assertEquals(3.375, anchor.resolve(List.of("new", "a", "b", "a", "c", "d"), Function.identity(), 2, 10));
        assertEquals(2.0, anchor.resolve(List.of("new", "a", "b", "a", "c", "d"), Function.identity(), 4, 10));
    }

    @Test void clampsMissingRowsAndDoesNotReuseAnotherAccountGeneration() {
        var anchor = MusicListAnchor.capture(List.of("a", "b", "c", "d"), Function.identity(), 2.375, 10);
        assertEquals(1.0, anchor.resolve(List.of("d", "b", "a"), Function.identity(), 2, 10));
        assertEquals(0.0, anchor.resolve(List.of("a", "b", "c", "d"), Function.identity(), 1, 11));
    }

    @Test void appendingDuringInertiaPreservesDrawnFractionRatherThanSnapping() {
        var scroll = new ScrollRegionState();
        scroll.pixelRows();
        scroll.configure(0, 20, 5);
        scroll.restoreViewportOffset(8.25);
        scroll.wheel(-1, 100, false);
        scroll.advance(115, false);
        double drawn = scroll.viewportOffset();
        assertTrue(drawn > 8.25 && drawn < 9.25);
        scroll.configure(scroll.offset(), 40, 5);
        assertEquals(drawn, scroll.viewportOffset());
        assertFalse(scroll.atForwardLimit());
    }
}
