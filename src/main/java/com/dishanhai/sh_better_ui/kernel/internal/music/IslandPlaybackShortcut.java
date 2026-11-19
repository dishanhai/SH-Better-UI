// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService.BackgroundCommand;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService.BackgroundTarget;

/** Deliberate input for the in-process background player, never for system media sessions. */
public enum IslandPlaybackShortcut {
    PLAY_PAUSE("play_pause"), PREVIOUS("previous"), NEXT("next"),
    SEEK_BACK("seek_back"), SEEK_FORWARD("seek_forward");

    private static final long SEEK_MS = 10_000;
    private static final String ONLINE = OnlineMusicService.KEY.id().toString();
    private final String key;

    IslandPlaybackShortcut(String key) { this.key = key; }

    public String translationKey() { return "key.sh_better_ui.music_island_" + key; }

    /** Select one physical press only, rejecting conflicting transport bindings. */
    public static IslandPlaybackShortcut select(boolean pressed, boolean eligible,
                                                 java.util.function.Predicate<IslandPlaybackShortcut> matches) {
        if (!pressed || !eligible) return null;
        IslandPlaybackShortcut selected = null;
        for (var shortcut : values()) {
            if (!matches.test(shortcut)) continue;
            if (selected != null) return null;
            selected = shortcut;
        }
        return selected;
    }

    public static BackgroundTarget target(MusicSnapshot snapshot) {
        if (!ONLINE.equals(snapshot.providerId()) || snapshot.session() == null
                || snapshot.session().track() == null) return null;
        return new BackgroundTarget(snapshot.session().id(), snapshot.session().track().key());
    }

    public boolean dispatch(OnlineMusicService service, BackgroundTarget shownTarget, MusicSnapshot snapshot, long nowNanos) {
        if (shownTarget == null || !shownTarget.equals(target(snapshot))) return false;
        var controls = service.backgroundControls(shownTarget).orElse(null);
        if (controls == null || !shownTarget.equals(controls.target())) return false;
        BackgroundCommand command = switch (this) {
            case PLAY_PAUSE -> controls.pause() ? BackgroundCommand.PAUSE
                    : controls.resume() ? BackgroundCommand.RESUME : null;
            case PREVIOUS -> controls.previous() ? BackgroundCommand.PREVIOUS : null;
            case NEXT -> controls.next() ? BackgroundCommand.NEXT : null;
            case SEEK_BACK, SEEK_FORWARD -> controls.seek() ? BackgroundCommand.SEEK : null;
        };
        if (command == null) return false;
        double fraction = -1;
        if (command == BackgroundCommand.SEEK) {
            var session = snapshot.session();
            long position = session.positionAt(nowNanos);
            long duration = session.durationMs().orElse(0);
            if (position < 0 || duration <= 0) return false;
            double toMs = (double) position + (this == SEEK_BACK ? -SEEK_MS : SEEK_MS);
            fraction = Math.max(0, Math.min(1, toMs / duration));
        }
        return service.controlBackground(shownTarget, command, fraction);
    }
}
