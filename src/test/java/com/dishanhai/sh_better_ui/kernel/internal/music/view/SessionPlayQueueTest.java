// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SessionPlayQueueTest {
    private static final OnlineTrack A = track("netease", "1"), B = track("qq", "1"), C = track("qq", "2");
    private static OnlineTrack track(String source, String id) { return new OnlineTrack(source, id, id, "Song", "Artist", "Album", 1000); }
    private static OnlineMusicPlayer.Snapshot frame(OnlineTrack track, OnlineMusicPlayer.State state) {
        return new OnlineMusicPlayer.Snapshot(track, state, 1000, 1000, OnlineMusicPlayer.LyricsState.EMPTY,
                LyricTimeline.empty(), OnlineMusicPlayer.Failure.NONE, false);
    }
    @Test void selectionRetainsProviderIdentityAndOriginalOrder() {
        var queue = new SessionPlayQueue(); assertEquals(B, queue.replace(List.of(A, B, C), 1).orElseThrow());
        assertEquals(List.of(A, B, C), queue.tracks()); assertEquals(1, queue.index());
        assertEquals(A, queue.previous().orElseThrow()); assertEquals(B, queue.next().orElseThrow());
    }
    @Test void endAdvancesExactlyOnceOnlyAfterPlaybackWasObserved() {
        var q = new SessionPlayQueue(); q.replace(List.of(A, B, C), 0);
        assertTrue(q.observe(frame(A, OnlineMusicPlayer.State.ENDED)).isEmpty());
        q.observe(frame(A, OnlineMusicPlayer.State.PLAYING));
        assertEquals(B, q.observe(frame(A, OnlineMusicPlayer.State.ENDED)).orElseThrow());
        assertTrue(q.observe(frame(A, OnlineMusicPlayer.State.ENDED)).isEmpty());
        assertTrue(q.observe(frame(B, OnlineMusicPlayer.State.ENDED)).isEmpty());
        q.observe(frame(B, OnlineMusicPlayer.State.PLAYING));
        assertEquals(C, q.observe(frame(B, OnlineMusicPlayer.State.ENDED)).orElseThrow());
    }
    @Test void failedOrStoppedPlaybackNeverAutoSkips() {
        for (var state : List.of(OnlineMusicPlayer.State.FAILED, OnlineMusicPlayer.State.IDLE, OnlineMusicPlayer.State.CLOSED)) {
            var q = new SessionPlayQueue(); q.replace(List.of(A, B), 0); q.observe(frame(A, OnlineMusicPlayer.State.PLAYING));
            assertTrue(q.observe(frame(A, state)).isEmpty());
            assertTrue(q.observe(frame(A, OnlineMusicPlayer.State.ENDED)).isEmpty()); assertEquals(0, q.index());
        }
    }
    @Test void removingPlayingTrackLeavesPlaybackUncontrolledUntilNextExplicitSelection() {
        var q = new SessionPlayQueue(); q.replace(List.of(A, B, C), 1); q.observe(frame(B, OnlineMusicPlayer.State.PLAYING));
        q.remove(1); assertEquals(List.of(A, C), q.tracks()); assertEquals(-1, q.index());
        assertTrue(q.observe(frame(B, OnlineMusicPlayer.State.ENDED)).isEmpty());
        assertFalse(q.canNext()); q.undo(); assertEquals(List.of(A, B, C), q.tracks()); assertEquals(1, q.index());
        assertTrue(q.observe(frame(B, OnlineMusicPlayer.State.ENDED)).isEmpty());
    }
    @Test void removalBeforeCurrentPreservesCurrentTrackAndUndoIsSingleUse() {
        var q = new SessionPlayQueue(); q.replace(List.of(A, B, C), 2); q.remove(0);
        assertEquals(1, q.index()); assertEquals(C, q.tracks().get(q.index()));
        q.undo(); assertEquals(2, q.index()); assertFalse(q.canUndo()); q.undo(); assertEquals(3, q.tracks().size());
    }
    @Test void clearIsReversibleButDoesNotResumePlayback() {
        var q = new SessionPlayQueue(); q.replace(List.of(A, B), 0); q.observe(frame(A, OnlineMusicPlayer.State.PLAYING));
        q.clear(); assertTrue(q.tracks().isEmpty()); assertEquals(-1, q.index());
        q.undo(); assertEquals(List.of(A, B), q.tracks()); assertTrue(q.observe(frame(A, OnlineMusicPlayer.State.ENDED)).isEmpty());
    }
    @Test void replacingCollectionInvalidatesUndoAndInvalidSelectionDoesNotMutate() {
        var q = new SessionPlayQueue(); q.replace(List.of(A, B), 0); q.remove(1);
        assertTrue(q.replace(List.of(C), 3).isEmpty()); assertEquals(List.of(A), q.tracks());
        q.replace(List.of(C), 0); assertFalse(q.canUndo()); assertFalse(q.canNext()); assertFalse(q.canPrevious());
        assertTrue(q.select(-1).isEmpty()); assertTrue(q.select(2).isEmpty()); assertEquals(0, q.index());
    }
    @Test void unownedTrackCannotDriveQueueAdvance() {
        var q = new SessionPlayQueue(); q.replace(List.of(A, B), 0);
        q.observe(frame(A, OnlineMusicPlayer.State.PLAYING)); q.observe(frame(C, OnlineMusicPlayer.State.PLAYING));
        assertTrue(q.observe(frame(A, OnlineMusicPlayer.State.ENDED)).isEmpty());
    }
    @Test void undoKeepsNewPlaybackSelectionRatherThanRestoringOldCursor() {
        var q = new SessionPlayQueue(); q.replace(List.of(A,B,C), 0); q.remove(1);
        assertEquals(C, q.next().orElseThrow()); q.undo();
        assertEquals(C, q.tracks().get(q.index())); assertEquals(2, q.index());
    }
    @Test void suspendingBetweenPlaybackAndEndCannotResumeQueueOnReturn() {
        var q = new SessionPlayQueue(); q.replace(List.of(A,B),0);
        q.observe(frame(A,OnlineMusicPlayer.State.PLAYING)); q.suspend();
        assertTrue(q.observe(frame(A,OnlineMusicPlayer.State.ENDED)).isEmpty());
        q.observe(frame(A,OnlineMusicPlayer.State.PAUSED));
        assertTrue(q.observe(frame(A,OnlineMusicPlayer.State.ENDED)).isEmpty());
        assertEquals(0,q.index());
        q.observe(frame(A,OnlineMusicPlayer.State.PLAYING));
        assertEquals(B,q.observe(frame(A,OnlineMusicPlayer.State.ENDED)).orElseThrow());
    }
    @Test void lastTrackEndsWithoutWraparound() {
        var q = new SessionPlayQueue(); q.replace(List.of(A), 0); q.observe(frame(A, OnlineMusicPlayer.State.PLAYING));
        assertTrue(q.observe(frame(A, OnlineMusicPlayer.State.ENDED)).isEmpty()); assertEquals(0, q.index());
    }
}
