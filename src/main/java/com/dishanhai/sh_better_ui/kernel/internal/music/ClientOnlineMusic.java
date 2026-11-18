// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.List;

/** Independent sessions plus a kernel-owned player for the ordinary background music route. */
public final class ClientOnlineMusic implements OnlineMusicService, AutoCloseable {
    private OnlineMusicPlayer background;
    private OnlineMusicFrameBridge bridge;
    private boolean closed;

    @Override public synchronized OnlineMusicView backgroundView() {
        if (closed) throw new IllegalStateException("Music service closed");
        if (background == null || background.snapshot().state() == OnlineMusicPlayer.State.CLOSED) {
            if (bridge != null) bridge.close();
            background = open();
            bridge = new OnlineMusicFrameBridge(background, com.dishanhai.sh_better_ui.client.music.ClientMusic.foreground());
        }
        return new com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl(background, false);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (bridge != null) { bridge.close(); bridge = null; }
        if (background != null) { background.close(); background = null; }
    }
    @Override public OnlineMusicPlayer open() { return open(List.of(new NeteaseSource(), new QQMusicSource())); }
    @Override public OnlineMusicPlayer open(List<OnlineMusicSource> sources) {
        var player = new OnlinePlayerSession(sources, Mp3Output::new, new MusicHttp()::audio,
                new MusicCredentialStore(java.nio.file.Path.of("config", "sh_better_ui", "music-accounts")),
                new MusicQueueStore(java.nio.file.Path.of("config", "sh_better_ui", "music-queue.bin")));
        player.restoreSavedCredentials();
        return player;
    }
    @Override public OnlineMusicView view() { return view(open()); }
    @Override public OnlineMusicView view(OnlineMusicPlayer player) { return new com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl(player); }
}
