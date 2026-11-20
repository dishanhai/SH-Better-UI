// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicQueue;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicSeekModel;
import com.dishanhai.sh_better_ui.kernel.internal.music.OnlinePlaybackOccurrence;
import java.util.Objects;
import java.util.function.LongSupplier;

/** UI-thread presentation adapter. It does not own the session or make network reads. */
final class MusicFrontendTransport {
    enum Primary { NONE, PLAY, PAUSE, RETRY }
    record Frame(OnlineMusicPlayer.Snapshot playback, String seekKey, Primary primary,
                 boolean seekable, boolean previous, boolean next, long lyricOffsetMs) {}
    record LyricContent(OnlineTrack track, LyricTimeline timeline, long offsetMs, String seekKey) {}
    record LyricFrame(LyricContent content, long songPositionMs, boolean seekable, boolean preview) {}
    private record Occurrence(OnlineTrack track, int queueIndex, long durationMs, boolean preview,
                              long lyricOffsetMs, long nativeGeneration) {}

    private final OnlineMusicPlayer player;
    private final LongSupplier nativeGeneration;
    private Occurrence occurrence;
    private MusicSeekModel.Edit previewEdit;
    private long version;
    private boolean wasSeekable;

    MusicFrontendTransport(OnlineMusicPlayer player) {
        this(player, () -> OnlinePlaybackOccurrence.generation(player));
    }
    MusicFrontendTransport(OnlineMusicPlayer player, LongSupplier nativeGeneration) {
        this.player = Objects.requireNonNull(player);
        this.nativeGeneration = Objects.requireNonNull(nativeGeneration);
    }

    Frame frame() { synchronized (player) { return sample(); } }
    private Frame sample() {
        var snapshot = player.snapshot();
        var queue = player.queue();
        var order = queue == null ? null : queue.snapshot();
        var state = snapshot.state();
        boolean seekable = snapshot.track() != null && snapshot.durationMs() > 0
                && (state == OnlineMusicPlayer.State.PLAYING || state == OnlineMusicPlayer.State.PAUSED
                    || state == OnlineMusicPlayer.State.ENDED);
        long offset = snapshot.preview() ? Math.max(0, player.lyricOffsetMs(snapshot)) : 0;
        var next = new Occurrence(snapshot.track(), order == null ? -1 : order.index(), snapshot.durationMs(),
                                  snapshot.preview(), offset, nativeGeneration.getAsLong());
        if (seekable != wasSeekable || !Objects.equals(next, occurrence)) {
            occurrence = next;
            wasSeekable = seekable;
            version++;
            previewEdit = null;
        }
        var primary = switch (state) {
            case PLAYING -> Primary.PAUSE;
            case PAUSED, ENDED -> Primary.PLAY;
            case FAILED -> player.canRetryCurrent() ? Primary.RETRY : Primary.NONE;
            default -> Primary.NONE;
        };
        return new Frame(snapshot, seekable ? Long.toString(version) : "", primary, seekable,
                         order != null && order.canPrevious(), order != null && order.canNext(), offset);
    }

    /** A scrub preview is only for the occurrence that supplied the thumb position. */
    boolean preview(MusicSeekModel.Edit edit) { synchronized (player) {
        var fresh = sample();
        if (!fresh.seekable() || !fresh.seekKey().equals(edit.trackKey())) return false;
        previewEdit = edit;
        return true;
    } }

    void cancelPreview() { synchronized (player) { previewEdit = null; } }

    LyricFrame lyrics(boolean windowActive) { synchronized (player) {
        if (!windowActive) previewEdit = null;
        var fresh = sample();
        var playback = fresh.playback();
        var content = lyricContent(fresh);
        boolean ready = playback.lyricsState() == OnlineMusicPlayer.LyricsState.READY && playback.track() != null;
        boolean preview = ready && previewEdit != null;
        long position = preview ? clamp(previewEdit.positionMs(), playback.durationMs())
                : OnlineLyricPresentation.position(playback);
        return new LyricFrame(content, OnlineLyricPresentation.songPosition(position, content.offsetMs()),
                windowActive && !preview && OnlineLyricPresentation.seekable(playback), preview);
    } }

    private static LyricContent lyricContent(Frame frame) {
        var playback = frame.playback();
        var timeline = playback.track() != null && playback.lyricsState() == OnlineMusicPlayer.LyricsState.READY
                ? playback.lyrics() : LyricTimeline.empty();
        return new LyricContent(playback.track(), timeline, frame.lyricOffsetMs(), frame.seekKey());
    }

    /** One action against the exact rendered timeline, not whichever song is audible at click time. */
    boolean seekLyric(LyricContent content, long songPositionMs) { synchronized (player) {
        var fresh = sample();
        if (content == null || previewEdit != null || !fresh.seekable()
                || !content.equals(lyricContent(fresh)) || songPositionMs < content.offsetMs()) return false;
        var target = new LyricSeekTarget(content.track(), content.timeline(), songPositionMs, content.offsetMs())
                .position(fresh.playback());
        if (target.isEmpty() || target.getAsLong() > fresh.playback().durationMs()) return false;
        player.seek(target.getAsLong());
        return true;
    } }

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
        previewEdit = null;
        player.seek(clamp(edit.positionMs(), fresh.playback().durationMs()));
        return true;
    } }

    private static long clamp(long position, long duration) {
        return Math.max(0L, Math.min(position, duration));
    }
}
