// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;

/** Session-owned queue. Selection starts playback atomically; edits do not stop the audible track.
 * Only explicitly supplied tracks are queued: no implicit remote pagination or failure skipping. Playback modes are session-local. */
public interface OnlineMusicQueue {
    enum Mode { SEQUENTIAL, REPEAT_ONE, REPEAT_ALL, SHUFFLE }
    record Snapshot(List<OnlineTrack> tracks, int index, boolean canUndo, Mode mode,
                    boolean canPrevious, boolean canNext) {
        public Snapshot { tracks = List.copyOf(tracks); }
        /** Source-compatible constructor for existing view-local queue implementations. */
        public Snapshot(List<OnlineTrack> tracks, int index, boolean canUndo) {
            this(tracks, index, canUndo, Mode.SEQUENTIAL, index > 0,
                    index >= 0 && index + 1 < tracks.size());
        }
    }
    default Mode mode() { return Mode.SEQUENTIAL; }
    /** Changes order without starting or resuming playback. Explicit next skips a repeat-one track. */
    default void mode(Mode mode) { throw new UnsupportedOperationException("Queue modes unavailable"); }
    Snapshot snapshot();
    void replace(List<OnlineTrack> tracks, int selected);
    void select(int selected);
    void next();
    void previous();
    void remove(int selected);
    void clear();
    void undo();
}
