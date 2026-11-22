// Author: dishanhai
package com.muoniumplayer.core.ncm.music;

import com.google.gson.JsonParser;
import com.muoniumplayer.core.ncm.music.dto.Music;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Listen1PlaylistImportTest {
    @Test
    void savedMixedPlaylistRetainsPlaybackIdentitiesAfterRestart(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("listen1_backup.json"), """
                {
                  "playerlists":["myplaylist_1"],
                  "myplaylist_1":{"info":{"id":"myplaylist_1","title":"混合歌单"},"tracks":[
                    {"id":"netrack_123","source":"netease","title":"first"},
                    {"id":"qqtrack_004abc","source":"qq","title":"second","url":"https://example.test/audio"},
                    {"id":"kgtrack_ABCDEF0123456789ABCDEF0123456789","source":"kugou","title":"third"},
                    {"id":"bitrack_v_BV1AbC2dE3fG-98765","source":"bilibili","title":"fourth"},
                    {"id":"netrack_123","source":"netease","title":"first"},
                    {"id":"kwtrack_MUSIC_432","source":"kuwo","title":"fifth"},
                    {"id":"mgtrack_600ABC","song_id":32145,"source":"migu","title":"sixth"},
                    {"id":"netrack_9999999999999999999","source":"netease"},
                    {"id":"qqtrack_bad","source":"kugou"},
                    {"id":"kwtrack_not_a_rid","source":"kuwo"},
                    {"id":"bitrack_123","source":"bilibili"}
                  ]}
                }
                """);
        var result = Listen1PlaylistImport.read(file);
        assertEquals(7, result.tracks());
        assertEquals(4, result.skippedTracks());
        var saved = result.playlists().getFirst();
        assertTrue(saved.musicsLoaded);
        assertEquals(ImportedPlaylistStore.keyOf(saved),
                ImportedPlaylistStore.keyOf(Listen1PlaylistImport.read(file).playlists().getFirst()));

        var json = ImportedPlaylistStore.serialize(saved);
        assertFalse(json.toString().contains("example.test"));
        var restored = ImportedPlaylistStore.deserialize(json);
        assertNotNull(restored);
        assertTrue(restored.musicsQueried);
        assertTrue(restored.musicsLoaded);
        assertEquals("混合歌单", restored.getName());
        var tracks = restored.getMusics();
        assertEquals(List.of(MusicPlatform.NETEASE, MusicPlatform.QQ, MusicPlatform.KUGOU,
                MusicPlatform.BILIBILI, MusicPlatform.NETEASE, MusicPlatform.KUWO, MusicPlatform.MIGU),
                tracks.stream().map(Music::getSource).toList());
        assertEquals(List.of("123", "004abc", "abcdef0123456789abcdef0123456789",
                "BV1AbC2dE3fG_c98765", "123", "432", "32145"), tracks.stream().map(Music::getSourceId).toList());
        assertEquals(123, tracks.getFirst().getId());
        assertEquals("004abc", tracks.get(1).toMuTrack().getMid());
        assertEquals("abcdef0123456789abcdef0123456789", tracks.get(2).toKuGouTrack().hash);
        assertEquals("98765", BilibiliMusicService.parseSourceId(tracks.get(3).getSourceId()).cid);
        assertEquals("600ABC", tracks.get(6).getSourceMid());
    }

    @Test
    void favoritesRoundTripAsLazyReferences(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("listen1_backup.json"), """
                {
                  "favoriteplayerlists":["qqplaylist_70","neplaylist_85","kgplaylist_96","biplaylist_12"],
                  "qqplaylist_70":{"info":{"id":"qqplaylist_70","title":"QQ"}},
                  "neplaylist_85":{"info":{"id":"neplaylist_85","title":"网易云"}},
                  "kgplaylist_96":{"info":{"id":"kgplaylist_96","title":"酷狗"}},
                  "biplaylist_12":{"info":{"id":"biplaylist_12","title":"音频歌单"}}
                }
                """);
        var result = Listen1PlaylistImport.read(file);
        assertEquals(1, result.skippedFavorites());
        assertEquals(List.of("70", "85", "96"),
                result.playlists().stream().map(p -> p.getPlatformPlaylistId()).toList());
        for (var playlist : result.playlists()) {
            var json = ImportedPlaylistStore.serialize(playlist);
            assertFalse(json.has("tracks"));
            var restored = ImportedPlaylistStore.deserialize(json);
            assertNotNull(restored);
            assertFalse(restored.musicsQueried);
            assertEquals(playlist.getPlatform(), restored.getPlatform());
            assertEquals(playlist.getPlatformPlaylistId(), restored.getPlatformPlaylistId());
        }
        assertEquals(85, result.playlists().get(1).getId());
    }

    @Test
    void selectsBilibiliPartByCidAndDoesNotFallBackToFirstPart() {
        var data = JsonParser.parseString("""
                {"aid": "99", "cid":98764, "pages":[{"page":1,"cid":98764},{"page":2,"cid":98765}]}
                """).getAsJsonObject();
        assertEquals(2, BilibiliMusicService.pageForCid(data, "98765"));
        assertEquals(0, BilibiliMusicService.pageForCid(data, "12345"));
        assertEquals(2, BilibiliMusicService.parseSourceId("BV1AbC2dE3fG_p2").page);
        assertEquals(2, BilibiliMusicService.parseSourceId("BV1AbC2dE3fG:2").page);
    }
}
