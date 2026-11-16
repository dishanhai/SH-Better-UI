package com.dishanhai.sh_better_ui.client.gui.scaled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiLayeredMotionTest {
    @Test void reversesFromCurrentProgressAndCanReopen() {
        var m = new UiLayeredMotion(400, 200);
        m.setShown(true, 0, false); m.tick(100, 1, false);
        assertEquals(.25f, m.rawProgress());
        m.setShown(false, 100, false); assertEquals(.25f, m.rawProgress());
        m.tick(120, 1, false); assertEquals(.15f, m.rawProgress(), 1e-6);
        m.setShown(true, 120, false); m.tick(220, 1, false);
        assertEquals(.4f, m.rawProgress(), 1e-6);
    }
    @Test void allLayersUseOneClockAndAreReversible() {
        var m = new UiLayeredMotion(400,400); m.setShown(true,0,false);m.tick(200,1,false);
        assertEquals(1,m.layer(0,.4)); assertEquals(.5f,m.layer(.25,.75));assertEquals(0,m.layer(.6,1));
        m.setShown(false,200,false);m.tick(300,1,false);
        assertEquals(0,m.layer(.25,.75));assertTrue(m.layer(0,.4)>0);
    }
    @Test void reducedMotionSnapsBothDirectionsAndDisposedMotionStaysGone() {
        var m=new UiLayeredMotion(400,200);m.setShown(true,0,true);assertTrue(m.settled());assertEquals(1,m.progress());
        m.setShown(false,1,true);assertEquals(0,m.progress());m.close();m.setShown(true,2,false);assertEquals(0,m.tick(100,1,false));
    }
    @Test void repeatedAndBackwardTimeCannotAddOrRemoveProgress() {
        var m=new UiLayeredMotion(400,400);m.setShown(true,100,false);m.tick(200,1,false);
        m.tick(200,1,false); assertEquals(.25f,m.rawProgress());
        m.tick(150,1,false);m.tick(200,1,false);assertEquals(.25f,m.rawProgress());
        m.tick(250,1,false);assertEquals(.375f,m.rawProgress());
    }
    @Test void pauseGapBoundedAndInvalidSpeedFallsBack() {
        var m=new UiLayeredMotion(1000,1000);m.setShown(true,0,false);m.tick(100000,Double.NaN,false);
        assertEquals(.25f,m.rawProgress());assertFalse(m.settled());m.finish();assertTrue(m.settled());
    }
    @Test void repeatingTargetDoesNotRestartTheClock() {
        var m=new UiLayeredMotion(400,400);m.setShown(true,0,false);
        m.setShown(true,50,false);m.tick(100,1,false);assertEquals(.25f,m.rawProgress());
    }
    @Test void windowsRejectBadInputWithoutNanLeak() {
        assertThrows(IllegalArgumentException.class,()->new UiLayeredMotion(0,10));
        assertThrows(IllegalArgumentException.class,()->UiLayeredMotion.window(.3,.5,.5));
        assertThrows(IllegalArgumentException.class,()->UiLayeredMotion.window(.3,Double.NaN,1));
        assertEquals(0,UiLayeredMotion.window(Double.NaN,0,1));assertEquals(1,UiLayeredMotion.window(20,0,1));
    }
    @org.junit.jupiter.api.Test void reversingWithAnOlderTimestampDoesNotRewindClock() {
        UiLayeredMotion motion = new UiLayeredMotion(400, 400);
        motion.setShown(true, 100, false);
        motion.tick(200, 1, false);
        motion.setShown(false, 150, false);
        motion.tick(200, 1, false);
        org.junit.jupiter.api.Assertions.assertEquals(.25f, motion.rawProgress());
        motion.tick(250, 1, false);
        org.junit.jupiter.api.Assertions.assertEquals(.125f, motion.rawProgress());
    }
}
