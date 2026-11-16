package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlinePlaylist;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic transport fixtures only: never logs in, sends credentials or writes to a music service. */
class MusicAccountReadReliabilityTest {
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static QQMusicSource qq(QQMusicSource.Transport transport) {
        var source = new QQMusicSource(new QQFavorites(), transport);
        source.credentials("uin=123; qm_keyst=fixture-not-a-credential"); return source;
    }
    @Test void musicScopedIdentityWinsAndEmptyKeyAliasDoesNotShadowFallback() throws Exception {
        var source = qq((url, body, type, referer, cookie) -> {
            assertTrue(url.contains("userid=456"));
            assertTrue(cookie.contains("uin=456"));
            assertTrue(cookie.contains("qm_keyst=fixture-fallback"));
            assertFalse(cookie.contains("uin=123"));
            return json("{\"code\":0,\"data\":{\"creator\":{\"nick\":\"Fixture user\"}}}");
        });
        source.credentials("uin=123; qqmusic_uin=456; qm_keyst=; qqmusic_key=fixture-fallback");
        assertEquals("456", source.account().id());
    }
    @Test void emptyMusicAliasFallsBackAndSnapshotKeepsOriginalRequestIdentity() throws Exception {
        var seen = new ArrayList<String>();
        var source = qq((url, body, type, referer, cookie) -> {
            seen.add(cookie); return json("{\"code\":0,\"data\":{\"creator\":{\"nick\":\"Test\"}}}");
        });
        source.credentials("qqmusic_uin=; uin=123; qm_keyst=fixture-old");
        var snapshot = source.requestSnapshot();
        source.credentials("qqmusic_uin=456; qm_keyst=fixture-new");
        assertEquals("123", snapshot.account().id()); assertTrue(seen.getFirst().contains("fixture-old"));
        assertEquals("456", source.account().id()); assertTrue(seen.getLast().contains("fixture-new"));
    }
    @Test void rpcOuterAuthenticationRejectionCannotBeMaskedBySuccessfulInnerResult() {
        for (int code : new int[]{1000, 104009, 401}) {
            var source = qq((url, body, type, referer, cookie) -> json("{\"code\":" + code + ",\"req\":{\"code\":0,\"data\":{\"songlist\":[]}}}"));
            assertThrows(OnlineMusicSource.LoginRequired.class, () -> source.liked(1, 20));
        }
    }
    @Test void signatureBusinessAndMalformedErrorsNeverClaimAuthenticationExpired() {
        for (String response : new String[]{
                "{\"code\":2000,\"req\":{\"code\":0,\"data\":{}}}",
                "{\"code\":0,\"req\":{\"code\":2000,\"data\":{}}}",
                "{\"code\":0,\"req\":{\"code\":\"bad\",\"data\":{}}}",
                "{\"req\":{\"code\":0,\"data\":{}}}"}) {
            var source = qq((url, body, type, referer, cookie) -> json(response));
            var error = assertThrows(IOException.class, () -> source.liked(1, 20));
            assertFalse(error instanceof OnlineMusicSource.LoginRequired);
            assertFalse(error.getMessage().contains("fixture-not-a-credential"));
        }
    }
    @Test void subscribedPlaylistExpirationUsesSameAuthenticationClassification() {
        var source = qq((url, body, type, referer, cookie) -> url.contains("musicu.fcg")
                ? json("{\"code\":0,\"req\":{\"code\":0,\"data\":{\"v_playlist\":[]}}}")
                : json("{\"code\":104009}"));
        assertThrows(OnlineMusicSource.LoginRequired.class, () -> source.playlists(1, 20));
    }
    @Test void malformedProfileIsNotAValidAccountOrAnAuthenticationVerdict() {
        var source = qq((url, body, type, referer, cookie) -> json("{\"code\":\"bad\",\"data\":{\"creator\":{\"nick\":\"Fixture\"}}}"));
        var failure = assertThrows(IOException.class, source::account);
        assertFalse(failure instanceof OnlineMusicSource.LoginRequired);
    }
    private static NeteaseSource netease() {
        var source = new NeteaseSource((url, body, type, referer, cookie) -> {
            assertTrue(cookie.contains("MUSIC_U=fixture-only"));
            if (url.contains("account/get")) return json("{\"code\":200,\"account\":{\"id\":123},\"profile\":{\"nickname\":\"Fixture\"}}");
            if (url.contains("/song/like/get")) return json("{\"code\":200,\"ids\":[1,{},2,3]}");
            if (url.contains("/playlist/detail")) return json("{\"code\":200,\"playlist\":{\"trackIds\":[{\"id\":1},{}, {\"id\":2},{\"id\":3}]}}");
            if (url.contains("/song/detail")) return json("{\"code\":200,\"songs\":[{\"id\":1,\"name\":\"One\"},{\"id\":2,\"name\":\"Two\"},{\"id\":3,\"name\":\"Three\"}]}");
            if (url.contains("/user/playlist")) return json("{\"code\":200,\"playlist\":[{\"id\":1,\"name\":\"List\",\"trackCount\":-1}]}");
            throw new AssertionError("Unexpected fixture request");
        });
        source.credentials("MUSIC_U=fixture-only"); return source;
    }
    @Test void malformedLikedIdsDoNotStealItemsFromFollowingPage() throws Exception {
        var source = netease();
        assertEquals(java.util.List.of("1"), source.liked(1, 2).stream().map(t -> t.id()).toList());
        // Real detail response contains only requested IDs; this fixture deliberately includes extras.
        // The source must ignore unrelated songs while preserving the requested order.
        assertEquals(java.util.List.of("2", "3"), source.liked(2, 2).stream().map(t -> t.id()).toList());
    }
    @Test void malformedPlaylistIdsRespectPageBoundaryAndNegativeCountsAreSafe() throws Exception {
        var source = netease(); var playlist = new OnlinePlaylist("netease", "1", "Fixture", 4);
        assertEquals(java.util.List.of("1"), source.playlist(playlist, 1, 2).stream().map(t -> t.id()).toList());
        assertEquals(0, source.playlists(1, 20).getFirst().trackCount());
    }
}
