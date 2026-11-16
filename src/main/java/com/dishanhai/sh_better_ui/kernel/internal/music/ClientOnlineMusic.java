// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.List;

/** Stateless UUK factory; each caller owns and closes its independent session. */
public final class ClientOnlineMusic implements OnlineMusicService {
    @Override public OnlineMusicPlayer open() { return open(List.of(new NeteaseSource(), new QQMusicSource())); }
    @Override public OnlineMusicPlayer open(List<OnlineMusicSource> sources) {
        var player = new OnlinePlayerSession(sources, new MusicCredentialStore(java.nio.file.Path.of("config", "sh_better_ui", "music-accounts")));
        player.restoreSavedCredentials();
        return player;
    }
    @Override public OnlineMusicView view() { return view(open()); }
    @Override public OnlineMusicView view(OnlineMusicPlayer player) { return new com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl(player); }
}
