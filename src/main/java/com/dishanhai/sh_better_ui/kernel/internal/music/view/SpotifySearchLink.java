// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Public Spotify Web Player handoff, not a Spotify Platform client. */
final class SpotifySearchLink {
    private static final String SEARCH = "https://open.spotify.com/search";

    private static final String MADE_FOR_YOU = "https://open.spotify.com/genre/0JQ5DAt0tbjZptfcdMSKl3";

    private SpotifySearchLink() {}

    static String madeForYou() { return MADE_FOR_YOU; }

    static String forQuery(String text) {
        String query = text.strip();
        // Browsers normalize dot-only path segments, even when percent-encoded.
        if (query.isEmpty() || query.equals(".") || query.equals("..")) return SEARCH;
        return SEARCH + "/" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
