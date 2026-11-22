// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Fetches the app's MusicKit developer token. The service receives no Apple account information. */
final class AppleMusicManagedToken {
    private static final int MAX_TOKEN_BYTES = 8192;

    static Supplier<CompletableFuture<String>> bundled() {
        try (var input = AppleMusicManagedToken.class.getResourceAsStream("/assets/sh_better_ui/music/token-url.txt")) {
            if (input == null) return null;
            byte[] bytes = input.readNBytes(2049);
            if (bytes.length > 2048) return null;
            URI endpoint = URI.create(new String(bytes, StandardCharsets.UTF_8).strip());
            if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getUserInfo() != null
                    || endpoint.getQuery() != null || endpoint.getFragment() != null) return null;
            return () -> CompletableFuture.supplyAsync(() -> fetch(endpoint)).orTimeout(9, TimeUnit.SECONDS);
        } catch (IOException | IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String fetch(URI endpoint) {
        try {
            var connection = (HttpsURLConnection) endpoint.toURL().openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            try {
                if (connection.getResponseCode() != 200) throw new IOException("Token service unavailable");
                try (var input = connection.getInputStream()) {
                    byte[] bytes = input.readNBytes(MAX_TOKEN_BYTES + 1);
                    if (bytes.length > MAX_TOKEN_BYTES) throw new IOException("Token response too large");
                    return new String(bytes, StandardCharsets.US_ASCII).strip();
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException unavailable) {
            throw new UncheckedIOException(unavailable);
        }
    }

    private AppleMusicManagedToken() {}
}
