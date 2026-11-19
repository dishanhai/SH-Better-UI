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
    void pause();
    void resume();
    State state();
    long positionMs();
    default float audioActivity() { return 0f; }
    @Override void close();
}
