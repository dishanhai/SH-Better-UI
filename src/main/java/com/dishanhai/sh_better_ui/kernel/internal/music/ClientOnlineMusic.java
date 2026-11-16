// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.List;

/** Stateless UUK factory; each caller owns and closes its independent session. */
public final class ClientOnlineMusic implements OnlineMusicService {
    @Override public OnlineMusicPlayer open() { return open(List.of(new NeteaseSource(), new QQMusicSource())); }
    @Override public OnlineMusicPlayer open(List<OnlineMusicSource> sources) { return new OnlinePlayerSession(sources); }
}
