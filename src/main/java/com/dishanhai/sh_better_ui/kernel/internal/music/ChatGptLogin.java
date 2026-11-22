// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonObject;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.dishanhai.sh_better_ui.kernel.internal.music.MusicChatClient.text;

/** Public-client Sign in with ChatGPT for the locally installed music player. */
final class ChatGptLogin {
    private static final String DYNAMIC_CLIENT = "dynamic_agent_client";
    private static final String CALLBACK = "/auth/callback";
    private static final String PLAN_SCOPE = "chatgpt.tokens.use.direct";
    private static final String SCOPES = "openid profile email offline_access resource.invoke " + PLAN_SCOPE;
    private static final URI AUTHORIZE = URI.create(ChatGptIdentity.ISSUER + "/api/accounts/authorize");
    private static final URI TOKEN = URI.create(ChatGptIdentity.ISSUER + "/api/accounts/oauth/token");
    private static final URI REVOKE = URI.create(ChatGptIdentity.ISSUER + "/api/accounts/oauth/revoke");
    private static final URI JWKS = URI.create(ChatGptIdentity.ISSUER + "/.well-known/jwks.json");
    static final URI USAGE = URI.create("https://chatgpt.com/settings/usage");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final MusicCredentialStore HOST_STORE = store("chatgpt-host", 1024);
    private static final MusicCredentialStore ACCOUNTS = store("chatgpt", 32_768);
    private static final Set<String> DEAD_REFRESH = Set.of("invalid_grant", "invalid_refresh_token", "token_expired",
            "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused");

    record Account(String id, String email, boolean authorized) {
        String label() {
            String suffix = id.substring(Math.max(0, id.length() - 8));
            return email.isBlank() ? suffix : email + " (" + suffix + ")";
        }
    }

    private record Grant(String client, String subject, String email, String identity, String access, String refresh,
                         String scopes, long expires, long refreshAfter) {
        boolean allowed() {
            var granted = Arrays.asList(scopes.split("\\s+"));
            return granted.contains("resource.invoke") && granted.contains(PLAN_SCOPE) && !access.isEmpty();
        }

        Account account() { return new Account(client, email, allowed()); }
        Grant signedOut() { return new Grant(client, subject, email, "", "", "", "", 0, 0); }
        @Override public String toString() { return "ChatGptGrant[redacted]"; }
    }

    private record Callback(String client, String code) {
        @Override public String toString() { return "ChatGptCallback[redacted]"; }
    }

    static final class Attempt implements AutoCloseable {
        final URI browser;
        final URI copyLink;
        final CompletableFuture<Grant> result;

        Attempt(URI browser, URI copyLink, CompletableFuture<Grant> result) {
            this.browser = browser;
            this.copyLink = copyLink;
            this.result = result;
        }

        Account finish() throws IOException {
            Grant grant = result.join();
            var store = requireStore(ACCOUNTS);
            String file = file(grant.client);
            return store.locked(file, () -> {
                Grant previous = load(grant.client);
                if (previous != null && !previous.subject.equals(grant.subject)) throw failure("subscription_login_failed");
                store.put(file, encode(grant));
                return grant.account();
            });
        }

        boolean done() { return result.isDone(); }
        @Override public void close() { result.cancel(true); }
    }

    static Attempt start(String selected, String receivedMessage, String declinedMessage) {
        String host;
        Grant previous;
        try {
            host = hostId();
            previous = selected.isEmpty() ? null : load(selected);
        } catch (IOException | SecurityException error) {
            throw failure("storage_error");
        }
        if (!selected.isEmpty() && previous == null) throw failure("subscription_sign_in_required");
        try {
            return begin(host, previous, receivedMessage, declinedMessage);
        } catch (IOException | SecurityException | LinkageError error) {
            throw failure("subscription_callback_error");
        }
    }

    /** Minecraft configures AWT as headless even in a windowed client. */
    static boolean openBrowser(URI uri) {
        try {
            if (Platform.isWindows()) {
                return Shell32.INSTANCE.ShellExecute(null, "open", uri.toASCIIString(), null, null,
                        WinUser.SW_SHOWNORMAL).longValue() > 32;
            }
            String launcher = Platform.isMac() ? "/usr/bin/open" : "xdg-open";
            new ProcessBuilder(launcher, uri.toASCIIString()).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return true;
        } catch (IOException | RuntimeException | LinkageError error) {
            return false;
        }
    }

