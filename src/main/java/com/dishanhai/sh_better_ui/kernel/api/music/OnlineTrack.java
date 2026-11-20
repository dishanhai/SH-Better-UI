// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;
import java.util.Objects;

/** Immutable search result. IDs belong to the source; never match tracks by title alone. */
public record OnlineTrack(String source, String id, String mediaId, String title,
                          String artist, String album, long durationMs, String artworkUrl, List<OnlineArtist> artists) {
    /** Compatibility constructor for tracks without provider artist identities. */
    public OnlineTrack(String source, String id, String mediaId, String title, String artist, String album,
                       long durationMs, String artworkUrl) {
        this(source, id, mediaId, title, artist, album, durationMs, artworkUrl, List.of());
    }
    /** Compatibility constructor for sources without artwork. */
    public OnlineTrack(String source, String id, String mediaId, String title, String artist, String album, long durationMs) {
        this(source, id, mediaId, title, artist, album, durationMs, "");
    }
    public OnlineTrack {
        Objects.requireNonNull(source);
        Objects.requireNonNull(id);
        mediaId = mediaId == null ? id : mediaId;
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        album = album == null ? "" : album;
        durationMs = Math.max(0, durationMs);
        artworkUrl = artworkUrl == null ? "" : artworkUrl;
        artists = List.copyOf(artists);
        for (OnlineArtist contributor : artists)
            if (!source.equals(contributor.source())) throw new IllegalArgumentException("Artist source does not match track");
    }
}
