// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.*;

class OnlinePlayerSessionTest {
    private Object previousRenderThread;
    private java.lang.reflect.Field renderThread;
    @org.junit.jupiter.api.BeforeEach void bindRenderThread() throws Exception {
        renderThread = com.mojang.blaze3d.systems.RenderSystem.class.getDeclaredField("renderThread");
        renderThread.setAccessible(true); previousRenderThread = renderThread.get(null);
        renderThread.set(null, Thread.currentThread());
    }
    @org.junit.jupiter.api.AfterEach void restoreRenderThread() throws Exception {
        renderThread.set(null, previousRenderThread);
    }

    private static OnlineTrack track(String id) { return new OnlineTrack("test", id, id, id, "Artist", "", 10000); }
    private static class Source implements OnlineMusicSource {
        @Override public String id() { return "test"; }
        @Override public List<OnlineTrack> search(String query, int page, int limit) throws Exception { return List.of(track(query)); }
        @Override public Stream resolve(OnlineTrack track) throws Exception { return new Stream(URI.create("https://example.org/" + track.id()), 0, 10000, false); }
        @Override public LyricTimeline lyrics(OnlineTrack track) throws Exception { return LrcParser.parse("[00:00]" + track.id(), null, 10000); }
    }
    private static class Output implements PlaybackOutput {
        State state; long position; boolean closed; float gain=1f;
        @Override public void volume(float value) { gain=value; }
        @Override public void start(byte[] bytes, long position, boolean playing) { this.position = position; state = playing ? State.PLAYING : State.PAUSED; }
        @Override public void pause() { state = State.PAUSED; }
        @Override public void resume() { state = State.PLAYING; }
        @Override public State state() { return state; }
        @Override public long positionMs() { return position; }
        @Override public void close() { closed = true; }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "asynchronous state did not settle");
    }
    @Test void bridgeTracksBorrowedViewRemovalPauseSwitchAndStop() throws Exception {
        var frames = new com.dishanhai.sh_better_ui.client.music.ForegroundMusicFrames();
        try (var session = new OnlinePlayerSession(List.of(new Source()), Output::new, uri -> new byte[]{1});
             var bridge = new OnlineMusicFrameBridge(session, frames.acquire())) {
            session.play(track("first"));
            await(() -> frames.select(null, System.nanoTime()) != null);
            var view = new com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl(session, false);
            view.suspend(); view.close();
            await(() -> frames.select(null, System.nanoTime()).snapshot().session().status()
                    == com.dishanhai.sh_better_ui.client.music.MusicSnapshot.PlaybackStatus.PLAYING);
            String id = frames.select(null, System.nanoTime()).snapshot().session().id();
            session.pause();
            await(() -> frames.select(null, System.nanoTime()).snapshot().session().status()
                    == com.dishanhai.sh_better_ui.client.music.MusicSnapshot.PlaybackStatus.PAUSED);
            assertEquals(id, frames.select(null, System.nanoTime()).snapshot().session().id(), "pause keeps the playback attempt");
            session.play(track("second"));
            await(() -> frames.select(null, System.nanoTime()).snapshot().session().track().key().equals("test:second"));
            assertNotEquals(id, frames.select(null, System.nanoTime()).snapshot().session().id(), "new playback revokes old action targets");
            session.stop(); await(() -> frames.select(null, System.nanoTime()) == null);
        }
    }

    @Test void softwareVolumeIsSessionLocalAndRetainedAcrossOutputReplacement() throws Exception {
        var last=new AtomicReference<Output>();
        var session=new OnlinePlayerSession(List.of(new Source()),()->{var output=new Output();last.set(output);return output;},uri->new byte[]{1});
        try {
            assertTrue(session.supportsVolume());session.volume(.25f);
            session.play(track("a"));await(()->session.snapshot().state()==State.PLAYING);
            assertEquals(.25f,last.get().gain);session.volume(.75f);assertEquals(.75f,last.get().gain);
            var previous=last.get();session.seek(2000);assertNotSame(previous,last.get());assertEquals(.75f,last.get().gain);
            session.stop();assertEquals(.75f,session.volume());
            session.play(track("b"));await(()->session.snapshot().state()==State.PLAYING);assertEquals(.75f,last.get().gain);
            assertThrows(IllegalArgumentException.class,()->session.volume(Float.NaN));assertEquals(.75f,session.volume());
            session.volume(-10);assertEquals(0f,last.get().gain);session.volume(10);assertEquals(1f,last.get().gain);
        } finally {session.close();}
        assertThrows(IllegalStateException.class,()->session.volume(.5f));
    }
    @Test void leavingAndReopeningBorrowedViewKeepsActiveOutputAndExplicitControlsWork() throws Exception {
        var output = new Output();
        try (var session = new OnlinePlayerSession(List.of(new Source()), () -> output, uri -> new byte[]{1})) {
            session.play(track("background"));
            await(() -> session.snapshot().state() == State.PLAYING);
            var view = new com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl(session, false);
            view.suspend(); view.close();
            assertFalse(output.closed);
            assertEquals(State.PLAYING, session.snapshot().state());
            output.position = 2300;
            var reopened = new com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl(session, false);
            assertEquals(2300, reopened.player().snapshot().positionMs());
            reopened.player().pause(); assertEquals(State.PAUSED, session.snapshot().state());
            reopened.player().resume(); assertEquals(State.PLAYING, session.snapshot().state());
            reopened.player().stop(); assertTrue(output.closed);
            assertEquals(State.IDLE, session.snapshot().state()); reopened.close();
        }
    }
    @Test void pendingTrackCanFinishLoadingAfterBackgroundViewCloses() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var session = new OnlinePlayerSession(List.of(new Source()), Output::new, uri -> {
            started.countDown(); release.await(); return new byte[]{1};
        })) {
            session.play(track("background")); assertTrue(started.await(2, TimeUnit.SECONDS));
            var view = new com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl(session, false);
            view.close(); release.countDown();
            await(() -> session.snapshot().state() == State.PLAYING);
        } finally { release.countDown(); }
    }
    @Test void pauseSeekStopAndCloseReleaseOwnedOutputs() throws Exception {
        var last = new AtomicReference<Output>();
        var session = new OnlinePlayerSession(List.of(new Source()), () -> { var out = new Output(); last.set(out); return out; }, uri -> new byte[]{1});
        try {
            session.play(track("a"));
            await(() -> session.snapshot().state() == State.PLAYING && session.snapshot().lyricsState() == LyricsState.READY);
            session.pause(); assertEquals(State.PAUSED, session.snapshot().state());
            var old = last.get(); session.seek(7000);
            assertTrue(old.closed); assertEquals(State.PAUSED, session.snapshot().state());
            assertEquals(7000, session.snapshot().positionMs());
            assertEquals(7000, session.lyricFeed().frame().snapshot().positionMs());
            session.resume(); assertEquals(State.PLAYING, session.snapshot().state());
            session.stop(); assertEquals(State.IDLE, session.snapshot().state()); assertTrue(last.get().closed);
            assertFalse(session.lyricFeed().isAlive());
        } finally { session.close(); }
        assertEquals(State.CLOSED, session.snapshot().state());
        assertThrows(IllegalStateException.class, () -> session.play(track("a")));
        session.close();
    }
    @Test void lateAudioAndLyricsCannotReplaceNewTrackEvenIfProviderIgnoresCancellation() throws Exception {
        var started = new CountDownLatch(2); var release = new CountDownLatch(1);
        var source = new Source() {
            void waitOld(OnlineTrack track) {
                if (!track.id().equals("old")) return;
                started.countDown();
                boolean done = false;
                while (!done) { try { release.await(); done = true; } catch (InterruptedException ignored) { } }
            }
            @Override public Stream resolve(OnlineTrack track) throws Exception { waitOld(track); return super.resolve(track); }
            @Override public LyricTimeline lyrics(OnlineTrack track) throws Exception { waitOld(track); return super.lyrics(track); }
        };
        try (var session = new OnlinePlayerSession(List.of(source), Output::new, uri -> new byte[]{1})) {
            session.play(track("old")); assertTrue(started.await(2, TimeUnit.SECONDS));
            session.play(track("new")); release.countDown();
            await(() -> session.snapshot().state() == State.PLAYING && session.snapshot().lyricsState() == LyricsState.READY);
            assertEquals("new", session.snapshot().track().id());
            assertEquals("new", session.snapshot().lyrics().line(0).text());
        } finally { release.countDown(); }
    }
    @Test void unavailableStreamIsNotReportedAsNetworkFailureAndLyricsCannotStartPlayback() throws Exception {
        var source = new Source() { @Override public Stream resolve(OnlineTrack track) { return null; } };
        try (var session = new OnlinePlayerSession(List.of(source), Output::new, uri -> new byte[]{1})) {
            session.play(track("a")); await(() -> session.snapshot().state() == State.FAILED);
            assertEquals(Failure.UNAVAILABLE, session.snapshot().failure());
            assertFalse(session.lyricFeed().isAlive());
        }
    }
    @Test void pausingWhileDownloadingCannotStartSoundWhenTheViewIsHidden() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var session = new OnlinePlayerSession(List.of(new Source()), Output::new, uri -> {
            started.countDown(); release.await(); return new byte[]{1};
        })) {
            session.play(track("a")); assertTrue(started.await(2, TimeUnit.SECONDS));
            session.pause(); release.countDown();
            await(() -> session.snapshot().state() == State.PAUSED);
            session.resume(); assertEquals(State.PLAYING, session.snapshot().state());
        } finally { release.countDown(); }
    }
    @Test void cancellingSearchInterruptsTheProviderAndClosingCompletesPendingFutures() throws Exception {
        var started = new CountDownLatch(1); var interrupted = new CountDownLatch(1);
        var source = new Source() {
            @Override public List<OnlineTrack> search(String query, int page, int limit) throws Exception {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException error) { interrupted.countDown(); throw error; }
                return List.of();
            }
        };
        try (var session = new OnlinePlayerSession(List.of(source), Output::new, uri -> new byte[]{1})) {
            var query = session.search("test", "query", 1); assertTrue(started.await(2, TimeUnit.SECONDS));
            query.cancel(true); assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            var second = session.search("test", "query", 1);
            session.close(); assertTrue(second.isCancelled());
        }
    }
}
