package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IslandShellStyleTest {
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
