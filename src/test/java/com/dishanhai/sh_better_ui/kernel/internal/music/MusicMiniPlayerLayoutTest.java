// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicMiniPlayerLayout;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLayout;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class MusicMiniPlayerLayoutTest {
    @Test void allActionsRemainInsideTheirPlayerWithoutOverlappingMetadataOrSeek() {
        for (int width = OnlineMusicLayout.MIN_WIDTH; width <= 1024; width++) {
            for (int height : new int[]{240, 340, 720}) {
                var page = OnlineMusicLayout.of(new Bounds(19,31,width,height));
                var mini = MusicMiniPlayerLayout.of(page.transport(),page.expanded());
                var regions = new ArrayList<>(mini.actions());
                regions.add(mini.metadata()); regions.add(page.seekBar());
                for (var region : regions) {
                    assertTrue(region.width() >= 16 && region.height() >= 12);
                    assertTrue(region.x() >= page.transport().x() && region.y() >= page.transport().y());
                    assertTrue(region.right() <= page.transport().right() && region.bottom() <= page.transport().bottom());
                }
                for (int i=0;i<regions.size();i++) for (int j=i+1;j<regions.size();j++)
                    assertFalse(overlaps(regions.get(i),regions.get(j)), width+"x"+height+" regions "+i+"/"+j);
                assertEquals(6,mini.actions().size());
                assertTrue(mini.previous().right() <= mini.play().x());
                assertTrue(mini.play().right() <= mini.next().x());
            }
        }
    }
    @Test void compactMetadataGetsItsOwnRowWithoutIncreasingTheReservedPlayerHeight() {
        var page = OnlineMusicLayout.of(new Bounds(0,0,208,240));
        var mini = MusicMiniPlayerLayout.of(page.transport(),page.expanded());
        assertTrue(mini.metadata().bottom() <= mini.previous().y());
        assertTrue(mini.queue().bottom() <= page.seekBar().y());
        assertEquals(64,page.transport().height());
        assertEquals(184,mini.metadata().width());
    }
    private static boolean overlaps(Bounds a,Bounds b) {
        return a.x()<b.right()&&b.x()<a.right()&&a.y()<b.bottom()&&b.y()<a.bottom();
    }
}
