package com.dishanhai.sh_better_ui.kernel.api.music.presentation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtworkPaletteTest {
    @Test void samplesActualQuadrantsPreservesNeutralArtworkAndCapsWhiteTextContrast() {
        var palette=ArtworkPalette.fromArgb(2,2,new int[]{0xffe08080,0xff306030,0xff807040,0xff402050});
        assertNotEquals(palette.topLeft(),palette.topRight());
        for(int color:new int[]{palette.topLeft(),palette.topRight(),palette.bottomLeft(),palette.bottomRight()})
            assertTrue((1.05)/(ArtworkPalette.luminance(color)+.05)>=5.5);
        var gray=ArtworkPalette.fromArgb(1,1,new int[]{0xffcccccc});
        assertEquals((gray.topLeft()>>>16)&255,gray.topLeft()&255);
        assertEquals((gray.topLeft()>>>8)&255,gray.topLeft()&255);
    }
    @Test void rejectsUnboundedSamplesAndTransparentPixelsCannotColorTheBackdrop() {
        assertThrows(IllegalArgumentException.class,()->ArtworkPalette.fromArgb(65,65,new int[4225]));
        assertThrows(IllegalArgumentException.class,()->ArtworkPalette.fromArgb(0,0,new int[0]));
        assertThrows(IllegalArgumentException.class,()->ArtworkPalette.fromArgb(2,2,new int[1]));
        var p=ArtworkPalette.fromArgb(1,1,new int[]{0x00ff0000});
        assertEquals(ArtworkPalette.fallback(0xff29282b),p);
    }
    @Test void colorFieldMatchesCornersAndNeverHasTransparentSeams() {
        var p=new ArtworkPalette(0xff403040,0xff604040,0xff202830,0xff303020);
        assertEquals(p.topLeft(),p.color(0,0));assertEquals(p.bottomRight(),p.color(1,1));
        assertEquals(255,p.color(.5,.5)>>>24);
        assertEquals(p,p.interpolate(p,.5));
    }
    @Test void transitionsRestartFromVisibleColorAndAccessibilityModesSettleImmediately() {
        var m=new ArtworkStageMotion();var a=ArtworkPalette.fallback(0xff887744);var b=ArtworkPalette.fallback(0xff884477);
        assertEquals(a,m.sample(a,0,false,false).palette());
        assertEquals(a,m.sample(b,50,false,false).palette());
        var mid=m.sample(b,100,false,false).palette();assertNotEquals(a,mid);
        assertEquals(mid,m.sample(a,150,false,false).palette());
        assertEquals(b,m.sample(b,200,true,false).palette());
        double phase=m.sample(b,250,true,false).phase();
        assertEquals(phase,m.sample(a,300,false,true).phase());
        m.reset();assertEquals(0,m.sample(a,0,false,false).phase());
    }
    @Test void invisiblePaddingCannotIntroduceAnUnrelatedHueAndFullyOpaqueOutputIsStable() {
        var padded=ArtworkPalette.fromArgb(2,2,new int[]{0x00ff0000,0x00ff0000,0xff668866,0xff668866});
        var solid=ArtworkPalette.fromArgb(1,1,new int[]{0xff668866});
        assertEquals(solid,padded);
        assertEquals(255,padded.topLeft()>>>24);assertEquals(255,padded.bottomRight()>>>24);
        // Uniform semitransparency changes weight, not the artwork's RGB identity.
        assertEquals(solid,ArtworkPalette.fromArgb(1,1,new int[]{0x80668866}));
    }
    @Test void boundedClockDoesNotCatchUpAfterHiddenPagesOrOverflowAndResetDetachesOldTrack() {
        var motion=new ArtworkStageMotion();var a=ArtworkPalette.fallback(0xff667744);
        var b=ArtworkPalette.fallback(0xff884477);
        motion.sample(a,Long.MIN_VALUE,false,false);
        motion.sample(b,Long.MIN_VALUE+1,false,false);
        var jump=motion.sample(b,Long.MAX_VALUE,false,false);
        assertNotEquals(b,jump.palette()); // An enormous elapsed interval is still only one 50ms step.
        assertTrue(Double.isFinite(jump.phase()));assertTrue(jump.phase()>=0&&jump.phase()<Math.PI*2);
        assertEquals(jump,motion.sample(b,Long.MIN_VALUE,false,false));
        motion.reset();assertEquals(b,motion.sample(b,50,false,false).palette());
    }
    @Test void whiteArtworkFallbackAndEveryInteriorSampleKeepNormalTextReadable() {
        var p=ArtworkPalette.fromArgb(2,2,new int[]{0xffffffff,0xffff0088,0xff00ff00,0xff0000ff});
        double foreground=ArtworkPalette.luminance(0xffeae8eb);
        for(int y=0;y<=20;y++)for(int x=0;x<=20;x++) {
            int color=p.color(x/20d,y/20d);
            assertTrue((foreground+.05)/(ArtworkPalette.luminance(color)+.05)>=4.5);
        }
        assertTrue(ArtworkPalette.luminance(ArtworkPalette.fallback(0xffffffff).topLeft())<=.14);
        assertThrows(IllegalArgumentException.class,()->p.color(Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->p.interpolate(p,Double.NaN));
    }
}
