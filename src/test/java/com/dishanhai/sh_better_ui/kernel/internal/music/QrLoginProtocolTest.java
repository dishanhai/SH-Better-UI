// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLogin.State.*;

class QrLoginProtocolTest {
    private static MusicHttp.Response response(String body, String... cookies) {
        return new MusicHttp.Response(200, cookies.length == 0 ? Map.of() : Map.of("Set-Cookie", List.of(cookies)), body.getBytes(StandardCharsets.UTF_8));
    }
    static byte[] png() throws IOException {
        var image = new BufferedImage(72, 72, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 72; y++) for (int x = 0; x < 72; x++) image.setRGB(x, y, x < 36 ? 0xffffff : 0);
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return out.toByteArray();
    }
    @Test void neteaseMapsStatesAndRequiresTheAccountCookieOnConfirmation() throws Exception {
        var count = new AtomicInteger();
        var http = new LoginHttp(Set.of("music.163.com"), (uri, body, type, referer, cookie) -> {
            assertEquals("https", uri.getScheme()); assertNotNull(body); assertTrue(body.contains("encSecKey="));
            assertTrue(cookie.contains("os=pc"));
            return switch (count.getAndIncrement()) {
                case 0 -> response("{\"code\":200,\"unikey\":\"fixture_key_12345678\"}");
                case 1 -> response("{\"code\":801}");
                case 2 -> response("{\"code\":802}");
                default -> response("{\"code\":803}", "MUSIC_U=fixture-secret; Domain=.music.163.com; Path=/; Secure; HttpOnly");
            };
        });
        try (var login = new NeteaseQrLogin(http)) {
            assertTrue(login.image().width() > 21);
            assertEquals(WAITING, login.poll().state()); assertEquals(SCANNED, login.poll().state());
            var success = login.poll(); assertEquals(CONFIRMED, success.state());
            assertTrue(success.cookie().contains("MUSIC_U=fixture-secret"));
            assertFalse(success.toString().contains("fixture-secret"));
        }
        assertEquals("", http.value("MUSIC_U"));
        var missing = new LoginHttp(Set.of("music.163.com"), (uri, body, type, referer, cookie) ->
                response(uri.getPath().endsWith("unikey") ? "{\"code\":200,\"unikey\":\"fixture_key_12345678\"}" : "{\"code\":803}"));
        try (var login = new NeteaseQrLogin(missing)) { assertEquals(FAILED, login.poll().state()); }
    }
    @Test void neteaseExpiryAndUnknownStateAreTerminalProtocolResults() throws Exception {
        var count = new AtomicInteger();
        try (var login = new NeteaseQrLogin(new LoginHttp(Set.of("music.163.com"), (uri, body, type, referer, cookie) ->
                response(switch (count.getAndIncrement()) {
                    case 0 -> "{\"code\":200,\"unikey\":\"fixture_key_12345678\"}";
                    case 1 -> "{\"code\":800}";
                    default -> "{\"code\":500}";
                })))) {
            assertEquals(EXPIRED, login.poll().state()); assertEquals(FAILED, login.poll().state());
        }
    }
    @Test void cookiesRespectDomainPathAndAttemptBoundariesAndRedirectHostsAreChecked() throws Exception {
        var headers = new ArrayList<String>();
        var http = new LoginHttp(Set.of("graph.qq.com", "u.y.qq.com"), (uri, body, type, referer, cookie) -> {
            headers.add(cookie);
            return response("{}", "p_skey=private; Domain=graph.qq.com; Path=/oauth2.0; Secure",
                    "bad=public-suffix; Domain=.com; Path=/", "foreign=other; Domain=.163.com; Path=/");
        });
        try (http) {
            http.request("https://graph.qq.com/oauth2.0/login", null, null, null);
            http.request("https://graph.qq.com/oauth2.0/authorize", null, null, null);
            http.request("https://u.y.qq.com/cgi-bin/musicu.fcg", null, null, null);
            http.request("https://graph.qq.com/other", null, null, null);
            assertTrue(headers.get(1).contains("p_skey=private")); assertEquals("", headers.get(2)); assertEquals("", headers.get(3));
            assertEquals("", http.value("bad")); assertEquals("", http.value("foreign"));
            for (String url : List.of("http://graph.qq.com/", "https://graph.qq.com.evil.test/", "https://user@graph.qq.com/", "https://graph.qq.com:444/"))
                assertThrows(IOException.class, () -> http.target(url));
        }
        assertEquals("", http.value("p_skey"));
        assertThrows(IOException.class, () -> http.request("https://graph.qq.com/", null, null, null));
    }
    @Test void qqCompletesTheOAuthExchangeAndReturnsOnlyMusicCredentials() throws Exception {
        var paths = new ArrayList<String>();
        var oauthState = new java.util.concurrent.atomic.AtomicReference<String>();
        var hosts = Set.of("ssl.ptlogin2.qq.com", "ssl.ptlogin2.graph.qq.com", "graph.qq.com", "y.qq.com", "u.y.qq.com");
        var http = new LoginHttp(hosts, (uri, body, type, referer, cookie) -> {
            paths.add(uri.getPath());
            return switch (uri.getPath()) {
                case "/ptqrshow" -> new MusicHttp.Response(200, Map.of("Set-Cookie", List.of("qrsig=fixture-signature; Path=/; Secure")), png());
                case "/ptqrlogin" -> {
                    assertTrue(cookie.contains("qrsig=fixture-signature"));
                    assertTrue(uri.getQuery().contains("ptqrtoken=" + QQQrLogin.hash33("fixture-signature", 0)));
                    yield response("ptuiCB('0','0','https://ssl.ptlogin2.graph.qq.com/check_sig?uin=123456&ptsigx=fixture-sigx','0','ok','user');");
                }
                case "/check_sig" -> response("", "p_skey=fixture-skey; Domain=graph.qq.com; Path=/; Secure");
                case "/oauth2.0/authorize" -> {
                    assertTrue(cookie.contains("p_skey=fixture-skey"));
                    assertTrue(body.contains("g_tk=" + QQQrLogin.hash33("fixture-skey", 5381)));
                    for (String field : body.split("&")) if (field.startsWith("state=")) oauthState.set(field.substring(6));
                    yield new MusicHttp.Response(302, Map.of("Location", List.of("/oauth2.0/continue")), new byte[0]);
                }
                case "/oauth2.0/continue" -> new MusicHttp.Response(302, Map.of("Location", List.of("https://y.qq.com/portal/wx_redirect.html?code=fixture-code&state=" + oauthState.get())), new byte[0]);
                case "/cgi-bin/musicu.fcg" -> {
                    assertFalse(cookie.contains("p_skey")); assertFalse(cookie.contains("qrsig"));
                    assertTrue(body.contains("fixture-code")); assertTrue(body.contains("QQConnectLogin.LoginServer"));
                    yield response("{\"req\":{\"code\":0,\"data\":{\"musicid\":\"987654\",\"musickey\":\"fixture-key\"}}}");
                }
                default -> throw new AssertionError("Unexpected login request");
            };
        });
        try (var login = new QQQrLogin(http)) {
            assertEquals(80, login.image().width());
            var result = login.poll(); assertEquals(CONFIRMED, result.state());
            assertEquals("uin=987654; qm_keyst=fixture-key", result.cookie());
            var source = new QQMusicSource(); assertDoesNotThrow(() -> source.credentials(result.cookie()));
            assertEquals(List.of("/ptqrshow", "/ptqrlogin", "/check_sig", "/oauth2.0/authorize", "/oauth2.0/continue", "/cgi-bin/musicu.fcg"), paths);
        }
    }
    @Test void qqCallbackStatesAndHashMatchTheProtocolAndUntrustedCallbacksCannotAuthorize() throws Exception {
        assertEquals(193485963, QQQrLogin.hash33("abc", 5381));
        assertEquals(108966, QQQrLogin.hash33("abc", 0));
        assertEquals(List.of("67", "escaped'quote"), QQQrLogin.callback("ptuiCB('67','escaped\\'quote');"));
        assertTrue(QQQrLogin.callback("arbitrary('0')").isEmpty());
        var count = new AtomicInteger();
        try (var login = new QQQrLogin(new LoginHttp(Set.of("ssl.ptlogin2.qq.com"), (uri, body, type, referer, cookie) -> {
            if (uri.getPath().equals("/ptqrshow")) return new MusicHttp.Response(200, Map.of("Set-Cookie", List.of("qrsig=fixture; Path=/")), png());
            return response(switch (count.getAndIncrement()) {
                case 0 -> "ptuiCB('66');"; case 1 -> "ptuiCB('67');"; case 2 -> "ptuiCB('65');";
                default -> "ptuiCB('0','0','https://evil.test/?uin=123&ptsigx=secret');";
            });
        }))) {
            assertEquals(WAITING, login.poll().state()); assertEquals(SCANNED, login.poll().state());
            assertEquals(EXPIRED, login.poll().state()); assertThrows(IOException.class, login::poll);
        }
    }
    @Test void qqRejectsForeignOAuthRedirectsAndMismatchedOrMissingStateBeforeCredentialExchange() throws Exception {
        for (String location : List.of("https://evil.test/?code=secret&state=state",
                "https://y.qq.com/portal/wx_redirect.html?code=fixture&state=wrong", "https://y.qq.com/portal/wx_redirect.html?code=fixture")) {
            var hosts = Set.of("ssl.ptlogin2.qq.com", "ssl.ptlogin2.graph.qq.com", "graph.qq.com", "y.qq.com", "u.y.qq.com");
            try (var login = new QQQrLogin(new LoginHttp(hosts, (uri, body, type, referer, cookie) -> switch (uri.getPath()) {
                case "/ptqrshow" -> new MusicHttp.Response(200, Map.of("Set-Cookie", List.of("qrsig=fixture; Path=/")), png());
                case "/ptqrlogin" -> response("ptuiCB('0','0','https://ssl.ptlogin2.graph.qq.com/check_sig?uin=123&ptsigx=fixture');");
                case "/check_sig" -> response("", "p_skey=fixture; Domain=graph.qq.com; Path=/; Secure");
                case "/oauth2.0/authorize" -> new MusicHttp.Response(302, Map.of("Location", List.of(location)), new byte[0]);
                default -> throw new AssertionError("Untrusted OAuth result reached credential exchange");
            }))) { assertThrows(IOException.class, login::poll); }
        }
    }
    @Test void qrImagesHaveQuietZonesAndRejectInvalidOrOversizedImages() throws Exception {
        var image = LoginImages.encode("https://music.163.com/login?codekey=fixture_key");
        for (int i = 0; i < image.width(); i++) { assertFalse(image.dark(i, 0)); assertFalse(image.dark(0, i)); }
        assertTrue(image.dark(4, 4));
        var png = LoginImages.png(png()); assertEquals(80, png.width()); assertFalse(png.dark(4, 4)); assertTrue(png.dark(50, 4));
        assertThrows(IOException.class, () -> LoginImages.png(new byte[20]));
        assertThrows(IOException.class, () -> LoginImages.png(new byte[1024 * 1024]));
        var bits = new BitSet(); bits.set(0); var immutable = new OnlineMusicLogin.Image(21, 21, bits); bits.clear(); assertTrue(immutable.dark(0, 0));
    }
}
