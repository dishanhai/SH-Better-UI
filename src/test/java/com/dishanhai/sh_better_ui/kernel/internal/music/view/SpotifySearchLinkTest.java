// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpotifySearchLinkTest {
    @Test void noQueryOpensSpotifySearchWithoutKeywords() {
        assertEquals("https://open.spotify.com/search", SpotifySearchLink.forQuery(" \t\n "));
    }

    @Test void searchesUnicodeAndKeepsLiteralPlusSeparateFromSpace() {
        assertEquals("https://open.spotify.com/search/%E5%91%A8%E6%9D%B0%E4%BC%A6%20%2B%20Bowie",
                SpotifySearchLink.forQuery(" 周杰伦 + Bowie "));
    }

    @Test void inputCannotChangeHostInjectParametersOrAddPathSegments() {
        var url = URI.create(SpotifySearchLink.forQuery("//evil.example/?q=%2f#x"));
        assertEquals("https", url.getScheme());
        assertEquals("open.spotify.com", url.getHost());
        assertNull(url.getUserInfo());
        assertNull(url.getRawQuery());
        assertNull(url.getFragment());
        assertEquals("/search/%2F%2Fevil.example%2F%3Fq%3D%252f%23x", url.getRawPath());
    }

    @Test void browserDotPathNormalizationCannotEscapeSpotifySearch() {
        var base = SpotifySearchLink.forQuery("");
        assertEquals(base, SpotifySearchLink.forQuery("."));
        assertEquals(base, SpotifySearchLink.forQuery(".."));
        assertEquals(base + "/%252e%252e", SpotifySearchLink.forQuery("%2e%2e"));
    }
}
