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
    private final ForegroundMusicFrames.Lease lease;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "shb-online-media-bridge"); t.setDaemon(true); return t;
    });
    private final String sessionId = PROVIDER + ":" + UUID.randomUUID();
    private OnlineTrack track;
    private long revision;

    OnlineMusicFrameBridge(OnlineMusicPlayer player, ForegroundMusicFrames.Lease lease) {
        this.player = player; this.lease = lease;
        worker.scheduleWithFixedDelay(this::observe, 0, 50, TimeUnit.MILLISECONDS);
    }
    private void observe() {
        try {
            OnlineMusicPlayer.Snapshot snapshot;
            long offset = 0;
            if (player instanceof OnlinePlayerSession internal) {
                var observation = internal.mediaObservation();
                snapshot = observation.snapshot(); offset = observation.offsetMs();
            } else snapshot = player.snapshot();
            if (!Objects.equals(track, snapshot.track())) {
                track = snapshot.track(); revision++;
            }
            long now = System.nanoTime();
            lease.publish(frame(snapshot, offset, sessionId, revision, now), now);
        } catch (RuntimeException unavailable) {
            // A third-party player may be closed concurrently. Never leave its last song on the HUD.
            lease.publish(null, System.nanoTime());
        }
    }
    static MusicService.Frame frame(OnlineMusicPlayer.Snapshot sample, long offset, String sessionId, long revision, long now) {
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
        var session = new MusicSnapshot.Session(sessionId,
                MusicSnapshot.Track.of(song.source() + ":" + song.id(), song.title(), song.artist(), song.album()),
                status, OptionalLong.of(position), OptionalLong.of(duration), OptionalDouble.of(1), now, null, capabilities);
        return new MusicService.Frame(new MusicSnapshot(PROVIDER, MusicSnapshot.State.AVAILABLE, session),
                new MusicService.Lyrics(lyricState, lyricState == MusicService.LyricsState.AVAILABLE ? sample.lyrics() : LyricTimeline.empty()), revision);
    }
    @Override public void close() { lease.close(); worker.shutdownNow(); }
}
