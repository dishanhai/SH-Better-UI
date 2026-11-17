// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MusicLyricsModelTest {
    private MusicLyricsModel timed(){var m=new MusicLyricsModel();m.content("song",List.of(new MusicLyricsModel.Line("first",100,500),new MusicLyricsModel.Line("second",1000,2000)));return m;}
    @Test void highlightsOnlyRealIntervalsAndSeeksOnlyWhenEnabled(){
        var m=timed();m.playback(0,true);assertEquals(-1,m.activeIndex());
        m.playback(100,true);assertEquals(0,m.activeIndex());m.playback(500,true);assertEquals(-1,m.activeIndex());
        m.playback(1000,true);assertEquals(1,m.activeIndex());assertEquals(1000,m.seekTarget(1).orElseThrow());
        m.playback(1400,false);assertTrue(m.seekTarget(1).isEmpty());
        assertTrue(m.seekTarget(-1).isEmpty());assertTrue(m.seekTarget(2).isEmpty());
    }
    @Test void unsynchronizedAndOutOfOrderContentNeverPretendsToBeTimed(){
        var m=new MusicLyricsModel();m.content("plain",List.of(MusicLyricsModel.Line.unsynchronized("text")));
        m.playback(500,true);assertFalse(m.synchronizedLyrics());assertEquals(-1,m.activeIndex());assertTrue(m.seekTarget(0).isEmpty());
        m.content("bad",List.of(new MusicLyricsModel.Line("a",200),new MusicLyricsModel.Line("b",100)));
        assertFalse(m.synchronizedLyrics());assertEquals("a",m.lines().get(0).text());
    }
    @Test void manualBrowseFocusAndFollowNeverChangePlayback(){
        var m=timed();m.playback(200,true);m.moveFocus(1);assertEquals(1,m.focusedIndex());assertTrue(m.browsing());assertEquals(0,m.activeIndex());
        m.focus(999);assertEquals(1,m.focusedIndex());m.focus(-1);assertEquals(0,m.focusedIndex());
        m.follow();assertFalse(m.browsing());assertEquals(-1,m.focusedIndex());assertEquals(0,m.activeIndex());
    }
    @Test void sameContentKeepsBrowseButTrackChangeResetsAndCannotSeekStaleIndex(){
        var m=timed();m.browse();m.focus(1);m.content("song",m.lines());assertTrue(m.browsing());
        m.content("new",List.of(new MusicLyricsModel.Line("new",300)));assertFalse(m.browsing());assertEquals(-1,m.focusedIndex());assertEquals("new",m.contentKey());assertTrue(m.seekTarget(1).isEmpty());
    }
    @Test void inputIsBoundedAndImmutable(){
        var m=new MusicLyricsModel();var list=new java.util.ArrayList<MusicLyricsModel.Line>();list.add(new MusicLyricsModel.Line("text",0));m.content(1,list);list.clear();assertEquals(1,m.lines().size());
        assertThrows(IllegalArgumentException.class,()->new MusicLyricsModel.Line("x",-2));
        assertThrows(IllegalArgumentException.class,()->new MusicLyricsModel.Line("x",100,99));
        assertThrows(IllegalArgumentException.class,()->new MusicLyricsModel.Line("x".repeat(4097),0));
        assertThrows(IllegalArgumentException.class,()->m.content(2,java.util.Collections.nCopies(4097,new MusicLyricsModel.Line("x",0))));
    }
}
