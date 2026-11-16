// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LyricSeekTargetTest {
    private final OnlineTrack track = new OnlineTrack("qq", "a", "a", "a", "", "", 10000);
    private final LyricTimeline timeline = new LyricTimeline(List.of());
    private OnlineMusicPlayer.Snapshot snapshot(OnlineTrack song, LyricTimeline lyrics, OnlineMusicPlayer.State state) {
        return new OnlineMusicPlayer.Snapshot(song, state, 100, 10000,
                OnlineMusicPlayer.LyricsState.READY, lyrics, OnlineMusicPlayer.Failure.NONE, true);
    }
    @Test void rejectsStaleTrackTimelineAndStoppedPlayback() {
        var target = new LyricSeekTarget(track, timeline, 4000, 1000);
        assertTrue(target.position(snapshot(new OnlineTrack("qq", "b", "b", "b", "", "", 10000), timeline, OnlineMusicPlayer.State.PLAYING)).isEmpty());
        assertTrue(target.position(snapshot(track, new LyricTimeline(List.of()), OnlineMusicPlayer.State.PLAYING)).isEmpty());
        assertTrue(target.position(snapshot(track, timeline, OnlineMusicPlayer.State.IDLE)).isEmpty());
    }
    @Test void previewOffsetsRemainRelativeAndNonnegative() {
        assertEquals(3000, new LyricSeekTarget(track, timeline, 4000, 1000).position(snapshot(track, timeline, OnlineMusicPlayer.State.PLAYING)).orElseThrow());
        assertEquals(0, new LyricSeekTarget(track, timeline, 400, 1000).position(snapshot(track, timeline, OnlineMusicPlayer.State.PAUSED)).orElseThrow());
    }
}
