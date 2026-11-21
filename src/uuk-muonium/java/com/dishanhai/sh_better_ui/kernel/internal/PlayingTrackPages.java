// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.muoniumplayer.core.ncm.api.CloudMusicApi;
import com.muoniumplayer.core.ncm.apple.AppleMusicService;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.KuGouMusicService;
import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.ncm.music.NeteaseMusicService;
import com.muoniumplayer.core.ncm.music.dto.Artist;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.ncm.music.dto.PlayList;
import com.muoniumplayer.core.ncm.spotify.SpotifyMusicService;
import com.muoniumplayer.core.screens.ncm.NCMScreen;
import com.muoniumplayer.core.screens.ncm.panels.PlaylistPanel;
import com.muoniumplayer.core.utils.json.JsonUtils;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayingTrackPages {
    private PlayingTrackPages() {}

    record Destination(MusicPlatform source, boolean album, String nativeId, String lookupSongId) {}

    public static String firstArtist(Music track) {
        Artist artist = primaryArtist(track);
        return artist == null ? "" : artist.getName().strip();
    }

    private static Artist primaryArtist(Music track) {
        if (track == null || track.getArtists() == null) return null;
        for (Artist artist : track.getArtists()) {
            if (artist == null || artist.getName() == null || artist.getName().isBlank()) continue;
            String name = artist.getName().strip();
            if (!name.equalsIgnoreCase("Unknown") && !name.equalsIgnoreCase("Unknown Artist")) return artist;
        }
        return null;
    }

    public static boolean canOpen(Music track, boolean album) {
        return !displayName(track, album).isEmpty();
    }

    static Destination destination(Music track, boolean album) {
        if (track == null || track.isDjProgram()) return null;
        Artist artist = primaryArtist(track);
        if (displayName(track, album).isEmpty()) return null;
        MusicPlatform source = track.getSource();
        String id;
        String lookupSongId = "";
        switch (source) {
            case NETEASE -> {
                // The built-in generic music-source adapter hashes names. Never send those as catalog IDs.
                if (track.getMuTrack() != null) {
                    id = "";
                    lookupSongId = numericId(track.getSourceId());
                } else {
                    long numericId = album ? track.getAlbum().getId() : artist.getId();
                    id = numericId > 0 ? Long.toString(numericId) : "";
                }
            }
            case SPOTIFY -> id = providerId(album ? track.getCatalogAlbumId() : track.getCatalogArtistId());
            case APPLE -> {
                id = numericId(album ? track.getCatalogAlbumId() : track.getCatalogArtistId());
                if (id.isEmpty()) lookupSongId = numericId(track.getSourceId());
            }
            case KUGOU -> id = album ? numericId(track.getKugouAlbumId()) : "";
            default -> { return null; }
        }
        return id.isEmpty() && lookupSongId.isEmpty() ? null : new Destination(source, album, id, lookupSongId);
    }

    private static String numericId(String id) {
        return id != null && id.matches("[1-9][0-9]{0,17}") ? id : "";
    }

    private static String providerId(String id) {
        return id != null && id.matches("[a-zA-Z0-9]+") ? id : "";
    }

    public static String originalPlaylistName(Music track) {
        PlayList origin = originFor(track, CloudMusic.currentPlaylistContext);
        return origin == null ? "" : origin.getName();
    }

    /** Do not confuse the live queue, a search result, or a different playlist with this track's origin. */
    static PlayList originFor(Music track, PlayList candidate) {
        if (track == null || candidate == null || candidate.isPersonalFm() || candidate.isSearchMode()
                || candidate.getName() == null || candidate.getName().isBlank()) return null;
        String id = candidate.getPlatformPlaylistId();
        if (id.isBlank() || id.equals("0") || id.startsWith("album:") || id.startsWith("artist:")
                || id.startsWith("radio:") || id.startsWith("chart:")) return null;
        // Only the actual row object is proof, not another occurrence with a coincidentally equal track ID.
        List<Music> rows = candidate.musics;
        if (rows != null) for (Music row : rows) {
            if (row == track) return candidate;
        }
        return null;
    }

    public static void openPlaylist(NCMScreen screen, Music track) {
        PlayList origin = originFor(track, CloudMusic.currentPlaylistContext);
        if (origin == null) return;
        screen.openPlayingTrackPage(origin.getName(), () -> new PlaylistPanel(origin, true, true),
                () -> CloudMusic.playbackPresentation().displayTrack == track
                        && originFor(track, CloudMusic.currentPlaylistContext) == origin);
    }

    public static void open(NCMScreen screen, Music track, boolean album) {
        Destination destination = destination(track, album);
        String name = displayName(track, album);
        if (name.isEmpty()) return;
        String title = Component.translatable("sh_better_ui.playing_metadata."
                + (destination == null ? "search" : album ? "album" : "artist"), name).getString();
        screen.openPlayingTrackPage(title, () -> {
            List<Music> songs = destination == null ? searchByName(track, name, album) : load(destination);
            String heading = title;
            boolean ordered = destination != null;
            // Spotify Development Mode does not expose artist top tracks.
            if (songs.isEmpty() && destination != null && destination.source() == MusicPlatform.SPOTIFY && !album) {
                songs = searchByName(track, name, false);
                ordered = false;
                heading = Component.translatable("sh_better_ui.playing_metadata.search", name).getString();
            }
            if (songs.isEmpty()) return null;
            var playlist = new PlayList(0, heading, "", songs.size(), 0, null, "", 0);
            playlist.setPlatform(track.getSource());
            playlist.setSearchMode(true);
            playlist.setMusics(new CopyOnWriteArrayList<>(songs));
            playlist.setMusicsQueried(true);
            playlist.setMusicsLoaded(true);
            return new PlaylistPanel(playlist, true, ordered);
        }, () -> CloudMusic.playbackPresentation().displayTrack == track);
    }

    private static String displayName(Music track, boolean album) {
        if (track == null || track.isDjProgram()) return "";
        if (!album) return firstArtist(track);
        return track.getAlbum() == null || track.getAlbum().getName() == null
                ? "" : track.getAlbum().getName().strip();
    }

    private static List<Music> searchByName(Music track, String name, boolean album) {
        String artist = firstArtist(track);
        String keyword = album && !artist.isEmpty() ? name + " " + artist : name;
        List<Music> results = NeteaseMusicService.search(track.getSource(), keyword, 50);
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(track), results.stream().filter(candidate -> candidate.getSource() == track.getSource())
                .filter(candidate -> (artist.isEmpty() || candidate.getArtists() != null
                        && candidate.getArtists().stream().filter(java.util.Objects::nonNull)
                        .anyMatch(row -> row.getName() != null && artist.equalsIgnoreCase(row.getName().strip()))))
                .filter(candidate -> !album || name.equalsIgnoreCase(displayName(candidate, true))))
                .distinct().toList();
    }

    private static List<Music> load(Destination destination) {
        String id = destination.nativeId();
        boolean album = destination.album();
        return switch (destination.source()) {
            case NETEASE -> {
                if (id.isEmpty()) {
                    String songId = destination.lookupSongId();
                    List<Music> details = parseNetease(CloudMusicApi.songDetail(Long.parseLong(songId)).toJsonObject(), "songs");
                    Music exact = details.stream().filter(m -> songId.equals(Long.toString(m.getId())))
                            .findFirst().orElse(null);
                    Artist artist = primaryArtist(exact);
                    long detailId = album
                            ? (exact == null || exact.getAlbum() == null ? 0L : exact.getAlbum().getId())
                            : (artist == null ? 0L : artist.getId());
                    if (detailId <= 0) yield List.of();
                    id = Long.toString(detailId);
                }
                long nativeId = Long.parseLong(id);
                yield album ? parseNetease(CloudMusicApi.album(nativeId).toJsonObject(), "songs")
                        : parseNetease(CloudMusicApi.artist(nativeId).toJsonObject(), "hotSongs");
            }
            case KUGOU -> KuGouMusicService.albumSongs(id, 500);
            case SPOTIFY -> (album ? SpotifyMusicService.playlistTracks(SpotifyMusicService.ALBUM_PREFIX + id, 500)
                    : SpotifyMusicService.artistTopTracks(id)).stream().map(Music::fromSpotifyTrack)
                    .filter(java.util.Objects::nonNull).toList();
            case APPLE -> {
                if (id.isEmpty()) id = numericId(AppleMusicService.songRelationId(destination.lookupSongId(), album));
                if (id.isEmpty()) yield List.of();
                yield (album ? AppleMusicService.playlistTracks(AppleMusicService.ALBUM_PREFIX + id, 500)
                        : AppleMusicService.artistTopTracks(id)).stream().map(Music::fromAppleTrack)
                        .filter(java.util.Objects::nonNull).toList();
            }
            default -> List.of();
        };
    }

    static List<Music> parseNetease(JsonObject payload, String field) {
        List<Music> tracks = new ArrayList<>();
        JsonArray rows = payload != null && payload.has(field) && payload.get(field).isJsonArray()
                ? payload.getAsJsonArray(field) : null;
        if (rows != null) for (JsonElement row : rows) {
            if (!row.isJsonObject()) continue;
            JsonObject song = row.getAsJsonObject();
            Music music = JsonUtils.parse(song, Music.class);
            if (music == null || music.getId() <= 0) continue;
            music.applyNeteaseMetadata(song, null);
            tracks.add(music);
        }
        return tracks;
    }
}
