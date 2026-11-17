package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IslandShellStyleTest {
    @Test void fullCatalogueCyclesOnceWithoutChangingLegacyIds() {
        var seen = new java.util.HashSet<String>();
        var style = IslandShellStyle.CLASSIC;
        for (int i = 0; i < IslandShellStyle.values().length; i++) {
            assertTrue(seen.add(style.id()));
            assertTrue(IslandShellStyle.isKnownId(style.id()));
            style = style.next();
        }
        assertEquals(IslandShellStyle.CLASSIC, style);
        assertEquals(11, seen.size());
        assertFalse(IslandShellStyle.isKnownId(null));
        assertEquals(450, IslandShellStyle.MUSIC_CAPSULE.hoverDelayMs());
        assertEquals(0, IslandShellStyle.CLASSIC.hoverDelayMs());
    }
    @Test void musicCapsuleIsBlackWithQuietCompactEdgesAndReadableContrast() {
        var compact = IslandShellStyle.MUSIC_CAPSULE.shell(10,28,false,false);
        assertEquals(14,compact.radius());
        assertEquals(0,compact.border());
        assertEquals(0xFF000000,compact.fill());
        assertEquals(1,IslandShellStyle.MUSIC_CAPSULE.shell(10,28,true,false).border());
        assertEquals(2,IslandShellStyle.MUSIC_CAPSULE.shell(10,28,false,true).border());
    }
    @Test void tinyHostRadiiStayWithinSurface() {
        for (var s : IslandShellStyle.values()) {
            assertTrue(s.shell(10, 6, false, false).radius() <= 3);
            assertTrue(s.shell(10, 0, false, false).radius() >= 0);
        }
    }

    @Test void stableIdsFallbackAndDistinctGeometry() {
        assertEquals(IslandShellStyle.CLASSIC, IslandShellStyle.fromId(null));
        assertEquals(IslandShellStyle.CLASSIC, IslandShellStyle.fromId("unknown"));
        for(var s:IslandShellStyle.values()) assertEquals(s,IslandShellStyle.fromId(s.id()));
        assertEquals(10,IslandShellStyle.CLASSIC.shell(10,30,false,false).radius());
        assertEquals(15,IslandShellStyle.PILL.shell(10,30,false,false).radius());
        assertEquals(4,IslandShellStyle.MINIMAL.shell(10,30,false,false).radius());
    }
    @Test void contrastOverridesEveryShellAndHoverHasVisibleFeedback() {
        for(var s:IslandShellStyle.values()) {
            var contrast=s.shell(10,30,false,true);
            assertEquals(0xFFFFFFFF,contrast.edge()); assertEquals(2,contrast.border());
            assertEquals(255,contrast.fill()>>>24);
            assertNotEquals(s.shell(10,30,false,false).edge(),s.shell(10,30,true,false).edge());
        }
    }
}
