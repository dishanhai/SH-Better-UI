// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;

/** Internal presentation bridge: built-in attempts have an identity even for repeats of the same track. */
public final class OnlinePlaybackOccurrence {
    private OnlinePlaybackOccurrence() {}

    /** Sample under the same player monitor as the rest of the presentation snapshot. */
    public static long generation(OnlineMusicPlayer player) {
        return player instanceof OnlinePlayerSession session ? session.playbackGeneration() : -1;
    }
}
