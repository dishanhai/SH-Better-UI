// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;
import java.util.Objects;

/** An album's credits, description and tracks in release order. */
public record OnlineAlbumDetail(OnlineAlbum album, List<OnlineArtist> artists, String description,
                                List<OnlineTrack> tracks) {
    public OnlineAlbumDetail {
        Objects.requireNonNull(album);
        artists = List.copyOf(artists);
        description = Objects.requireNonNullElse(description, "");
        tracks = List.copyOf(tracks);
    }
}
