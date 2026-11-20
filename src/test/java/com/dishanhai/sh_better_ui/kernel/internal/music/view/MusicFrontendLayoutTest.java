// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendLayoutTest {
    @Test void compactNavigationFollowsMiniPlayerAndReservesOnlyThreePrimaryDestinations() {
        var layout = compute(new Bounds(12, -3, 208, 240), Route.SEARCH, true);
        assertEquals(Mode.COMPACT, layout.mode());
        assertEquals(List.of(Route.DISCOVER, Route.LIBRARY, Route.SEARCH), List.copyOf(layout.tabs().keySet()));
        assertEquals(0, layout.tab(Route.ACCOUNTS).width());
        assertEquals(0, layout.tab(Route.QUEUE).width());
        assertTrue(layout.content().bottom() < layout.transport().y());
        assertTrue(layout.transport().bottom() < layout.navigation().y());
        assertEquals(layout.navigation().x(), layout.tab(Route.DISCOVER).x());
        assertEquals(layout.tab(Route.DISCOVER).right(), layout.tab(Route.LIBRARY).x());
        assertEquals(layout.tab(Route.LIBRARY).right(), layout.tab(Route.SEARCH).x());
        assertEquals(layout.navigation().right(), layout.tab(Route.SEARCH).right());
        assertTrue(layout.tab(Route.DISCOVER).width() >= 60);
        assertFalse(layout.tab(Route.DISCOVER).contains(layout.tab(Route.DISCOVER).right(), layout.navigation().y()));
        assertThrows(UnsupportedOperationException.class, () -> layout.tabs().clear());
    }

    @Test void desktopRailAppearsOnlyWhenRequestedAndDoesNotDuplicateMainTransportRoutes() {
        assertEquals(Mode.COMPACT, compute(new Bounds(0, 0, 559, 240), Route.DISCOVER, true).mode());
        assertEquals(Mode.DESKTOP, compute(new Bounds(0, 0, 560, 240), Route.DISCOVER, true).mode());
        var frame = new Bounds(13, 25, 864, 500);
        assertEquals(0, compute(new Bounds(13, 25, 863, 500), Route.DISCOVER, true).queueRail().width());
        assertEquals(0, compute(frame, Route.DISCOVER, false).queueRail().width());
        var layout = compute(frame, Route.DISCOVER, true);
        assertTrue(layout.queueRail().width() >= 148);
        assertTrue(layout.content().right() < layout.queueRail().x());
        for (var route : List.of(Route.NOW_PLAYING, Route.LYRICS, Route.QUEUE))
            assertEquals(0, compute(frame, route, true).queueRail().width());
    }

    @Test void constrainedScreensHaveNoInvisibleClickTargets() {
        for (var frame : List.of(new Bounds(4, 7, 207, 240), new Bounds(4, 7, 208, 239),
                new Bounds(4, 7, 0, 0))) {
            var layout = compute(frame, Route.DISCOVER, true);
            assertFalse(layout.supported());
            for (var control : List.of(layout.tab(Route.SEARCH), layout.back(), layout.utility(),
                    layout.transportControls().playPause(), layout.body(), layout.queueRail())) {
                assertEquals(new Bounds(4, 7, 0, 0), control);
                assertFalse(control.contains(4, 7));
            }
        }
    }

    @Test void nonOverlayingClippedGeometryAcrossRoutesAndThresholds() {
        for (int width : new int[]{208, 209, 240, 320, 559, 560, 561, 863, 864, 1280, 4096})
            for (int height : new int[]{240, 241, 320, 540, 1080})
                for (var route : Route.values())
                    for (boolean useRail : new boolean[]{false, true})
                        verify(compute(new Bounds(17, -9, width, height), route, useRail));
    }

    private static void verify(MusicFrontendLayout layout) {
        assertTrue(layout.supported());
        var all = List.of(layout.back(), layout.utility(), layout.navigation(), layout.content(), layout.transport(), layout.queueRail());
        for (int i = 0; i < all.size(); i++) {
            assertInside(layout.frame(), all.get(i));
            for (int j = 0; j < i; j++) assertDisjoint(all.get(i), all.get(j));
        }
        assertInside(layout.content(), layout.header());
        assertInside(layout.content(), layout.body());
        assertTrue(layout.body().height() >= 64);
        assertDisjoint(layout.header(), layout.body());
        for (var tab : layout.tabs().values()) assertInside(layout.navigation(), tab);
        var bar = layout.transportControls();
        var items = List.of(bar.track(), bar.previous(), bar.playPause(), bar.next(), bar.progress());
        for (int i = 0; i < items.size(); i++) {
            assertInside(layout.transport(), items.get(i));
            for (int j = 0; j < i; j++) assertDisjoint(items.get(i), items.get(j));
        }
    }

    private static void assertInside(Bounds parent, Bounds child) {
        assertTrue(child.x() >= parent.x() && child.y() >= parent.y()
                && child.right() <= parent.right() && child.bottom() <= parent.bottom(),
                () -> child + " not in " + parent);
    }

    private static void assertDisjoint(Bounds a, Bounds b) {
        assertFalse(a.width() != 0 && a.height() != 0 && b.width() != 0 && b.height() != 0
                && a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y(),
                () -> a + " overlaps " + b);
    }
}
