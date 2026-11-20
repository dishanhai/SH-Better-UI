// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.Objects;

/** A verified provider artist ID and display name. Names alone do not identify artists. */
public record OnlineArtist(String source, String id, String name) {
    public OnlineArtist {
        Objects.requireNonNull(source);
        Objects.requireNonNull(id);
        name = Objects.requireNonNull(name).strip();
        if (source.isBlank() || id.isBlank() || id.length() > 80 || name.isBlank() || name.length() > 200)
            throw new IllegalArgumentException("Invalid music artist");
    }
}
