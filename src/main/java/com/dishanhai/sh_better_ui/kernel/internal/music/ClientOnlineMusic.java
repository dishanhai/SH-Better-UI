// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import com.dishanhai.sh_better_ui.client.music.*;

/** Independent sessions plus a kernel-owned player for the ordinary background music route. */
public final class ClientOnlineMusic implements OnlineMusicService, AutoCloseable {
    private OnlineMusicPlayer background;
    private OnlineMusicFrameBridge bridge;
    private boolean closed;
    private final Supplier<OnlineMusicPlayer> backgroundFactory;
    private final Supplier<MusicService.Frame> foreground;
    private final Supplier<ForegroundMusicFrames.Lease> lease;

    public ClientOnlineMusic() {
        backgroundFactory = this::open;
        foreground = ClientMusic::frame;
        lease = ClientMusic::foreground;
    }
    ClientOnlineMusic(Supplier<OnlineMusicPlayer> factory, Supplier<MusicService.Frame> foreground,
                      Supplier<ForegroundMusicFrames.Lease> lease) {
        this.backgroundFactory = factory; this.foreground = foreground; this.lease = lease;
    }

    @Override public synchronized Optional<BackgroundControls> backgroundControls(BackgroundTarget target) {
        if (closed || background == null || bridge == null || !(background instanceof OnlinePlayerSession)) return Optional.empty();
        synchronized (background) { return controls(target); }
    }
    private Optional<BackgroundControls> controls(BackgroundTarget target) {
        var frame = foreground.get();
        if (frame == null || frame.snapshot().state() != MusicSnapshot.State.AVAILABLE
                || !OnlineMusicFrameBridge.PROVIDER.equals(frame.snapshot().providerId())) return Optional.empty();
        var session = frame.snapshot().session();
        if (session == null || session.track() == null || !bridge.sessionId(((OnlinePlayerSession) background).playbackGeneration()).equals(target.sessionId())
                || !target.sessionId().equals(session.id()) || !target.trackKey().equals(session.track().key())) return Optional.empty();
        var sample = background.snapshot();
        if (sample.track() == null || !target.trackKey().equals(sample.track().source() + ":" + sample.track().id())
                || sample.state() != OnlineMusicPlayer.State.PLAYING && sample.state() != OnlineMusicPlayer.State.PAUSED) return Optional.empty();
        var queue = background.queue();
        var queued = queue == null ? null : queue.snapshot();
        return Optional.of(new BackgroundControls(target, sample.state() == OnlineMusicPlayer.State.PLAYING,
                sample.state() == OnlineMusicPlayer.State.PAUSED, queued != null && queued.canPrevious(), queued != null && queued.canNext(),
                !sample.preview() && sample.durationMs() > 0));
    }
    @Override public synchronized boolean controlBackground(BackgroundTarget target, BackgroundCommand command, double fraction) {
        java.util.Objects.requireNonNull(target); java.util.Objects.requireNonNull(command);
        if (command == BackgroundCommand.SEEK && (!Double.isFinite(fraction) || fraction < 0 || fraction > 1))
            throw new IllegalArgumentException("Seek fraction must be in [0,1]");
        if (closed || background == null || bridge == null || !(background instanceof OnlinePlayerSession)) return false;
        // Built-in player and its queue mutate under this same monitor. Check and action are atomic with track changes.
        synchronized (background) {
            var available = controls(target).orElse(null);
            if (available == null) return false;
            switch (command) {
                case PAUSE -> { if (!available.pause()) return false; background.pause(); }
                case RESUME -> { if (!available.resume()) return false; background.resume(); }
                case PREVIOUS -> { if (!available.previous()) return false; background.queue().previous(); }
                case NEXT -> { if (!available.next()) return false; background.queue().next(); }
                case SEEK -> { if (!available.seek()) return false; background.seek(Math.round(background.snapshot().durationMs() * fraction)); }
            }
            return true;
        }
    }

    @Override public synchronized OnlineMusicView backgroundView() {
        if (closed) throw new IllegalStateException("Music service closed");
        if (background == null || background.snapshot().state() == OnlineMusicPlayer.State.CLOSED) {
            if (bridge != null) bridge.close();
            background = backgroundFactory.get();
            bridge = new OnlineMusicFrameBridge(background, lease.get());
        }
        return new com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl(background, false);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (bridge != null) { bridge.close(); bridge = null; }
        if (background != null) { background.close(); background = null; }
    }
    @Override public OnlineMusicPlayer open() { return open(List.of(new NeteaseSource(), new QQMusicSource())); }
    @Override public OnlineMusicPlayer open(List<OnlineMusicSource> sources) {
        var player = new OnlinePlayerSession(sources, new MusicCredentialStore(java.nio.file.Path.of("config", "sh_better_ui", "music-accounts")));
        player.restoreSavedCredentials();
        return player;
    }
    @Override public OnlineMusicView view() { return view(open()); }
    @Override public OnlineMusicView view(OnlineMusicPlayer player) { return new com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl(player); }
}
