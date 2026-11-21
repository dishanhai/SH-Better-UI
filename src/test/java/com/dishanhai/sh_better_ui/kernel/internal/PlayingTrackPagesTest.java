package com.dishanhai.sh_better_ui.kernel.internal;

import com.google.gson.JsonParser;
import com.muoniumplayer.core.ncm.apple.AppleMusicService;
import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.ncm.music.dto.PlayList;
import com.muoniumplayer.core.ncm.spotify.SpotifyMusicService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayingTrackPagesTest {
    @Test
    void nativeNeteaseIdsAndOrderArePreservedWithoutDeduplicatingRows() {
        List<Music> tracks = PlayingTrackPages.parseNetease(JsonParser.parseString("""
                {"hotSongs":[{"id":10,"name":"Second"},
                {"id":20,"name":"First","ar":[{"id":42,"name":"Artist"}],"al":{"id":84,"name":"Album"}},
                {"id":10,"name":"Second"}]}
                """).getAsJsonObject(), "hotSongs");
        assertEquals(List.of(10L, 20L, 10L), tracks.stream().map(Music::getId).toList());
        assertEquals("42", PlayingTrackPages.destination(tracks.get(1), false).nativeId());
        assertEquals("84", PlayingTrackPages.destination(tracks.get(1), true).nativeId());
    }

    @Test
    void spotifyUsesNativeIdsNotSyntheticHashesAndRejectsNonCatalogIds() {
        var track = new SpotifyMusicService.Track();
        track.id = "trackId";
        track.name = "Track";
        track.artist = "Artist";
        track.album = "Album";
        track.artistId = "SpotifyArtist";
        track.albumId = "SpotifyAlbum";
        var music = Music.fromSpotifyTrack(track);
        assertEquals("SpotifyArtist", PlayingTrackPages.destination(music, false).nativeId());
        assertEquals("SpotifyAlbum", PlayingTrackPages.destination(music, true).nativeId());
        track.albumId = "album/path";
        assertNull(PlayingTrackPages.destination(Music.fromSpotifyTrack(track), true));
    }

    @Test
    void appleCanLookUpExactRelationshipsUsingOnlyCatalogSongIds() {
        var track = new AppleMusicService.Track();
        track.id = "123";
        track.name = "Track";
        track.artist = "Artist";
        track.album = "Album";
        var destination = PlayingTrackPages.destination(Music.fromAppleTrack(track), true);
        assertEquals(MusicPlatform.APPLE, destination.source());
        assertEquals("", destination.nativeId());
        assertEquals("123", destination.lookupSongId());
        track.id = "i.library";
        assertNull(PlayingTrackPages.destination(Music.fromAppleTrack(track), true));
    }

    @Test
    void playlistRequiresActualOriginNotSameIdOrVirtualList() {
        var payload = JsonParser.parseString("{\"songs\":[{\"id\":7,\"name\":\"Track\"}]}").getAsJsonObject();
        var rows = PlayingTrackPages.parseNetease(payload, "songs");
        var song = rows.get(0);
        var sameId = PlayingTrackPages.parseNetease(payload, "songs").get(0);
        var origin = new PlayList(15, "Playlist", "", 1, 0, null, "", 0);
        origin.setMusics(rows);
        assertSame(origin, PlayingTrackPages.originFor(song, origin));
        assertNull(PlayingTrackPages.originFor(sameId, origin));
        origin.setPlatformPlaylistId("album:15");
        assertNull(PlayingTrackPages.originFor(song, origin));
        origin.setPlatformPlaylistId("15");
        origin.setSearchMode(true);
        assertNull(PlayingTrackPages.originFor(song, origin));
    }
}
