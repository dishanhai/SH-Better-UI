// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicOverlayLayoutTest {
    @Test void smallestIslandRemainsRenderable() {
        var full = MusicOverlayLayout.panel(854,480,true,.5f,.5f,.05f);
        var compact = MusicOverlayLayout.compact(full,.5f);
        assertEquals(110,compact.width());
        assertEquals(29,compact.height());
        assertEquals(full.x(),compact.x());
        assertEquals(full.y(),compact.y());
    }

    @Test void allSupportedSizesHaveRenderableCompactHeight() {
        for(int i=5;i<=20;i++) {
            float scale=i/10f;
            var full=MusicOverlayLayout.panel(854,480,true,scale,.5f,.05f);
            var compact=MusicOverlayLayout.compact(full,scale);
            assertTrue(compact.height()>=29);
            assertTrue(compact.height()<=full.height());
        }
    }

    @Test void normalizedEdgesUseFullScreenForBothSurfaces() {
        for(boolean island : new boolean[]{true,false}) {
            var full=MusicOverlayLayout.panel(854,480,island,1,1,1);
            assertEquals(854,full.right());
            assertEquals(480,full.bottom());
            assertEquals(island?220:230,full.width());
        }
    }

    @Test void tinyViewportStillClampsRatherThanOverflows() {
        var full=MusicOverlayLayout.panel(60,20,true,.5f,.5f,.05f);
        assertEquals(20,MusicOverlayLayout.compact(full,.5f).height());
        assertTrue(full.right()<=60);
    }
}
