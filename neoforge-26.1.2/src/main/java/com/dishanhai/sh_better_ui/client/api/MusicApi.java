// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LyricFeed;
import java.util.function.Consumer;

/** 26.x immutable frame/lyrics API. GPU covers, system capture and a global HUD are separate migrations. */
public final class MusicApi {
    MusicApi() {}
    public MusicService.Frame getFrame() { return ClientMusic.frame(); }
    public MusicSnapshot getSnapshot() { return getFrame().snapshot(); }
    public MusicService.Lyrics getCurrentLyrics() { return getFrame().lyrics(); }
    public LyricFeed lyricFeed() { return new MusicLyricFeed(this::getFrame); }
    /** Listener observes system-provider changes, not the internal online player. */
    public AutoCloseable addListener(Consumer<MusicEvent> listener) {
        var service = ClientMusic.service();
        if (service == null) throw new IllegalStateException("Native music metadata service is not running");
        return service.addListener(listener);
    }
    public void removeListener(Consumer<MusicEvent> listener) {
        var service = ClientMusic.service();
        if (service != null) service.removeListener(listener);
    }
    public void retry() { var service = ClientMusic.service(); if (service != null) service.retry(); }
    public void refreshLyrics() { var service = ClientMusic.service(); if (service != null) service.refreshLyrics(); }
}
