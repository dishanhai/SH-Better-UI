// Author: dishanhai
package com.dishanhai.sh_better_ui.client.music;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class NativeMusicFramesTest {
    private static MusicService.Frame sample(long revision) {
        var session = new MusicSnapshot.Session("online", MusicSnapshot.Track.of("track", "Song", "Artist", "Album"),
                MusicSnapshot.PlaybackStatus.PLAYING, OptionalLong.of(1234), OptionalLong.of(90000),
                OptionalDouble.of(1), System.nanoTime(), null, Set.of(MusicSnapshot.Capability.METADATA,MusicSnapshot.Capability.TIMELINE));
        return new MusicService.Frame(new MusicSnapshot("sh_better_ui:online_music",MusicSnapshot.State.AVAILABLE,session),
                new MusicService.Lyrics(MusicService.LyricsState.UNAVAILABLE,LyricTimeline.empty()),revision);
    }
    @Test void nativePublicApiReadsRealForegroundFrameAndReturnsAfterRelease() {
        var fallback=ClientMusic.frame();var frame=sample(7);
        try(var lease=ClientMusic.foreground()) {
            lease.publish(frame,System.nanoTime());
            assertSame(frame,ShBetterUiApi.music().getFrame());
            assertEquals("Song",ShBetterUiApi.music().getSnapshot().session().track().title().orElseThrow());
        }
        assertEquals(fallback.snapshot().providerId(),ClientMusic.frame().snapshot().providerId());
    }
    @Test void replacedOrExpiredPublisherCannotKeepAnOldSongVisible() {
        try(var old=ClientMusic.foreground();var current=ClientMusic.foreground()) {
            var oldFrame=sample(1);var currentFrame=sample(2);
            old.publish(oldFrame,System.nanoTime());
            assertNotSame(oldFrame,ClientMusic.frame());
            current.publish(currentFrame,System.nanoTime());assertSame(currentFrame,ClientMusic.frame());
            current.publish(currentFrame,System.nanoTime()-2_000_000_000L);
            assertNotSame(currentFrame,ClientMusic.frame());
        }
    }
}
