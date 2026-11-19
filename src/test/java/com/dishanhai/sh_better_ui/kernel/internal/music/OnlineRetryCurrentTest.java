// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;

class OnlineRetryCurrentTest {
    private static final OnlineTrack A = song("a"), B = song("b");
    private static OnlineTrack song(String id) { return new OnlineTrack("test", id, id, id, "Artist", "", 1000); }
    private static final class Source implements OnlineMusicSource {
        final List<String> resolved = new CopyOnWriteArrayList<>();
        volatile boolean fail = true;
        int credentialChanges;
        volatile CountDownLatch block;
        @Override public String id() { return "test"; }
        @Override public List<OnlineTrack> search(String q, int page, int limit) { return List.of(); }
        @Override public Stream resolve(OnlineTrack t) throws Exception {
            resolved.add(t.id());
            if (block != null) block.await();
            if (fail) throw new java.io.IOException("test failure");
            return new Stream(URI.create("https://example.org/" + t.id()), 0, 1000, false);
        }
        @Override public LyricTimeline lyrics(OnlineTrack t) { return LyricTimeline.empty(); }
        @Override public void credentials(String cookie) { credentialChanges++; }
    }
    private static final class Output implements PlaybackOutput {
        volatile State state;
        @Override public void start(byte[] bytes, long position, boolean playing) { assertEquals(0, position); state = State.PLAYING; }
        @Override public void pause() { state = State.PAUSED; }
        @Override public void resume() { state = State.PLAYING; }
        @Override public State state() { return state; }
        @Override public long positionMs() { return 0; }
        @Override public void close() { }
    }
    private static final class Rig implements AutoCloseable {
        final Source source = new Source();
        final List<Output> outputs = new CopyOnWriteArrayList<>();
        final OnlinePlayerSession player = new OnlinePlayerSession(List.of(source), () -> {
            var output = new Output(); outputs.add(output); return output;
        }, uri -> new byte[]{1});
        @Override public void close() { player.close(); }
    }
    private static void await(BooleanSupplier c) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!c.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(c.getAsBoolean());
    }
    @Test void freshResolveAndLoadingRejectsRepeatedClicksWithoutQueueMutation() throws Exception {
        try (var r = new Rig()) {
            var q = r.player.queue(); q.replace(List.of(A, B, A, B), 2);
            await(() -> r.player.snapshot().state() == State.FAILED);
            var before = q.snapshot();
            r.source.fail = false; r.source.block = new CountDownLatch(1);
            assertTrue(r.player.retryCurrent());
            assertEquals(State.LOADING, r.player.snapshot().state());
            for (int i = 0; i < 20; i++) assertFalse(r.player.retryCurrent());
            await(() -> r.source.resolved.size() == 2);
            assertEquals(before, q.snapshot());
            r.source.block.countDown();
            await(() -> r.player.snapshot().state() == State.PLAYING);
            assertEquals(List.of("a", "a"), r.source.resolved);
            assertEquals(0, r.source.credentialChanges);
            assertFalse(r.player.retryCurrent());
            r.outputs.get(0).state = State.ENDED;
            await(() -> q.snapshot().index() == 3);
            assertEquals(B, r.player.snapshot().track());
        }
    }
    @Test void failedRetryNeverRetriesOrSkipsAutomatically() throws Exception {
        try (var r = new Rig()) {
            r.player.queue().replace(List.of(A,B), 0);
            await(() -> r.player.snapshot().state() == State.FAILED);
            assertTrue(r.player.retryCurrent());
            await(() -> r.source.resolved.size() == 2 && r.player.snapshot().state() == State.FAILED);
            Thread.sleep(180);
            assertEquals(List.of("a","a"), r.source.resolved);
            assertEquals(0, r.player.queue().snapshot().index());
            assertTrue(r.player.retryCurrent());
            await(() -> r.source.resolved.size() == 3 && r.player.snapshot().state() == State.FAILED);
        }
    }
    @Test void directPlaybackOfSameTrackDoesNotReattachQueue() throws Exception {
        try (var r = new Rig()) {
            var q = r.player.queue(); q.replace(List.of(A,B), 0);
            await(() -> r.player.snapshot().state() == State.FAILED);
            r.player.play(A);
            await(() -> r.source.resolved.size() == 2 && r.player.snapshot().state() == State.FAILED);
            r.source.fail = false; assertTrue(r.player.retryCurrent());
            await(() -> r.player.snapshot().state() == State.PLAYING);
            r.outputs.get(0).state = State.ENDED; Thread.sleep(180);
            assertEquals(3, r.source.resolved.size());
            assertEquals(A, r.player.snapshot().track());
            assertEquals(0, q.snapshot().index());
        }
    }
    @Test void outputFailureUsesFreshResolveAndRetainsShuffleHistory() throws Exception {
        try (var r = new Rig()) {
            r.source.fail = false;
            var q = r.player.queue(); q.replace(List.of(A,B), 0);
            await(() -> r.player.snapshot().state() == State.PLAYING);
            q.mode(OnlineMusicQueue.Mode.SHUFFLE); q.next();
            await(() -> r.outputs.size() == 2 && r.player.snapshot().state() == State.PLAYING);
            var before = q.snapshot(); assertTrue(before.canPrevious());
            r.outputs.get(1).state = State.FAILED;
            assertTrue(r.player.retryCurrent());
            assertEquals(before, q.snapshot());
            await(() -> r.outputs.size() == 3 && r.player.snapshot().state() == State.PLAYING);
            assertEquals(3, r.source.resolved.size());
            q.previous(); await(() -> r.outputs.size() == 4);
            assertEquals(A, r.player.snapshot().track());
        }
    }
    @Test void idleStoppedClosedAndPausedDoNotStartRequests() throws Exception {
        var r = new Rig();
        try {
            assertFalse(r.player.retryCurrent());
            r.source.fail = false; r.player.play(A);
            await(() -> r.player.snapshot().state() == State.PLAYING);
            r.player.pause(); assertFalse(r.player.retryCurrent());
            r.player.stop(); assertFalse(r.player.retryCurrent());
            r.player.close(); assertFalse(r.player.retryCurrent());
            assertEquals(1, r.source.resolved.size());
        } finally { r.close(); }
    }
    @Test void stopDuringRetryDiscardsLateCompletion() throws Exception {
        try (var r = new Rig()) {
            r.player.play(A); await(() -> r.player.snapshot().state() == State.FAILED);
            r.source.fail = false; r.source.block = new CountDownLatch(1);
            assertTrue(r.player.retryCurrent()); await(() -> r.source.resolved.size() == 2);
            r.player.stop(); r.source.block.countDown(); Thread.sleep(100);
            assertEquals(State.IDLE, r.player.snapshot().state());
            assertNull(r.player.snapshot().track()); assertTrue(r.outputs.isEmpty());
        }
    }
}
