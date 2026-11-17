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

class BackgroundPlayQueueTest {
    private static final OnlineTrack A = song("a"), B = song("b"), C = song("c");
    private static OnlineTrack song(String id) { return new OnlineTrack("test", id, id, id, "Artist", "", 1000); }
    private static class Source implements OnlineMusicSource {
        final List<String> resolved = new CopyOnWriteArrayList<>();
        @Override public String id() { return "test"; }
        @Override public List<OnlineTrack> search(String query, int page, int limit) { return List.of(); }
        @Override public Stream resolve(OnlineTrack track) throws Exception {
            resolved.add(track.id()); return new Stream(URI.create("https://example.org/" + track.id()), 0, 1000, false);
        }
        @Override public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
        @Override public void credentials(String cookie) {}
    }
    private static final class Output implements PlaybackOutput {
        volatile State state; volatile boolean closed;
        @Override public void start(byte[] bytes, long position, boolean playing) { state = playing ? State.PLAYING : State.PAUSED; }
        @Override public void pause() { state = State.PAUSED; }
        @Override public void resume() { state = State.PLAYING; }
        @Override public State state() { return state; }
        @Override public long positionMs() { return 0; }
        @Override public void close() { closed = true; }
    }
    private static final class Rig implements AutoCloseable {
        final Source source;
        final List<Output> outputs = new CopyOnWriteArrayList<>();
        final OnlinePlayerSession player;
        Rig() { this(new Source()); }
        Rig(Source source) {
            this.source = source;
            player = new OnlinePlayerSession(List.of(source), () -> {
                var out = new Output(); outputs.add(out); return out;
            }, uri -> new byte[]{1});
        }
        Output output(int number) throws Exception {
            await(() -> outputs.size() > number && outputs.get(number).state != null); return outputs.get(number);
        }
        @Override public void close() { player.close(); }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }
    private static void noAdvance(Rig rig, int count) throws Exception {
        Thread.sleep(180); assertEquals(count, rig.outputs.size());
    }

