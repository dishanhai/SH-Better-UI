// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.dock;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockEdge;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockMode;
import com.dishanhai.sh_better_ui.kernel.api.dock.DockRequest;
import com.dishanhai.sh_better_ui.kernel.api.dock.ExpansionLayoutMode;
import com.dishanhai.sh_better_ui.kernel.api.dock.WindowPlacement;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static com.dishanhai.sh_better_ui.kernel.api.dock.DockEdge.*;
import static com.dishanhai.sh_better_ui.kernel.api.dock.ExpansionLayoutMode.*;
import static org.junit.jupiter.api.Assertions.*;

class DockLayoutTest {
    @Test
    void eachEdgeAnchorsAnimatedBoundsAndKeepsTheCompactTriggerInside() {
        Rect safe = rect(10, 20, 300, 160);
        var left = item("left", LEFT, 100, 80, 0.5, OVERLAY);
        var right = item("right", RIGHT, 100, 80, 0.5, OVERLAY);
        var top = item("top", TOP, 100, 80, 0.5, OVERLAY);
        var bottom = item("bottom", BOTTOM, 100, 80, 0.5, OVERLAY);
        var layout = DockLayout.solve(12, safe, List.of(left, right, top, bottom), 4);

        assertEquals(12, layout.version());
        assertEquals(rect(10, 20, 65, 50), layout.entries().get(left.id()).bounds());
        assertEquals(rect(245, 20, 65, 50), layout.entries().get(right.id()).bounds());
        assertEquals(rect(10, 20, 65, 50), layout.entries().get(top.id()).bounds());
        assertEquals(rect(10, 130, 65, 50), layout.entries().get(bottom.id()).bounds());
        assertEquals(rect(280, 20, 30, 20), layout.entries().get(right.id()).compactBounds());
        assertEquals(rect(10, 160, 30, 20), layout.entries().get(bottom.id()).compactBounds());
        layout.entries().values().forEach(entry -> {
            assertTrue(safe.contains(entry.bounds()));
            assertTrue(entry.bounds().contains(entry.compactBounds()));
        });
        assertThrows(UnsupportedOperationException.class, () -> layout.entries().clear());
    }

    @Test
    void groupsUseTheirHighestRankAndCompactCapacityIsReservedBeforeExpansion() {
        var group = id("tools");
        var head = new DockLayout.Item(id("head"), WindowPlacement.docked(LEFT, DockMode.AUTO_HIDE, 80, 70)
                .withGroup(group), 30, 20, 1, 3, 1, OVERLAY);
        var member = new DockLayout.Item(id("member"), head.placement(), 30, 20, 0, 0, 1, OVERLAY);
        var other = new DockLayout.Item(id("other"), head.placement().withGroup(null),
                30, 20, 0, 2, 5, OVERLAY);
        var safe = rect(0, 0, 80, 60);
        var result = DockLayout.solve(1, safe, List.of(other, member, head), 2);

        assertEquals(result, DockLayout.solve(1, safe, List.of(head, other, member), 2));
        assertEquals(List.of(head.id(), member.id(), other.id()), List.copyOf(result.entries().keySet()));
        assertEquals(rect(0, 0, 80, 38), result.entries().get(head.id()).bounds());
        assertEquals(rect(0, 40, 30, 20), result.entries().get(member.id()).bounds());
        assertFalse(result.entries().get(other.id()).visible());
        assertEquals(List.of(other.id()), result.collisions().overflowed());
        assertTrue(result.collisions().overlaps().isEmpty());

        var resized = DockLayout.solve(2, rect(0, 0, 80, 100), List.of(other, member, head), 2);
        assertTrue(resized.entries().get(other.id()).visible());
        assertTrue(resized.collisions().overflowed().isEmpty());
    }

    @Test
    void floatingAndDetachedWindowsClampWithoutChangingPreferredCoordinates() {
        var floating = WindowPlacement.docked(BOTTOM, DockMode.AUTO_HIDE, 400, 50)
                .withRequest(DockRequest.floating(999, -100));
        var detached = floating.withRequest(DockRequest.detached(999, 999)).withSize(40, 30);
        var safe = rect(8, 12, 100, 70);
        var result = DockLayout.solve(2, safe, List.of(
                new DockLayout.Item(id("floating"), floating, 30, 20, 1, 0, 2, PUSH),
                new DockLayout.Item(id("detached"), detached, 30, 20, 1, 0, 3, PUSH)), 3);

        assertEquals(BOTTOM, floating.edge());
        assertEquals(floating, result.entries().get(id("floating")).placement());
        assertEquals(rect(8, 12, 100, 50), result.entries().get(id("floating")).bounds());
        assertEquals(rect(68, 52, 40, 30), result.entries().get(id("detached")).bounds());
        assertEquals(rect(68, 52, 40, 10), result.collisions().overlaps().get(0).bounds());
        assertEquals(0, result.pushInsets().bottom());
        assertEquals(detached.withRequest(DockRequest.edge(BOTTOM, DockMode.AUTO_HIDE)).y(), detached.y());

        assertTrue(DockLayout.solve(3, safe, List.of(), 3).collisions().overlaps().isEmpty());
    }