    static Attempt begin(String host, Grant previous, String receivedMessage, String declinedMessage) throws IOException {
        String state = randomToken();
        String nonce = randomToken();
        String verifier = randomToken();
        String client = previous == null ? DYNAMIC_CLIENT : previous.client;
        var callback = new CompletableFuture<Callback>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String redirect = "http://127.0.0.1:" + server.getAddress().getPort() + CALLBACK;
        server.createContext(CALLBACK, exchange ->
                receive(exchange, state, client, callback, receivedMessage, declinedMessage));

        var query = new LinkedHashMap<>(Map.of("client_id", client, "ext_agent_host_id", host,
                "response_type", "code", "redirect_uri", redirect, "scope", SCOPES,
                "resource", MusicChatConfig.DEFAULT_URL, "state", state, "nonce", nonce,
                "code_challenge_method", "S256", "code_challenge", base64(sha256(verifier))));
        if (previous == null) {
            query.put("agent_name_hint", "SH-Better-UI");
        } else if (!previous.allowed() && !previous.identity.isEmpty()) {
            query.put("prompt", "consent");
        }
        URI copyLink = URI.create(AUTHORIZE + "?" + form(query));
        if (previous != null) {
            if (!previous.identity.isEmpty()) query.put("id_token_hint", previous.identity);
            if (!previous.email.isEmpty()) query.put("login_hint", previous.email);
        }
        URI browser = URI.create(AUTHORIZE + "?" + form(query));
        server.start();
        var result = callback.orTimeout(3, TimeUnit.MINUTES)
                .thenCompose(returned -> exchange(returned, redirect, verifier, nonce, previous));
        callback.whenCompleteAsync((ignored, error) -> server.stop(0));
        result.whenComplete((ignored, error) -> callback.cancel(true));
        return new Attempt(browser, copyLink, result);
    }

    private static void receive(HttpExchange exchange, String expectedState, String client,
                                CompletableFuture<Callback> callback, String received, String declined) throws IOException {
        try (exchange) {
            String query = exchange.getRequestURI().getRawQuery();
            if (!"GET".equals(exchange.getRequestMethod()) || !CALLBACK.equals(exchange.getRequestURI().getRawPath())
                    || !("127.0.0.1:" + exchange.getLocalAddress().getPort()).equals(exchange.getRequestHeaders().getFirst("Host"))
                    || query == null || query.length() > 8192 || callback.isDone()) {
                browserReply(exchange, 400, declined);
                return;
            }
            Map<String, String> values;
            try {
                values = query(query);
            } catch (IllegalArgumentException ignored) {
                browserReply(exchange, 400, declined);
                return;
            }
            String state = values.getOrDefault("state", "");
            if (!MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
                browserReply(exchange, 400, declined);
                return;
            }
            String code = values.getOrDefault("code", "");
            String issued = values.getOrDefault("client_id", client);
            if (values.containsKey("error") || !validClient(issued) || code.isBlank()
                    || !DYNAMIC_CLIENT.equals(client) && !client.equals(issued)) {
                try { browserReply(exchange, 400, declined); }
                finally {
                    callback.completeExceptionally(failure("access_denied".equals(values.get("error"))
                            ? "subscription_cancelled" : "subscription_login_failed"));
                }
                return;
            }
            try { browserReply(exchange, 200, received); }
            finally { callback.complete(new Callback(issued, code)); }
        }
    }

    private static void browserReply(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }

    private static CompletableFuture<Grant> exchange(Callback callback, String redirect, String verifier,
                                                     String nonce, Grant previous) {
        return MusicChatClient.send(post(TOKEN, Map.of("grant_type", "authorization_code", "client_id", callback.client,
                        "code", callback.code, "code_verifier", verifier, "redirect_uri", redirect,
                        "resource", MusicChatConfig.DEFAULT_URL)))
                .thenApply(response -> oauthBody(response.statusCode(), response.body(), "subscription_login_failed"))
                .thenCompose(body -> MusicChatClient.send(MusicChatClient.request(JWKS).GET().build())
                        .thenApply(keys -> {
                            String identity = token(body, "id_token", false);
                            try {
                                var claims = ChatGptIdentity.verify(identity,
                                        oauthBody(keys.statusCode(), keys.body(), "subscription_login_failed"),
                                        callback.client, nonce, now());
                                String subject = text(claims.get("sub"));
                                if (previous != null && !previous.subject.equals(subject)) throw failure("subscription_login_failed");
                                return fromTokens(body, callback.client, subject, safeEmail(text(claims.get("email"))),
                                        identity, "");
                            } catch (GeneralSecurityException | IllegalArgumentException ignored) {
                                throw failure("subscription_login_failed");
                            }
                        }));
    }

