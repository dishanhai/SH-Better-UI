// Author: dishanhai
package com.muoniumplayer.core.ncm.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MusicAudioCacheTest {
    @TempDir Path root;

    @Test void manualClearRemovesIdleGroupsAndPreservesActiveAndDownloadedSongs() throws Exception {
        var cache = Files.createDirectory(root.resolve("cache"));
        var downloads = Files.createDirectory(root.resolve("downloads"));
        var audio = Files.writeString(cache.resolve("idle.m4a"), "audio");
        var sidecar = Files.writeString(cache.resolve("idle.decoded.wav"), "decoded");
        var current = Files.writeString(cache.resolve("current.flac"), "current");
        var pending = Files.writeString(cache.resolve("pending.download"), "partial");
        var saved = Files.writeString(downloads.resolve("song.flac"), "saved");

        var result = MusicAudioCache.sweep(cache.toFile(), 0, System.currentTimeMillis(), Map.of(),
                0, MusicAudioCache.PARTIAL_MAX_AGE_MILLIS, true, "current"::equals);

        assertEquals(12, result.freedBytes);
        assertEquals(14, result.remainingBytes);
        assertEquals(1, result.removedGroups);
        assertEquals(2, result.skippedGroups);
        assertFalse(Files.exists(audio));
        assertFalse(Files.exists(sidecar));
        assertEquals("current", Files.readString(current));
        assertEquals("partial", Files.readString(pending));
        assertEquals("saved", Files.readString(saved));
    }

    @Test void automaticSweepStillTreatsZeroAsUnlimited() throws Exception {
        var audio = Files.writeString(root.resolve("idle.flac"), "audio");
        var result = MusicAudioCache.sweep(root.toFile(), 0, System.currentTimeMillis(), Map.of(),
                0, MusicAudioCache.PARTIAL_MAX_AGE_MILLIS);
        assertEquals(0, result.freedBytes);
        assertTrue(Files.exists(audio));
    }
}
