// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.List;

/** UUK entry point for online playback. Sources can be supplied by a separate consumer module. */
public interface OnlineMusicService extends UIService {
    ServiceKey<OnlineMusicService> KEY = new ServiceKey<>(UiId.parse("sh_better_ui:online_music"), OnlineMusicService.class, 1);
    OnlineMusicPlayer open();
    OnlineMusicPlayer open(List<OnlineMusicSource> sources);
}