    static List<Account> accounts() throws IOException {
        if (ACCOUNTS == null) return List.of();
        var result = new ArrayList<Account>();
        for (String saved : ACCOUNTS.load().values()) result.add(decode(saved).account());
        result.sort(Comparator.comparing(Account::label).thenComparing(Account::id));
        return List.copyOf(result);
    }

    static CompletableFuture<String> access(String client) {
        return withGrant(client, grant -> {
            if (grant == null) throw failure("subscription_sign_in_required");
            if (!grant.allowed()) throw failure(grant.identity.isEmpty()
                    ? "subscription_sign_in_required" : "subscription_permission");
            long now = now();
            if (grant.expires > now && (grant.expires - now > 45 || grant.refreshAfter > now || grant.refresh.isEmpty())) {
                return grant.access;
            }
            if (grant.refresh.isEmpty()) throw failure("subscription_sign_in_required");
            var response = MusicChatClient.send(post(TOKEN, Map.of("grant_type", "refresh_token", "client_id", client,
                    "refresh_token", grant.refresh, "resource", MusicChatConfig.DEFAULT_URL))).join();
            if (response.statusCode() != 200 && DEAD_REFRESH.contains(oauthCode(response.body()))) {
                ACCOUNTS.put(file(client), encode(grant.signedOut()));
                throw failure("subscription_sign_in_required");
            }
            Grant refreshed = fromTokens(oauthBody(response.statusCode(), response.body(), "subscription_refresh_failed"),
                    client, grant.subject, grant.email, grant.identity, grant.scopes);
            ACCOUNTS.put(file(client), encode(refreshed));
            if (!refreshed.allowed()) throw failure("subscription_permission");
            return refreshed.access;
        });
    }

