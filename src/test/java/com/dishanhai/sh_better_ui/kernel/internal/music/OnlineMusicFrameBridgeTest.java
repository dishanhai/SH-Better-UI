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
                OnlineMusicPlayer.State.ENDED}) assertNull(frame(state));
    }
    @Test void failureWithdrawsSongAndControlsButExposesBoundedRealFailure() {
        var failed = frame(OnlineMusicPlayer.State.FAILED);
        assertEquals(MusicSnapshot.State.TEMPORARILY_UNAVAILABLE, failed.snapshot().state());
        assertNull(failed.snapshot().session());
        assertEquals(MusicService.LyricsState.NO_TRACK, failed.lyrics().state());
        var window = new OnlineMusicFrameBridge.FailureWindow();
        assertTrue(window.visible(OnlineMusicPlayer.State.FAILED, "attempt1", 100));
        assertTrue(window.visible(OnlineMusicPlayer.State.FAILED, "attempt1", 5_000_000_099L));
        assertFalse(window.visible(OnlineMusicPlayer.State.FAILED, "attempt1", 5_000_000_100L));
        assertFalse(window.visible(OnlineMusicPlayer.State.FAILED, "attempt1", 9_000_000_100L));
        assertTrue(window.visible(OnlineMusicPlayer.State.FAILED, "attempt2", 9_000_000_101L));
        assertFalse(window.visible(OnlineMusicPlayer.State.CLOSED, "attempt2", 9_000_000_102L));
        assertFalse(window.visible(OnlineMusicPlayer.State.IDLE, "attempt2", 9_000_000_103L));
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

    @Test void playbackRateScalesHudLyricClockAndPreservesPreviewOffset() {
        long anchor = 1_000_000L;
        long sampled = anchor + 500_000_000L;
        var faster = OnlineMusicFrameBridge.frame(sample(OnlineMusicPlayer.State.PLAYING, true),
                45000, "session", 3, anchor, null, 1.5).snapshot().session();
        assertEquals(1.5, faster.rate().orElseThrow());
        assertEquals(47750, faster.positionAt(sampled));
        var slower = OnlineMusicFrameBridge.frame(sample(OnlineMusicPlayer.State.PLAYING, false),
                0, "session", 3, anchor, null, 0.5).snapshot().session();
        assertEquals(2250, slower.positionAt(sampled));
        var paused = OnlineMusicFrameBridge.frame(sample(OnlineMusicPlayer.State.PAUSED, false),
                0, "session", 3, anchor, null, 2).snapshot().session();
        assertEquals(2000, paused.positionAt(sampled));
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
    @Test void workerFailureWindowTemporarilyOwnsIslandThenRestoresSystemAndCloseClears() {
        var frames = new ForegroundMusicFrames(); var clock=new java.util.concurrent.atomic.AtomicLong(1000);
        var snapshot=new java.util.concurrent.atomic.AtomicReference<>(sample(OnlineMusicPlayer.State.FAILED,false));
        OnlineMusicPlayer player=(OnlineMusicPlayer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class},(proxy,method,args)->{
                    if(method.getName().equals("snapshot"))return snapshot.get();
                    throw new AssertionError("Observer must not invoke "+method.getName());
                });
        var system=frame(OnlineMusicPlayer.State.PLAYING);
        var bridge=new OnlineMusicFrameBridge(player,frames.acquire(),clock::get,false);
        bridge.observe();
        assertEquals(MusicSnapshot.State.TEMPORARILY_UNAVAILABLE,frames.select(system,clock.get()).snapshot().state());
        assertNull(frames.select(system,clock.get()).snapshot().session());
        clock.addAndGet(4_999_999_999L); bridge.observe();
        assertNotSame(system,frames.select(system,clock.get()));
        clock.incrementAndGet(); bridge.observe(); assertSame(system,frames.select(system,clock.get()));
        clock.incrementAndGet(); bridge.observe(); assertSame(system,frames.select(system,clock.get()));
        snapshot.set(sample(OnlineMusicPlayer.State.IDLE,false));bridge.observe();
        snapshot.set(sample(OnlineMusicPlayer.State.FAILED,false));bridge.observe();
        assertNotSame(system,frames.select(system,clock.get()));
        bridge.close(); bridge.observe(); assertSame(system,frames.select(system,clock.get()));
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
