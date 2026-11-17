// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.platform;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/**
 * Official Apple Music catalog metadata, deliberately separate from MP3 playback sources.
 * Blocking: invoke on a worker. No request occurs at construction. The caller provides a
 * short-lived developer token; never ship a signing private key in a client mod.
 * This does not grant MusicKit playback, user-library access, lyrics, or account login.
 */
public final class AppleMusicCatalog {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Metadata only. Neither link is an audio stream. */
    public record Song(String id, String title, String artist, String album, long durationMs,
                       URI artwork, URI appleMusicUrl) {}
    public record Page(List<Song> songs, boolean hasNext) {
        public Page { songs = List.copyOf(songs); }
    }
    @FunctionalInterface interface Transport {
        String get(URI uri, String token) throws IOException;
    }
    private final String storefront;
    private final Supplier<String> developerToken;
    private final Transport transport;

    public AppleMusicCatalog(String storefront, Supplier<String> developerToken) {
        this(storefront, developerToken, AppleMusicCatalog::get);
    }
    AppleMusicCatalog(String storefront, Supplier<String> developerToken, Transport transport) {
        Objects.requireNonNull(storefront);
        if (!storefront.matches("[a-zA-Z]{2}")) throw new IllegalArgumentException("Expected a two-letter storefront");
        this.storefront = storefront.toLowerCase(Locale.ROOT);
        this.developerToken = Objects.requireNonNull(developerToken);
        this.transport = Objects.requireNonNull(transport);
    }

    /** Zero-based offset; Apple currently permits at most 25 catalog search results per type. */
    public Page search(String term, int offset, int limit) throws IOException {
        Objects.requireNonNull(term);
        if (offset < 0 || limit < 1 || limit > 25) throw new IllegalArgumentException("Invalid catalog pagination");
        String query = term.strip();
        if (query.isEmpty() || query.length() > 512) throw new IllegalArgumentException("Invalid catalog search term");
        URI uri = URI.create("https://api.music.apple.com/v1/catalog/" + storefront + "/search?types=songs&term="
                + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&offset=" + offset + "&limit=" + limit);
        String token = developerToken.get();
        // Validate without ever including a credential in an error or an exception cause.
        if (token == null || token.isBlank()) throw new IOException("Apple Music developer token required");
        if (token.length() > 8192 || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
            throw new IOException("Invalid Apple Music developer token");
        String json = transport.get(uri, token);
        if (json == null || json.length() > MAX_BYTES) throw new IOException("Invalid Apple Music response");
        return parse(json, limit);
    }

    static Page parse(String json, int limit) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("errors")) throw new IOException("Apple Music rejected catalog request");
            if (!root.has("results") || !root.get("results").isJsonObject())
                throw new IOException("Invalid Apple Music results response");
            JsonObject results = object(root, "results");
            if (!results.has("songs")) return new Page(List.of(), false);
            JsonObject songs = object(results, "songs");
            if (!songs.has("data") || !songs.get("data").isJsonArray()) throw new IOException("Invalid Apple Music songs response");
            var parsed = new ArrayList<Song>();
            var seen = new HashSet<String>();
            for (JsonElement element : songs.getAsJsonArray("data")) {
                if (parsed.size() >= limit) break;
                if (!element.isJsonObject()) continue;
                JsonObject song = element.getAsJsonObject(), attributes = object(song, "attributes");
                String id = text(song, "id");
                if (!"songs".equals(text(song, "type")) || !id.matches("[0-9]{1,24}") || !seen.add(id)) continue;
                String title = text(attributes, "name");
                if (title.isBlank()) continue;
                long duration = 0;
                try { duration = Math.max(0, Long.parseLong(text(attributes, "durationInMillis"))); }
                catch (NumberFormatException ignored) {}
                URI link = trustedUrl(text(attributes, "url"), "music.apple.com");
                if (link == null) continue; // Attribution must survive parsing.
                String art = text(object(attributes, "artwork"), "url").replace("{w}", "512").replace("{h}", "512");
                parsed.add(new Song(id, title, text(attributes, "artistName"), text(attributes, "albumName"), duration,
                        trustedUrl(art, "mzstatic.com"), link));
            }
            // Never follow an API-supplied URL. Callers request the next numeric offset explicitly.
            return new Page(parsed, !text(songs, "next").isBlank());
        } catch (IllegalStateException | JsonParseException | UnsupportedOperationException invalid) {
            throw new IOException("Invalid Apple Music response");
        }
    }
    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static String text(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
    private static URI trustedUrl(String value, String domain) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (host.equalsIgnoreCase(domain) || host.toLowerCase(Locale.ROOT).endsWith("." + domain)) ? uri : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }
    private static String get(URI uri, String token) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        var connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false); // Authorization never follows a redirect.
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Apple Music HTTP status " + status);
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Apple Music response too large");
            try (InputStream in = connection.getInputStream(); var out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    if (System.nanoTime() > deadline) throw new IOException("Apple Music request timed out");
                    if (out.size() + read > MAX_BYTES) throw new IOException("Apple Music response too large");
                    out.write(buffer, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
