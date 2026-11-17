// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.platform;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AppleMusicCatalogTest {
    private static final String TOKEN = "header.payload.signature";
    private static final String SONG = """
        {"id":"123","type":"songs","attributes":{"name":"Song","artistName":"Artist",
        "albumName":"Album","durationInMillis":123000,
        "url":"https://music.apple.com/us/album/song/100?i=123",
        "artwork":{"url":"https://is1-ssl.mzstatic.com/image/{w}x{h}bb.jpg"}}}
        """;
    private static String response(String data) { return "{\"results\":{\"songs\":{\"data\":[" + data + "]}}}"; }

    @Test void buildsOnlyOfficialEncodedRequestsAndDoesNotFetchAtConstruction() throws Exception {
        var calls = new AtomicInteger();
        var api = new AppleMusicCatalog("TW", () -> TOKEN, (uri, token) -> {
            calls.incrementAndGet();
            assertEquals("https://api.music.apple.com/v1/catalog/tw/search?types=songs&term=A+%26+B&offset=25&limit=25", uri.toString());
            assertEquals(TOKEN, token);
            return response(SONG);
        });
        assertEquals(0, calls.get());
        var page = api.search(" A & B ", 25, 25);
        assertEquals(1, calls.get());
        var song = page.songs().get(0);
        assertEquals("123", song.id());
        assertEquals(123000, song.durationMs());
        assertEquals("https://is1-ssl.mzstatic.com/image/512x512bb.jpg", song.artwork().toString());
        assertFalse(page.hasNext());
        assertThrows(UnsupportedOperationException.class, () -> page.songs().clear());
    }
    @Test void preservesOfficialAttributionAndRejectsUntrustedLinks() throws Exception {
        String bad = SONG.replace("music.apple.com", "music.apple.com.evil.invalid");
        assertTrue(AppleMusicCatalog.parse(response(bad), 25).songs().isEmpty());
        bad = SONG.replace("https://music.apple.com", "http://music.apple.com");
        assertTrue(AppleMusicCatalog.parse(response(bad), 25).songs().isEmpty());
        bad = SONG.replace("is1-ssl.mzstatic.com", "evil.invalid");
        assertNull(AppleMusicCatalog.parse(response(bad), 25).songs().get(0).artwork());
    }
    @Test void rejectsMissingTokensAndHeaderInjectionBeforeTransport() {
        for (String token : new String[]{"", "   ", "secret\r\nCookie: bad", "secret"}) {
            var api = new AppleMusicCatalog("us", () -> token, (uri, value) -> fail("No request allowed"));
            var failure = assertThrows(IOException.class, () -> api.search("test", 0, 25));
            assertFalse(failure.toString().contains("secret"));
            assertNull(failure.getCause());
        }
    }
    @Test void validatesStorefrontQueryAndPagination() {
        assertThrows(IllegalArgumentException.class, () -> new AppleMusicCatalog("../me", () -> TOKEN));
        var api = new AppleMusicCatalog("us", () -> TOKEN, (uri, token) -> fail("No request allowed"));
        assertThrows(IllegalArgumentException.class, () -> api.search(" ", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> api.search("a", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> api.search("a", 0, 26));
        assertThrows(IllegalArgumentException.class, () -> api.search("a", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> api.search("a".repeat(513), 0, 1));
    }
    @Test void refreshesTokenPerRequestWithoutKeepingSnapshot() throws Exception {
        var generation = new AtomicInteger();
        var api = new AppleMusicCatalog("us", () -> "header." + generation.incrementAndGet() + ".signature", (uri, token) -> {
            assertEquals("header." + generation.get() + ".signature", token);
            return "{\"results\":{}}";
        });
        assertTrue(api.search("one", 0, 10).songs().isEmpty());
        api.search("two", 0, 10);
        assertEquals(2, generation.get());
    }
    @Test void capsAndDeduplicatesResultsAndDoesNotReturnNextUrl() throws Exception {
        String json = response(SONG + "," + SONG + "," + SONG.replace("123", "456"));
        assertEquals(2, AppleMusicCatalog.parse(json, 25).songs().size());
        assertEquals(1, AppleMusicCatalog.parse(json, 1).songs().size());
        json = json.replace("\"data\":", "\"next\":\"https://evil.invalid\",\"data\":");
        assertTrue(AppleMusicCatalog.parse(json, 25).hasNext());
    }
    @Test void reportsMalformedAndErrorResponsesRatherThanFalseSuccess() {
        for (String json : new String[]{"not json", "[]", "{}", "{\"errors\":[{}]}", "{\"results\":{\"songs\":{}}}"})
            assertThrows(IOException.class, () -> AppleMusicCatalog.parse(json, 25));
    }
    @Test void ignoresInvalidEntriesAndUnplayableMetadataIsNotAnOnlineTrack() throws Exception {
        String invalid = "null,{}," + SONG.replace("\"songs\"", "\"music-videos\"") + "," + SONG;
        assertEquals(1, AppleMusicCatalog.parse(response(invalid), 25).songs().size());
        assertEquals(7, AppleMusicCatalog.Song.class.getRecordComponents().length);
    }
}
