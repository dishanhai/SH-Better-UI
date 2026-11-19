package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MusicScrollParallaxTest {
    @Test void scrollMovesOppositeDirectionAndSettles() {
        var m=new MusicScrollParallax(); assertEquals(0,m.sample("lyrics:a",0,false));
        m.scroll(2); assertTrue(m.sample("lyrics:a",16,false)<0);
        for(int i=32;i<5000;i+=16)m.sample("lyrics:a",i,false);
        assertEquals(0,m.sample("lyrics:a",5000,false));
    }
    @Test void enormousThumbJumpsAndRepeatedScrollStayBounded() {
        var m=new MusicScrollParallax();m.sample("list",0,false);
        for(int i=1;i<1000;i++) {
            m.scroll(i%2==0?Double.MAX_VALUE:-Double.MAX_VALUE);
            assertTrue(Math.abs(m.sample("list",i*16L,false))<=MusicScrollParallax.LIMIT);
        }
    }
    @Test void reducedMotionDiscardsInputAndDoesNotReplayIt() {
        var m=new MusicScrollParallax();m.sample("a",0,false);m.scroll(4);
        assertEquals(0,m.sample("a",16,true));m.scroll(4);
        assertEquals(0,m.sample("a",32,false));assertEquals(0,m.sample("a",64,false));
    }
    @Test void contextTrackTabAndReopenStartAtRest() {
        var m=new MusicScrollParallax();m.sample("lyrics:a",0,false);m.scroll(4);
        assertNotEquals(0,m.sample("lyrics:a",16,false));
        assertEquals(0,m.sample("lyrics:b",32,false));m.scroll(4);
        assertEquals(0,m.sample("queue:b",48,false));m.scroll(4);m.reset();
        assertEquals(0,m.sample("queue:b",64,false));
    }
    @Test void invalidInputRepeatedAndReversedTimeAreSafe() {
        var m=new MusicScrollParallax();m.sample("a",100,false);m.scroll(Double.NaN);m.scroll(Double.POSITIVE_INFINITY);
        assertEquals(0,m.sample("a",116,false));m.scroll(1);
        assertEquals(0,m.sample("a",116,false));
        assertEquals(0,m.sample("a",10,false));
        m.scroll(4);assertTrue(Float.isFinite(m.sample("a",Long.MAX_VALUE,false)));
    }
    @Test void playbackTimeCannotDriveParallaxWithoutScroll() {
        var m=new MusicScrollParallax();
        for(int t=0;t<100000;t+=1000)assertEquals(0,m.sample("same-track",t,false));
    }
}
