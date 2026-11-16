// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.Objects;

/** Immutable search result. IDs belong to the source; never match tracks by title alone. */
public record OnlineTrack(String source, String id, String mediaId, String title,
                          String artist, String album, long durationMs) {
    public OnlineTrack {
        Objects.requireNonNull(source);
        Objects.requireNonNull(id);
        mediaId = mediaId == null ? id : mediaId;
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        album = album == null ? "" : album;
        durationMs = Math.max(0, durationMs);
    }
}
