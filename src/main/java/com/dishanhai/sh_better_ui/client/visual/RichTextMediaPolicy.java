// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Explicit client authorization for trusted Java code to request images from exact HTTPS hosts. */
public final class RichTextMediaPolicy {
    private static final Pattern DNS_NAME = Pattern.compile(
            "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private final Set<String> hosts;

    private RichTextMediaPolicy(Set<String> hosts) { this.hosts = hosts; }

    /** Allow 1–16 exact DNS names. Subdomains, address literals and non-HTTPS origins require rejection. */
    public static RichTextMediaPolicy allowHttps(Set<String> exactHosts) {
        Objects.requireNonNull(exactHosts, "exactHosts");
        if (exactHosts.isEmpty() || exactHosts.size() > 16) {
            throw new IllegalArgumentException("Expected between one and sixteen trusted HTTPS hosts");
        }
        var normalized = new HashSet<String>();
        for (var host : exactHosts) {
            String lower = Objects.requireNonNull(host, "host").toLowerCase(Locale.ROOT);
            if (lower.length() > 253 || !DNS_NAME.matcher(lower).matches()) {
                throw new IllegalArgumentException("Expected an exact HTTPS DNS name");
            }
            normalized.add(lower);
        }
        return new RichTextMediaPolicy(Set.copyOf(normalized));
    }

    void validate(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !hosts.contains(host.toLowerCase(Locale.ROOT)) || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null || uri.getPort() != -1 && uri.getPort() != 443
                || uri.toASCIIString().length() > 2048) {
            throw new IllegalArgumentException("Inline image URI is outside its authorized HTTPS origin policy");
        }
    }
}
