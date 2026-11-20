// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService.*;

final class BackgroundMusicControlsTest {
    private Object previousRenderThread;
    private java.lang.reflect.Field renderThread;
    @BeforeEach void bind() throws Exception {
        renderThread = com.mojang.blaze3d.systems.RenderSystem.class.getDeclaredField("renderThread");
        renderThread.setAccessible(true); previousRenderThread = renderThread.get(null); renderThread.set(null, Thread.currentThread());
    }
    @AfterEach void restore() throws Exception { renderThread.set(null, previousRenderThread); }
    static OnlineTrack song(String id) { return new OnlineTrack("test", id, id, "Song " + id, "Artist", "", 10000); }
    static final class Source implements OnlineMusicSource {
        final boolean preview;
        Source(boolean preview) { this.preview = preview; }
        public String id() { return "test"; }
        public List<OnlineTrack> search(String query, int page, int limit) { return List.of(); }
        public Stream resolve(OnlineTrack song) { return new Stream(URI.create("https://invalid.test/audio"), preview ? 3000 : 0, 10000, preview); }
        public LyricTimeline lyrics(OnlineTrack song) { return LyricTimeline.empty(); }
    }
    static final class Output implements PlaybackOutput {
        OnlineMusicPlayer.State state; long position;
        public void start(byte[] bytes, long position, boolean playing) { this.position = position; state = playing ? OnlineMusicPlayer.State.PLAYING : OnlineMusicPlayer.State.PAUSED; }
        public void pause() { state = OnlineMusicPlayer.State.PAUSED; }
        public void resume() { state = OnlineMusicPlayer.State.PLAYING; }
        public OnlineMusicPlayer.State state() { return state; }
        public long positionMs() { return position; }
        public void close() {}
    }
    static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }
    static final class Fixture implements AutoCloseable {
        final ForegroundMusicFrames frames = new ForegroundMusicFrames();
        final OnlinePlayerSession player;
        final ClientOnlineMusic service;
        final AtomicInteger creations = new AtomicInteger();
        Fixture(boolean preview) {
            player = new OnlinePlayerSession(List.of(new Source(preview)), Output::new, uri -> new byte[]{1});
            service = new ClientOnlineMusic(() -> { creations.incrementAndGet(); return player; },
                    () -> frames.select(null, System.nanoTime()), frames::acquire);
        }
        BackgroundTarget play() throws Exception {
            service.backgroundView(); return startQueue();
        }
        BackgroundTarget startQueue() throws Exception {
            player.queue().replace(List.of(song("a"), song("b")), 0);
            await(() -> player.snapshot().state() == OnlineMusicPlayer.State.PLAYING && frames.select(null, System.nanoTime()) != null);
            var session = frames.select(null, System.nanoTime()).snapshot().session();
            return new BackgroundTarget(session.id(), session.track().key());
        }
        public void close() { service.close(); player.close(); }
    }
    @Test void redesignedWorkspaceCanClosePlayThroughHudAndReopenWithoutAnotherSession() throws Exception {
        try (var f = new Fixture(false)) {
            var preview = f.service.redesignedBackgroundView();
            assertSame(f.player, preview.player());
            var playing = f.startQueue();
            preview.close();
            assertEquals(OnlineMusicPlayer.State.PLAYING, f.player.snapshot().state());
            assertTrue(f.service.controlBackground(playing, BackgroundCommand.PAUSE, -1));
            assertTrue(f.service.controlBackground(playing, BackgroundCommand.NEXT, -1));
            assertEquals("b", f.player.snapshot().track().id());
            try (var reopened = f.service.redesignedBackgroundView(); var stock = f.service.backgroundView()) {
                assertSame(f.player, reopened.player());
                assertSame(f.player, stock.player());
                assertEquals(1, f.creations.get());
                assertEquals(1, reopened.player().queue().snapshot().index());
                assertFalse(f.service.controlBackground(playing, BackgroundCommand.RESUME, -1));
            }
            assertNotEquals(OnlineMusicPlayer.State.CLOSED, f.player.snapshot().state());
        }
    }
    @Test void queryingAbsentSessionNeverCreatesOrStartsAnything() {
        try (var f = new Fixture(false)) {
            var target = new BackgroundTarget("missing", "test:a");
            assertTrue(f.service.backgroundControls(target).isEmpty());
            assertFalse(f.service.controlBackground(target, BackgroundCommand.PAUSE, -1));
            assertEquals(0, f.creations.get());
        }
    }
    @Test void pauseResumeSeekAndQueueCommandsUseActualExistingPlayer() throws Exception {
        try (var f = new Fixture(false)) {
            var target = f.play();
            assertTrue(f.service.backgroundControls(target).orElseThrow().next());
            assertFalse(f.service.backgroundControls(target).orElseThrow().previous());
            assertTrue(f.service.controlBackground(target, BackgroundCommand.PAUSE, -1));
            assertEquals(OnlineMusicPlayer.State.PAUSED, f.player.snapshot().state());
            assertTrue(f.service.controlBackground(target, BackgroundCommand.SEEK, .5));
            assertEquals(5000, f.player.snapshot().positionMs());
            assertTrue(f.service.controlBackground(target, BackgroundCommand.RESUME, -1));
            assertTrue(f.service.controlBackground(target, BackgroundCommand.NEXT, -1));
            assertEquals("b", f.player.snapshot().track().id());
            assertFalse(f.service.controlBackground(target, BackgroundCommand.PAUSE, -1));
            await(() -> { var frame = f.frames.select(null, System.nanoTime()); return frame != null && frame.snapshot().session().track().key().equals("test:b") && f.player.snapshot().state() == OnlineMusicPlayer.State.PLAYING; });
            var current = new BackgroundTarget(f.frames.select(null, System.nanoTime()).snapshot().session().id(), "test:b");
            assertTrue(f.service.controlBackground(current, BackgroundCommand.PREVIOUS, -1));
            assertEquals("a", f.player.snapshot().track().id());
        }
    }
    @Test void foregroundReplacementStopAndShutdownInvalidateOldControls() throws Exception {
        try (var f = new Fixture(false)) {
            var target = f.play();
            var competing = f.frames.acquire();
            assertFalse(f.service.controlBackground(target, BackgroundCommand.PAUSE, -1));
            assertEquals(OnlineMusicPlayer.State.PLAYING, f.player.snapshot().state());
            competing.close(); f.player.stop();
            assertTrue(f.service.backgroundControls(target).isEmpty());
            f.service.close(); assertFalse(f.service.controlBackground(target, BackgroundCommand.RESUME, -1));
        }
    }
    @Test void restartingSameTrackRejectsOldGenerationBeforeAndAfterBridgeRefresh() throws Exception {
        try (var f = new Fixture(false)) {
            var old = f.play();
            synchronized (f.player) {
                f.player.play(song("a"));
                assertTrue(f.service.backgroundControls(old).isEmpty());
                assertFalse(f.service.controlBackground(old, BackgroundCommand.SEEK, .8));
            }
            await(() -> f.player.snapshot().state() == OnlineMusicPlayer.State.PLAYING);
            await(() -> { var frame = f.frames.select(null, System.nanoTime());
                return frame != null && !frame.snapshot().session().id().equals(old.sessionId()); });
            assertFalse(f.service.controlBackground(old, BackgroundCommand.PAUSE, -1));
            assertEquals(OnlineMusicPlayer.State.PLAYING, f.player.snapshot().state());
            var current = f.frames.select(null, System.nanoTime()).snapshot().session();
            assertEquals(old.trackKey(), current.track().key());
            assertTrue(f.service.controlBackground(new BackgroundTarget(current.id(), current.track().key()), BackgroundCommand.PAUSE, -1));
        }
    }
    @Test void previewDoesNotAdvertiseSongRelativeSeekAndInvalidFractionsReject() throws Exception {
        try (var f = new Fixture(true)) {
            var target = f.play();
            assertFalse(f.service.backgroundControls(target).orElseThrow().seek());
            assertFalse(f.service.controlBackground(target, BackgroundCommand.SEEK, .5));
            assertThrows(IllegalArgumentException.class, () -> f.service.controlBackground(target, BackgroundCommand.SEEK, Double.NaN));
        }
    }
}
