// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LyricFeed;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** An independent client session. Close when its owning feature exits. No network runs at construction. */
public interface OnlineMusicPlayer extends AutoCloseable {
    enum State { IDLE, LOADING, PLAYING, PAUSED, ENDED, FAILED, CLOSED }
    enum LyricsState { EMPTY, LOADING, READY, UNAVAILABLE }
    enum Failure { NONE, NETWORK, UNAVAILABLE, LOGIN_REQUIRED, AUDIO }
    record Snapshot(OnlineTrack track, State state, long positionMs, long durationMs,
                    LyricsState lyricsState, LyricTimeline lyrics, Failure failure, boolean preview) {}

    List<String> sources();
    /** One-based page, twenty results per page. Cancelling the future interrupts the request. */
    CompletableFuture<List<OnlineTrack>> search(String source, String query, int page);
    /** Replaces credentials only in this session and stops playback before applying them. */
    void credentials(String source, String cookie);
    /** Starts QR login; replaces/cancels this player's previous attempt. Does not persist credentials. */
    OnlineMusicLogin login(String source);
    /** Whether this session has received credentials; the platform can still expire/revoke them. */
    boolean authenticated(String source);
    void play(OnlineTrack track);
    void pause();
    void resume();
    void seek(long positionMs);
    void stop();
    Snapshot snapshot();
    LyricFeed lyricFeed();
    @Override void close();
}
