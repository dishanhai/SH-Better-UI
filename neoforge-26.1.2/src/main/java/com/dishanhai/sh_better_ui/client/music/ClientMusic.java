// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.music;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LocalLyricFiles;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.music.platform.WindowsMediaProvider;
import com.dishanhai.sh_better_ui.client.music.platform.MprisProvider;
import com.dishanhai.sh_better_ui.client.music.platform.MacMusicProvider;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Native 26.x metadata/lyrics and in-process online frame source; no GPU or system-output capture. */
public final class ClientMusic {
    private static final MusicService.Frame INACTIVE = new MusicService.Frame(
            new MusicSnapshot("system", MusicSnapshot.State.INITIALIZING, null),
            new MusicService.Lyrics(MusicService.LyricsState.NO_TRACK, LyricTimeline.empty()), 0);
    private static final MusicService.Frame CLOSED = new MusicService.Frame(
            new MusicSnapshot("system", MusicSnapshot.State.CLOSED, null),
            new MusicService.Lyrics(MusicService.LyricsState.NO_TRACK, LyricTimeline.empty()), 0);
    private static final ForegroundMusicFrames FOREGROUND = new ForegroundMusicFrames();
    private static volatile MusicService service;
    private static volatile boolean closed;

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
        try {
            created.start();
            service = created;
        } catch (RuntimeException | Error failure) {
            created.close();
            throw failure;
        }
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
        return FOREGROUND.select(active == null ? (closed ? CLOSED : INACTIVE) : active.getFrame(), System.nanoTime());
    }

    public static ForegroundMusicFrames.Lease foreground() { return FOREGROUND.acquire(); }

    public static MusicService service() { return service; }

    /** Owned by the physical client lifecycle, not by an individual screen. */
    public static void close() {
        MusicService media;
        synchronized (ClientMusic.class) {
            closed = true;
            media = service;
            service = null;
        }
        if (media != null) media.close();
        // Revoke any old online publisher; its stale lease cannot become selected again.
        FOREGROUND.acquire().close();
    }
}
