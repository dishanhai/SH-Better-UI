package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure-CPU regression of the screen-owned clock used by BlackHoleBackdrop. */
class BlackHoleMotionClockTest {
    @Test void millisecondHostTimesAdvanceAtConfiguredRadiansPerSecond() {
        var clock = new BlackHoleAnimationClock();
        long origin = 8_000_000;
        clock.advance(origin, false, .18f);
        for (int i=1; i<=100; i++) clock.advance(origin+i*10, false, .18f);
        assertEquals(.18f, clock.advance(origin+1000,false,.18f),1e-7f);
    }

    @Test void phaseDoesNotDependOnFramePartitionWithinStallBudget() {
        var fast = new BlackHoleAnimationClock();
        var slow = new BlackHoleAnimationClock();
        fast.advance(0,false,.18f); slow.advance(0,false,.18f);
        float a=0,b=0;
        for (int i=1;i<=1000;i++) a=fast.advance(i*10,false,.18f);
        for (int i=1;i<=200;i++) b=slow.advance(i*50,false,.18f);
        assertEquals(a,b,1e-7f);
    }

    @Test void zeroSpeedAndReducedMotionFreezeOnTheFirstRequestedFrame() {
        var clock = new BlackHoleAnimationClock();
        clock.advance(0,false,.18f);
        float moving=clock.advance(16,false,.18f);
        assertEquals(moving,clock.advance(32,false,0));
        assertEquals(moving,clock.advance(10000,false,0));
        assertEquals(moving,clock.advance(10016,true,.18f));
        assertEquals(moving,clock.advance(20000,true,.18f));
        assertEquals(moving,clock.advance(20016,false,.18f));
        assertEquals(moving+.00288f,clock.advance(20032,false,.18f),1e-7f);
    }

    @Test void closeAndReopenSameInstancePreservePhaseWithoutCatchUp() {
        var clock = new BlackHoleAnimationClock();
        clock.advance(0,false,.18f);
        float moving=clock.advance(16,false,.18f);
        clock.suspend();
        assertEquals(moving,clock.advance(1_000_000,false,.18f));
        assertEquals(moving+.00288f,clock.advance(1_000_016,false,.18f),1e-7f);
    }

    @Test void longStallDoesNotAdvanceOrCatchUp() {
        var clock = new BlackHoleAnimationClock();
        clock.advance(0,false,.18f);
        assertEquals(0f,clock.advance(10000,false,.18f));
        assertEquals(.00288f,clock.advance(10016,false,.18f),1e-7f);
    }

    @Test void alternatingOwnersDoNotShareFreezeOrPhase() {
        var a=new BlackHoleAnimationClock(); var b=new BlackHoleAnimationClock();
        a.advance(0,false,.18f); b.advance(0,true,.18f);
        for (int i=1;i<=100;i++) { a.advance(i*10,false,.18f); b.advance(i*10,true,.18f); }
        assertEquals(.18f,a.advance(1000,false,.18f),1e-7f);
        assertEquals(0,b.advance(1000,false,.18f));
        assertEquals(.00288f,b.advance(1016,false,.18f),1e-7f);
        assertEquals(.18f,a.advance(1016,true,.18f),1e-7f);
    }
    @Test void tenAndSixtyFpsTrackSameElapsedTime() {
        var ten=new BlackHoleAnimationClock(); var sixty=new BlackHoleAnimationClock();
        ten.advance(0,false,.18f); sixty.advance(0,false,.18f);
        float a=0,b=0;
        for(int i=1;i<=100;i++) a=ten.advance(i*100L,false,.18f);
        for(int i=1;i<=600;i++) b=sixty.advance(Math.round(i*1000d/60),false,.18f);
        assertEquals(1.8f,a,1e-6f); assertEquals(a,b,1e-7f);
    }
    @Test void restartingFromZeroSpeedDoesNotApplyPausedInterval() {
        var clock=new BlackHoleAnimationClock(); clock.advance(0,false,1);
        float before=clock.advance(20,false,1);
        assertEquals(before,clock.advance(40,false,0));
        assertEquals(before,clock.advance(200,false,1));
        assertEquals(before+.02f,clock.advance(220,false,1),1e-7f);
    }
    @Test void stallBoundaryReversalAndOverflowAreExplicit() {
        var clock=new BlackHoleAnimationClock(); clock.advance(0,false,1);
        assertEquals(.25f,clock.advance(250,false,1));
        assertEquals(.25f,clock.advance(501,false,1));
        assertEquals(.25f,clock.advance(-1,false,1));
        assertEquals(.25f,clock.advance(Long.MIN_VALUE,false,1));
        assertEquals(.25f,clock.advance(Long.MAX_VALUE,false,1));
    }

}
