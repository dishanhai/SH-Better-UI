// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.Objects;

/** Provider identity and display metadata for a release. */
public record OnlineAlbum(String source, String id, String name, String artworkUrl, String releaseDate) {
    public OnlineAlbum {
        Objects.requireNonNull(source);
        Objects.requireNonNull(id);
        name = Objects.requireNonNull(name).strip();
        artworkUrl = Objects.requireNonNullElse(artworkUrl, "");
        releaseDate = Objects.requireNonNullElse(releaseDate, "");
        if (source.isBlank() || !id.matches("[A-Za-z0-9]{1,80}") || id.equals("0") || name.isBlank())
            throw new IllegalArgumentException("Invalid music album");
    }
}
