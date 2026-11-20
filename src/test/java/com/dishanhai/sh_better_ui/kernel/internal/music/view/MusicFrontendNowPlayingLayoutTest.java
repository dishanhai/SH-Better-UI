// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendNowPlayingLayoutTest {
    @Test void immersiveLayoutKeepsContentAndHitTargetsSeparateAtEveryGuiScale() {
        for (int width = 208; width < 1000; width += 11) {
            for (int height = 240; height < 550; height += 9) {
                var frame = new Bounds(12, 17, width, height);
                for (boolean detail : List.of(false, true)) {
                    var p = MusicFrontendNowPlayingLayout.fit(frame, detail);
                    var t = p.transport();
                    var boxes = new ArrayList<>(List.of(p.cover(), p.title(), p.subtitle(), p.lyrics(), p.queue(),
                            p.elapsed(), p.remaining(), t.previous(), t.playPause(), t.next(), t.progress()));
                    if (detail) {
                        assertTrue(p.detail().height() >= 70, "Queue must fit at least one playable row");
                        boxes.add(p.detail());
                    }
                    for (int i = 0; i < boxes.size(); i++) {
                        var a = boxes.get(i);
                        assertTrue(a.width() > 0 && a.height() > 0);
                        assertTrue(a.x() >= frame.x() && a.y() >= frame.y()
                                && a.right() <= frame.right() && a.bottom() <= frame.bottom());
                        for (int j = 0; j < i; j++) {
                            var b = boxes.get(j);
                            assertTrue(a.right() <= b.x() || a.x() >= b.right()
                                            || a.bottom() <= b.y() || a.y() >= b.bottom(),
                                    () -> "Overlap at " + frame + ": " + a + ", " + b);
                        }
                    }
                }
            }
        }
    }

    @Test void coverLeadsInNowPlayingAndOnlyBecomesThumbnailInCompactDetail() {
        var narrow = new Bounds(0, 0, 320, 300);
        var album = MusicFrontendNowPlayingLayout.fit(narrow, false);
        var compact = MusicFrontendNowPlayingLayout.fit(narrow, true);
        var wide = MusicFrontendNowPlayingLayout.fit(new Bounds(0, 0, 750, 300), true);
        assertTrue(album.title().y() > album.cover().bottom());
        assertTrue(album.cover().width() > compact.cover().width() * 2);
        assertEquals(38, compact.cover().width());
        assertFalse(compact.split());
        assertTrue(wide.split());
        assertTrue(wide.detail().x() > wide.cover().right());
        assertTrue(wide.detail().height() > compact.detail().height());
    }
}
