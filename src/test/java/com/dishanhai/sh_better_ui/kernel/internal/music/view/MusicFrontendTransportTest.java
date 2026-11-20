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
    @Test void oldRenderedLineCannotSeekNewTrackOrARefreshedVersionOfTheSameLyrics() {
        var rig = new Rig();
        var adapter = new MusicFrontendTransport(rig.player);
        var rendered = adapter.lyrics(true).content();
        assertTrue(adapter.seekLyric(rendered, 2000));
        rig.track = new OnlineTrack("netease", "3", "3", "Next", "Artist", "Album", 12_000);
        assertFalse(adapter.seekLyric(rendered, 3000));
        var next = adapter.lyrics(true).content();
        rig.timeline = timeline();
        assertFalse(adapter.seekLyric(next, 4000));
        assertTrue(adapter.seekLyric(adapter.lyrics(true).content(), 5000));
        assertEquals(List.of("seek:2000", "seek:5000"), rig.actions);
    }

    @Test void changingNativeAttemptBetweenFramesAlsoInvalidatesSameTrackAndSameQueueIndex() {
        var rig = new Rig();
        var adapter = new MusicFrontendTransport(rig.player, () -> rig.generation);
        var rendered = adapter.lyrics(true).content();
        var edit = new MusicSeekModel.Edit(adapter.frame().seekKey(), 7_000);
        assertTrue(adapter.preview(edit));
        rig.generation++;
        assertFalse(adapter.seekLyric(rendered, 1000));
        var fresh = adapter.lyrics(true);
        assertEquals(1000L, fresh.songPositionMs());
        assertFalse(fresh.preview());
        assertTrue(fresh.seekable());
        assertFalse(adapter.preview(edit));
        assertFalse(adapter.commit(edit));
        assertTrue(rig.actions.isEmpty());
    }

    @Test void repeatedQueueOccurrenceInvalidatesStaleScrubEvenForFallbackProviders() {
        var rig = new Rig();
        var adapter = new MusicFrontendTransport(rig.player);
        var old = adapter.lyrics(true).content();
        var edit = new MusicSeekModel.Edit(adapter.frame().seekKey(), 9_000);
        assertTrue(adapter.preview(edit));
        rig.index = 1;
        assertFalse(adapter.preview(edit)); // Drag events can precede the next widget sync.
        assertFalse(adapter.commit(edit));
        assertFalse(adapter.seekLyric(old, 2_000)); // Same timeline and track object, different occurrence.
        assertEquals(1_000, adapter.lyrics(true).songPositionMs());
        var editNew = new MusicSeekModel.Edit(adapter.frame().seekKey(), 3_000);
        assertTrue(adapter.preview(editNew));
        assertTrue(adapter.commit(editNew));
        assertFalse(adapter.lyrics(true).preview());
        assertEquals(List.of("seek:3000"), rig.actions);
    }

    @Test void clipLyricSeekRejectsOutOfRangeAndActiveScrubAndUsesSongTimeOnlyForDisplay() {
        var rig = new Rig();
        rig.previewStream = true;
        rig.offset = 30_000;
        var adapter = new MusicFrontendTransport(rig.player);
        var rendered = adapter.lyrics(true).content();
        var thumb = new MusicSeekModel.Edit(adapter.frame().seekKey(), 5_000);
        assertEquals(31_000, adapter.lyrics(true).songPositionMs());
        assertFalse(adapter.seekLyric(rendered, 29_999));
        assertFalse(adapter.seekLyric(rendered, 42_001));
        assertTrue(adapter.preview(thumb));
        assertEquals(35_000, adapter.lyrics(true).songPositionMs());
        assertTrue(adapter.lyrics(true).preview());
        assertFalse(adapter.lyrics(true).seekable());
        assertFalse(adapter.seekLyric(rendered, 34_000));
        assertFalse(adapter.lyrics(false).seekable());
        assertFalse(adapter.lyrics(true).preview());
        assertEquals(31_000, adapter.lyrics(true).songPositionMs());
        assertTrue(adapter.seekLyric(rendered, 34_000));
        rig.offset = 31_000;
        assertFalse(adapter.seekLyric(rendered, 34_000));
        assertEquals(List.of("seek:4000"), rig.actions);
    }

    @Test void unavailableTimelineClearsPaintedContentAndRejectsOldLines() {
        var rig = new Rig();
        var adapter = new MusicFrontendTransport(rig.player);
        var rendered = adapter.lyrics(true).content();
        var edit = new MusicSeekModel.Edit(adapter.frame().seekKey(), 3_000);
        assertTrue(adapter.preview(edit));
        rig.lyricsState = OnlineMusicPlayer.LyricsState.LOADING;
        rig.state = State.LOADING;
        var waiting = adapter.lyrics(true);
        assertSame(LyricTimeline.empty(), waiting.content().timeline());
        assertFalse(waiting.preview());
        assertFalse(waiting.seekable());
        assertEquals(-1, waiting.songPositionMs());
        assertFalse(adapter.seekLyric(rendered, 2_000));
        assertFalse(adapter.commit(edit));
        assertTrue(rig.actions.isEmpty());
    }

    @Test void dragFramesAreReadOnlyAndOneReleaseKeepsItsTargetWhileAudioReopens() {
        var rig = new Rig();
        rig.seekStartsLoading = true;
        var adapter = new MusicFrontendTransport(rig.player);
        var content = adapter.lyrics(true).content();
        var frame = adapter.frame();
        var thumb = new MusicSeekModel();
        thumb.sync(frame.seekKey(), 1_000, 12_000, frame.seekable());
        thumb.begin(.25).ifPresent(adapter::preview);
        assertEquals(3_000, adapter.lyrics(true).songPositionMs());
        thumb.drag(.75).ifPresent(adapter::preview);
        assertEquals(9_000, adapter.lyrics(true).songPositionMs());
        assertTrue(rig.actions.isEmpty());
        thumb.release().ifPresent(adapter::commit);
        thumb.release().ifPresent(adapter::commit);
        assertEquals(List.of("seek:9000"), rig.actions);

        int before = rig.snapshots;
        var pending = adapter.presentation(true);
        assertEquals(before + 1, rig.snapshots);
        assertEquals(content, pending.lyrics().content(), "a decoder restart must not reset browse mode");
        assertEquals(9_000, pending.lyrics().songPositionMs());
        assertEquals(9_000, pending.transport().displayedPositionMs());
        assertFalse(pending.lyrics().preview());
        assertFalse(pending.lyrics().seekable());

        rig.state = State.PAUSED;
        rig.position = 9_012;
        assertEquals(9_012, adapter.lyrics(true).songPositionMs());
        rig.state = State.LOADING;
        assertEquals(-1, adapter.lyrics(true).songPositionMs(), "only a user seek pins the loading display");
    }

    @Test void pendingSeekPositionIsNotCarriedIntoARepeatedOccurrence() {
        var rig = new Rig();
        rig.seekStartsLoading = true;
        var adapter = new MusicFrontendTransport(rig.player);
        assertTrue(adapter.commit(new MusicSeekModel.Edit(adapter.frame().seekKey(), 5_000)));
        assertEquals(5_000, adapter.lyrics(true).songPositionMs());
        rig.index++;
        assertEquals(-1, adapter.lyrics(true).songPositionMs());
    }

    private static LyricTimeline timeline() { return new LyricTimeline(List.of()); }

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
        OnlineTrack track=SONG;
        LyricTimeline timeline=timeline();
        OnlineMusicPlayer.LyricsState lyricsState=OnlineMusicPlayer.LyricsState.READY;
        long generation, offset;
        boolean previewStream, seekStartsLoading;
        int index, snapshots;
        long position = 1_000;
        final List<String> actions=new ArrayList<>();
        final OnlineMusicQueue queue=(OnlineMusicQueue)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{OnlineMusicQueue.class},
                (p,m,a)->switch(m.getName()){
                    case "snapshot" -> new OnlineMusicQueue.Snapshot(List.of(SONG,SONG),index,false);
                    case "next","previous" -> {actions.add(m.getName());yield null;}
                    default -> throw new AssertionError(m.getName());
                });
        final OnlineMusicPlayer player=(OnlineMusicPlayer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{OnlineMusicPlayer.class},
                (p,m,a)->switch(m.getName()){
                    case "snapshot" -> {
                        snapshots++;
                        yield new OnlineMusicPlayer.Snapshot(track,state,position,12_000,lyricsState,
                                timeline,OnlineMusicPlayer.Failure.NONE,previewStream);
                    }
                    case "lyricOffsetMs" -> offset;
                    case "queue" -> queue;
                    case "seek" -> {
                        actions.add("seek:"+a[0]);
                        if (seekStartsLoading) { state = State.LOADING; position = 0; }
                        yield null;
                    }
                    case "canRetryCurrent" -> true;
                    case "retryCurrent" -> {actions.add(m.getName());yield true;}
                    case "pause", "resume" -> {actions.add(m.getName());yield null;}
                    default -> throw new AssertionError(m.getName());
                });
    }
}