    static CompletableFuture<Boolean> signOut(String client) {
        return withGrant(client, grant -> {
            if (grant == null) return true;
            boolean revoked = grant.refresh.isEmpty();
            for (int attempt = 0; !revoked && attempt < 2; attempt++) {
                if (attempt > 0) CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)).join();
                try {
                    int status = MusicChatClient.send(post(REVOKE, Map.of("token", grant.refresh,
                            "token_type_hint", "refresh_token", "client_id", client))).join().statusCode();
                    revoked = status == 200;
                    if (status < 500) break;
                } catch (java.util.concurrent.CompletionException ignored) {
                    // Clear the local session even when remote revocation cannot be confirmed.
                }
            }
            ACCOUNTS.put(file(client), encode(grant.signedOut()));
            return revoked;
        });
    }

    private interface AccountUpdate<T> { T run(Grant grant) throws IOException; }

    private static <T> CompletableFuture<T> withGrant(String client, AccountUpdate<T> update) {
        return CompletableFuture.supplyAsync(() -> {
            var store = requireStore(ACCOUNTS);
            try {
                return store.locked(file(client), () -> update.run(load(client)));
            } catch (IOException ignored) {
                throw failure("storage_error");
            }
        });
    }

    private static Grant fromTokens(JsonObject body, String client, String subject, String email,
                                    String identity, String previousScopes) {
        String scopes = body.has("scope") ? text(body.get("scope")) : previousScopes;
        if (!Arrays.asList(scopes.split("\\s+")).contains(PLAN_SCOPE)) {
            return new Grant(client, subject, email, identity, "", "", scopes, 0, 0);
        }
        if (!"Bearer".equalsIgnoreCase(text(body.get("token_type")))) throw failure("subscription_login_failed");
        long expiresIn = body.get("expires_in").getAsLong();
        if (expiresIn <= 0 || expiresIn > 86_400) throw failure("subscription_login_failed");
        long now = now();
        String access = token(body, "access_token", false);
        String refresh = token(body, "refresh_token", !Arrays.asList(scopes.split("\\s+")).contains("offline_access"));
        long earliest = 0;
        if (body.has("earliest_refresh_at") && !body.get("earliest_refresh_at").isJsonNull()) {
            try { earliest = body.get("earliest_refresh_at").getAsLong(); }
            catch (NumberFormatException ignored) { /* Some issuers omit a numeric refresh hint. */ }
        }
        return new Grant(client, subject, email, identity, access, refresh, scopes, now + expiresIn, earliest);
    }

    private static Grant load(String client) throws IOException {
        String saved = requireStore(ACCOUNTS).load(file(client));
        if (saved.isEmpty()) return null;
        Grant grant = decode(saved);
        if (!client.equals(grant.client)) throw new IOException("Invalid ChatGPT registration");
        return grant;
    }

    private static Grant decode(String saved) throws IOException {
        try {
            var data = com.google.gson.JsonParser.parseString(saved).getAsJsonObject();
            String client = text(data.get("client_id"));
            String subject = text(data.get("subject"));
            if (!validClient(client) || subject.isBlank()) throw new IllegalArgumentException();
            return new Grant(client, subject, safeEmail(text(data.get("email"))), token(data, "id_token", true),
                    token(data, "access_token", true), token(data, "refresh_token", true), text(data.get("scope")),
                    data.get("expires").getAsLong(), data.get("refresh_after").getAsLong());
        } catch (RuntimeException ignored) {
            throw new IOException("Invalid saved ChatGPT session");
        }
    }

    private static String encode(Grant grant) {
        var data = new JsonObject();
        data.addProperty("client_id", grant.client);
        data.addProperty("subject", grant.subject);
        data.addProperty("email", grant.email);
        data.addProperty("id_token", grant.identity);
        data.addProperty("access_token", grant.access);
        data.addProperty("refresh_token", grant.refresh);
        data.addProperty("scope", grant.scopes);
        data.addProperty("expires", grant.expires);
        data.addProperty("refresh_after", grant.refreshAfter);
        return data.toString();
    }

    private static JsonObject oauthBody(int status, byte[] bytes, String errorKey) {
        if (status != 200) throw new MusicChatClient.Failure(errorKey, status);
        return MusicChatClient.json(bytes);
    }

    private static String oauthCode(byte[] bytes) {
        try { return text(MusicChatClient.json(bytes).get("error")); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static HttpRequest post(URI uri, Map<String, String> fields) {
        return MusicChatClient.request(uri).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields))).build();
    }

    static Map<String, String> query(String query) {
        var result = new HashMap<String, String>();
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) throw new IllegalArgumentException("Invalid OAuth callback");
            String name = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            if (result.putIfAbsent(name, value) != null) throw new IllegalArgumentException("Duplicate OAuth parameter");
        }
        return result;
    }

    private static String form(Map<String, String> values) {
        var encoded = new StringJoiner("&");
        values.forEach((name, value) -> encoded.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return encoded.toString();
    }

    private static String hostId() throws IOException {
        var store = requireStore(HOST_STORE);
        return store.locked("host", () -> {
            String id = store.load("host");
            if (id.isEmpty()) {
                id = "urn:uuid:" + UUID.randomUUID();
                store.put("host", id);
            }
            return id;
        });
    }

    private static MusicCredentialStore store(String name, int limit) {
        var directory = MusicChatConfig.credentialDirectory();
        return directory == null ? null : new MusicCredentialStore(directory.resolve(name), limit);
    }

    private static MusicCredentialStore requireStore(MusicCredentialStore store) {
        if (store == null) throw failure("subscription_storage_required");
        return store;
    }

    static boolean validClient(String value) { return value.matches("oaiapp_[a-zA-Z0-9_-]{1,121}"); }

    private static String file(String client) {
        if (!validClient(client)) throw failure("subscription_login_failed");
        return HexFormat.of().formatHex(sha256(client), 0, 20);
    }

    static boolean validBearer(String value) {
        return !value.isEmpty() && value.length() <= 16_384 && value.chars().allMatch(c -> c >= 33 && c <= 126);
    }

    private static String token(JsonObject body, String field, boolean optional) {
        String value = text(body.get(field));
        if (!(optional && value.isEmpty()) && !validBearer(value)) throw failure("subscription_login_failed");
        return value;
    }

    private static String safeEmail(String email) {
        return email.length() <= 254 && email.chars().noneMatch(Character::isISOControl) ? email : "";
    }

    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return base64(bytes);
    }

    private static String base64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static long now() { return Instant.now().getEpochSecond(); }
    private static MusicChatClient.Failure failure(String key) { return new MusicChatClient.Failure(key, 0); }
}
