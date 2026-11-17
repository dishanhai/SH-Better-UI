// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music;

import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LocalLyricFiles;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.music.platform.WindowsMediaProvider;
import com.dishanhai.sh_better_ui.client.music.platform.MprisProvider;
import com.dishanhai.sh_better_ui.client.music.platform.MacMusicProvider;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Physical client only. The two loader entry points own its lifetime. */
public final class ClientMusic {
    private static final MusicService.Frame INACTIVE = new MusicService.Frame(
            new MusicSnapshot("system", MusicSnapshot.State.INITIALIZING, null),
            new MusicService.Lyrics(MusicService.LyricsState.NO_TRACK, LyricTimeline.empty()), 0);
    private static final MusicArtworkTexture ARTWORK = new MusicArtworkTexture();
    private static final ForegroundMusicFrames FOREGROUND = new ForegroundMusicFrames();
    private static volatile MusicService service;
    private static volatile SystemAudioCapture capture;
    private static boolean closed;

    private ClientMusic() {}

    public static synchronized void start(Path configDirectory) {
        if (service != null || closed) return;
        var local = new LocalLyricFiles(configDirectory.resolve("sh_better_ui-lyrics"));
        MusicProvider provider;
        if (Boolean.getBoolean("shb.music.disabled")) provider = unsupported("disabled");
        else if (System.getProperty("os.name", "").startsWith("Windows")) {
            provider = new WindowsMediaProvider();
        } else if (System.getProperty("os.name", "").startsWith("Linux")) {
            provider = new MprisProvider(System.getProperty("shb.music.playerctl", "playerctl"));
        } else if (System.getProperty("os.name", "").startsWith("Mac") && Boolean.getBoolean("shb.music.macAutomation")) {
            provider = new MacMusicProvider();
        } else provider = unsupported("system");
        var created = new MusicService(provider, List.of(local::load),
                Duration.ofMillis(Long.getLong("shb.music.pollMillis", 750L)));
        service = created;
        Runtime.getRuntime().addShutdownHook(new Thread(ClientMusic::close, "shb-music-shutdown"));
        created.start();
    }

    private static MusicProvider unsupported(String id) {
        return new MusicProvider() {
            @Override public String id() { return id; }
            @Override public Poll poll() throws Unsupported { throw new Unsupported("No system media provider"); }
            @Override public void close() {}
        };
    }

    public static MusicService.Frame frame() {
        var active = service;
        return FOREGROUND.select(active == null ? INACTIVE : active.getFrame(), System.nanoTime());
    }

    public static ForegroundMusicFrames.Lease foreground() { return FOREGROUND.acquire(); }

    public static MusicService service() { return service; }

    public static Optional<AudioFrame> audio() {
        var active = capture;
        return active != null && active.status() == SystemAudioCapture.Status.RUNNING
                ? Optional.of(active.sample()) : Optional.empty();
    }

    /** Opt in to mixed system audio output. Never called by loader initialization. */
    public static synchronized void setCaptureEnabled(boolean enabled) {
        if (closed) return;
        if (!enabled) {
            if (capture != null) capture.requestStop();
            capture = null;
        } else if (capture == null || capture.status() == SystemAudioCapture.Status.FAILED) {
            capture = new SystemAudioCapture();
            capture.start();
        }
    }

    public static MusicArtworkTexture artwork() { return ARTWORK; }

    private static void close() {
        MusicService media;
        SystemAudioCapture audio;
        synchronized (ClientMusic.class) {
            closed = true;
            media = service;
            audio = capture;
        }
        if (media != null) media.close();
        if (audio != null) audio.close();
        ARTWORK.closeDecoder();
    }
}
