// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicFrameBridgeTest {
    private final OnlineTrack song = new OnlineTrack("qq", "123", "123", "Title", "Artist", "Album", 180000);
    private OnlineMusicPlayer.Snapshot sample(OnlineMusicPlayer.State state, boolean preview) {
        return new OnlineMusicPlayer.Snapshot(song, state, 2000, preview ? 30000 : 180000,
                OnlineMusicPlayer.LyricsState.READY, LyricTimeline.empty(), OnlineMusicPlayer.Failure.NONE, preview);
    }
    private MusicService.Frame frame(OnlineMusicPlayer.State state) {
        return OnlineMusicFrameBridge.frame(sample(state, false), 0, "session", 3, 1000);
    }
    @Test void mapsStatesWithoutInventingPlaying() {
        assertEquals(MusicSnapshot.PlaybackStatus.PLAYING, frame(OnlineMusicPlayer.State.PLAYING).snapshot().session().status());
        assertEquals(MusicSnapshot.PlaybackStatus.PAUSED, frame(OnlineMusicPlayer.State.PAUSED).snapshot().session().status());
        assertEquals(MusicSnapshot.PlaybackStatus.BUFFERING, frame(OnlineMusicPlayer.State.LOADING).snapshot().session().status());
        for (var state : new OnlineMusicPlayer.State[]{OnlineMusicPlayer.State.CLOSED, OnlineMusicPlayer.State.IDLE,
                OnlineMusicPlayer.State.ENDED, OnlineMusicPlayer.State.FAILED}) assertNull(frame(state));
    }
    @Test void immutableMetadataAndPreviewUseSongRelativeClock() {
        var current = OnlineMusicFrameBridge.frame(sample(OnlineMusicPlayer.State.PAUSED, true), 45000, "session", 3, 1000);
        var session = current.snapshot().session();
        assertEquals("qq:123", session.track().key());
        assertEquals("Title", session.track().title().orElseThrow());
        assertEquals(47000, session.positionAt(9000));
        assertEquals(180000, session.durationMs().orElseThrow());
        assertFalse(session.capabilities().contains(MusicSnapshot.Capability.AUDIO_FEATURES));
        assertFalse(session.capabilities().contains(MusicSnapshot.Capability.PLAYBACK_CONTROL));
        assertEquals(3, current.revision());
    }
    @Test void scopedSelectionNeverResurrectsOldPublisherOrKeepsStalledFrames() {
        var frames = new ForegroundMusicFrames();
        var system = frame(OnlineMusicPlayer.State.PAUSED);
        var online = frame(OnlineMusicPlayer.State.PLAYING);
        var first = frames.acquire(); first.publish(online, 1000);
        assertSame(online, frames.select(system, 1001));
        assertSame(system, frames.select(system, 1_000_001_000L));
        var second = frames.acquire(); second.publish(online, 2000);
        first.close(); first.publish(system, 3000);
        assertSame(online, frames.select(system, 3000));
        second.close(); second.publish(online, 4000);
        assertSame(system, frames.select(system, 4001));
    }
    @Test void stopImmediatelyReleasesSelectionAndPauseDoesNotAdvance() {
        var frames = new ForegroundMusicFrames(); var lease = frames.acquire();
        var paused = frame(OnlineMusicPlayer.State.PAUSED);
        lease.publish(paused, 1000);
        assertEquals(2000, frames.select(null, 2000).snapshot().session().positionAt(5_000_001_000L));
        lease.publish(null, 2001);
        assertNull(frames.select(null, 2002));
    }
    @Test void backgroundWorkerPublishesAndCloseCannotRepublish() throws Exception {
        var frames = new ForegroundMusicFrames();
        OnlineMusicPlayer player = (OnlineMusicPlayer) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("snapshot")) return sample(OnlineMusicPlayer.State.PLAYING, false);
                    throw new AssertionError("Observer must not invoke " + method.getName());
                });
        var bridge = new OnlineMusicFrameBridge(player, frames.acquire());
        try {
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (frames.select(null, System.nanoTime()) == null && System.nanoTime() < deadline) Thread.sleep(5);
            assertNotNull(frames.select(null, System.nanoTime()));
        } finally { bridge.close(); }
        assertNull(frames.select(null, System.nanoTime()));
    }
}
