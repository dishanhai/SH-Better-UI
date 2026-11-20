// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;

/** Device boundary for deterministic session tests. Position is measured from audible PCM frames. */
interface PlaybackOutput extends AutoCloseable {
    void start(byte[] mp3, long positionMs, boolean playing);
    default void volume(float value) {}
    default boolean supportsEqualizer() { return false; }
    default void equalizerPreset(com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicEqualizerPreset preset) {
        throw new UnsupportedOperationException("Output equalizer unavailable");
    }
    default boolean supportsPlaybackRate() { return false; }
    default void playbackRate(double rate) {
        if (!Double.isFinite(rate) || rate < 0.5 || rate > 2.0)
            throw new IllegalArgumentException("Playback rate must be between 0.5 and 2.0");
        throw new UnsupportedOperationException("Output playback rate unavailable");
    }
    enum CrossfadeStatus { NONE, PREPARING, READY, MIXING, COMPLETE, REJECTED }
    default boolean supportsCrossfade() { return false; }
    /** Prepare on a worker, not under the caller's player lock. Tokens are positive and monotonically increasing. */
    default boolean queueCrossfade(long token, byte[] mp3, int seconds, long outgoingDurationMs) throws java.io.IOException {
        return false;
    }
    default CrossfadeStatus crossfadeStatus(long token) { return CrossfadeStatus.NONE; }
    /** True when playback did not irrevocably start mixing this transition. */
    default boolean cancelCrossfade(long token) { return true; }
    /** Accept a consumed COMPLETE transition and remap its position to the successor's local clock. */
    default void acceptCrossfade(long token) {}
    default boolean supportsSoundEffects() { return false; }
    default void soundEffect(com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSoundEffect effect) {
        throw new UnsupportedOperationException("Output sound effects unavailable");
    }
    void pause();
    void resume();
    State state();
    long positionMs();
    default float audioActivity() { return 0f; }
    @Override void close();
}
