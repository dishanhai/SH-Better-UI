// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendQueue.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendQueueTest {
    private static final OnlineTrack SAME = new OnlineTrack("qq", "1", "1", "重复", "", "", 1000);
    private static final OnlineTrack OTHER = new OnlineTrack("qq", "2", "2", "另一首", "", "", 1000);

    @Test void pageAndScrollCoverTheTailAndClampAfterQueueShrinks() {
        var q = new FakeQueue(List.of(SAME, OTHER, SAME, OTHER, SAME, OTHER, SAME));
        var model = model(q);
        var shown = model.read();
        model.page(shown, 3, 1);
        assertEquals(3, model.row(shown, 3, 0).index());
        model.page(shown, 3, 1);
        assertEquals(6, model.row(shown, 3, 2).index());
        assertEquals(4, model.offset(shown, 3));
        model.moveTo(shown, 3, Integer.MIN_VALUE);
        assertEquals(0, model.offset(shown, 3));
        model.moveTo(shown, 3, Integer.MAX_VALUE);
        q.tracks = List.of(SAME, OTHER);
        assertEquals(0, model.offset(model.read(), 3));
        assertNull(model.row(model.read(), 3, 2));
    }

    @Test void identicalTracksBindToTheDisplayedOccurrenceNotTheFirstMatchingId() {
        var q = new FakeQueue(List.of(SAME, OTHER, SAME, OTHER));
        var model = model(q);
        model.page(model.read(), 2, 1);
        assertEquals(Result.APPLIED, model.invoke(model.row(model.read(), 2, 0), Action.PLAY));
        assertEquals("play:2", q.last);
        model.toggleEditing(model.read());
        assertEquals(Result.APPLIED, model.invoke(model.row(model.read(), 2, 0), Action.UP));
        assertEquals("move:2:1", q.last);
        assertEquals(Result.APPLIED, model.invoke(model.row(model.read(), 2, 1), Action.REMOVE));
        assertEquals("remove:3", q.last);
        assertEquals(Result.APPLIED, model.undo(model.read()));
        assertEquals("undo", q.last);
    }

    @Test void staleRowAndUndoRejectReplacedQueueEvenWithIdenticalTrackReferences() {
        var q = new FakeQueue(List.of(SAME, SAME));
        var model = model(q);
        model.toggleEditing(model.read());
        q.canUndo = true;
        var shown = model.read();
        var occurrence = model.row(shown, 2, 1);
        q.tracks = List.copyOf(new ArrayList<>(q.tracks));
        assertEquals(Result.CHANGED, model.invoke(occurrence, Action.REMOVE));
        assertEquals(Result.CHANGED, model.undo(shown));
        assertNull(q.last);
        var now = model.row(model.read(), 2, 1);
        q.index = 0;
        assertEquals(Result.CHANGED, model.invoke(now, Action.PLAY));
    }

    @Test void unsupportedAndBusyPreserveTheDisplayedQueue() {
        var q = new FakeQueue(List.of(SAME, OTHER));
        var model = model(q);
        q.supportsEditing = false;
        model.toggleEditing(model.read());
        assertFalse(model.editing());
        assertEquals(Result.UNAVAILABLE, model.invoke(model.row(model.read(), 2, 0), Action.REMOVE));
        q.supportsEditing = true;
        model.toggleEditing(model.read());
        assertEquals(Result.UNAVAILABLE, model.invoke(model.row(model.read(), 2, 0), Action.UP));
        q.busy = true;
        assertEquals(Result.BUSY, model.invoke(model.row(model.read(), 2, 0), Action.REMOVE));
        assertEquals(List.of(SAME, OTHER), q.tracks);
        assertNull(q.last);
        model.reset();
        assertFalse(model.editing());
    }

    private static MusicFrontendQueue model(FakeQueue q) {
        var player = (OnlineMusicPlayer) Proxy.newProxyInstance(OnlineMusicPlayer.class.getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (p, method, args) -> {
                    if (method.getName().equals("queue")) return q;
                    throw new AssertionError(method);
                });
        return new MusicFrontendQueue(player);
    }

    private static final class FakeQueue implements OnlineMusicQueue {
        List<OnlineTrack> tracks, before;
        boolean supportsEditing = true, canUndo, busy;
        String last;
        int index = -1;
        FakeQueue(List<OnlineTrack> tracks) { this.tracks = tracks; }
        @Override public boolean supportsEditing() { return supportsEditing; }
        @Override public Snapshot snapshot() { return new Snapshot(tracks, index, canUndo); }
        @Override public void select(int position) { last = "play:" + position; index = position; }
        @Override public void move(int from, int to) {
            before(); last = "move:" + from + ":" + to;
            var copy = new ArrayList<>(tracks); copy.add(to, copy.remove(from)); tracks = List.copyOf(copy);
        }
        @Override public void remove(int position) {
            before(); last = "remove:" + position;
            var copy = new ArrayList<>(tracks); copy.remove(position); tracks = List.copyOf(copy);
        }
        private void before() {
            if (busy) throw new TransitionInProgress();
            before = tracks; canUndo = true;
        }
        @Override public void undo() { tracks = before; canUndo = false; last = "undo"; }
        @Override public void replace(List<OnlineTrack> tracks, int index) { throw new AssertionError(); }
        @Override public void next() { throw new AssertionError(); }
        @Override public void previous() { throw new AssertionError(); }
        @Override public void clear() { throw new AssertionError(); }
    }
}
