// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;
import java.util.Objects;

/** A provider's artist profile and the first releases in publication order. */
public record OnlineArtistOverview(String artworkUrl, long followers, String description,
                                   List<OnlineAlbum> albums, long albumCount, boolean mayHaveMore) {
    public OnlineArtistOverview {
        artworkUrl = Objects.requireNonNullElse(artworkUrl, "");
        description = Objects.requireNonNullElse(description, "");
        albums = List.copyOf(albums);
    }
}
