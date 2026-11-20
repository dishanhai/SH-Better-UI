// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekModel;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendTransportTest {
    private static final OnlineTrack SONG = new OnlineTrack("qq", "1", "1", "Title", "Artist", "Album", 12_000);
    @Test void seekIsReleaseOnlyAndRepeatedQueueOccurrenceCannotCommitAnOldDrag() {
        var rig = new Rig();
        var adapter = new MusicFrontendTransport(rig.player);
        var frame = adapter.frame();
        assertTrue(frame.seekable()); assertTrue(rig.actions.isEmpty());
        var edit = new MusicSeekModel.Edit(frame.seekKey(), 3_000);
        rig.index = 1;
        assertFalse(adapter.commit(edit));
        assertTrue(rig.actions.isEmpty());
        assertTrue(adapter.commit(new MusicSeekModel.Edit(adapter.frame().seekKey(), 90_000)));
        assertEquals(List.of("seek:12000"), rig.actions);
    }
    @Test void loadingDoesNotResumeAndFailedTrackOnlyTriggersExplicitRetry() {
        var rig = new Rig(); var adapter = new MusicFrontendTransport(rig.player);
        rig.state = State.LOADING;
        assertFalse(adapter.activate());
        rig.state = State.FAILED;
        assertTrue(adapter.activate());
        rig.state = State.PLAYING;
        assertTrue(adapter.activate());
        rig.state = State.PAUSED;
        assertTrue(adapter.activate());
        assertEquals(List.of("retryCurrent","pause","resume"), rig.actions);
    }
    @Test void skipsUseRealQueueCapabilities() {
        var rig = new Rig(); var adapter = new MusicFrontendTransport(rig.player);
        assertFalse(adapter.skip(false));
        assertTrue(adapter.skip(true));
        rig.index = 1;
        assertFalse(adapter.skip(true));
        assertTrue(adapter.skip(false));
        assertEquals(List.of("next","previous"), rig.actions);
    }
    private static final class Rig {
        State state=State.PLAYING;
        int index;
        final List<String> actions=new ArrayList<>();
        final OnlineMusicQueue queue=(OnlineMusicQueue)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{OnlineMusicQueue.class},
                (p,m,a)->switch(m.getName()){
                    case "snapshot" -> new OnlineMusicQueue.Snapshot(List.of(SONG,SONG),index,false);
                    case "next","previous" -> {actions.add(m.getName());yield null;}
                    default -> throw new AssertionError(m.getName());
                });
        final OnlineMusicPlayer player=(OnlineMusicPlayer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{OnlineMusicPlayer.class},
                (p,m,a)->switch(m.getName()){
                    case "snapshot" -> new OnlineMusicPlayer.Snapshot(SONG,state,1_000,12_000,OnlineMusicPlayer.LyricsState.EMPTY,
                            LyricTimeline.empty(),OnlineMusicPlayer.Failure.NONE,false);
                    case "queue" -> queue;
                    case "seek" -> {actions.add("seek:"+a[0]); yield null;}
                    case "canRetryCurrent" -> true;
                    case "retryCurrent" -> {actions.add(m.getName());yield true;}
                    case "pause", "resume" -> {actions.add(m.getName());yield null;}
                    default -> throw new AssertionError(m.getName());
                });
    }
}