    @Test void naturalEndAdvancesWithoutRenderingOrSnapshotPollingAndStopsAtLast() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B,C), 0);
            var first = rig.output(0); first.state = State.ENDED;
            var second = rig.output(1); assertTrue(first.closed); second.state = State.ENDED;
            rig.output(2).state = State.ENDED; noAdvance(rig, 3);
            assertEquals(List.of("a","b","c"), rig.source.resolved);
            assertEquals(2, rig.player.queue().snapshot().index());
        }
    }
    @Test void duplicateTracksAdvanceByQueuePositionNotTrackIdentity() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,A,B), 0);
            rig.output(0).state = State.ENDED; rig.output(1).state = State.ENDED; rig.output(2);
            assertEquals(List.of("a","a","b"), rig.source.resolved);
            assertEquals(2, rig.player.queue().snapshot().index());
        }
    }
    @Test void failedNextDownloadStopsRatherThanSkippingMoreSongs() throws Exception {
        var source = new Source() {
            @Override public Stream resolve(OnlineTrack track) throws Exception {
                if (track.equals(B)) throw new java.io.IOException("offline");
                return super.resolve(track);
            }
        };
        try (var rig = new Rig(source)) {
            rig.player.queue().replace(List.of(A,B,C), 0); rig.output(0).state = State.ENDED;
            await(() -> rig.player.snapshot().state() == State.FAILED);
            noAdvance(rig, 1); assertEquals(B, rig.player.snapshot().track());
            assertEquals(1, rig.player.queue().snapshot().index());
        }
    }
    @Test void replacingQueueWhileOldDownloadFinishesRejectsItsOutput() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var source = new Source() {
            @Override public Stream resolve(OnlineTrack track) throws Exception {
                if (track.equals(A)) {
                    entered.countDown();
                    while (release.getCount() > 0) try { release.await(); } catch (InterruptedException ignored) { }
                }
                return super.resolve(track);
            }
        };
        try (var rig = new Rig(source)) {
            rig.player.queue().replace(List.of(A,B), 0); assertTrue(entered.await(2, TimeUnit.SECONDS));
            rig.player.queue().replace(List.of(C), 0); rig.output(0); release.countDown();
            noAdvance(rig, 1); assertEquals(C, rig.player.snapshot().track());
        } finally { release.countDown(); }
    }

    @Test void pausedEndDoesNotAdvanceUntilExplicitResume() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B), 0); var first = rig.output(0);
            rig.player.pause(); first.state = State.ENDED; noAdvance(rig, 1);
            rig.player.resume(); var replay = rig.output(1); // Existing resume-at-end semantics replay current.
            assertEquals(A, rig.player.snapshot().track()); replay.state = State.ENDED;
            rig.output(2); assertEquals(B, rig.player.snapshot().track());
        }
    }
    @Test void stopAndSingleTrackPlayDetachAutomaticQueueProgress() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B), 0); var first = rig.output(0);
            rig.player.stop(); first.state = State.ENDED; noAdvance(rig, 1);
            assertEquals(State.IDLE, rig.player.snapshot().state());
            rig.player.play(C); rig.output(1).state = State.ENDED; noAdvance(rig, 2);
            assertEquals(List.of(A,B), rig.player.queue().snapshot().tracks());
        }
    }
    @Test void staleOutputEndCannotSkipNewQueueSelection() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B,C), 0); var old = rig.output(0);
            rig.player.queue().select(1); var current = rig.output(1);
            old.state = State.ENDED; noAdvance(rig, 2);
            assertEquals(B, rig.player.snapshot().track());
            current.state = State.ENDED; rig.output(2); assertEquals(C, rig.player.snapshot().track());
        }
    }
    @Test void failureDoesNotSkipAndExplicitSelectionRecovers() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B), 0); rig.output(0).state = State.FAILED;
            noAdvance(rig, 1); rig.player.queue().next(); rig.output(1);
            assertEquals(B, rig.player.snapshot().track());
        }
    }
    @Test void removingCurrentAndUndoNeverSilentlyAdvance() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B,C), 1); var current = rig.output(0);
            rig.player.queue().remove(1); assertEquals(-1, rig.player.queue().snapshot().index());
            rig.player.queue().undo(); assertEquals(1, rig.player.queue().snapshot().index());
            current.state = State.ENDED; noAdvance(rig, 1);
            rig.player.queue().next(); rig.output(1); assertEquals(C, rig.player.snapshot().track());
        }
    }
    @Test void removalBeforeCurrentKeepsNextCorrectAndClearUndoIsMetadataOnly() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B,C), 1); var current = rig.output(0);
            rig.player.queue().remove(0); assertEquals(0, rig.player.queue().snapshot().index());
            current.state = State.ENDED; var last = rig.output(1); assertEquals(C, rig.player.snapshot().track());
            rig.player.queue().clear(); rig.player.queue().undo(); last.state = State.ENDED; noAdvance(rig, 2);
            assertEquals(List.of(B,C), rig.player.queue().snapshot().tracks());
        }
    }
    @Test void undoOfUnplayedTrackRemovalPreservesAutomaticProgress() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B,C), 0); var current = rig.output(0);
            rig.player.queue().remove(1); rig.player.queue().undo(); current.state = State.ENDED;
            rig.output(1); assertEquals(B, rig.player.snapshot().track());
        }
    }
    @Test void cancelledWatcherWaitingForSessionLockCannotAdvanceRestoredQueue() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B), 0); var current = rig.output(0);
            synchronized (rig.player) {
                current.state = State.ENDED;
                await(() -> Thread.getAllStackTraces().keySet().stream().anyMatch(thread ->
                        thread.getName().equals("shb-music-queue") && thread.getState() == Thread.State.BLOCKED));
                rig.player.queue().clear(); rig.player.queue().undo();
            }
            noAdvance(rig, 1); assertEquals(A, rig.player.snapshot().track());
        }
    }

    @Test void invalidReplacementPreservesExistingPlaybackAndQueue() throws Exception {
        try (var rig = new Rig()) {
            rig.player.queue().replace(List.of(A,B), 0); rig.output(0);
            assertThrows(IllegalArgumentException.class, () -> rig.player.queue().replace(List.of(C,song("bad/id")), 0));
            rig.player.queue().select(-1); rig.player.queue().replace(List.of(C), 2);
            assertEquals(List.of(A,B), rig.player.queue().snapshot().tracks()); assertEquals(A, rig.player.snapshot().track());
        }
    }
    @Test void closingSessionClearsQueueAndCannotRestart() throws Exception {
        var rig = new Rig();
        rig.player.queue().replace(List.of(A,B), 0); var first = rig.output(0);
        rig.close(); first.state = State.ENDED; noAdvance(rig, 1);
        assertTrue(rig.player.queue().snapshot().tracks().isEmpty());
        assertThrows(IllegalStateException.class, () -> rig.player.queue().next());
        assertEquals(State.CLOSED, rig.player.snapshot().state());
    }
    @Test void pauseDuringDownloadRemainsPausedAndCredentialChangesCancelQueue() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var source = new Source() {
            @Override public Stream resolve(OnlineTrack track) throws Exception {
                entered.countDown(); release.await(); return super.resolve(track);
            }
        };
        try (var rig = new Rig(source)) {
            rig.player.queue().replace(List.of(A,B), 0); assertTrue(entered.await(2, TimeUnit.SECONDS));
            rig.player.pause(); release.countDown(); assertEquals(State.PAUSED, rig.output(0).state);
            rig.player.credentials("test", "fixture"); rig.outputs.get(0).state = State.ENDED; noAdvance(rig, 1);
            assertEquals(State.IDLE, rig.player.snapshot().state());
        } finally { release.countDown(); }
    }
}
