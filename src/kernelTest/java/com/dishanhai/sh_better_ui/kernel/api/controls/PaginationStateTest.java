// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PaginationStateTest {
    @Test void emptyDataAndNavigationClampWithoutWrapping() {
        var state = new PaginationState(0, 25, 8);
        assertEquals(0, state.page());
        assertEquals(0, state.pageCount());
        assertFalse(state.select(1));
        state.setTotalItems(101);
        assertEquals(1, state.page());
        assertEquals(5, state.pageCount());
        assertFalse(state.previous());
        assertTrue(state.select(100));
        assertEquals(5, state.page());
        assertFalse(state.next());
        assertEquals(100, state.fromIndex());
        assertEquals(101, state.toIndex());
        state.setTotalItems(3);
        assertEquals(1, state.page());
    }

    @Test void resizingKeepsTheFirstVisibleItemAndHandlesLongCounts() {
        var state = new PaginationState(1000, 25, 4);
        state.setPageSize(10);
        assertEquals(8, state.page());
        assertEquals(70, state.fromIndex());
        assertEquals(80, state.toIndex());
        var huge = new PaginationState(Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(4_294_967_299L, huge.pageCount());
        assertEquals(Long.MAX_VALUE, huge.toIndex());
        assertThrows(IllegalArgumentException.class, () -> state.setPageSize(0));
        assertThrows(IllegalArgumentException.class, () -> state.setTotalItems(-1));
    }

    @Test void rangeUsesEllipsesOnlyForActualGaps() {
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), pages(PaginationRange.items(5, 2, 1, 1)));
        assertEquals(List.of(1L, 0L, 4L, 5L, 6L, 0L, 10L), pages(PaginationRange.items(10, 5, 1, 1)));
        assertEquals(List.of(1L, 2L, 3L, 4L, 0L, 10L), pages(PaginationRange.items(10, 3, 1, 1)));
        assertTrue(PaginationRange.items(0, 0, 1, 1).isEmpty());
    }

    @Test void rangeIsBoundedAndOrderedEvenAtLongMaximum() {
        for (long selected : new long[]{1, 2, Long.MAX_VALUE - 1, Long.MAX_VALUE}) {
            var items = PaginationRange.items(Long.MAX_VALUE, selected, 1, 1);
            assertTrue(items.size() <= 7);
            long previous = 0;
            for (var item : items) if (!item.ellipsis()) {
                assertTrue(item.page() > previous);
                previous = item.page();
            }
            assertTrue(items.stream().anyMatch(item -> item.page() == selected));
        }
        assertThrows(IllegalArgumentException.class, () -> PaginationRange.items(10, 1, -1, 1));
    }

    private static List<Long> pages(List<PaginationRange.Item> items) {
        return items.stream().map(PaginationRange.Item::page).toList();
    }
}
