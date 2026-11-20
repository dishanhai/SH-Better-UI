// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;
import static org.junit.jupiter.api.Assertions.*;

class PlaylistAccountRevocationTest {
    @TempDir Path temp;
    private static final OnlineTrack QQ_A = track("qq", "a"), QQ_B = track("qq", "b"), NE = track("netease", "a");

    @Test void rejectionRevokesTheSourceAndItsLateReadWithoutStoppingAnotherSourceQueue() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var netease = new Source("netease") {
            @Override public OnlineMusicAccount account() {
                started.countDown();
                awaitUninterruptibly(release);
                return new OnlineMusicAccount(id(), "42", "obsolete");
            }
        };
        try (var rig = new Rig(new Source("qq"), netease, new MusicCredentialStore(temp.resolve("accounts")))) {
            var queue = rig.player.queue();
            queue.replace(List.of(QQ_A, QQ_B), 0);
            var output = rig.output(0);
            var before = queue.snapshot();
            var pending = rig.player.account("netease");
            assertTrue(started.await(3, TimeUnit.SECONDS));
            assertRejected(rig.player.playlistDetail("netease", "8"));
            assertTrue(pending.isCancelled());
            assertFalse(rig.player.authenticated("netease"));
            assertTrue(rig.player.authenticated("qq"));
            assertEquals("", netease.cookie);
            assertEquals("qq-cookie", rig.store.load("qq"));
            assertEquals("", rig.store.load("netease"));
            assertFalse(output.closed);
            assertEquals(State.PLAYING, rig.player.snapshot().state());
            assertEquals(QQ_A, rig.player.snapshot().track());
            assertEquals(before, queue.snapshot());
            release.countDown();
            output.state = State.ENDED;
            rig.output(1);
            assertEquals(QQ_B, rig.player.snapshot().track());
            assertEquals(1, queue.snapshot().index());
        } finally {
            release.countDown();
        }
    }

    @Test void failedDiskRevocationStillRevokesOnlyTheRejectedSourceWhileOtherAudioLoads() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var source = new Source("qq") {
            @Override public Stream resolve(OnlineTrack track) throws Exception {
                started.countDown(); release.await();
                return super.resolve(track);
            }
        };
        var folder = temp.resolve("blocked-store");
        var netease = new Source("netease");
        try (var rig = new Rig(source, netease, new MusicCredentialStore(folder))) {
            try (var paths = Files.list(folder)) {
                for (var path : paths.toList()) Files.delete(path);
            }
            Files.delete(folder);
            Files.writeString(folder, "block store directory for fixture");
            rig.player.queue().replace(List.of(QQ_A, QQ_B), 1);
            assertTrue(started.await(3, TimeUnit.SECONDS));
            assertRejected(rig.player.playlistDetail("netease", "8"));
            assertFalse(rig.player.authenticated("netease"));
            assertTrue(rig.player.authenticated("qq"));
            assertEquals("", netease.cookie);
            assertEquals(QQ_B, rig.player.snapshot().track());
            assertEquals(State.LOADING, rig.player.snapshot().state());
            assertEquals(1, rig.player.queue().snapshot().index());
            release.countDown();
            rig.output(0);
            assertEquals(State.PLAYING, rig.player.snapshot().state());
        } finally {
            release.countDown();
        }
    }

    @Test void rejectionStopsMatchingOutputButExplicitAccountChangesStillStopOtherSources() throws Exception {
        try (var rig = new Rig(new Source("qq"), new Source("netease"), null)) {
            var queue = rig.player.queue();
            queue.replace(List.of(NE, QQ_A), 0);
            var first = rig.output(0);
            var before = queue.snapshot();
            assertRejected(rig.player.playlistDetail("netease", "8"));
            assertTrue(first.closed);
            assertEquals(State.IDLE, rig.player.snapshot().state());
            assertNull(rig.player.snapshot().track());
            assertEquals(before, queue.snapshot());

            queue.select(1);
            var other = rig.output(1);
            rig.player.credentials("netease", "explicit-replacement");
            assertTrue(other.closed);
            assertEquals(State.IDLE, rig.player.snapshot().state());
            assertEquals(1, queue.snapshot().index());
        }
    }

    @Test void rejectionCancelsMatchingPendingAudioBeforeItCanOpenAnOutput() throws Exception {
        var resolving = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var netease = new Source("netease") {
            @Override public Stream resolve(OnlineTrack track) throws Exception {
                resolving.countDown();
                try { release.await(); }
                catch (InterruptedException ex) { interrupted.countDown(); awaitUninterruptibly(release); }
                return super.resolve(track);
            }
        };
        try (var rig = new Rig(new Source("qq"), netease, null)) {
            var queue = rig.player.queue(); queue.replace(List.of(NE, QQ_A), 0);
            assertTrue(resolving.await(3, TimeUnit.SECONDS));
            assertRejected(rig.player.playlistDetail("netease", "8"));
            assertTrue(interrupted.await(3, TimeUnit.SECONDS));
            assertEquals(State.IDLE, rig.player.snapshot().state());
            assertEquals(0, queue.snapshot().index());
            release.countDown();
            queue.select(1);
            rig.output(0);
            assertEquals(1, rig.outputs.size());
            assertEquals(QQ_A, rig.player.snapshot().track());
        } finally {
            release.countDown();
        }
    }

    private static void assertRejected(CompletableFuture<?> pending) {
        var denied = assertThrows(ExecutionException.class, () -> pending.get(3, TimeUnit.SECONDS));
        assertInstanceOf(OnlineMusicSource.LoginRequired.class, denied.getCause());
    }
    private static void awaitUninterruptibly(CountDownLatch release) {
        for (;;) {
            try { release.await(); return; } catch (InterruptedException ignored) { }
        }
    }
    private static OnlineTrack track(String provider, String id) { return new OnlineTrack(provider, id, id, "Track", "", "", 1000); }

    private static class Source implements OnlineMusicSource {
        private final String id;
        volatile String cookie = "";
        Source(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public void credentials(String value) { cookie = value; }
        @Override public boolean supportsPlaylistDetail() { return true; }
        @Override public OnlinePlaylistDetail playlistDetail(String ignored) throws Exception { throw new LoginRequired(); }
        @Override public List<OnlineTrack> search(String text, int page, int limit) { return List.of(); }
        @Override public Stream resolve(OnlineTrack track) throws Exception {
            return new Stream(URI.create("https://example.org/" + id + "/" + track.id()), 0, 1000, false);
        }
        @Override public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
    }
    private static final class Output implements PlaybackOutput {
        volatile State state;
        volatile boolean closed;
        @Override public void start(byte[] bytes, long position, boolean playing) { state = playing ? State.PLAYING : State.PAUSED; }
        @Override public void pause() { state = State.PAUSED; }
        @Override public void resume() { state = State.PLAYING; }
        @Override public State state() { return state; }
        @Override public long positionMs() { return 0; }
        @Override public void close() { closed = true; }
    }
    private static final class Rig implements AutoCloseable {
        final List<Output> outputs = new CopyOnWriteArrayList<>();
        final OnlinePlayerSession player;
        final MusicCredentialStore store;
        Rig(Source qq, Source ne, MusicCredentialStore store) {
            this.store = store;
            player = new OnlinePlayerSession(List.of(qq, ne), () -> {
                var output = new Output(); outputs.add(output); return output;
            }, uri -> new byte[]{1}, store);
            for (var source : List.of(qq, ne)) {
                if (store != null) player.rememberCredentials(source.id(), true);
                player.credentials(source.id(), source.id() + "-cookie");
            }
        }
        Output output(int index) throws InterruptedException {
            BooleanSupplier ready = () -> outputs.size() > index && outputs.get(index).state != null;
            long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!ready.getAsBoolean() && System.nanoTime() < timeout) Thread.sleep(5);
            assertTrue(ready.getAsBoolean());
            return outputs.get(index);
        }
        @Override public void close() { player.close(); }
    }
}
