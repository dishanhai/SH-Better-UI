// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;

/** Catalog reads for the embedded player, which owns async navigation and playback. */
public final class OnlineMusicCatalog {
    private OnlineMusicCatalog() {}

    public static SongEncyclopedia encyclopedia(String source, String id) throws Exception {
        if ("qq".equals(source)) return new QQMusicSource().encyclopedia(id);
        if (!"netease".equals(source)) throw new IllegalArgumentException("Unsupported music catalog source");
        var provider = new NeteaseSource();
        var release = provider.catalogTrack(id).release();
        if (release == null) return new SongEncyclopedia("", "", "", "", "", "", "");
        var detail = provider.albumDetail(release);
        return new SongEncyclopedia("", "", "", "", "", detail.album().releaseDate(), detail.description());
    }

    public static OnlineTrack track(String source, String id) throws Exception {
        return switch (source) {
            case "qq" -> new QQMusicSource().catalogTrack(id);
            case "netease" -> new NeteaseSource().catalogTrack(id);
            default -> throw new IllegalArgumentException("Unsupported music catalog source");
        };
    }

    public static OnlineArtistTracks artistTracks(OnlineArtist artist, int page) throws Exception {
        return source(artist.source()).artistTracks(artist, page, 50);
    }

    public static OnlineArtistOverview artistOverview(OnlineArtist artist, int page) throws Exception {
        var source = source(artist.source());
        return source.supportsArtistOverview() ? source.artistOverview(artist, page, 30) : null;
    }

    public static OnlineAlbumDetail album(OnlineAlbum album) throws Exception {
        return source(album.source()).albumDetail(album);
    }

    private static OnlineMusicSource source(String source) {
        return switch (source) {
            case "qq" -> new QQMusicSource();
            case "netease" -> new NeteaseSource();
            default -> throw new IllegalArgumentException("Unsupported music catalog source");
        };
    }
}
