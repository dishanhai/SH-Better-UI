// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ChatGptLoginTest {
    @Test void browserCallbackBindsStateAndAcceptsAnExplicitDenialWithoutExchangingCredentials() throws Exception {
        String host = "urn:uuid:" + UUID.randomUUID();
        try (var attempt = ChatGptLogin.begin(host, null, "Received", "Declined")) {
            assertEquals("https", attempt.browser.getScheme());
            assertEquals("auth.openai.com", attempt.browser.getHost());
            var params = ChatGptLogin.query(attempt.browser.getRawQuery());
            assertEquals("dynamic_agent_client", params.get("client_id"));
            assertEquals(host, params.get("ext_agent_host_id"));
            assertEquals("S256", params.get("code_challenge_method"));
            assertTrue(params.get("scope").contains("chatgpt.tokens.use.direct"));
            assertEquals(43, params.get("state").length());
            assertNotEquals(params.get("state"), params.get("nonce"));
            assertFalse(params.containsKey("client_secret"));
            assertFalse(params.containsKey("code_verifier"));
            assertEquals(params, ChatGptLogin.query(attempt.copyLink.getRawQuery()));
            URI redirect = URI.create(params.get("redirect_uri"));
            assertEquals("127.0.0.1", redirect.getHost());
            assertEquals("/auth/callback", redirect.getPath());

            var http = HttpClient.newHttpClient();
            assertEquals(400, callback(http, redirect, "state=wrong&error=access_denied"));
            assertEquals(400, callback(http, redirect, "state=" + params.get("state") + "&state=other&error=access_denied"));
            assertFalse(attempt.done());
            assertEquals(400, callback(http, redirect, "state=" + params.get("state") + "&error=access_denied"));
            assertThrows(CompletionException.class, () -> attempt.result.orTimeout(5, TimeUnit.SECONDS).join());
            var declined = MusicChatClient.describe(assertThrows(CompletionException.class, attempt::finish));
            assertEquals("subscription_cancelled", declined.key);
        }
    }

    @Test void cancellingClosesTheLocalListener() throws Exception {
        var attempt = ChatGptLogin.begin("urn:uuid:" + UUID.randomUUID(), null, "Received", "Declined");
        var redirect = URI.create(ChatGptLogin.query(attempt.copyLink.getRawQuery()).get("redirect_uri"));
        attempt.close();
        assertTrue(attempt.done());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            try (var socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(redirect.getHost(), redirect.getPort()), 100);
            } catch (java.net.ConnectException stopped) {
                return;
            }
            Thread.sleep(10);
        }
        fail("The login listener is still accepting connections after cancellation");
    }

    private static int callback(HttpClient http, URI redirect, String query) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(redirect + "?" + query)).timeout(Duration.ofSeconds(5)).build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test void verifiesTheSignedIdentityAudienceNonceAndExpiry() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = (RSAPublicKey) pair.getPublic();
        var jwks = JsonParser.parseString("""
                {"keys":[{"kty":"RSA","alg":"RS256","kid":"test","n":"%s","e":"%s"}]}
                """.formatted(base64(key.getModulus().toByteArray()), base64(key.getPublicExponent().toByteArray())))
                .getAsJsonObject();
        var claims = JsonParser.parseString("""
                {"iss":"https://auth.openai.com","sub":"test-subject","aud":["oaiapp_demo"],
                 "nonce":"test-nonce","exp":200,"iat":100}
                """).getAsJsonObject();
        String payload = base64("{\"alg\":\"RS256\",\"kid\":\"test\"}") + "." + base64(claims.toString());
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.US_ASCII));
        String token = payload + "." + base64(signature.sign());
        assertEquals("test-subject", ChatGptIdentity.verify(token, jwks, "oaiapp_demo", "test-nonce", 150)
                .get("sub").getAsString());
        assertThrows(Exception.class, () -> ChatGptIdentity.verify(token, jwks, "oaiapp_other", "test-nonce", 150));
        assertThrows(Exception.class, () -> ChatGptIdentity.verify(token, jwks, "oaiapp_demo", "wrong", 150));
        assertThrows(Exception.class, () -> ChatGptIdentity.verify(token, jwks, "oaiapp_demo", "test-nonce", 210));
        JsonObject changed = claims.deepCopy();
        changed.addProperty("sub", "different");
        String tampered = payload.substring(0, payload.indexOf('.') + 1) + base64(changed.toString())
                + token.substring(token.lastIndexOf('.'));
        assertThrows(Exception.class, () -> ChatGptIdentity.verify(tampered, jwks, "oaiapp_demo", "test-nonce", 150));
    }

    private static String base64(String value) { return base64(value.getBytes(StandardCharsets.UTF_8)); }
    private static String base64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
}
