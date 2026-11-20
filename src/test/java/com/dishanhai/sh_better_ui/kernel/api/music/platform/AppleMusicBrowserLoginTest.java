// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.platform;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class AppleMusicBrowserLoginTest {
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static URI endpoint(AppleMusicBrowserLogin login, String path) { return login.browserUri().resolve(path); }
    private HttpResponse<String> get(AppleMusicBrowserLogin login, String path, boolean session) throws Exception {
        var builder=HttpRequest.newBuilder(endpoint(login,path)).timeout(Duration.ofSeconds(3));
        if(session) builder.header("X-SHBUI-Session",login.browserUri().getFragment());
        return client.send(builder.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(AppleMusicBrowserLogin login,String origin,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint(login,"/state")).timeout(Duration.ofSeconds(3))
                .header("X-SHBUI-Session",login.browserUri().getFragment()).header("Origin",origin)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private String origin(AppleMusicBrowserLogin login) { return "http://"+login.browserUri().getAuthority(); }
    @Test void servesLocalPageWithoutSecretsAndGatesConfiguration() throws Exception {
        try(var login=new AppleMusicBrowserLogin("header.payload.signature")) {
            assertEquals("127.0.0.1",login.browserUri().getHost());
            var page=get(login,"/",false);
            assertEquals(200,page.statusCode());assertFalse(page.body().contains("header.payload.signature"));
            assertEquals("no-store",page.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(403,get(login,"/config",false).statusCode());
            assertEquals(200,get(login,"/config",true).statusCode());
            assertEquals(403,get(login,"/status",false).statusCode());
            assertEquals("{}",get(login,"/status",true).body());
            assertEquals(404,get(login,"/missing",false).statusCode());
            var playback=get(login,"/playback-flow.mjs",false);
            assertEquals(200,playback.statusCode());
            assertTrue(playback.headers().firstValue("Content-Type").orElseThrow().startsWith("text/javascript"));
            assertTrue(playback.body().contains("createPlaybackFlow"));
            assertFalse(playback.body().contains("header.payload.signature"));
            for (String name : new String[]{ "catalog-flow", "catalog-view" }) {
                var asset=get(login,"/" + name + ".mjs",false);
                assertEquals(200,asset.statusCode());
                assertTrue(asset.headers().firstValue("Content-Type").orElseThrow().startsWith("text/javascript"));
                assertFalse(asset.body().contains("header.payload.signature"));
            }
            assertTrue(page.body().contains("catalog-browse"));
            assertEquals(400,get(login,"/config?token=bad",true).statusCode());
        }
    }
    @Test void acceptsOnlyAuthenticatedSameOriginStateAndNoCredentialPayloads() throws Exception {
        try(var login=new AppleMusicBrowserLogin("header.payload.signature")) {
            assertEquals(403,post(login,"https://evil.invalid","{\"state\":\"AUTHORIZED\"}").statusCode());
            assertEquals(AppleMusicBrowserLogin.State.WAITING,login.state());
            assertEquals(200,post(login,origin(login),"{\"state\":\"AUTHORIZING\"}").statusCode());
            assertEquals(200,post(login,origin(login),"{\"state\":\"AUTHORIZED\"}").statusCode());
            assertEquals(AppleMusicBrowserLogin.State.AUTHORIZED,login.state());
            assertEquals(400,post(login,origin(login),"{\"state\":\"AUTHORIZED\",\"token\":\"secret\"}").statusCode());
            assertEquals(400,post(login,origin(login),"{\"state\":\"CLOSED\"}").statusCode());
            assertEquals(413,post(login,origin(login),"x".repeat(257)).statusCode());
        }
    }
    @Test void selectedSongIsSessionScopedVersionedAndValidated() throws Exception {
        try (var login = new AppleMusicBrowserLogin("header.payload.signature")) {
            assertEquals("{}", get(login, "/status", true).body());
            for (String invalid : new String[]{"", "0", "1/2", "123456789012345678901"})
                assertThrows(IllegalArgumentException.class, () -> login.selectSong(invalid));
            login.selectSong("123");
            assertEquals(403, get(login, "/status", false).statusCode());
            var config = com.google.gson.JsonParser.parseString(get(login, "/config", true).body()).getAsJsonObject();
            assertEquals("123", config.get("catalogId").getAsString());
            assertEquals(1, config.get("selectionRevision").getAsLong());
            login.selectSong("456");
            var status = com.google.gson.JsonParser.parseString(get(login, "/status", true).body()).getAsJsonObject();
            assertEquals("456", status.get("catalogId").getAsString());
            assertEquals(2, status.get("selectionRevision").getAsLong());
            assertFalse(status.has("developerToken"));
            login.close();
            assertThrows(IllegalStateException.class, () -> login.selectSong("789"));
        }
    }
    @Test void closeAndExpiryAreTerminalAndDoNotClaimBrowserSignout() throws Exception {
        var login=new AppleMusicBrowserLogin("header.payload.signature");login.close();login.close();
        assertEquals(AppleMusicBrowserLogin.State.CLOSED,login.state());
        try(var expiring=new AppleMusicBrowserLogin("header.payload.signature",Duration.ofMillis(50))) {
            long end=System.nanoTime()+Duration.ofSeconds(3).toNanos();
            while(expiring.state()!=AppleMusicBrowserLogin.State.EXPIRED && System.nanoTime()<end) Thread.sleep(10);
            assertEquals(AppleMusicBrowserLogin.State.EXPIRED,expiring.state());
        }
    }
    @Test void occupiedConfiguredOriginFailsInsteadOfChangingPort() throws Exception {
        try(var first=new AppleMusicBrowserLogin("header.payload.signature")) {
            assertThrows(java.io.IOException.class,()->new AppleMusicBrowserLogin("header.payload.signature",first.browserUri().getPort()));
        }
    }
    @Test void rejectsBadDeveloperTokensBeforeOpeningServer() {
        assertThrows(IllegalArgumentException.class,()->new AppleMusicBrowserLogin(""));
        assertThrows(IllegalArgumentException.class,()->new AppleMusicBrowserLogin("header.payload.signature\r\nCookie: bad"));
    }
    private HttpResponse<String> ratePost(AppleMusicBrowserLogin login, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint(login,"/playback-rate"))
                .header("X-SHBUI-Session",login.browserUri().getFragment()).header("Origin",origin(login))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void rateCommandIsSessionScopedValidatedAndAcknowledged() throws Exception {
        try(var login=new AppleMusicBrowserLogin("header.payload.signature")) {
            assertFalse(login.supportsPlaybackRate());
            assertEquals(403,get(login,"/playback-rate",false).statusCode());
            assertEquals(200,get(login,"/rate-bridge.mjs",false).statusCode());
            assertEquals(200,ratePost(login,"{\"supported\":true,\"rate\":1,\"ack\":0,\"status\":\"OBSERVED\"}").statusCode());
            assertFalse(login.supportsPlaybackRate(), "browser observation is insufficient before authorization");
            post(login,origin(login),"{\"state\":\"AUTHORIZED\"}");
            assertEquals(400,ratePost(login,"{\"supported\":true,\"rate\":1,\"ack\":0.5,\"status\":\"OBSERVED\"}").statusCode());
            assertEquals(400,ratePost(login,"{\"supported\":true,\"rate\":1,\"ack\":0,\"status\":\"OBSERVED\",\"token\":\"secret\"}").statusCode());
            assertEquals(200,ratePost(login,"{\"supported\":true,\"rate\":1,\"ack\":0,\"status\":\"OBSERVED\"}").statusCode());
            assertTrue(login.supportsPlaybackRate()); login.playbackRate(1.5);
            assertEquals("PENDING",login.playbackRateResult());
            assertTrue(get(login,"/playback-rate",true).body().contains("\"rate\":1.5"));
            assertEquals(200,ratePost(login,"{\"supported\":true,\"rate\":1.5,\"ack\":1,\"status\":\"APPLIED\"}").statusCode());
            assertEquals("APPLIED",login.playbackRateResult()); assertEquals(1.5,login.playbackRate());
            post(login,origin(login),"{\"state\":\"SIGNED_OUT\"}");
            assertFalse(login.supportsPlaybackRate());
        }
    }
}
