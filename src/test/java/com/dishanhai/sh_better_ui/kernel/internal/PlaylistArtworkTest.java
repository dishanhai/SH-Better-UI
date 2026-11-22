package com.dishanhai.sh_better_ui.kernel.internal;

import com.google.gson.Gson;
import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.ncm.music.dto.PlayList;
import com.muoniumplayer.core.rendering.TextureManager;
import com.muoniumplayer.core.rendering.texture.AbstractTexture;
import com.muoniumplayer.core.utils.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaylistArtworkTest {
    @Test
    void acceptsProtocolRelativeQqCoversAndPreservesTheirQuery() {
        PlayList playlist = playlistWithCover(" //example.test/cover.jpg?size=300 ", MusicPlatform.QQ);

        assertEquals("https://example.test/cover.jpg?size=300", PlaylistArtwork.requestUrl(playlist));
    }

    @Test
    void addsNeteaseSizeOnlyToAnUnparameterizedCover() {
        assertEquals("https://example.test/cover.jpg?param=256y256",
                PlaylistArtwork.requestUrl(playlistWithCover("http://example.test/cover.jpg", MusicPlatform.NETEASE)));
        assertEquals("https://example.test/cover.jpg?param=512y512",
                PlaylistArtwork.requestUrl(playlistWithCover("https://example.test/cover.jpg?param=512y512", MusicPlatform.NETEASE)));
    }

    @Test
    void skipsMissingOrUnsupportedCoverUrls() {
        assertEquals("", PlaylistArtwork.requestUrl(playlistWithCover(null, MusicPlatform.QQ)));
        assertEquals("", PlaylistArtwork.requestUrl(playlistWithCover("cover.jpg", MusicPlatform.QQ)));
    }

    @Test
    void usesACachedTrackCoverUntilPlaylistArtworkIsReady() {
        PlayList playlist = new PlayList(90123L, "Playlist", null, 0, 0L, null, "", 0L);
        Music missing = new Gson().fromJson("{\"id\":90124}", Music.class);
        Music track = new Gson().fromJson(
                "{\"id\":90125,\"al\":{\"picUrl\":\"https://example.test/cover.jpg\"}}", Music.class);
        Location fallback = Location.of("muonium/textures/music/" + track.getStableKey() + "/playlist_cover.png");
        var textures = TextureManager.getInstance();
        try {
            PlaylistArtwork artwork = new PlaylistArtwork(playlist);
            assertNull(artwork.loadedLocation());

            textures.loadTexture(fallback, new AbstractTexture() {});
            artwork.requestTrackFallback(List.of(missing, track));
            assertEquals(fallback, artwork.loadedLocation());

            textures.loadTexture(playlist.getCoverLocation(), new AbstractTexture() {});
            assertEquals(playlist.getCoverLocation(), artwork.loadedLocation());
        } finally {
            textures.mapTextureObjects.remove(fallback);
            textures.mapTextureObjects.remove(playlist.getCoverLocation());
        }
    }

    private static PlayList playlistWithCover(String cover, MusicPlatform platform) {
        PlayList playlist = new PlayList(1L, "Playlist", cover, 0, 0L, null, "", 0L);
        playlist.setPlatform(platform);
        return playlist;
    }
}
