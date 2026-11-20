// Author: dishanhai
package com.muoniumplayer.core.ncm.music;

import com.google.gson.Gson;
import com.muoniumplayer.core.ncm.music.dto.Music;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class LocalTrackOwnershipTest {
    @Test
    void partialStreamRecoveryNeverTreatsImportedAudioAsDisposable(@TempDir Path library) throws Exception {
        Music track = new Gson().fromJson("{\"dt\":180000}", Music.class);
        Path original = library.resolve("recording.flac");
        byte[] content = { 'f', 'L', 'a', 'C', 1, 2, 3 };
        Files.write(original, content);

        track.setSource(MusicPlatform.LOCAL);
        assertFalse(CrossSourceSwitcher.isIncompleteRemoteStream(track, 20_000));
        AudioCacheFiles.discardFailedDownload(original.toFile(), track);
        assertArrayEquals(content, Files.readAllBytes(original));
        Path sidecar = AudioCacheFiles.getDecodedWavFile(original.toFile(), true).toPath().toAbsolutePath();
        assertFalse(sidecar.startsWith(library));

        track.setSource(MusicPlatform.NETEASE);
        assertTrue(CrossSourceSwitcher.isIncompleteRemoteStream(track, 20_000));
        AudioCacheFiles.discardFailedDownload(original.toFile(), track);
        assertArrayEquals(content, Files.readAllBytes(original));
    }
}
