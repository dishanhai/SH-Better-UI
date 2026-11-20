// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import java.util.List;

/** Ordered provider-search matches, not a ranking or complete discography.
 * A filtered page may have zero entries while the next upstream page may still have matches. */
public record OnlineArtistTracks(List<OnlineTrack> tracks, boolean mayHaveMore) {
    public OnlineArtistTracks { tracks = List.copyOf(tracks); }
}
