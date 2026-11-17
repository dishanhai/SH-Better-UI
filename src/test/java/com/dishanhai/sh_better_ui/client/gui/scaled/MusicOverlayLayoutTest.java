// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicOverlayLayoutTest {
    @Test void defaultIslandIsCompactAndExpandsAroundSameCenter() {
        var full = MusicOverlayLayout.panel(854,480,true,1,.5f,.05f);
        var compact = MusicOverlayLayout.compact(full,1);
        assertEquals(208,full.width());
        assertEquals(92,full.height());
        assertEquals(126,compact.width());
        assertEquals(28,compact.height());
        assertEquals(full.x()+full.width()/2,compact.x()+compact.width()/2);
        assertEquals(full.y(),compact.y());
    }

    @Test void smallestIslandRetainsNativeArtworkSize() {
        var full = MusicOverlayLayout.panel(854,480,true,.5f,.5f,.05f);
        var compact = MusicOverlayLayout.compact(full,.5f);
        assertEquals(168,full.width());
        assertEquals(92,full.height());
        assertEquals(94,compact.width());
        assertEquals(28,compact.height());
        assertEquals(full.x()+37,compact.x());
    }

    @Test void allSupportedSizesRemainWithinExpansionEnvelope() {
        for(int i=5;i<=20;i++) {
            float scale=i/10f;
            var full=MusicOverlayLayout.panel(854,480,true,scale,.5f,.05f);
            var compact=MusicOverlayLayout.compact(full,scale);
            assertTrue(compact.height()>=28);
            assertTrue(compact.height()<=full.height());
            assertTrue(compact.x()>=full.x());
            assertTrue(compact.right()<=full.right());
            assertTrue(Math.abs((compact.x()*2+compact.width())-(full.x()*2+full.width()))<=1);
        }
    }

    @Test void normalizedEdgesUseFullScreenForBothSurfaces() {
        for(boolean island : new boolean[]{true,false}) {
            var full=MusicOverlayLayout.panel(854,480,island,1,1,1);
            assertEquals(854,full.right());
            assertEquals(480,full.bottom());
            assertEquals(island?208:230,full.width());
            assertEquals(island?92:112,full.height());
        }
    }

    @Test void tinyViewportStillClampsRatherThanOverflows() {
        var full=MusicOverlayLayout.panel(60,20,true,.5f,.5f,.05f);
        var compact=MusicOverlayLayout.compact(full,.5f);
        assertEquals(20,compact.height());
        assertTrue(compact.x()>=0);
        assertTrue(compact.right()<=60);
        assertTrue(full.right()<=60);
    }
}
