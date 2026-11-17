// Author: dishanhai. Native session queue inspired by MuoniumPlayer PlayQueuePanel (Apache-2.0).
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/** View-owned queue, not a provider playlist. No I/O and no account persistence. */
final class SessionPlayQueue {
    private List<OnlineTrack> tracks = List.of();
    private int index = -1;
    private boolean armed;
    private List<OnlineTrack> undoTracks;
    private int undoIndex;

    List<OnlineTrack> tracks() { return tracks; }
    int index() { return index; }
    boolean canPrevious() { return index > 0; }
    boolean canNext() { return index >= 0 && index + 1 < tracks.size(); }
    boolean canUndo() { return undoTracks != null; }
    void suspend() { armed = false; }
    Optional<OnlineTrack> replace(List<OnlineTrack> list, int selected) {
        if (selected < 0 || selected >= list.size()) return Optional.empty();
        tracks = List.copyOf(list); undoTracks = null;
        return select(selected);
    }
    Optional<OnlineTrack> select(int selected) {
        if (selected < 0 || selected >= tracks.size()) return Optional.empty();
        index = selected; armed = false;
        return Optional.of(tracks.get(index));
    }
    Optional<OnlineTrack> next() { return canNext() ? select(index + 1) : Optional.empty(); }
    Optional<OnlineTrack> previous() { return canPrevious() ? select(index - 1) : Optional.empty(); }
    void remove(int selected) {
        if (selected < 0 || selected >= tracks.size()) return;
        remember();
        var copy = new ArrayList<>(tracks); copy.remove(selected); tracks = List.copyOf(copy);
        if (selected == index) { index = -1; armed = false; }
        else if (selected < index) index--;
    }
    void clear() {
        if (tracks.isEmpty()) return;
        remember(); tracks = List.of(); index = -1; armed = false;
    }
    void undo() {
        if (undoTracks == null) return;
        var selected = index >= 0 ? tracks.get(index) : null;
        tracks = undoTracks;
        index = selected == null ? undoIndex : tracks.indexOf(selected);
        undoTracks = null; armed = false;
    }
    private void remember() { undoTracks = tracks; undoIndex = index; }
    /** Advance only after this request was observed running. Old ENDED frames cannot skip tracks. */
    Optional<OnlineTrack> observe(OnlineMusicPlayer.Snapshot snapshot) {
        if (index < 0 || !tracks.get(index).equals(snapshot.track())) { armed = false; return Optional.empty(); }
        if (snapshot.state() == OnlineMusicPlayer.State.PLAYING)
            armed = true;
        else if (snapshot.state() == OnlineMusicPlayer.State.ENDED && armed) {
            armed = false; return next();
        } else if (snapshot.state() == OnlineMusicPlayer.State.IDLE || snapshot.state() == OnlineMusicPlayer.State.FAILED
                || snapshot.state() == OnlineMusicPlayer.State.CLOSED) armed = false;
        return Optional.empty();
    }
}
