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

    @Test void appendToIdleNeverStartsNetworkOrAudioAndCanUndo() {
        try (var rig = new Rig()) {
            var queue = rig.player.queue();
            assertTrue(queue.supportsEditing()); queue.append(List.of(A,A,B));
            assertEquals(List.of(A,A,B), queue.snapshot().tracks());
            assertEquals(-1, queue.snapshot().index());
            assertEquals(State.IDLE, rig.player.snapshot().state());
            assertTrue(rig.source.resolved.isEmpty()); assertTrue(rig.outputs.isEmpty());
            queue.undo(); assertTrue(queue.snapshot().tracks().isEmpty());
        }
    }
    @Test void appendRetainsPausedOutputAndNaturalEndUsesNewEntry() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A), 0);
            var output = rig.output(0); rig.player.pause();
            queue.append(List.of(B));
            assertEquals(State.PAUSED, rig.player.snapshot().state());
            assertFalse(output.closed); assertEquals(1, rig.outputs.size());
            rig.player.resume(); output.state = State.ENDED; rig.output(1);
            assertEquals(B, rig.player.snapshot().track());
        }
    }
    @Test void moveKeepsExactDuplicateEntryAndUpdatesNaturalSuccessor() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A,A,B,C), 1);
            var output = rig.output(0); queue.move(1, 2);
            assertEquals(List.of(A,B,A,C), queue.snapshot().tracks());
            assertEquals(2, queue.snapshot().index()); assertFalse(output.closed);
            queue.undo(); assertEquals(1, queue.snapshot().index());
            queue.move(0, 3); assertEquals(0, queue.snapshot().index());
            output.state = State.ENDED; rig.output(1);
            assertEquals(B, rig.player.snapshot().track());
        }
    }
    @Test void editsPreserveModeAndUndoRestoresShuffleNavigation() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A,B,C), 0); rig.output(0);
            queue.mode(OnlineMusicQueue.Mode.SHUFFLE); queue.next(); rig.output(1);
            int selected = queue.snapshot().index(); assertTrue(queue.snapshot().canPrevious());
            queue.append(List.of(A)); assertEquals(OnlineMusicQueue.Mode.SHUFFLE, queue.mode());
            assertFalse(queue.snapshot().canPrevious()); queue.undo();
            assertTrue(queue.snapshot().canPrevious()); assertEquals(selected, queue.snapshot().index());
            queue.previous(); rig.output(2); assertEquals(A, rig.player.snapshot().track());
        }
    }
    @Test void undoAfterAdvanceRestoresExactDuplicatePosition() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A,B,A,C), 0); rig.output(0);
            queue.move(0,1); queue.next(); var output = rig.output(1);
            queue.undo(); assertEquals(2, queue.snapshot().index()); assertFalse(output.closed);
            queue.next(); rig.output(2); assertEquals(C, rig.player.snapshot().track());
        }
    }
    @Test void undoAfterPlayingAppendedDuplicateDetachesWithoutInterruptingAudio() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A,B), 0); rig.output(0);
            queue.append(List.of(A)); queue.select(2); var output = rig.output(1);
            queue.undo(); assertEquals(-1, queue.snapshot().index());
            assertEquals(List.of(A,B), queue.snapshot().tracks()); assertFalse(output.closed);
            output.state = State.ENDED; noAdvance(rig,2);
        }
    }

    @Test void editDuringLoadingPreservesSelectionAndCompletion() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var source = new Source() {
            @Override public Stream resolve(OnlineTrack track) throws Exception {
                entered.countDown(); release.await(); return super.resolve(track);
            }
        };
        try (var rig = new Rig(source)) {
            var queue = rig.player.queue(); queue.replace(List.of(A,B), 0);
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            queue.move(0,1); queue.append(List.of(C)); release.countDown();
            var output = rig.output(0); assertEquals(1,queue.snapshot().index());
            assertEquals(A,rig.player.snapshot().track()); output.state = State.ENDED;
            rig.output(1); assertEquals(C,rig.player.snapshot().track());
        } finally { release.countDown(); }
    }
    @Test void undoAfterDirectPlaybackDoesNotReattachOldQueue() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A,B),0); rig.output(0);
            queue.append(List.of(C)); rig.player.play(C); var output = rig.output(1);
            queue.undo(); assertEquals(-1,queue.snapshot().index());
            assertFalse(output.closed); output.state = State.ENDED; noAdvance(rig,2);
        }
    }

    @Test void invalidAppendIsAtomicAndNoOpEditsKeepUndo() {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.append(List.of(A));
            var before = queue.snapshot();
            var unknown = new OnlineTrack("unknown", "x", "x", "x", "", "", 1);
            assertThrows(IllegalArgumentException.class, () -> queue.append(List.of(B,unknown)));
            assertEquals(before, queue.snapshot());
            queue.append(List.of()); queue.move(-1,0); queue.move(0,1); queue.move(0,0);
            assertEquals(before, queue.snapshot()); rig.player.close();
            assertThrows(IllegalStateException.class, () -> queue.append(List.of(A)));
            assertThrows(IllegalStateException.class, () -> queue.move(0,0));
        }
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
    @Test void repeatOneContinuesWithoutUiAndExplicitNextSkips() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.mode(OnlineMusicQueue.Mode.REPEAT_ONE);
            queue.replace(List.of(A,B), 0); rig.output(0).state = State.ENDED;
            rig.output(1); assertEquals(A, rig.player.snapshot().track());
            queue.next(); rig.output(2); assertEquals(B, rig.player.snapshot().track());
            rig.output(2).state = State.ENDED; rig.output(3);
            assertEquals(B, rig.player.snapshot().track());
        }
    }
    @Test void repeatAllWrapsAndSwitchingToSequentialAtEndStops() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.mode(OnlineMusicQueue.Mode.REPEAT_ALL);
            queue.replace(List.of(A,B), 1); rig.output(0).state = State.ENDED;
            rig.output(1); assertEquals(A, rig.player.snapshot().track());
            queue.next(); rig.output(2); queue.mode(OnlineMusicQueue.Mode.SEQUENTIAL);
            rig.output(2).state = State.ENDED; noAdvance(rig, 3);
        }
    }
    @Test void modeChangeCannotReattachStoppedOrRemovedThenRestoredQueue() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A,B), 0); rig.output(0);
            rig.player.stop(); queue.mode(OnlineMusicQueue.Mode.REPEAT_ONE); noAdvance(rig, 1);
            queue.select(0); var output = rig.output(1);
            queue.remove(0); queue.undo(); queue.mode(OnlineMusicQueue.Mode.REPEAT_ALL);
            output.state = State.ENDED; noAdvance(rig, 2);
        }
    }
    @Test void changingModeWhilePausedDoesNotResumeAndShuffleRunsInBackground() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A,B,C), 0); rig.output(0);
            rig.player.pause(); queue.mode(OnlineMusicQueue.Mode.SHUFFLE); noAdvance(rig, 1);
            assertEquals(State.PAUSED, rig.player.snapshot().state()); rig.player.resume();
            rig.output(0).state = State.ENDED; rig.output(1).state = State.ENDED; rig.output(2);
            assertEquals(3, new java.util.HashSet<>(rig.source.resolved).size());
        }
    }
    @Test void replayAfterNaturalEndCanEnableRepeatWithoutReattachingStoppedQueue() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A), 0);
            rig.output(0).state = State.ENDED; noAdvance(rig, 1);
            queue.mode(OnlineMusicQueue.Mode.REPEAT_ONE); noAdvance(rig, 1);
            rig.player.resume(); rig.output(1).state = State.ENDED; rig.output(2);
            assertEquals(A, rig.player.snapshot().track());
        }
    }
    @Test void undoUnplayedRemovalRestoresShufflePreviousNavigation() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.mode(OnlineMusicQueue.Mode.SHUFFLE);
            queue.replace(List.of(A,B,C), 0); rig.output(0); queue.next(); rig.output(1);
            int current = queue.snapshot().index(); int unplayed = current == 1 ? 2 : 1;
            queue.remove(unplayed); queue.undo();
            assertTrue(queue.snapshot().canPrevious()); queue.previous(); rig.output(2);
            assertEquals(A, rig.player.snapshot().track());
        }
    }
    @Test void repeatDoesNotRetryOutputFailureAndStopStillCancelsIt() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.mode(OnlineMusicQueue.Mode.REPEAT_ONE);
            queue.replace(List.of(A), 0); rig.output(0).state = State.FAILED; noAdvance(rig, 1);
            assertEquals(State.FAILED, rig.player.snapshot().state());
            queue.select(0); var output = rig.output(1); rig.player.stop(); output.state = State.ENDED;
            noAdvance(rig, 2);
        }
    }
    @Test void seekAfterNaturalEndRearmsRepeatWatcher() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A), 0);
            rig.output(0).state = State.ENDED; noAdvance(rig, 1);
            queue.mode(OnlineMusicQueue.Mode.REPEAT_ONE); rig.player.seek(0);
            rig.output(1).state = State.ENDED; rig.output(2);
        }
    }
    @Test void undoPreservesSelectedPositionAmongDuplicateTracks() throws Exception {
        try (var rig = new Rig()) {
            var queue = rig.player.queue(); queue.replace(List.of(A,A,B), 1); rig.output(0);
            queue.remove(2); queue.undo(); assertEquals(1, queue.snapshot().index());
            queue.next(); rig.output(1); assertEquals(B, rig.player.snapshot().track());
        }
    }
}
