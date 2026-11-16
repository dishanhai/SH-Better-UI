// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.Objects;

/** Provider-owned playlist identity and display metadata. */
public record OnlinePlaylist(String source, String id, String name, int trackCount) {
    public OnlinePlaylist {
        Objects.requireNonNull(source);
        Objects.requireNonNull(id);
        name = name == null ? "" : name;
        trackCount = Math.max(0, trackCount);
    }
}
