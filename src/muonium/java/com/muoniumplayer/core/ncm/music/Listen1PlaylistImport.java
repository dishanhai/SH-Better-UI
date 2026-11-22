// Author: dishanhai
package com.muoniumplayer.core.ncm.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.internal.music.Listen1Backup;
import com.google.gson.JsonObject;
import com.muoniumplayer.core.music.source.MusicSource;
import com.muoniumplayer.core.music.source.Track;
import com.muoniumplayer.core.ncm.kugou.KuGouMusicApi;
import com.muoniumplayer.core.ncm.lxsource.LxSourceService;
import com.muoniumplayer.core.ncm.music.dto.Album;
import com.muoniumplayer.core.ncm.music.dto.Artist;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.ncm.music.dto.PlayList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Adapts Listen 1 identities to the same tracks and playlist references used by the main player. */
public final class Listen1PlaylistImport {
    private static final String SAVED_PREFIX = "listen1:";

    private Listen1PlaylistImport() {}

    public record Result(List<PlayList> playlists, int tracks, int skippedTracks, int skippedFavorites) {
        public Result {
            playlists = List.copyOf(playlists);
        }
    }

    public static Result read(Path backup) throws IOException {
        var library = Listen1Backup.readLibrary(backup);
        var saved = library.saved();
        var playlists = new ArrayList<PlayList>();
        for (var playlist : saved.playlists()) {
            String key = SAVED_PREFIX + playlist.id();
            var tracks = saved.tracks(playlist).stream().map(Listen1PlaylistImport::music).toList();
            PlayList imported = new PlayList(stableId(key), playlist.name(), "", tracks.size(), 0, null, "", 0);
            imported.setPlatformPlaylistId(key);
            imported.musics = new CopyOnWriteArrayList<>(tracks);
            imported.musicsQueried = imported.musicsLoaded = true;
            playlists.add(imported);
        }
        for (var favorite : library.favorites()) {
            MusicPlatform platform = MusicPlatform.valueOf(favorite.source().toUpperCase(Locale.ROOT));
            long id = platform == MusicPlatform.NETEASE ? Long.parseLong(favorite.id())
                    : stableId(favorite.source() + ":" + favorite.id());
            PlayList imported = new PlayList(id, favorite.name(), "", 0, 0, null, "", 0);
            imported.setPlatform(platform);
            imported.setPlatformPlaylistId(favorite.id());
            playlists.add(imported);
        }
        return new Result(playlists, saved.supportedTracks(), saved.skippedTracks(), library.skippedFavorites());
    }

    public static boolean hasSavedTracks(PlayList playlist) {
        return playlist.getPlatformPlaylistId().startsWith(SAVED_PREFIX);
    }

    private static Music music(OnlineTrack track) {
        return switch (track.source()) {
            case "netease", "qq" -> {
                boolean qq = track.source().equals("qq");
                yield Music.fromMuSourceTrack(new Track(qq ? MusicSource.QQ : MusicSource.NETEASE,
                        track.id(), qq ? track.id() : "", track.title(), track.artist(), track.album(), 0, "", false));
            }
            case "kugou" -> {
                var song = new KuGouMusicApi.Track();
                song.hash = track.id();
                song.name = track.title();
                song.artist = track.artist();
                song.album = track.album();
                yield Music.fromKuGouTrack(song);
            }
            case "bilibili" -> bilibiliMusic(track);
            case "kuwo", "migu" -> lxMusic(track);
            default -> throw new IllegalArgumentException("Unknown Listen 1 provider");
        };
    }

    private static Music lxMusic(OnlineTrack track) {
        boolean migu = "migu".equals(track.source());
        var hit = new JsonObject();
        hit.addProperty("name", track.title());
        hit.addProperty("singer", track.artist());
        hit.addProperty("albumName", track.album());
        hit.addProperty("songmid", track.mediaId());
        if (migu) hit.addProperty("copyrightId", track.id());
        return Music.fromLxSearchHit(LxSourceService.SearchHit.of(hit, migu ? "mg" : "kw"));
    }

    private static Music bilibiliMusic(OnlineTrack track) {
        var video = BilibiliMusicService.parseSourceId(track.id());
        if (video.cid == null) {
            return Music.fromBilibiliTrack(new BilibiliMusicService.Track(video.bvid, video.page,
                    track.title(), track.artist(), 0, "", "", ""));
        }
        var artists = List.of(new Artist(stableId(track.artist()), track.artist(), List.of(), List.of()));
        long id = stableId(track.id());
        var album = new Album(id, track.album(), "", List.of());
        var song = new Music(track.title(), track.title(), "", id, artists, List.of(), album, 0, 0, 0, List.of());
        song.setSource(MusicPlatform.BILIBILI);
        song.setSourceId(track.id());
        song.setSourceMid(video.bvid);
        return song;
    }

    private static long stableId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).getLeastSignificantBits();
    }
}
