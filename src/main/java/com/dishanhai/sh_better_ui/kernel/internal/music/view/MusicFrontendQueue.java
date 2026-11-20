// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicQueue;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;

import java.util.List;

/** A bounded view of the session queue. An action applies only to the exact snapshot that was painted. */
final class MusicFrontendQueue {
    enum Action { PLAY, UP, DOWN, REMOVE }
    enum Result { APPLIED, UNAVAILABLE, CHANGED, BUSY }
    record State(OnlineMusicQueue queue, OnlineMusicQueue.Snapshot snapshot, boolean supportsEditing) {
        List<OnlineTrack> tracks() { return snapshot == null ? List.of() : snapshot.tracks(); }
        int activeIndex() { return snapshot == null ? -1 : snapshot.index(); }
        boolean canUndo() { return snapshot != null && snapshot.canUndo(); }
    }
    record Row(State state, int index, OnlineTrack track) {}
    private final OnlineMusicPlayer player;
    private int offset;
    private boolean editing;

    MusicFrontendQueue(OnlineMusicPlayer player) { this.player = player; }
    State read() {
        var queue = player.queue();
        return queue == null ? new State(null, null, false) : new State(queue, queue.snapshot(), queue.supportsEditing());
    }
    static boolean sameSnapshot(State first, State next) {
        if (first == null || first.queue() != next.queue()) return false;
        if (first.queue() == null) return true;
        // Built-in queue replaces its immutable track list on every edit; equality would
        // falsely consider separate occurrences of the same track interchangeable.
        return first.tracks() == next.tracks() && first.activeIndex() == next.activeIndex()
                && first.canUndo() == next.canUndo() && first.snapshot().mode() == next.snapshot().mode();
    }
    void reset() { offset = 0; editing = false; }
    boolean editing() { return editing; }
    void toggleEditing(State state) { editing = !editing && state.supportsEditing(); }
    int offset(State state, int slots) { return moveTo(state, slots, offset); }
    int moveTo(State state, int slots, int target) {
        offset = Math.max(0, Math.min(target, Math.max(0, state.tracks().size() - Math.max(1, slots))));
        return offset;
    }
    void page(State state, int slots, int direction) { moveTo(state, slots, offset + Integer.signum(direction) * slots); }
    Row row(State state, int slots, int slot) {
        int index = offset(state, slots) + slot;
        return slot >= 0 && slot < slots && index < state.tracks().size()
                ? new Row(state, index, state.tracks().get(index)) : null;
    }
    Result invoke(Row row, Action action) {
        if (row == null) return Result.UNAVAILABLE;
        // Built-in queue operations also take the session monitor. Keep revalidation
        // and the indexed operation indivisible from auto-advance on its worker.
        synchronized (player) {
            var current = read();
            if (!sameSnapshot(row.state(), current)) return Result.CHANGED;
            if (action != Action.PLAY && (!editing || !current.supportsEditing())) return Result.UNAVAILABLE;
            if ((action == Action.UP && row.index() == 0) ||
                    (action == Action.DOWN && row.index() + 1 >= current.tracks().size())) return Result.UNAVAILABLE;
            try {
                switch (action) {
                    case PLAY -> current.queue().select(row.index());
                    case UP -> current.queue().move(row.index(), row.index() - 1);
                    case DOWN -> current.queue().move(row.index(), row.index() + 1);
                    case REMOVE -> current.queue().remove(row.index());
                }
                return Result.APPLIED;
            } catch (OnlineMusicQueue.TransitionInProgress busy) { return Result.BUSY; }
            catch (UnsupportedOperationException unavailable) { return Result.UNAVAILABLE; }
        }
    }
    Result undo(State displayed) {
        synchronized (player) {
            var current = read();
            if (!sameSnapshot(displayed, current)) return Result.CHANGED;
            if (!editing || !current.supportsEditing() || !current.canUndo()) return Result.UNAVAILABLE;
            try { current.queue().undo(); return Result.APPLIED; }
            catch (OnlineMusicQueue.TransitionInProgress busy) { return Result.BUSY; }
            catch (UnsupportedOperationException unavailable) { return Result.UNAVAILABLE; }
        }
    }
}
