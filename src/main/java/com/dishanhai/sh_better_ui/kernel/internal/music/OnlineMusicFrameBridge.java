// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.*;
import java.util.concurrent.*;

/** Kernel-session observer; no login, system capture, playback commands or render-thread I/O. */
final class OnlineMusicFrameBridge implements AutoCloseable {
    static final String PROVIDER = "sh_better_ui:online_music";
    private final OnlineMusicPlayer player;
    private final java.util.function.LongSupplier clock;
    private final OnlineCoverFrames covers = new OnlineCoverFrames();
    private final ForegroundMusicFrames.Lease lease;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "shb-online-media-bridge"); t.setDaemon(true); return t;
    });
    private final String sessionId = PROVIDER + ":" + UUID.randomUUID();
    private OnlineTrack track;
    private long revision;
    private final FailureWindow failureWindow = new FailureWindow();

    /** One bounded terminal observation per playback attempt, never a persistent stale song. */
    static final class FailureWindow {
        private String attempt;
        private long since;
        boolean visible(OnlineMusicPlayer.State state, String session, long now) {
            if (state != OnlineMusicPlayer.State.FAILED) { attempt = null; return false; }
            if (!Objects.equals(attempt, session)) { attempt = session; since = now; }
            return now >= since && now - since < 5_000_000_000L;
        }
    }

    OnlineMusicFrameBridge(OnlineMusicPlayer player, ForegroundMusicFrames.Lease lease) {
        this(player, lease, System::nanoTime, true);
    }
    OnlineMusicFrameBridge(OnlineMusicPlayer player, ForegroundMusicFrames.Lease lease,
                           java.util.function.LongSupplier clock, boolean scheduled) {
        this.player = player; this.lease = lease; this.clock = clock;
        if (scheduled) worker.scheduleWithFixedDelay(this::observe, 0, 50, TimeUnit.MILLISECONDS);
    }
    String sessionId(long generation) { return sessionId + ":" + generation; }
    void observe() {
        try {
            OnlineMusicPlayer.Snapshot snapshot;
            long offset = 0, playbackRevision = -1;
            String observedSession = sessionId;
            if (player instanceof OnlinePlayerSession internal) {
                var observation = internal.mediaObservation();
                snapshot = observation.snapshot(); offset = observation.offsetMs();
                observedSession = sessionId(observation.generation());
                playbackRevision = observation.generation();
            } else snapshot = player.snapshot();
            if (!Objects.equals(track, snapshot.track())) {
                track = snapshot.track(); revision++;
            }
            long observedRevision = playbackRevision >= 0 ? playbackRevision : revision;
            long now = clock.getAsLong();
            var projected = frame(snapshot, offset, observedSession, observedRevision, now);
            boolean failed = failureWindow.visible(snapshot.state(), observedSession, now);
            if (snapshot.state() == OnlineMusicPlayer.State.FAILED && !failed) projected = null;
            // Withdraw/cancel obsolete artwork work on terminal playback, rather than starting a download for it.
            var artwork = covers.artwork(projected == null || failed ? null : snapshot.track());
            lease.publish(projected == null ? null : frame(snapshot, offset, observedSession, observedRevision, now, artwork), now);
        } catch (RuntimeException unavailable) {
            // A third-party player may be closed concurrently. Never leave its last song on the HUD.
            lease.publish(null, clock.getAsLong());
        }
    }
    static MusicService.Frame frame(OnlineMusicPlayer.Snapshot sample, long offset, String sessionId, long revision, long now) {
        return frame(sample, offset, sessionId, revision, now, null);
    }
    static MusicService.Frame frame(OnlineMusicPlayer.Snapshot sample, long offset, String sessionId, long revision,
                                    long now, MusicSnapshot.Artwork artwork) {
        if (sample.state() == OnlineMusicPlayer.State.FAILED)
            return new MusicService.Frame(new MusicSnapshot(PROVIDER, MusicSnapshot.State.TEMPORARILY_UNAVAILABLE, null),
                    new MusicService.Lyrics(MusicService.LyricsState.NO_TRACK, LyricTimeline.empty()), revision);
        var song = sample.track();
        if (song == null || sample.state() == OnlineMusicPlayer.State.CLOSED
                || sample.state() == OnlineMusicPlayer.State.IDLE || sample.state() == OnlineMusicPlayer.State.FAILED
                || sample.state() == OnlineMusicPlayer.State.ENDED) return null;
        var status = switch (sample.state()) {
            case PLAYING -> MusicSnapshot.PlaybackStatus.PLAYING;
            case PAUSED -> MusicSnapshot.PlaybackStatus.PAUSED;
            default -> MusicSnapshot.PlaybackStatus.BUFFERING;
        };
        var lyricState = switch (sample.lyricsState()) {
            case READY -> MusicService.LyricsState.AVAILABLE;
            case LOADING -> MusicService.LyricsState.LOADING;
            default -> MusicService.LyricsState.UNAVAILABLE;
        };
        // Preview streams carry a song-relative offset: both HUD progress and lyrics use that clock.
        long position = Math.max(0, sample.positionMs()) + Math.max(0, offset);
        long duration = sample.preview() ? song.durationMs() : sample.durationMs();
        var capabilities = EnumSet.of(MusicSnapshot.Capability.METADATA, MusicSnapshot.Capability.PLAYBACK_STATE,
                MusicSnapshot.Capability.TIMELINE);
        if (lyricState == MusicService.LyricsState.AVAILABLE) capabilities.add(MusicSnapshot.Capability.LYRICS);
        if (artwork != null) capabilities.add(MusicSnapshot.Capability.ARTWORK);
        var session = new MusicSnapshot.Session(sessionId,
                MusicSnapshot.Track.of(song.source() + ":" + song.id(), song.title(), song.artist(), song.album()),
                status, OptionalLong.of(position), OptionalLong.of(duration), OptionalDouble.of(1), now, artwork, capabilities);
        return new MusicService.Frame(new MusicSnapshot(PROVIDER, MusicSnapshot.State.AVAILABLE, session),
                new MusicService.Lyrics(lyricState, lyricState == MusicService.LyricsState.AVAILABLE ? sample.lyrics() : LyricTimeline.empty()), revision);
    }
    @Override public void close() { lease.close(); worker.shutdownNow(); covers.close(); }
}
