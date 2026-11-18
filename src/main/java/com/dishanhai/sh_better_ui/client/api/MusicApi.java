// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LyricFeed;
import com.dishanhai.sh_better_ui.client.music.ClientMusic;
import com.dishanhai.sh_better_ui.client.music.MusicArtworkTexture;
import com.dishanhai.sh_better_ui.client.music.MusicEvent;
import com.dishanhai.sh_better_ui.client.music.MusicLyricFeed;
import com.dishanhai.sh_better_ui.client.music.MusicService;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import java.util.Optional;
import java.util.function.Consumer;

/** Shared client entry point. Read one frame when drawing multiple music fields together. */
public final class MusicApi {
    MusicApi() {}

    public MusicService.Frame getFrame() { return ClientMusic.frame(); }
    public MusicSnapshot getSnapshot() { return getFrame().snapshot(); }
    public MusicService.Lyrics getCurrentLyrics() { return getFrame().lyrics(); }
    public Optional<AudioFrame> getAudioFeatures() { return getAudioFeatures(getFrame()); }
    /** Mixed system output does not describe the internal online player. */
    public Optional<AudioFrame> getAudioFeatures(MusicService.Frame frame) {
        return frame.snapshot().providerId().equals("sh_better_ui:online_music") ? Optional.empty() : ClientMusic.audio();
    }
    public com.dishanhai.sh_better_ui.client.gui.scaled.LocalMusicIsland island() {
        return new com.dishanhai.sh_better_ui.client.gui.scaled.LocalMusicIsland(this);
    }

    public LyricFeed lyricFeed() { return new MusicLyricFeed(this::getFrame); }

    /** Render thread only. Returns empty while decoding or when the cover is unavailable. */
    public Optional<MusicArtworkTexture.Texture> cover(MusicService.Frame frame) {
        var session = frame.snapshot().session();
        return ClientMusic.artwork().texture(session == null ? null : session.artwork());
    }

    /** System-provider media worker callback (not internal online playback). Close the returned handle when the owner is removed. */
    public AutoCloseable addListener(Consumer<MusicEvent> listener) {
        var service = ClientMusic.service();
        return service == null ? () -> {} : service.addListener(listener);
    }

    public void removeListener(Consumer<MusicEvent> listener) {
        var service = ClientMusic.service();
        if (service != null) service.removeListener(listener);
    }

    public void retry() {
        var service = ClientMusic.service();
        if (service != null) service.retry();
    }

    public void refreshLyrics() {
        var service = ClientMusic.service();
        if (service != null) service.refreshLyrics();
    }

    /** Enable only in response to explicit user consent for mixed system-output capture. */
    public void setSystemAudioCaptureEnabled(boolean enabled) { ClientMusic.setCaptureEnabled(enabled); }
}
