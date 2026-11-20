// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicQueue;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekModel;
import java.util.Objects;

/** UI-thread presentation adapter. It does not own the session or make network reads. */
final class MusicFrontendTransport {
    enum Primary { NONE, PLAY, PAUSE, RETRY }
    record Frame(OnlineMusicPlayer.Snapshot playback, String seekKey, Primary primary,
                 boolean seekable, boolean previous, boolean next) {}
    private record Occurrence(OnlineTrack track, int queueIndex, long durationMs, boolean preview) {}

    private final OnlineMusicPlayer player;
    private Occurrence occurrence;
    private long version;
    private boolean wasSeekable;

    MusicFrontendTransport(OnlineMusicPlayer player) { this.player = Objects.requireNonNull(player); }

    Frame frame() { synchronized (player) { return sample(); } }
    private Frame sample() {
        var snapshot = player.snapshot();
        var queue = player.queue();
        var order = queue == null ? null : queue.snapshot();
        var state = snapshot.state();
        boolean seekable = snapshot.track() != null && snapshot.durationMs() > 0
                && (state == OnlineMusicPlayer.State.PLAYING || state == OnlineMusicPlayer.State.PAUSED
                    || state == OnlineMusicPlayer.State.ENDED);
        var next = new Occurrence(snapshot.track(), order == null ? -1 : order.index(), snapshot.durationMs(), snapshot.preview());
        if (seekable != wasSeekable || !Objects.equals(next, occurrence)) {
            occurrence = next;
            wasSeekable = seekable;
            version++;
        }
        var primary = switch (state) {
            case PLAYING -> Primary.PAUSE;
            case PAUSED, ENDED -> Primary.PLAY;
            case FAILED -> player.canRetryCurrent() ? Primary.RETRY : Primary.NONE;
            default -> Primary.NONE;
        };
        return new Frame(snapshot, seekable ? Long.toString(version) : "", primary, seekable,
                         order != null && order.canPrevious(), order != null && order.canNext());
    }

    boolean activate() {
        synchronized (player) { return switch (sample().primary()) {
            case PAUSE -> { player.pause(); yield true; }
            case PLAY -> { player.resume(); yield true; }
            case RETRY -> player.retryCurrent();
            case NONE -> false;
        }; }
    }

    boolean skip(boolean forward) { synchronized (player) {
        var queue = player.queue();
        if (queue == null) return false;
        var order = queue.snapshot();
        if (forward ? !order.canNext() : !order.canPrevious()) return false;
        try {
            if (forward) queue.next(); else queue.previous();
            return true;
        } catch (OnlineMusicQueue.TransitionInProgress busy) { return false; }
    } }

    /** Called on release/key action only; preview is not a playback command. */
    boolean commit(MusicSeekModel.Edit edit) { synchronized (player) {
        var fresh = sample();
        if (!fresh.seekable() || !fresh.seekKey().equals(edit.trackKey())) return false;
        player.seek(Math.max(0L, Math.min(edit.positionMs(), fresh.playback().durationMs())));
        return true;
    } }
}
