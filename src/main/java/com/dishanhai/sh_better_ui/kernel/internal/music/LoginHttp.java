// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import java.io.IOException;
import java.net.*;
import java.util.*;

/** Per-attempt cookie store, exact HTTPS host allowlist, no automatic redirects or global cookie handler. */
final class LoginHttp implements AutoCloseable {
    @FunctionalInterface interface Transport {
        MusicHttp.Response send(URI uri, String body, String type, String referer, String cookie) throws IOException;
    }
    private final Set<String> hosts;
    private record Stored(HttpCookie cookie, String domain, String path, boolean hostOnly) {}
    private final Map<String, Stored> cookies = new LinkedHashMap<>();
    private final Transport transport;
    private volatile boolean closed;
    LoginHttp(Set<String> hosts) {
        this(hosts, (uri, body, type, referer, cookie) -> new MusicHttp().exchange(uri, body, type, referer, MusicHttp.JSON_LIMIT, cookie));
    }
    LoginHttp(Set<String> hosts, Transport transport) { this.hosts = Set.copyOf(hosts); this.transport = transport; }
    void seed(String url, String... values) throws IOException {
        accept(target(url), Map.of("Set-Cookie", List.of(values)));
    }
    URI target(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !hosts.contains(uri.getHost())
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443))
                throw new IOException("Untrusted login target");
            return uri;
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid login target"); }
    }
    MusicHttp.Response request(String url, String body, String type, String referer) throws IOException {
        URI uri = target(url);
        if (closed || Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
        String cookie;
        synchronized (cookies) {
            cookies.values().removeIf(c -> c.cookie().hasExpired());
            cookie = cookies.values().stream().filter(c -> c.hostOnly() ? uri.getHost().equals(c.domain())
                    : uri.getHost().equals(c.domain()) || uri.getHost().endsWith("." + c.domain()))
                    .filter(c -> pathMatches(uri.getPath(), c.path()))
                    .sorted(Comparator.comparingInt((Stored c) -> c.path().length()).reversed())
                    .map(c -> c.cookie().getName() + "=" + c.cookie().getValue())
                    .collect(java.util.stream.Collectors.joining("; "));
        }
        var response = transport.send(uri, body, type, referer, MusicHttp.cookie(cookie));
        accept(uri, response.headers());
        return response;
    }
    private void accept(URI uri, Map<String, List<String>> headers) throws IOException {
        synchronized (cookies) {
            if (closed || Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            // Reject public-suffix and unrelated cookie domains even if a server attempts to set them.
            for (var header : headers.entrySet()) {
                if (!header.getKey().equalsIgnoreCase("Set-Cookie")) continue;
                for (String line : header.getValue()) {
                    try {
                        for (var item : HttpCookie.parse(line)) {
                            boolean hostOnly = item.getDomain() == null;
                            String domain = hostOnly ? uri.getHost() : item.getDomain().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
                            if (!hostOnly) {
                                if (!domain.equals("qq.com") && !domain.endsWith(".qq.com") && !domain.equals("music.163.com")) continue;
                                if (!uri.getHost().equals(domain) && !uri.getHost().endsWith("." + domain)) continue;
                            }
                            if (!item.getName().matches("[A-Za-z0-9_!#$%&'*+.^|~-]{1,128}")) continue;
                            MusicHttp.cookie(item.getName() + "=" + item.getValue());
                            String path = item.getPath();
                            if (path == null || !path.startsWith("/")) {
                                String sourcePath = uri.getPath();
                                int slash = sourcePath.lastIndexOf('/');
                                path = slash <= 0 ? "/" : sourcePath.substring(0, slash);
                            }
                            String key = item.getName() + "\0" + domain + "\0" + path;
                            if (item.hasExpired()) cookies.remove(key);
                            else if (cookies.size() < 128 || cookies.containsKey(key)) cookies.put(key, new Stored(item, domain, path, hostOnly));
                        }
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        }
    }
    private static boolean pathMatches(String request, String cookie) {
        return request.equals(cookie) || (request.startsWith(cookie) && (cookie.endsWith("/") || request.charAt(cookie.length()) == '/'));
    }
    String value(String name) {
        synchronized (cookies) { return cookies.values().stream().map(Stored::cookie).filter(c -> c.getName().equals(name) && !c.hasExpired())
                .map(HttpCookie::getValue).findFirst().orElse(""); }
    }
    String credentials() {
        synchronized (cookies) { return MusicHttp.cookie(cookies.values().stream().map(Stored::cookie).filter(c -> !c.hasExpired())
                .map(c -> c.getName() + "=" + c.getValue()).collect(java.util.stream.Collectors.joining("; "))); }
    }
    @Override public void close() { closed = true; synchronized (cookies) { cookies.clear(); } }
}
