// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.List;

/** UUK entry point for online playback. Sources can be supplied by a separate consumer module. */
public interface OnlineMusicService extends UIService {
    ServiceKey<OnlineMusicService> KEY = new ServiceKey<>(UiId.parse("sh_better_ui:online_music"), OnlineMusicService.class, 1);
    /** Existing foreground background session only. Queries never allocate a player or log in. */
    record BackgroundTarget(String sessionId, String trackKey) {
        public BackgroundTarget { java.util.Objects.requireNonNull(sessionId); java.util.Objects.requireNonNull(trackKey); }
    }
    record BackgroundControls(BackgroundTarget target, boolean pause, boolean resume, boolean previous, boolean next, boolean seek) {}
    enum BackgroundCommand { PAUSE, RESUME, PREVIOUS, NEXT, SEEK }
    default java.util.Optional<BackgroundControls> backgroundControls(BackgroundTarget target) { return java.util.Optional.empty(); }
    /** Returns false for stale/unsupported operations. Seek fraction is song-relative, in [0,1]. */
    default boolean controlBackground(BackgroundTarget target, BackgroundCommand command, double fraction) { return false; }
    OnlineMusicPlayer open();
    OnlineMusicPlayer open(List<OnlineMusicSource> sources);
    /** Complete, independently owned page; mount with the existing PreviewContext contract. */
    OnlineMusicView view();
    /** User-facing view whose playback survives view removal until explicit stop or kernel shutdown.
     * Implementations without background support retain the independent-view behavior. */
    default OnlineMusicView backgroundView() { return view(); }
    /** Mount an existing/custom-source session. The resulting view takes ownership of that session. */
    OnlineMusicView view(OnlineMusicPlayer player);
}
