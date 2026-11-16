// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePickerStateTest {
    private static ResourcePickerState.Entry<String> entry(String id, String name, int count) {
        return new ResourcePickerState.Entry<>(id, id, name, id.split(":")[0], List.of("c:stones"),
                () -> "Building block", count, id);
    }
    @Test void selectionIsStagedAndConfirmedOnlyOnce() {
        var stone = entry("minecraft:stone", "石头", 1);
        var glass = entry("minecraft:glass", "玻璃", 2);
        var state = new ResourcePickerState<>(stone);
        state.choose(glass, false);
        state.choose(glass, false);
        assertEquals(glass, state.selected());
        assertEquals(2, state.amount());
        assertEquals(glass, state.finish(true).orElseThrow());
        assertTrue(state.finish(true).isEmpty());
        state.choose(stone, false);
        assertEquals(glass, state.selected());
        var cancelled = new ResourcePickerState<>(stone);
        cancelled.choose(glass, false);
        assertTrue(cancelled.finish(false).isEmpty());
    }
    @Test void rightClickTogglesAndInvalidNumbersNeverReplaceTheAmount() {
        var stone = entry("minecraft:stone", "Stone", 1);
        var state = new ResourcePickerState<>(stone);
        state.choose(stone, true);
        assertNull(state.selected());
        state.choose(stone, false);
        assertTrue(state.amount("64"));
        assertFalse(state.amount(""));
        assertFalse(state.amount("0"));
        assertFalse(state.amount("2147483648"));
        assertEquals(64, state.amount());
        state.stepAmount(-1, 100);
        assertEquals(1, state.amount());
        state.stepAmount(1, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, state.amount());
    }
    @Test void sourcesFiltersAndSortingKeepTheExistingSelection() {
        var stone = entry("minecraft:stone", "Stone", 2);
        var granite = entry("test:granite", "Granite", 64);
        var state = new ResourcePickerState<>(stone);
        state.source(List.of(stone, granite));
        assertEquals(List.of(stone), state.search("@minecraft #stones *stone", "", ResourcePickerState.Sort.DEFAULT, false));
        assertEquals(List.of(granite), state.search("$building", "test", ResourcePickerState.Sort.DEFAULT, false));
        assertEquals(List.of(granite, stone), state.search("", "", ResourcePickerState.Sort.COUNT, false));
        state.source(List.of());
        assertEquals(stone, state.selected());
    }
    @Test void additionalTextMatchesCombineWithExistingFilters() {
        var stone = entry("minecraft:stone", "石头", 1);
        var other = entry("test:stone", "石头", 1);
        var state = new ResourcePickerState<String>(null);
        state.source(List.of(stone, other));
        assertEquals(List.of(stone), state.search("st @minecraft", "", ResourcePickerState.Sort.DEFAULT, false,
                (entry, word) -> word.equals("st")));
        assertTrue(state.search("st #missing", "", ResourcePickerState.Sort.DEFAULT, false,
                (entry, word) -> true).isEmpty());
    }
    @Test void tooltipWorkIsLazyAndSmallViewportKeepsThreeUsableAreas() {
        var calls = new AtomicInteger();
        var row = new ResourcePickerState.Entry<>("a", "a:stone", "Stone", "A", List.of(),
                () -> { calls.incrementAndGet(); return "Building"; }, 1, "a");
        var state = new ResourcePickerState<>(row);
        state.source(List.of(row));
        state.search("stone", "", ResourcePickerState.Sort.DEFAULT, false);
        assertEquals(0, calls.get());
        state.search("$building", "", ResourcePickerState.Sort.DEFAULT, false);
        assertEquals(1, calls.get());
        for (int[] size : List.of(new int[]{320, 180}, new int[]{420, 240}, new int[]{800, 480})) {
            var layout = ResourcePickerState.layout(size[0], size[1]);
            assertTrue(layout.gridWidth() >= 58);
            assertTrue(layout.contentHeight() >= 58);
            assertTrue(layout.selectionX() + layout.selectionWidth() <= size[0]);
            assertTrue(layout.footerY() + 20 <= size[1]);
        }
    }
}
