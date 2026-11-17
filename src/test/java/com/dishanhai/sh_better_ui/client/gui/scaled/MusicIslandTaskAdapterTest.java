// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicIslandTaskAdapterTest {
    private MusicService.Frame frame(String track,long revision,MusicService.LyricsState state) {
        var session=new MusicSnapshot.Session("session",MusicSnapshot.Track.of(track,track,"artist",""),
                MusicSnapshot.PlaybackStatus.PLAYING,OptionalLong.empty(),OptionalLong.empty(),OptionalDouble.empty(),0,null,Set.of());
        return new MusicService.Frame(new MusicSnapshot("provider",MusicSnapshot.State.AVAILABLE,session),
                new MusicService.Lyrics(state,LyricTimeline.empty()),revision);
    }
    @Test void loadingAndCompletionReflectRealTransitionsWithoutPercent() {
        var adapter=new MusicIslandTaskAdapter();
        var loading=frame("A",1,MusicService.LyricsState.LOADING);
        assertTrue(adapter.observe(loading).stream().anyMatch(u->u.messageKey().equals("lyrics_loading")&&u.ttlMs()==8000));
        assertTrue(adapter.observe(loading).isEmpty());
        assertTrue(adapter.observe(frame("A",1,MusicService.LyricsState.AVAILABLE)).stream().anyMatch(u->u.messageKey().equals("lyrics_ready")));
    }
    @Test void trackChangeCancelsAndOldRevisionCannotReappear() {
        var adapter=new MusicIslandTaskAdapter();
        adapter.observe(frame("A",1,MusicService.LyricsState.LOADING));
        assertTrue(adapter.observe(frame("B",2,MusicService.LyricsState.LOADING)).stream().anyMatch(u->u.cancel()&&u.id().equals(MusicIslandTaskAdapter.LYRICS)));
        assertTrue(adapter.observe(frame("A",1,MusicService.LyricsState.AVAILABLE)).isEmpty());
        assertTrue(adapter.observe(frame("B",2,MusicService.LyricsState.UNAVAILABLE)).stream().anyMatch(u->u.messageKey().equals("lyrics_unavailable")));
    }
    @Test void initialCachedResultIsNotInventedAsTaskCompletion() {
        var adapter=new MusicIslandTaskAdapter();
        assertTrue(adapter.observe(frame("A",1,MusicService.LyricsState.AVAILABLE)).stream().allMatch(MusicIslandTaskAdapter.Update::cancel));
        adapter.reset();
        assertTrue(adapter.observe(frame("B",0,MusicService.LyricsState.LOADING)).stream().anyMatch(u->u.messageKey().equals("lyrics_loading")));
    }
    @Test void providerFailureAndRecoveryAreRealTransitionsAndUnsupportedIsQuiet() {
        var adapter=new MusicIslandTaskAdapter();
        var unavailable=new MusicService.Frame(new MusicSnapshot("provider",MusicSnapshot.State.TEMPORARILY_UNAVAILABLE,null),
                new MusicService.Lyrics(MusicService.LyricsState.NO_TRACK,LyricTimeline.empty()),1);
        assertTrue(adapter.observe(unavailable).stream().anyMatch(u->u.messageKey().equals("provider_unavailable")));
        assertTrue(adapter.observe(unavailable).isEmpty());
        assertTrue(adapter.observe(frame("A",2,MusicService.LyricsState.AVAILABLE)).stream().anyMatch(u->u.messageKey().equals("provider_recovered")));
        var unsupported=new MusicService.Frame(new MusicSnapshot("provider",MusicSnapshot.State.UNSUPPORTED,null),unavailable.lyrics(),3);
        assertTrue(adapter.observe(unsupported).stream().allMatch(MusicIslandTaskAdapter.Update::cancel));
    }
}
