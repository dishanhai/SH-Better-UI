package com.dishanhai.sh_better_ui.kernel.internal;

import com.muoniumplayer.core.ncm.music.MusicPlatform;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.ncm.music.dto.PlayList;
import com.muoniumplayer.core.rendering.TextureManager;
import com.muoniumplayer.core.rendering.texture.Textures;
import com.muoniumplayer.core.utils.Location;

import java.util.List;

/** Resolves the artwork shared by the playlist header and the playlist grid. */
public final class PlaylistArtwork {
    private final Location playlistCover;
    private Location trackCover;

    public PlaylistArtwork(PlayList playlist) {
        playlistCover = playlist.getCoverLocation();
        Textures.downloadTextureAndLoadAsync(requestUrl(playlist), playlistCover);
    }

    public static String requestUrl(PlayList playlist) {
        String url = secureCoverUrl(playlist.getCoverUrl());
        if (!url.isEmpty() && playlist.getPlatform() == MusicPlatform.NETEASE
                && !playlist.isSearchMode() && !url.contains("?") && !url.contains("#")) {
            return url + "?param=256y256";
        }
        return url;
    }

    public void requestTrackFallback(List<Music> tracks) {
        if (trackCover != null || TextureManager.getInstance().getTexture(playlistCover) != null) return;
        for (Music track : tracks) {
            if (track == null) continue;
            String url = secureCoverUrl(track.getCoverUrl(256));
            if (url.isEmpty()) continue;
            trackCover = Location.of("muonium/textures/music/" + track.getStableKey() + "/playlist_cover.png");
            Textures.downloadTextureAndLoadAsync(url, trackCover);
            return;
        }
    }

    public Location loadedLocation() {
        TextureManager textures = TextureManager.getInstance();
        if (textures.getTexture(playlistCover) != null) return playlistCover;
        return trackCover != null && textures.getTexture(trackCover) != null ? trackCover : null;
    }

    private static String secureCoverUrl(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http://")) return "https://" + url.substring(7);
        return url.startsWith("https://") ? url : "";
    }
}
