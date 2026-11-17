// Author: dishanhai
package com.dishanhai.sh_better_ui.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlPanelLayoutTest {
    @Test void sidebarKeepsPreviousResponsiveFallbackButClassicNeverUsesRail() {
        assertEquals(136,ControlPanelLayout.SIDEBAR.sidebarWidth(500,300));
        assertEquals(0,ControlPanelLayout.SIDEBAR.sidebarWidth(499,300));
        assertEquals(0,ControlPanelLayout.SIDEBAR.sidebarWidth(500,299));
        assertEquals(0,ControlPanelLayout.CLASSIC.sidebarWidth(900,900));
        assertEquals(-30,ControlPanelLayout.CLASSIC.headerOffset(false));
        assertEquals(0,ControlPanelLayout.CLASSIC.headerOffset(true));
        assertEquals(0,ControlPanelLayout.SIDEBAR.headerOffset(false));
    }
    @Test void allGroupEditsPreserveExplicitLayout() {
        var s=ClientUiSettings.DEFAULT.withControlPanelLayout(ControlPanelLayout.CLASSIC);
        assertEquals(ControlPanelLayout.SIDEBAR,ClientUiSettings.DEFAULT.controlPanelLayout());
        for(var next:java.util.List.of(s.withBackdrop(s.backdrop()),s.withDisplay(true,true,false),s.withFeedback(s.feedback()),
                s.withTabs(s.tabs()),s.withTheme(s.theme()),s.withDynamicTheme(s.dynamicTheme()),s.withSpatial(s.spatial())))
            assertEquals(ControlPanelLayout.CLASSIC,next.controlPanelLayout());
        assertEquals(s.theme(),s.withControlPanelLayout(ControlPanelLayout.SIDEBAR).theme());
    }
}
