// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;

/** Session-owned queue. Selection starts playback atomically; edits do not stop the audible track.
 * Only explicitly supplied tracks are queued: no implicit remote pagination, repeat or failure skipping. */
public interface OnlineMusicQueue {
    record Snapshot(List<OnlineTrack> tracks, int index, boolean canUndo) {
        public Snapshot { tracks = List.copyOf(tracks); }
        public boolean canPrevious() { return index > 0; }
        public boolean canNext() { return index >= 0 && index + 1 < tracks.size(); }
    }
    Snapshot snapshot();
    void replace(List<OnlineTrack> tracks, int selected);
    void select(int selected);
    void next();
    void previous();
    void remove(int selected);
    void clear();
    void undo();
}
