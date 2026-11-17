package com.dishanhai.sh_better_ui.kernel.api.music.control;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MusicSeekModelTest {
    @Test void dragOnlyPreviewsAndReleaseCommitsExactlyOnce(){
        var m=new MusicSeekModel();m.sync("a",100,1000,true);
        assertEquals(250,m.begin(.25).orElseThrow().positionMs());
        assertEquals(750,m.drag(.75).orElseThrow().positionMs());
        m.sync("a",150,1000,true);assertEquals(750,m.positionMs());
        assertEquals(new MusicSeekModel.Edit("a",750),m.release().orElseThrow());
        assertTrue(m.release().isEmpty());
    }
    @Test void identityChangeCancelsOldGestureWithoutSeekingNewTrack(){
        var m=new MusicSeekModel();m.sync("a",100,1000,true);m.begin(.9);
        assertEquals(new MusicSeekModel.Edit("a",100),m.sync("b",0,2000,true).orElseThrow());
        assertFalse(m.editing());assertTrue(m.release().isEmpty());assertEquals(0,m.positionMs());
    }
    @Test void cancellationUsesLatestAuthoritativePositionNotOriginalPress(){
        var m=new MusicSeekModel();m.sync("a",100,1000,true);m.begin(.9);m.sync("a",200,1000,true);
        assertEquals(200,m.cancel().orElseThrow().positionMs());assertEquals(200,m.positionMs());
    }
    @Test void durationUnknownOrPermissionRevokedDisarmsAndDoesNotDivideByZero(){
        var m=new MusicSeekModel();m.sync("a",100,0,true);assertFalse(m.enabled());assertEquals(0,m.fraction());
        assertTrue(m.begin(.2).isEmpty());m.sync("a",100,1000,true);m.begin(.4);
        assertTrue(m.sync("a",100,1000,false).isPresent());assertTrue(m.release().isEmpty());
        assertTrue(m.keyboard(5).isEmpty());
    }
    @Test void resizingDurationInvalidatesPointerAndNonFiniteInputsDoNotCorrupt(){
        var m=new MusicSeekModel();m.sync("a",100,1000,true);assertTrue(m.begin(Double.NaN).isEmpty());
        m.begin(.3);assertTrue(m.drag(Double.POSITIVE_INFINITY).isEmpty());assertEquals(300,m.positionMs());
        assertTrue(m.sync("a",100,2000,true).isPresent());assertTrue(m.release().isEmpty());
    }
    @Test void keyboardBoundariesAndVolumeNormalizationAreExact(){
        var m=new MusicSeekModel();m.sync("volume-session",500,1000,true);
        assertEquals(1000,m.keyboard(Long.MAX_VALUE).orElseThrow().positionMs());
        assertEquals(0,m.keyboard(Long.MIN_VALUE).orElseThrow().positionMs());
        assertEquals(500,m.seekTo(500).orElseThrow().positionMs());
        m.begin(.2);assertTrue(m.keyboard(1).isEmpty());m.cancel();
        assertEquals(500,m.positionMs());
    }
}
