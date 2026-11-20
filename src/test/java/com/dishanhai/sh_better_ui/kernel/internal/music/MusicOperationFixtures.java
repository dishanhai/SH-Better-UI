// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;

import java.util.List;

public final class MusicOperationFixtures {
    private MusicOperationFixtures() {}

    public static OnlineMusicPlayer searchSession(OnlineMusicSource source) {
        return new OnlinePlayerSession(List.of(source),
                () -> { throw new AssertionError("A revoked result must not start playback"); },
                uri -> { throw new AssertionError("A revoked result must not fetch audio"); });
    }

    public static SessionMusicOperations registry(OnlineMusicPlayer player) {
        return new SessionMusicOperations(player);
    }
}
