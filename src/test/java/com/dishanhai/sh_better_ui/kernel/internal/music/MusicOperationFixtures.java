// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;

public final class MusicOperationFixtures {
    private MusicOperationFixtures() {}

    public static SessionMusicOperations registry(OnlineMusicPlayer player) {
        return new SessionMusicOperations(player);
    }
}
