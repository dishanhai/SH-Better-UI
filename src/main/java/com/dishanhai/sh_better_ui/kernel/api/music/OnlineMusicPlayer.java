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
    /** Replaces credentials and stops playback. Saved only when explicitly enabled for this source. */
    void credentials(String source, String cookie);
    /** Opt in before or after login/import; current credentials are saved immediately.
     * Disabling removes saved credentials while keeping the current login.
     * Built-in storage accepts source IDs matching [a-z0-9_-]{1,40}; other IDs remain session-only. */
    default void rememberCredentials(String source, boolean remember) { throw new UnsupportedOperationException(); }
    default boolean remembersCredentials(String source) { return false; }
    /** Starts QR login; replaces/cancels this player's previous attempt. */
    OnlineMusicLogin login(String source);
    /** Account operations run on workers. Pages are one-based with twenty entries. */
    default CompletableFuture<OnlineMusicAccount> account(String source) { return unsupported(); }
    default CompletableFuture<List<OnlinePlaylist>> playlists(String source, int page) { return unsupported(); }
    default CompletableFuture<List<OnlineTrack>> playlist(OnlinePlaylist playlist, int page) { return unsupported(); }
    default CompletableFuture<List<OnlineTrack>> liked(String source, int page) { return unsupported(); }
    default CompletableFuture<Void> like(OnlineTrack track, boolean liked) { return unsupported(); }
    /** Whether this provider implements verified write requests for the liked collection. */
    default boolean supportsLikes(String source) { return false; }
    /** Local pinyin candidates plus remote autocomplete, bounded to eight entries. */
    default CompletableFuture<List<String>> suggest(String source, String query) { return unsupported(); }
    private static <T> CompletableFuture<T> unsupported() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Music operation unavailable"));
    }
    /** Whether this session has received credentials; the platform can still expire/revoke them. */
    boolean authenticated(String source);
    /** Optional persistent queue; null keeps legacy third-party players on a view-local queue. */
    default OnlineMusicQueue queue() { return null; }
    void play(OnlineTrack track);
    void pause();
    void resume();
    void seek(long positionMs);
    void stop();
    /** Session-local software gain, independent of the operating-system mixer. */
    default boolean supportsVolume() { return false; }
    default float volume() { return 1f; }
    /** Normalized 0..1; implementations reject non-finite values and never amplify above unity. */
    default void volume(float value) { throw new UnsupportedOperationException("Music volume unavailable"); }
    /** True only when the current output supports native PCM tone shaping.
     * Recheck when playback/output changes; external/browser players default to unavailable. */
    default boolean supportsEqualizer() { return false; }
    /** Remembered session preference, not proof that the current output applies it. */
    default OnlineMusicEqualizerPreset equalizerPreset() { return OnlineMusicEqualizerPreset.FLAT; }
    /** Requires supportsEqualizer(); never silently accepts an unsupported effect. */
    default void equalizerPreset(OnlineMusicEqualizerPreset preset) {
        throw new UnsupportedOperationException("Music equalizer unavailable");
    }
    /** Whether the current QQ track uses a native output that can apply local sound effects. */
    default boolean supportsSoundEffects() { return false; }
    /** Remembered session preference; no effect is applied when this capability is unavailable. */
    default OnlineMusicSoundEffect soundEffect() { return OnlineMusicSoundEffect.OFF; }
    /** Requires supportsSoundEffects(); does not alter provider quality, licensing, or stream selection. */
    default void soundEffect(OnlineMusicSoundEffect effect) {
        throw new UnsupportedOperationException("Music sound effects unavailable");
    }
    /** Compressed bytes referenced by cache entries, counted once even if shared with active playback.
     * Additional playback-only buffers and decoder/device memory are not included. */
    record AudioCacheInfo(int entries, long retainedBytes, long capacityBytes) {}
    default AudioCacheInfo audioCacheInfo() { return new AudioCacheInfo(0, 0, 0); }
    /** Evicts reusable compressed audio without stopping playback. No files or credentials are deleted. */
    default void clearAudioCache() { throw new UnsupportedOperationException("Audio cache unavailable"); }
    /** Fresh post-gain PCM activity, normalized 0..1. Unavailable/protected audio is silent, never simulated. */
    default float audioActivity() { return 0f; }
    Snapshot snapshot();
    LyricFeed lyricFeed();
    @Override void close();
}
