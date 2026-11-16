package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IslandAmbientStateTest {
    @Test void valuesSampleAtFourHzWithoutChangingTemplates() {
        var a=new IslandAmbientState(); a.configure(IslandAmbientState.Settings.DEFAULT);
        a.update(60,40,9,5,100);
        var first=a.chips(); assertEquals("09:05",first.get(2).value());
        a.update(999,900,10,10,349); assertSame(first,a.chips());
        a.update(999,900,10,10,350);
        assertEquals(first.get(0).widthTemplate(),a.chips().get(0).widthTemplate());
        assertEquals(IslandAmbientState.Health.BAD,a.chips().get(1).health());
    }
    @Test void rotationFallbackAndReducedMotion() {
        var a=new IslandAmbientState(); a.configure(IslandAmbientState.Settings.DEFAULT);
        a.update(1,1,1,1,100);
        assertEquals(IslandAmbientState.Page.STATUS,a.page(100,true,false));
        assertEquals(IslandAmbientState.Page.NOW_PLAYING,a.page(5100,true,false));
        assertEquals(IslandAmbientState.Page.STATUS,a.page(10100,true,false));
        assertEquals(IslandAmbientState.Page.NOW_PLAYING,a.page(10100,true,true));
        assertEquals(IslandAmbientState.Page.STATUS,a.page(10100,false,false));
    }
    @Test void unknownReadingsAndReversedClockAreSafe() {
        var a=new IslandAmbientState(); a.configure(IslandAmbientState.Settings.DEFAULT);
        a.update(-1,-1,-1,-1,100);
        assertEquals("--",a.chips().get(0).value());
        assertEquals("--:--",a.chips().get(2).value());
        assertEquals(IslandAmbientState.Health.NEUTRAL,a.chips().get(1).health());
        a.update(60,10,0,0,50); assertEquals("60",a.chips().get(0).value());
        assertEquals(IslandAmbientState.Page.STATUS,a.page(50,true,false));
    }
    @Test void disableClearsPreviousReadingsAndRejectsUnboundedPeriods() {
        var a=new IslandAmbientState(); a.configure(IslandAmbientState.Settings.DEFAULT);
        a.update(60,10,0,0,0); a.configure(IslandAmbientState.Settings.DISABLED);
        assertTrue(a.chips().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new IslandAmbientState.Settings(true,true,true,true,true,0,1000));
    }
}
