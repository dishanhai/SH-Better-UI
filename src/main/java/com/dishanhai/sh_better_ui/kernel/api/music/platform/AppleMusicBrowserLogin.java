// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.platform;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Explicitly opened, loopback-only MusicKit Web authorization companion. Java never receives
 * an Apple password or Music User Token. AUTHORIZED is a UI report, not proof of entitlement
 * or a credential: it must not authorize native API access or claim playback is available.
 * Closing this bridge cannot sign out the browser; use its Disconnect button before closing.
 */
public final class AppleMusicBrowserLogin implements AutoCloseable {
    public enum State { WAITING, AUTHORIZING, AUTHORIZED, SIGNED_OUT, CANCELLED, FAILED, EXPIRED, CLOSED }
    private static final String RESOURCE = "/assets/sh_better_ui/music/apple-login/";
    private final HttpServer server;
    private final ExecutorService worker;
    private final ScheduledExecutorService expiry;
    private final String session;
    private final String origin;
    private volatile String developerToken;
    private volatile State state = State.WAITING;
    private volatile boolean stopped;
    private final AppleMusicRateControl rates = new AppleMusicRateControl();

    /** Starts no browser and does not contact Apple. Supply a short-lived signed developer JWT. */
    public AppleMusicBrowserLogin(String developerToken) throws IOException {
        this(developerToken, Duration.ofMinutes(10), 0);
    }
    /** Fixed configured loopback port for an origin-restricted token; never falls back to another origin. */
    public AppleMusicBrowserLogin(String developerToken, int loopbackPort) throws IOException {
        this(developerToken, Duration.ofMinutes(10), loopbackPort);
    }
    AppleMusicBrowserLogin(String token, Duration lifetime) throws IOException { this(token, lifetime, 0); }
    private AppleMusicBrowserLogin(String token, Duration lifetime, int port) throws IOException {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid loopback port");
        if (token == null || token.length() > 8192 || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("A valid Apple Music developer token is required");
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(30)) > 0)
            throw new IllegalArgumentException("Invalid authorization lifetime");
        developerToken = token;
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        session = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        worker = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "SHBUI-Apple-Music-Authorization");
            thread.setDaemon(true);
            return thread;
        });
        expiry = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "SHBUI-Apple-Music-Expiry");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(worker);
        server.createContext("/", this::handle);
        server.start();
        expiry.schedule(() -> stop(State.EXPIRED), lifetime.toMillis(), TimeUnit.MILLISECONDS);
    }
    /** This one-session fragment is not a platform credential; do not publish or log it. */
    public URI browserUri() { return URI.create(origin + "/#" + session); }
    public State state() { return state; }
    /** Only a recent, ready, authorized browser SDK observation enables rate control. */
    public boolean supportsPlaybackRate() { return !stopped && state == State.AUTHORIZED && rates.supported(System.nanoTime()); }
    /** Last observed SDK rate, not an optimistic requested value. */
    public double playbackRate() { return rates.rate(); }
    public synchronized void playbackRate(double rate) {
        AppleMusicRateControl.validate(rate);
        if (stopped || state != State.AUTHORIZED) throw new UnsupportedOperationException("Apple browser playback rate unavailable");
        rates.request(rate, System.nanoTime());
    }
    public String playbackRateResult() { return rates.result(System.nanoTime()); }
    @Override public void close() { stop(State.CLOSED); }
    private synchronized void stop(State terminal) {
        if (stopped) return;
        stopped = true;
        state = terminal;
        developerToken = "";
        rates.clear();
        server.stop(0);
        worker.shutdownNow();
        expiry.shutdownNow();
    }
    private void handle(HttpExchange exchange) throws IOException {
        try {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!URI.create(origin).getAuthority().equals(host)) { reply(exchange, 403, "text/plain", "Forbidden"); return; }
            String path = exchange.getRequestURI().getPath();
            if (exchange.getRequestURI().getRawQuery() != null) { reply(exchange, 400, "text/plain", "No query accepted"); return; }
            if (path.equals("/config") || path.equals("/state") || path.equals("/status") || path.equals("/playback-rate")) {
                if (!authorized(exchange)) { reply(exchange, 403, "text/plain", "Forbidden"); return; }
                if (path.equals("/playback-rate")) { rateRequest(exchange); return; }
                if (path.equals("/config") && exchange.getRequestMethod().equals("GET")) {
                    var config = new JsonObject();
                    config.addProperty("developerToken", developerToken);
                    reply(exchange, 200, "application/json", config.toString());
                } else if (path.equals("/status") && exchange.getRequestMethod().equals("GET")) {
                    reply(exchange, 200, "application/json", "{}");
                } else if (path.equals("/state") && exchange.getRequestMethod().equals("POST")) {
                    if (!origin.equals(exchange.getRequestHeaders().getFirst("Origin"))) { reply(exchange, 403, "text/plain", "Forbidden"); return; }
                    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) { reply(exchange, 415, "text/plain", "JSON required"); return; }
                    byte[] body = exchange.getRequestBody().readNBytes(257);
                    if (body.length > 256) { reply(exchange, 413, "text/plain", "Too large"); return; }
                    State next;
                    try {
                        var json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
                        if (json.size() != 1 || !json.has("state")) throw new IllegalArgumentException();
                        next = State.valueOf(json.get("state").getAsString());
                        if (next == State.WAITING || next == State.EXPIRED || next == State.CLOSED) throw new IllegalArgumentException();
                    } catch (RuntimeException invalid) { reply(exchange, 400, "text/plain", "Invalid state"); return; }
                    synchronized (this) { if (!stopped) { state = next; if (next != State.AUTHORIZED) rates.clear(); } }
                    reply(exchange, 200, "application/json", "{}");
                } else reply(exchange, 405, "text/plain", "Method not allowed");
                return;
            }
            if (!exchange.getRequestMethod().equals("GET")) { reply(exchange, 405, "text/plain", "Method not allowed"); return; }
            String name = switch (path) {
                case "/" -> "index.html";
                case "/login.mjs" -> "login.mjs";
                case "/auth-flow.mjs" -> "auth-flow.mjs";
                case "/playback-flow.mjs" -> "playback-flow.mjs";
                case "/rate-bridge.mjs" -> "rate-bridge.mjs";
                default -> null;
            };
            if (name == null) { reply(exchange, 404, "text/plain", "Not found"); return; }
            try (InputStream in = AppleMusicBrowserLogin.class.getResourceAsStream(RESOURCE + name)) {
                if (in == null) { reply(exchange, 500, "text/plain", "Authorization page unavailable"); return; }
                reply(exchange, 200, name.endsWith("html") ? "text/html" : "text/javascript", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally { exchange.close(); }
    }
    private void rateRequest(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET")) {
            var json = new JsonObject();
            var command = rates.command(System.nanoTime());
            if (command != null) { json.addProperty("id", command.id()); json.addProperty("rate", command.rate()); }
            reply(exchange, 200, "application/json", json.toString()); return;
        }
        if (!exchange.getRequestMethod().equals("POST")) { reply(exchange,405,"text/plain","Method not allowed"); return; }
        if (!origin.equals(exchange.getRequestHeaders().getFirst("Origin"))) { reply(exchange,403,"text/plain","Forbidden"); return; }
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) { reply(exchange,415,"text/plain","JSON required"); return; }
        byte[] body = exchange.getRequestBody().readNBytes(257);
        if (body.length > 256) { reply(exchange,413,"text/plain","Too large"); return; }
        try {
            var json = JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.keySet().equals(Set.of("supported","rate","ack","status"))
                    || !json.getAsJsonPrimitive("supported").isBoolean()
                    || !json.getAsJsonPrimitive("rate").isNumber()
                    || !json.getAsJsonPrimitive("ack").isNumber()
                    || !json.getAsJsonPrimitive("status").isString()) throw new IllegalArgumentException();
            long ack = json.get("ack").getAsBigDecimal().longValueExact();
            synchronized (this) {
                if (!stopped && state == State.AUTHORIZED) rates.observe(json.get("supported").getAsBoolean(), json.get("rate").getAsDouble(),
                        ack, json.get("status").getAsString(), System.nanoTime());
            }
            reply(exchange,200,"application/json","{}");
        } catch (RuntimeException invalid) { reply(exchange,400,"text/plain","Invalid rate observation"); }
    }
    private boolean authorized(HttpExchange exchange) {
        String supplied = exchange.getRequestHeaders().getFirst("X-SHBUI-Session");
        if (supplied == null || supplied.length() != session.length()) return false;
        String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
        return !stopped && (requestOrigin == null || requestOrigin.equals(origin))
                && MessageDigest.isEqual(session.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    }
    private static void reply(HttpExchange exchange, int status, String type, String text) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type + "; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.set("X-Frame-Options", "DENY");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
