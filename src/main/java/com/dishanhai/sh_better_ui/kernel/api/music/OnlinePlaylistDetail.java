// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;
import java.util.Objects;

/** One complete, provider-ordered playlist snapshot. Identical songs may occupy multiple positions. */
public record OnlinePlaylistDetail(OnlinePlaylist playlist, List<OnlineTrack> tracks) {
    public static final int MAX_TRACKS = 1000;

    public OnlinePlaylistDetail {
        Objects.requireNonNull(playlist);
        tracks = List.copyOf(tracks);
        if (tracks.size() > MAX_TRACKS || tracks.size() != playlist.trackCount()
                || tracks.stream().anyMatch(track -> !playlist.source().equals(track.source()))) {
            throw new IllegalArgumentException("Incomplete playlist detail");
        }
    }
}
