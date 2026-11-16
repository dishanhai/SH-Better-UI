// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicLayoutTest {
    @Test void responsivePagesKeepNavigationResultsAndPlaybackInsideTheViewport() {
        for (int width : new int[]{208, 260, 519, 520, 640, 960}) for (int height : new int[]{240, 263, 264, 300, 360, 540}) {
            var frame = new Bounds(19, 31, width, height); var page = OnlineMusicLayout.of(frame);
            assertTrue(page.supported());
            for (var region : new Bounds[]{page.navigation(), page.header(), page.content(), page.transport(), page.pager()}) contains(frame, region);
            assertTrue(page.content().bottom() < page.transport().y());
            assertTrue(page.navigation().bottom() < page.transport().y());
            if (page.sidebar()) assertTrue(page.navigation().right() < page.content().x());
            else assertTrue(page.navigation().bottom() < page.header().y());
            for (var row : page.rows()) { contains(page.content(), row); assertTrue(row.bottom() < page.pager().y()); }
        }
    }
    @Test void shortWindowsUseTheCompactHeaderAndTinyWindowsDoNotCreateOverlappingControls() {
        assertFalse(OnlineMusicLayout.of(new Bounds(0, 0, 960, 240)).sidebar());
        assertTrue(OnlineMusicLayout.of(new Bounds(0, 0, 520, 264)).sidebar());
        for (var box : new Bounds[]{new Bounds(0, 0, 207, 360), new Bounds(0, 0, 640, 239), new Bounds(0, 0, 0, 0)}) {
            var page = OnlineMusicLayout.of(box); assertFalse(page.supported()); assertTrue(page.rows().isEmpty());
        }
    }
    private static void contains(Bounds outer, Bounds inner) {
        assertTrue(inner.width() > 0 && inner.height() > 0);
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y());
        assertTrue(inner.right() <= outer.right() && inner.bottom() <= outer.bottom());
    }
}
