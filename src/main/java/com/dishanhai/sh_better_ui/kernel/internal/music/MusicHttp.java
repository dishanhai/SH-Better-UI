// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;

/** Bounded requests, finite socket timeouts, manual HTTPS redirects, no global cookies. */
final class MusicHttp {
    static final int JSON_LIMIT = 2 * 1024 * 1024;
    static final int AUDIO_LIMIT = 32 * 1024 * 1024;
    record Response(int status, Map<String, List<String>> headers, byte[] bytes) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> name.equalsIgnoreCase(e.getKey()))
                    .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }
        String text() { return new String(bytes, StandardCharsets.UTF_8); }
        JsonObject json() throws IOException {
            try { return JsonParser.parseString(text()).getAsJsonObject(); }
            catch (RuntimeException failure) { throw new IOException("Invalid login response"); }
        }
        @Override public String toString() { return "Music response [" + status + "]"; }
    }
    static String form(Map<String, ?> values) {
        return values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(String.valueOf(e.getValue())))
                .collect(java.util.stream.Collectors.joining("&"));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    static URI https(String url) {
        URI uri = URI.create(url.startsWith("http://") ? "https://" + url.substring(7) : url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Expected an HTTPS music resource");
        return uri;
    }
    JsonObject json(String url, String body, String type, String referer) throws IOException {
        return json(url, body, type, referer, "");
    }
    static String cookie(String value) {
        if (value == null || value.length() > 8192 || value.getBytes(StandardCharsets.UTF_8).length > 16384
                || value.chars().anyMatch(c -> c < 32 || c == 127))
            throw new IllegalArgumentException("Invalid music credentials");
        return value.strip();
    }
    JsonObject json(String url, String body, String type, String referer, String cookie) throws IOException {
        String text = new String(request(https(url), body, type, referer, JSON_LIMIT, cookie), StandardCharsets.UTF_8).trim();
        if (!text.startsWith("{")) {
            int first = text.indexOf('('), last = text.lastIndexOf(')');
            if (first >= 0 && last > first) text = text.substring(first + 1, last);
        }
        try { return JsonParser.parseString(text).getAsJsonObject(); }
        catch (RuntimeException failure) { throw new IOException("Invalid music response", failure); }
    }
    byte[] audio(URI uri) throws IOException { return request(uri, null, null, null, AUDIO_LIMIT, ""); }
    private byte[] request(URI target, String body, String type, String referer, int limit, String cookie) throws IOException {
        URI uri = https(target.toString());
        for (int hop = 0; hop < 5; hop++) {
            var response = exchange(uri, body, type, referer, limit, cookie);
            if (response.status() >= 300 && response.status() < 400) {
                String location = response.header("Location");
                if (location == null) throw new IOException("Missing redirect target");
                uri = https(uri.resolve(location).toString());
                if (body != null || !cookie.isEmpty()) throw new IOException("Unexpected API redirect");
                continue;
            }
            if (response.status() != 200) throw new IOException("Music HTTP status " + response.status());
            return response.bytes();
        }
        throw new IOException("Too many music redirects");
    }
    /** Single hop: the login transport validates every host and scopes cookies before following redirects. */
    Response exchange(URI target, String body, String type, String referer, int limit, String cookie) throws IOException {
        URI uri = https(target.toString());
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            var connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36");
            if (referer != null) connection.setRequestProperty("Referer", referer);
            if (!cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie(cookie));
            try {
                if (body != null) {
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", type);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(bytes.length);
                    try (var out = connection.getOutputStream()) { out.write(bytes); }
                }
                int status = connection.getResponseCode();
                var headers = new java.util.LinkedHashMap<String, List<String>>();
                connection.getHeaderFields().forEach((key, values) -> { if (key != null) headers.put(key, List.copyOf(values)); });
                if (status >= 300 && status < 400) return new Response(status, headers, new byte[0]);
                if (status != 200) throw new IOException("Music HTTP status " + status);
                if (connection.getContentLengthLong() > limit) throw new IOException("Music resource too large");
                try (InputStream in = connection.getInputStream(); var out = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
                    while ((read = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                        if (System.nanoTime() > deadline) throw new IOException("Music download timed out");
                        if (out.size() + read > limit) throw new IOException("Music resource too large");
                        out.write(buffer, 0, read);
                    }
                    return new Response(status, headers, out.toByteArray());
                }
            } finally { connection.disconnect(); }
    }
}
