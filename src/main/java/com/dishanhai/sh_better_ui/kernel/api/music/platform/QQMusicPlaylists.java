// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.platform;

import com.dishanhai.sh_better_ui.kernel.internal.music.ClientQQMusicPlaylists;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Editable QQ Music playlists, bound to the credentials present when opened. */
public interface QQMusicPlaylists {
    int NAME_LIMIT = 20;

    record Playlist(long id, int directoryId, String name, int trackCount) {}
    enum AddResult { ADDED, ALREADY_PRESENT }

    static QQMusicPlaylists open(Supplier<String> credentials, Executor executor) {
        return new ClientQQMusicPlaylists(credentials, executor);
    }

    boolean available();
    CompletableFuture<List<Playlist>> list();
    CompletableFuture<Playlist> create(String name);
    CompletableFuture<AddResult> add(Playlist playlist, String songMid);
}
