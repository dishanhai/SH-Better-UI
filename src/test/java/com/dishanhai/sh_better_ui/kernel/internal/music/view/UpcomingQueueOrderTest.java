package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class UpcomingQueueOrderTest {
    @Test void shuffleThenRestoreKeepsTheActiveOccurrenceAndExplicitNextSongs() {
        var queue = new PlaybackQueueOrder<>(List.of("a", "b", "c", "d"), track -> track, null);

        assertEquals(0, queue.shuffleFrom(1, 0));
        assertEquals("b", queue.get(0));
        var pending = new String("d");
        queue.add(1, pending);

        var resumed = new PlaybackQueueOrder<>(queue, track -> track, queue.getOriginalOrder());
        assertEquals(1, resumed.restoreFrom(0, 1));
        assertEquals(List.of("a", "b", "d", "c", "d"), resumed);
        assertSame(pending, resumed.get(2));
    }

    @Test void sequentialAndTheCurrentShuffleRoundShowOnlyTheRemainingOccurrences() {
        var order = UpcomingQueueOrder.indices(7, 2, 2, false, false, false);
        assertEquals(List.of(3, 4, 5, 6), order.getNext());
        assertEquals(List.of(), order.getRadio());
        assertEquals(List.of(), UpcomingQueueOrder.indices(7, 6, 0, false, false, false).getNext());
        assertEquals(List.of(), UpcomingQueueOrder.indices(7, -1, 0, false, false, false).getNext());
    }

    @Test void repeatOneKeepsExplicitPendingEntriesAndRepeatAllShowsTheWrap() {
        assertEquals(List.of(3, 4), UpcomingQueueOrder.indices(7, 2, 2, true, false, false).getNext());
        assertEquals(List.of(), UpcomingQueueOrder.indices(7, 2, 0, true, false, false).getNext());
        assertEquals(List.of(5, 6, 0, 1, 2, 3),
                UpcomingQueueOrder.indices(7, 4, 0, false, true, false).getNext());
    }

    @Test void personalFmShowsOnlyKnownRadioTracksAndKeepsManualEntriesSeparate() {
        var order = UpcomingQueueOrder.indices(6, 1, 2, false, false, true);
        assertEquals(List.of(2, 3), order.getNext());
        assertEquals(List.of(4, 5), order.getRadio());
        assertEquals(List.of(), UpcomingQueueOrder.indices(1, 0, 0, false, false, true).getRadio());
    }
}
