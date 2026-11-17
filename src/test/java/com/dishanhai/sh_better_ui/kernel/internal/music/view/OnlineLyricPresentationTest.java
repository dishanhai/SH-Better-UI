package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OnlineLyricPresentationTest {
    private static final OnlineTrack TRACK=new OnlineTrack("qq","1","1","Song","Singer","",5000);
    private OnlineMusicPlayer.Snapshot snapshot(OnlineTrack track,OnlineMusicPlayer.State state,long position,OnlineMusicPlayer.LyricsState lyricState){
        return new OnlineMusicPlayer.Snapshot(track,state,position,5000,lyricState,LyricTimeline.empty(),OnlineMusicPlayer.Failure.NONE,false);
    }
    @Test void onlyRealPlayableReadySnapshotsDriveHighlightAndSeek(){
        for(var state:OnlineMusicPlayer.State.values()) {
            var sample=snapshot(TRACK,state,1250,OnlineMusicPlayer.LyricsState.READY);
            boolean playable=state==OnlineMusicPlayer.State.PLAYING||state==OnlineMusicPlayer.State.PAUSED||state==OnlineMusicPlayer.State.ENDED;
            assertEquals(playable?1250:-1,OnlineLyricPresentation.position(sample));assertEquals(playable,OnlineLyricPresentation.seekable(sample));
        }
        assertEquals(-1,OnlineLyricPresentation.position(snapshot(null,OnlineMusicPlayer.State.PLAYING,1250,OnlineMusicPlayer.LyricsState.READY)));
        assertEquals(-1,OnlineLyricPresentation.position(snapshot(TRACK,OnlineMusicPlayer.State.PLAYING,1250,OnlineMusicPlayer.LyricsState.EMPTY)));
        assertEquals(5000,OnlineLyricPresentation.position(snapshot(TRACK,OnlineMusicPlayer.State.ENDED,9000,OnlineMusicPlayer.LyricsState.READY)));
    }
    @Test void adapterPreservesRealWordsTranslationAndSpeakerWithoutSynthesizingLrcTiming(){
        var lrc=new LyricLine("plain", "",0,1000,List.of(),"");
        var timed=new LyricLine("a b","翻译",1000,2000,List.of(new LyricWord("a ",1000,1500),new LyricWord("b",1500,2000)),"singer");
        var rows=OnlineLyricPresentation.lines(new LyricTimeline(List.of(lrc,timed)));
        assertFalse(rows.get(0).hasWordTiming());assertTrue(rows.get(1).hasWordTiming());
        assertEquals("翻译",rows.get(1).translation());assertEquals("singer",rows.get(1).speaker());assertEquals(1500,rows.get(1).words().get(1).startMillis());
    }
    @Test void oversizedContentIsBoundedBeforeItReachesPublicModel(){
        var line=new LyricLine("x".repeat(5000),"t".repeat(5000),0,1000,List.of(),"");
        var rows=OnlineLyricPresentation.lines(new LyricTimeline(List.of(line)));
        assertEquals(4096,rows.get(0).text().length());assertFalse(rows.get(0).hasWordTiming());
        var model=new MusicLyricsModel();assertDoesNotThrow(()->model.content("bounded",rows));
    }
}
