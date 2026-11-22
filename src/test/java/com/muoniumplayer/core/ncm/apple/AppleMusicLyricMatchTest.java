package com.muoniumplayer.core.ncm.apple;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.muoniumplayer.core.ncm.music.LyricSearchService;
import com.muoniumplayer.core.ncm.music.LyricSearchSource;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.screens.ncm.LyricParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class AppleMusicLyricMatchTest {
    private static final String WORDS = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">\
            <body><div><p begin="1s" end="2s">\
            <span begin="1s" end="1.5s">测试</span><span begin="1.5s" end="2s">主唱</span>\
            <span ttm:role="x-bg"><span begin="1.2s" end="1.7s">同时</span>\
            <span begin="1.7s" end="2.3s">和声</span></span>\
            </p></div></body></tt>
            """;
    private static final String LINE = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="3s" end="4s">测试行</p></div></body></tt>
            """;

    @BeforeEach
    void useFixtures() {
        AppleConfigStore.putForTest("test-user-token", "tw", false, true);
        AppleTokenManager.putForTest("test-web-token", 0);
        AppleApiClient.markAccountValid();
    }

    @AfterEach
    void clearFixtures() {
        AppleApiClient.resetTransportForTest();
        AppleApiClient.markAccountValid();
        AppleTokenManager.clearForTest();
        AppleConfigStore.clearForTest();
    }

    @Test
    void searchesAsGuestAndReadsSelectedTrackInItsOriginalStorefront() {
        AppleApiClient.setTransportForTest(request -> {
            URI uri = URI.create(request.url);
            assertEquals("amp-api.music.apple.com", uri.getHost());
            assertEquals("Bearer test-web-token", request.headers.get("Authorization"));
            if (uri.getPath().endsWith("/search")) {
                assertEquals("/v1/catalog/tw/search", uri.getPath());
                assertFalse(request.headers.containsKey("Media-User-Token"));
                assertTrue(uri.getRawQuery().contains("term=Example+Artist"));
                return ok("""
                        {"results":{"songs":{"data":[
                          {"id":"10","attributes":{"name":"Example","hasLyrics":false}},
                          {"id":"20","attributes":{"name":"Example","artistName":"Artist",
                           "albumName":"Album","durationInMillis":60000,"hasLyrics":true,
                           "artwork":{"url":"https://example.com/{w}x{h}.jpg"}}}
                        ]}}}
                        """);
            }
            assertEquals("/v1/catalog/tw/songs/20", uri.getPath());
            assertTrue(uri.getQuery().contains("include[songs]=lyrics,syllable-lyrics"));
            assertEquals("test-user-token", request.headers.get("Media-User-Token"));
            return ok(relationships(WORDS, LINE));
        });

        AppleMusicService.Track target = new AppleMusicService.Track();
        target.id = "1";
        target.name = "Example";
        target.artist = "Artist";
        Music music = Music.fromAppleTrack(target);
        String keyword = LyricSearchService.defaultQuery(music, LyricSearchSource.APPLE);
        var matches = LyricSearchService.search(LyricSearchSource.APPLE, keyword, music);
        assertEquals(1, matches.size());
        var hit = matches.get(0);
        assertEquals("Album", hit.album);
        assertEquals("https://example.com/600x600.jpg", hit.coverUrl);
        assertEquals("tw", hit.platform);
        var preview = LyricSearchService.fetchLyrics(hit);
        assertTrue(LyricParser.hasWordByWordTiming(preview));
        var first = preview.get(0);
        assertEquals(1000, first.getTimestamp());
        assertEquals(1300, first.duration);
        assertEquals(4, first.words.size());
        assertFalse(first.words.get(1).background);
        assertEquals(1500, first.words.get(1).timestamp);
        assertTrue(first.words.get(2).background);
        assertEquals(1200, first.words.get(2).timestamp);
        assertTrue(first.words.get(3).background);

        var saved = hit.toPin();
        assertEquals(LyricSearchSource.APPLE, LyricSearchSource.fromId(saved.source.id));
        AppleConfigStore.putForTest("test-user-token", "us", false, true);
        assertTrue(LyricParser.hasWordByWordTiming(LyricSearchService.fetchLyrics(saved)));
    }

    @Test
    void fallsBackToLineTimingAndRejectsUntimedTtml() {
        var pin = new com.muoniumplayer.core.ncm.music.LyricManualMemory.Pin(
                LyricSearchSource.APPLE, "20", "tw", "", "", "", 0, "");
        AppleApiClient.setTransportForTest(request -> ok(relationships("", LINE)));
        var lines = LyricSearchService.fetchLyrics(pin);
        assertEquals(1, lines.size());
        assertEquals(3000, lines.get(0).getTimestamp());
        assertFalse(LyricParser.hasWordByWordTiming(lines));

        AppleApiClient.setTransportForTest(request -> ok(relationships("", """
                <tt xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="None">
                  <body><div><p>测试歌词</p></div></body>
                </tt>
                """)));
        assertTrue(LyricSearchService.fetchLyrics(pin).isEmpty());
    }

    @Test
    void skipsUnauthenticatedLyricsAndRecognizesExpiredAccount() {
        AppleConfigStore.putForTest("", "tw", false, true);
        AppleApiClient.setTransportForTest(request -> fail("Guest lyrics must skip the request"));
        assertEquals("", AppleMusicService.ttml("20"));

        AppleConfigStore.putForTest("test-user-token", "tw", false, true);
        AppleApiClient.setTransportForTest(request -> fail("Invalid song ID must skip the request"));
        assertEquals("", AppleMusicService.ttml("20/lyrics"));
        AppleApiClient.setTransportForTest(request -> new AppleApiClient.Response(401, -1, ""));
        assertEquals("", AppleMusicService.ttml("20"));
        assertFalse(AppleMusicService.lyricsAccountAvailable());
    }

    private static AppleApiClient.Response ok(String body) {
        return new AppleApiClient.Response(200, -1, body);
    }

    private static String relationships(String words, String line) {
        return JsonParser.parseString("""
                {"data":[{"relationships":{
                  "syllable-lyrics":{"data":[null,{"attributes":{"ttml":%s}}]},
                  "lyrics":{"data":[{"attributes":{"ttmlLocalizations":%s}}]}
                }}]}
                """.formatted(new JsonPrimitive(words), new JsonPrimitive(line))).toString();
    }
}
