// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.platform;

import com.dishanhai.sh_better_ui.kernel.internal.music.ClientQQTrackFavorites;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** A QQ Music account's favorite state. A UI can queue lookups while laying out visible tracks. */
public interface QQTrackFavorites {
    enum State { SIGNED_OUT, LOADING, UNAVAILABLE, UNLIKED, LIKED, ADDING, REMOVING }

    static QQTrackFavorites create(Executor executor) {
        return new ClientQQTrackFavorites(executor);
    }

    void credentials(String cookie);
    State state(String songMid);
    void loadPending();
    CompletableFuture<Void> set(String songMid, boolean liked);
}
