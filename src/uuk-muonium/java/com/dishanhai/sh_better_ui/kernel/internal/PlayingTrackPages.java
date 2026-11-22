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
import com.muoniumplayer.core.music.source.MusicSource;
import com.muoniumplayer.core.music.source.Track;
import com.muoniumplayer.core.rendering.FoliaLucideIcons;
import com.muoniumplayer.core.screens.ncm.NCMPanel;
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.internal.music.OnlineMusicCatalog;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public final class PlayingTrackPages {
    private PlayingTrackPages() {}

    record Destination(MusicPlatform source, boolean album, String nativeId, String lookupSongId) {}
    /** Snapshot of embedded DTO fields exposed through Lombok-generated Java accessors. */
    public record SongMetadata(String catalogId, String album, long publishedAt) {}

    public static SongMetadata songMetadata(Music track) {
        String id = track.isQQ() ? track.getSourceMid() : track.isNetease() ? track.getSourceId() : "";
        return new SongMetadata(id, track.getAlbum() == null ? "" : track.getAlbum().getName(), track.getPublishTime());
    }

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

    public static List<String> artistNames(Music track) {
        var names = new LinkedHashMap<String, String>();
        if (track != null && track.getArtists() != null) {
            for (var artist : track.getArtists()) {
                if (artist == null || artist.getName() == null) continue;
                String name = artist.getName().strip();
                if (!name.isEmpty() && !name.equalsIgnoreCase("Unknown")) names.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return List.copyOf(names.values());
    }

    public static void open(NCMScreen screen, Music track, boolean album, Box anchor, boolean fromLyrics) {
        if (screen == null || track == null) return;
        if (album) {
            navigateTrack(screen, track, true, firstArtist(track), fromLyrics);
            return;
        }
        var names = artistNames(track);
        if (names.size() == 1) navigateTrack(screen, track, false, names.get(0), fromLyrics);
        else if (!names.isEmpty()) {
            screen.chooseCatalogArtist(anchor, names.stream().map(name -> artistChoice(name,
                    () -> navigateTrack(screen, track, false, name, fromLyrics))).toList(), () -> {
                if (!fromLyrics) return true;
                var playback = CloudMusic.playbackPresentation();
                return !playback.preparing && playback.displayTrack == track;
            });
        }
    }

    public static boolean isDetail(NCMPanel panel) { return panel instanceof PlayingCatalogPanel; }

    private static void navigateTrack(NCMScreen screen, Music track, boolean album, String artist, boolean fromLyrics) {
        String albumName = track.getAlbum() == null ? "" : track.getAlbum().getName();
        String name = album ? albumName : artist;
        if (name == null || name.isBlank()) return;

        String title = Component.translatable("sh_better_ui.playing_metadata." + (album ? "album" : "artist"),
                name.strip()).getString();
        Destination destination = album || sameName(artist, firstArtist(track)) ? destination(track, album) : null;
        var current = screen.getCurrentPanel();
        var known = current instanceof PlayingCatalogPanel panel ? panel.catalogTrack(track) : null;
        Supplier<NCMPanel> load = () -> {
            if (track.isQQ() || track.isNetease()) {
                try {
                    String id = track.isQQ() ? track.getSourceMid() : track.getSourceId();
                    var seed = known != null ? known : id == null || id.isBlank() ? null
                            : OnlineMusicCatalog.track(track.isQQ() ? "qq" : "netease", id);
                    if (seed != null && album && seed.release() != null) return albumPage(seed.release());
                    if (seed != null && !album) {
                        var match = matchingArtist(seed.artists(), artist);
                        if (match != null) return artistPage(match);
                    }
                } catch (Exception error) {
                    if (destination == null) throw unavailable(error);
                }
            }
            List<Music> nativeTracks = destination == null ? List.of() : load(destination);
            boolean ordered = !nativeTracks.isEmpty();
            List<Music> songs = ordered ? nativeTracks : searchByName(track, name, artist, album);
            String heading = ordered ? name : Component.translatable("sh_better_ui.playing_metadata.search", name).getString();
            var playlist = playlist(heading, track.getCoverUrl(300), songs);
            if (ordered) return new PlaylistPanel(playlist, true, true);
            return new PlayingCatalogPanel(playlist, !album, "", "", List.of(), List.of(), List.of(), null, false, false);
        };
        screen.navigateCatalogPage(title, load, !album, fromLyrics, () -> {
            if (!fromLyrics) return true;
            var playback = CloudMusic.playbackPresentation();
            return !playback.preparing && playback.displayTrack == track;
        });
    }

    static void openAlbum(NCMScreen screen, OnlineAlbum album) {
        screen.navigateCatalogPage(album.name(), () -> {
            try { return albumPage(album); }
            catch (Exception error) { throw unavailable(error); }
        }, false, false);
    }

    static void openArtists(NCMScreen screen, List<OnlineArtist> artists, Box anchor) {
        if (artists.size() == 1) openArtist(screen, artists.get(0));
        else if (!artists.isEmpty()) screen.chooseCatalogArtist(anchor, artists.stream().map(artist ->
                artistChoice(artist.name(), () -> openArtist(screen, artist))).toList(), () -> true);
    }

    private static AnchoredActionMenu.Entry artistChoice(String name, Runnable action) {
        return new AnchoredActionMenu.Entry(Component.literal(name), FoliaLucideIcons.MIC, action);
    }

    private static void openArtist(NCMScreen screen, OnlineArtist artist) {
        screen.navigateCatalogPage(artist.name(), () -> {
            try { return artistPage(artist); }
            catch (Exception error) { throw unavailable(error); }
        }, true, false);
    }

    static OnlineArtist matchingArtist(List<OnlineArtist> candidates, String name) {
        return candidates.stream().filter(artist -> sameName(name, artist.name())).findFirst().orElse(null);
    }

    private static NCMPanel artistPage(OnlineArtist artist) throws Exception {
        var tracks = OnlineMusicCatalog.artistTracks(artist, 1);
        OnlineArtistOverview overview;
        try { overview = OnlineMusicCatalog.artistOverview(artist, 1); }
        catch (Exception ignored) { overview = null; }
        String image = overview == null ? "" : overview.artworkUrl();
        String meta = overview != null && overview.followers() > 0
                ? text("detail.followers", java.text.NumberFormat.getIntegerInstance().format(overview.followers()))
                : text("source." + artist.source());
        return new PlayingCatalogPanel(playlist(artist.name(), image, music(tracks.tracks())), true, meta,
                overview == null ? "" : overview.description(), overview == null ? List.of() : overview.albums(),
                List.of(), tracks.tracks(), artist, tracks.mayHaveMore(), overview != null && overview.mayHaveMore());
    }

    private static NCMPanel albumPage(OnlineAlbum album) throws Exception {
        var detail = OnlineMusicCatalog.album(album);
        var release = detail.album();
        return new PlayingCatalogPanel(playlist(release.name(), release.artworkUrl(), music(detail.tracks())), false,
                release.releaseDate(), detail.description(), List.of(), detail.artists(), detail.tracks(), null, false, false);
    }

    private static PlayList playlist(String name, String cover, List<Music> songs) {
        var playlist = new PlayList(0, name, cover, songs.size(), 0, null, "", 0);
        if (!songs.isEmpty()) playlist.setPlatform(songs.get(0).getSource());
        playlist.setSearchMode(true);
        playlist.setMusics(new CopyOnWriteArrayList<>(songs));
        playlist.setMusicsQueried(true);
        playlist.setMusicsLoaded(true);
        return playlist;
    }

    static List<Music> music(List<OnlineTrack> tracks) {
        return tracks.stream().map(track -> {
            boolean qq = track.source().equals("qq");
            return Music.fromMuSourceTrack(new Track(qq ? MusicSource.QQ : MusicSource.NETEASE, track.catalogId(),
                    qq ? track.id() : "", track.title(), track.artist(), track.album(),
                    track.durationMs(), track.artworkUrl(), track.vip()));
        }).toList();
    }

    static String text(String key, Object... parameters) {
        return Component.translatable("sh_better_ui.online_music." + key, parameters).getString();
    }

    private static RuntimeException unavailable(Exception cause) {
        return new IllegalStateException(text("detail.failed"), cause);
    }

    private static boolean sameName(String expected, String actual) {
        return actual != null && expected.strip().equalsIgnoreCase(actual.strip());
    }

    private static String displayName(Music track, boolean album) {
        if (track == null || track.isDjProgram()) return "";
        if (!album) return firstArtist(track);
        return track.getAlbum() == null || track.getAlbum().getName() == null
                ? "" : track.getAlbum().getName().strip();
    }

    private static List<Music> searchByName(Music track, String name, String artist, boolean album) {
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
