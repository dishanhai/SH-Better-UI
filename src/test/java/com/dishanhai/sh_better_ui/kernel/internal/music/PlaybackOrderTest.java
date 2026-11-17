// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicQueue.Mode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackOrderTest {
    @Test void sequentialBoundariesAndRepeatOneExplicitSkip() {
        var order = new PlaybackOrder(); order.reset(3, 0);
        assertFalse(order.canPrevious()); assertEquals(-1, order.previous());
        assertEquals(1, order.next(true)); order.mode(Mode.REPEAT_ONE);
        assertEquals(1, order.next(true)); assertEquals(2, order.next(false));
        assertFalse(order.canNext()); assertEquals(-1, order.next(false));
        assertEquals(2, order.next(true)); assertEquals(1, order.previous());
    }
    @Test void listLoopWrapsInBothDirectionsAndSingleEntryLoops() {
        var order = new PlaybackOrder(); order.mode(Mode.REPEAT_ALL); order.reset(3, 2);
        assertEquals(0, order.next(true)); assertEquals(2, order.previous());
        order.reset(1, 0); assertEquals(0, order.next(true)); assertEquals(0, order.previous());
    }
    @Test void shuffleVisitsEveryPositionBeforeRefillAndPreviousRetracesHistory() {
        var order = new PlaybackOrder(new Random(8)); order.mode(Mode.SHUFFLE); order.reset(5, 2);
        var visited = new ArrayList<Integer>(); visited.add(2);
        for (int i = 0; i < 4; i++) visited.add(order.next(true));
        assertEquals(5, new HashSet<>(visited).size());
        assertEquals(visited.get(3), order.previous()); assertEquals(visited.get(4), order.next(false));
        assertNotEquals(visited.get(4), order.next(true));
    }
    @Test void detachedAndEmptyQueuesNeverAdvanceInAnyMode() {
        var order = new PlaybackOrder();
        for (var mode : Mode.values()) {
            order.mode(mode); order.reset(3, -1);
            assertFalse(order.canNext()); assertFalse(order.canPrevious());
            assertEquals(-1, order.next(true)); assertEquals(-1, order.previous());
            order.reset(0, -1); assertEquals(-1, order.next(true));
        }
    }
    @Test void shuffleHistoryIsBoundedAndEditsResetStalePositions() {
        var order = new PlaybackOrder(new Random(1)); order.mode(Mode.SHUFFLE); order.reset(4, 0);
        for (int i = 0; i < 1000; i++) assertTrue(order.next(true) >= 0);
        int count = 0; while (order.canPrevious()) { order.previous(); count++; }
        assertEquals(255, count);
        order.reset(1, 0); assertFalse(order.canPrevious()); assertFalse(order.canNext());
        assertEquals(0, order.next(true)); assertEquals(-1, order.next(false));
    }
    @Test void savedOrderRestoresShuffleHistoryAndRemainingBag() {
        var order = new PlaybackOrder(new Random(4)); order.mode(Mode.SHUFFLE); order.reset(4, 0);
        int first = order.next(true); var saved = order.save(); int next = order.next(true);
        order.reset(3, 1); order.restore(saved);
        assertEquals(0, order.previous()); assertEquals(first, order.next(false));
        assertEquals(next, order.next(true));
    }
}
