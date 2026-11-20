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

    @Test void openUsesOneLoopbackCompanionAndReportsOnlyBrowserState() throws Exception {
        var current = new AtomicReference<URI>();
        try (var account = new AppleMusicAccount(k -> null, TEST_CONFIG::get, uri -> assertTrue(current.compareAndSet(null, uri)))) {
            assertEquals("ready", account.stateKey());
            account.open();
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
                    .POST(HttpRequest.BodyPublishers.ofString("{\"state\":\"AUTHORIZED\"}")).build();
            assertEquals(200, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals("browser_authorized", account.stateKey());
            account.close();
            assertFalse(account.running());
            assertEquals("ready", account.stateKey()); // local closure never reports browser sign-out
            assertThrows(IOException.class, () -> client.send(request, HttpResponse.BodyHandlers.discarding()));
        }
    }

    @Test void fixedOccupiedPortDoesNotFallBackToAnUnconfiguredOrigin() throws Exception {
        try (var other = new AppleMusicBrowserLogin(FIXTURE_TOKEN)) {
            try (var account = new AppleMusicAccount(Map.of(AppleMusicAccount.PORT_PROPERTY, String.valueOf(other.browserUri().getPort()))::get,
                    TEST_CONFIG::get, uri -> fail("Unexpected browser launch"))) {
                account.open();
                assertFalse(account.running());
                assertEquals("bridge_failed", account.stateKey());
            }
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
            assertFalse(account.running());
            try (var replacement = new AppleMusicBrowserLogin(FIXTURE_TOKEN, uri.get().getPort())) {
                assertEquals(uri.get().getPort(), replacement.browserUri().getPort());
            }
        }
    }
}
