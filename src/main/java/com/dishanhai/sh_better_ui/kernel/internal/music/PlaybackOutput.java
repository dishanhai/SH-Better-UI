// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer.State;

/** Device boundary for deterministic session tests. Position is measured from audible PCM frames. */
interface PlaybackOutput extends AutoCloseable {
    void start(byte[] mp3, long positionMs, boolean playing);
    void pause();
    void resume();
    State state();
    long positionMs();
    @Override void close();
}