    @Test
    void pushUsesActualVisibleThicknessAndCapsOpposingInsets() {
        var left = item("left", LEFT, 90, 40, 1, PUSH);
        var right = item("right", RIGHT, 80, 40, 1, PUSH);
        var overlay = item("overlay", TOP, 60, 40, 1, OVERLAY);
        var compact = item("compact", BOTTOM, 60, 40, 0, PUSH);
        var result = DockLayout.solve(4, rect(0, 0, 100, 60), List.of(left, right, overlay, compact), 4);

        assertEquals(0, result.pushInsets().top());
        assertEquals(20, result.pushInsets().bottom());
        assertEquals(100, result.pushInsets().left() + result.pushInsets().right(), 0.0001);
        assertEquals(90.0 / 80, result.pushInsets().left() / result.pushInsets().right(), 0.0001);
    }

    @Test
    void snapUsesSafeEdgesAndDeterministicCornerTies() {
        Rect safe = rect(10, 20, 100, 80);
        assertEquals(LEFT, DockLayout.snap(rect(12, 22, 20, 20), safe, 2).orElseThrow());
        assertEquals(RIGHT, DockLayout.snap(rect(89, 50, 20, 20), safe, 2).orElseThrow());
        assertEquals(TOP, DockLayout.snap(rect(70, 21, 20, 20), safe, 2).orElseThrow());
        assertEquals(BOTTOM, DockLayout.snap(rect(60, 79, 20, 20), safe, 2).orElseThrow());
        assertTrue(DockLayout.snap(rect(60, 60, 20, 20), safe, 2).isEmpty());
    }

    @Test
    void tinyAndEmptyViewportsKeepBoundsValidAndDiagnoseOverflow() {
        var first = item("a", RIGHT, 100, 100, 1, PUSH);
        var second = item("b", RIGHT, 100, 100, 0, PUSH);
        Rect tiny = rect(2, 4, 3, 5);
        var result = DockLayout.solve(1, tiny, List.of(second, first), 0);
        assertEquals(tiny, result.entries().get(first.id()).bounds());
        assertEquals(List.of(second.id()), result.collisions().overflowed());

        var empty = DockLayout.solve(2, rect(2, 4, 0, 5), List.of(second, first), 0);
        assertEquals(List.of(first.id(), second.id()), empty.collisions().overflowed());
        assertEquals(0, empty.pushInsets().right());
        assertTrue(empty.entries().values().stream().noneMatch(entry -> entry.visible()));
    }

    @Test
    void fractionalZeroGapPackingKeepsHalfOpenBoundsDisjoint() {
        var windows = IntStream.range(0, 50).mapToObj(index -> new DockLayout.Item(id("w" + index),
                WindowPlacement.docked(RIGHT, DockMode.AUTO_HIDE, 0.8, 0.1 + index * 0.013), 0.31, 0.01,
                (index + 0.1) / 50, 0, 0, OVERLAY)).toList();
        Rect safe = rect(1.8, 2.3, 5.1, 4.7);
        var layout = DockLayout.solve(1, safe, windows, 0);

        assertTrue(layout.collisions().overlaps().isEmpty());
        assertTrue(layout.collisions().overflowed().isEmpty());
        layout.entries().values().forEach(entry -> {
            assertTrue(safe.contains(entry.bounds()));
            assertTrue(entry.bounds().contains(entry.compactBounds()));
        });
    }

    @Test
    void invalidCoordinatesAndDuplicateIdsFailEarly() {
        var item = item("same", LEFT, 100, 100, 1, OVERLAY);
        assertThrows(IllegalArgumentException.class, () -> DockLayout.solve(1, rect(0, 0, 100, 100),
                List.of(item, item), 3));
        assertThrows(IllegalArgumentException.class, () -> DockLayout.solve(1,
                new Rect(0, 0, 100, 100, Space.FRAMEBUFFER), List.of(item), 3));
        assertThrows(IllegalArgumentException.class, () -> DockRequest.floating(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> item.placement().withSize(0, 100));
    }

    private static DockLayout.Item item(String name, DockEdge edge, double width, double height, double progress,
                                        ExpansionLayoutMode mode) {
        return new DockLayout.Item(id(name), WindowPlacement.docked(edge, DockMode.AUTO_HIDE, width, height),
                30, 20, progress, 0, 0, mode);
    }

    private static UiId id(String path) {
        return new UiId("docktest", path);
    }

    private static Rect rect(double x, double y, double width, double height) {
        return new Rect(x, y, width, height, Space.GUI);
    }
}
