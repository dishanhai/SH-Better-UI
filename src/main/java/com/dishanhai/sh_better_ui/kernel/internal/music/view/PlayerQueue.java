// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.List;

/** Uses a persistent player queue where available without breaking third-party player implementations. */
final class PlayerQueue {
    private final OnlineMusicPlayer player;
    private final OnlineMusicQueue persistent;
    private final SessionPlayQueue local = new SessionPlayQueue();

    PlayerQueue(OnlineMusicPlayer player) { this.player = player; persistent = player.queue(); }
    List<OnlineTrack> tracks() { return persistent == null ? local.tracks() : persistent.snapshot().tracks(); }
    int index() { return persistent == null ? local.index() : persistent.snapshot().index(); }
    boolean canPrevious() { return persistent == null ? local.canPrevious() : persistent.snapshot().canPrevious(); }
    boolean canNext() { return persistent == null ? local.canNext() : persistent.snapshot().canNext(); }
    boolean canUndo() { return persistent == null ? local.canUndo() : persistent.snapshot().canUndo(); }
    boolean supportsEditing() { return persistent != null && persistent.supportsEditing(); }
    void append(OnlineTrack track) { if (supportsEditing()) persistent.append(List.of(track)); }
    void move(int from, int to) { if (supportsEditing()) persistent.move(from, to); }
    void replace(List<OnlineTrack> tracks, int selected) {
        if (persistent == null) local.replace(tracks, selected).ifPresent(player::play); else persistent.replace(tracks, selected);
    }
    void select(int selected) { if (persistent == null) local.select(selected).ifPresent(player::play); else persistent.select(selected); }
    void next() { if (persistent == null) local.next().ifPresent(player::play); else persistent.next(); }
    void previous() { if (persistent == null) local.previous().ifPresent(player::play); else persistent.previous(); }
    void remove(int selected) { if (persistent == null) local.remove(selected); else persistent.remove(selected); }
    void clear() { if (persistent == null) local.clear(); else persistent.clear(); }
    void undo() { if (persistent == null) local.undo(); else persistent.undo(); }
    void observe(OnlineMusicPlayer.Snapshot snapshot) { if (persistent == null) local.observe(snapshot).ifPresent(player::play); }
    void suspend() { if (persistent == null) local.suspend(); }
}
