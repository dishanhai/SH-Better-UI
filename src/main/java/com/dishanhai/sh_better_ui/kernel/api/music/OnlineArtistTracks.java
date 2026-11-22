// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;

/** Ordered artist results. A search-backed page can be empty even when more pages exist. */
public record OnlineArtistTracks(List<OnlineTrack> tracks, boolean mayHaveMore, long total) {
    public OnlineArtistTracks(List<OnlineTrack> tracks, boolean mayHaveMore) { this(tracks, mayHaveMore, -1); }
    public OnlineArtistTracks { tracks = List.copyOf(tracks); }
}
