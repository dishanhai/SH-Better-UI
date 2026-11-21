package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.platform.AppleMusicBrowserLogin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AppleMusicAccountTest {
    private static final String FIXTURE_TOKEN = "header.payload.signature";
    private static final Map<String, String> TEST_CONFIG = Map.of(
            AppleMusicAccount.TOKEN_ENV, FIXTURE_TOKEN, AppleMusicAccount.PORT_ENV, "0");

    @Test void missingOrInvalidProjectConfigurationDoesNotLaunchBrowser() {
        try (var missing = new AppleMusicAccount(key -> null, key -> null, uri -> fail("Unexpected launch"))) {
            assertEquals("configuration_missing", missing.stateKey());
            missing.open();
            assertEquals("configuration_missing", missing.stateKey());
            assertFalse(missing.running());
        }
        try (var invalid = new AppleMusicAccount(Map.of(AppleMusicAccount.PORT_PROPERTY, "65536")::get,
                TEST_CONFIG::get, uri -> fail("Unexpected launch"))) {
            assertEquals("ready", invalid.stateKey());
            invalid.open();
            assertEquals("configuration_invalid", invalid.stateKey());
            assertFalse(invalid.running());
        }
    }

    @Test void openUsesOneLoopbackCompanionAndReportsBrowserAccount() throws Exception {
        var current = new AtomicReference<URI>();
        try (var account = new AppleMusicAccount(k -> null, TEST_CONFIG::get, uri -> assertTrue(current.compareAndSet(null, uri)))) {
            assertEquals("ready", account.stateKey());
            account.toggleConnection(true);
            account.open(); // repeated UI press cannot create a second browser session
            assertTrue(account.running());
            assertEquals("browser_open", account.stateKey());
            URI target = current.get();
            assertEquals("http", target.getScheme());
            assertEquals("127.0.0.1", target.getHost());
            assertNull(target.getQuery());
            assertFalse(target.toString().contains(FIXTURE_TOKEN));
            URI origin = URI.create("http://" + target.getAuthority());
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder(origin.resolve("/state"))
                    .header("X-SHBUI-Session", target.getFragment())
                    .header("Origin", origin.toString()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"state\":\"AUTHORIZED\",\"storefront\":\"tw\"}")).build();
            assertEquals(200, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals("browser_authorized", account.stateKey());
            assertTrue(account.authorized());
            assertEquals("tw", account.storefront());
            account.toggleConnection(false);
            assertFalse(account.running());
            assertFalse(account.authorized());
            assertEquals("", account.storefront());
            assertEquals("ready", account.stateKey()); // local closure never reports browser sign-out
            assertThrows(IOException.class, () -> client.send(request, HttpResponse.BodyHandlers.discarding()));
        }
    }

    @Test void onlyCompleteProjectConfigurationEnablesOpenButRunningSessionCanCloseRegardless() throws Exception {
        var config = new java.util.HashMap<String,String>();
        try (var account = new AppleMusicAccount(k -> null, config::get, uri -> {})) {
            assertFalse(account.primaryAvailable(true));
            config.put(AppleMusicAccount.TOKEN_ENV, FIXTURE_TOKEN);
            assertFalse(account.primaryAvailable(true), "the token alone is not project configuration");
            config.clear();
            config.put(AppleMusicAccount.PORT_ENV, "0");
            assertFalse(account.primaryAvailable(true), "the port alone is not project configuration");
            config.put(AppleMusicAccount.TOKEN_ENV, FIXTURE_TOKEN);
            assertTrue(account.primaryAvailable(true));
            assertFalse(account.primaryAvailable(false));
            account.open();
            assertTrue(account.running());
            config.clear();
            assertTrue(account.primaryAvailable(false), "Close stays available even if configuration or link access was removed");
            account.close();
            assertFalse(account.primaryAvailable(true));
            assertEquals("configuration_missing", account.stateKey());
        }
    }

    @Test void fixedOccupiedPortDoesNotFallBackToAnUnconfiguredOrigin() throws Exception {
        try (var other = new AppleMusicBrowserLogin(FIXTURE_TOKEN)) {
            try (var account = new AppleMusicAccount(Map.of(AppleMusicAccount.PORT_PROPERTY, String.valueOf(other.browserUri().getPort()))::get,
                    TEST_CONFIG::get, uri -> fail("Unexpected browser launch"))) {
                account.open();
                assertFalse(account.running());
                assertEquals("bridge_failed", account.stateKey());
                assertTrue(account.primaryAvailable(true), "fixing a port conflict does not require rebuilding the screen");
            }
        }
    }

    @Test void selectedSongsReuseOneBrowserAndArePresentBeforeLaunch() throws Exception {
        var current = new AtomicReference<URI>();
        var config = new java.util.HashMap<>(TEST_CONFIG);
        try (var account = new AppleMusicAccount(k -> null, config::get, uri -> {
            assertTrue(current.compareAndSet(null, uri));
            try {
                var req = HttpRequest.newBuilder(uri.resolve("/config"))
                        .header("X-SHBUI-Session", uri.getFragment()).GET().build();
                assertTrue(HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString())
                        .body().contains("\"catalogId\":\"123\""));
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        })) {
            assertTrue(account.openSong("123"));
            config.clear();
            assertTrue(account.available());
            assertTrue(account.openSong("456"));
            var uri = current.get();
            var req = HttpRequest.newBuilder(uri.resolve("/status"))
                    .header("X-SHBUI-Session", uri.getFragment()).GET().build();
            var body = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString()).body();
            assertTrue(body.contains("\"catalogId\":\"456\""));
            assertTrue(body.contains("\"selectionRevision\":2"));
        }
    }

    @Test void browserLaunchFailureClosesTheNewLoopbackSession() throws Exception {
        var uri = new AtomicReference<URI>();
        try (var account = new AppleMusicAccount(k -> null, TEST_CONFIG::get, target -> {
            uri.set(target);
            throw new IOException("Default browser unavailable");
        })) {
            account.open();
            assertEquals("browser_failed", account.stateKey());
            assertTrue(account.primaryAvailable(true));
            assertFalse(account.running());
            try (var replacement = new AppleMusicBrowserLogin(FIXTURE_TOKEN, uri.get().getPort())) {
                assertEquals(uri.get().getPort(), replacement.browserUri().getPort());
            }
        }
    }
}
