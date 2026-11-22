// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;

/** Project credentials for the loopback MusicKit companion. Apple verifies the JWT signature. */
final class AppleMusicProjectConfig {
    static final String FILE_NAME = "sh_better_ui-apple-music.properties";
    static final int DEFAULT_PORT = 17832;
    private static final int MAX_FILE_BYTES = 16_384;
    private static final String LOOPBACK = "http://127.0.0.1:";

    final String token;
    final int port;
    private final long expires;
    private final String issue;

    private AppleMusicProjectConfig(String token, int port, long expires, String issue) {
        this.token = token;
        this.port = port;
        this.expires = expires;
        this.issue = issue;
    }

    String issue() {
        if (issue != null) return issue;
        return expires <= Instant.now().getEpochSecond() ? "configuration_expired" : null;
    }

    static AppleMusicProjectConfig load(Path directory, String token, String port) {
        if (directory != null && (token.isEmpty() || port.isEmpty())) {
            try {
                var file = read(directory.resolve(FILE_NAME));
                if (token.isEmpty()) token = file.getProperty("developerToken", "").strip();
                if (port.isEmpty()) port = file.getProperty("loopbackPort", "").strip();
            } catch (IOException | IllegalArgumentException | SecurityException unavailable) {
                if (token.isEmpty()) return invalid("configuration_file_failed");
            }
        }
        if (token.isEmpty()) return invalid("configuration_missing");
        if (token.length() > 8192 || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            return invalid("configuration_invalid");
        }
        try {
            String[] parts = token.split("\\.");
            var header = JsonParser.parseString(decode(parts[0])).getAsJsonObject();
            var claims = JsonParser.parseString(decode(parts[1])).getAsJsonObject();
            if (!"ES256".equals(header.get("alg").getAsString()) || !claims.getAsJsonPrimitive("exp").isNumber()) {
                return invalid("configuration_invalid");
            }
            long expires = claims.get("exp").getAsBigDecimal().longValueExact();
            if (expires <= Instant.now().getEpochSecond()) return invalid("configuration_expired");
            int requested = port.isEmpty() ? -1 : Integer.parseInt(port);
            if (!port.isEmpty() && (requested < 0 || requested > 65535)) return invalid("configuration_invalid");
            if (claims.has("origin")) {
                int matching = -1;
                for (var item : claims.getAsJsonArray("origin")) {
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                        return invalid("configuration_invalid");
                    }
                    int candidate = loopbackPort(item.getAsString());
                    if (matching == -1 && candidate > 0 && (requested == -1 || requested == candidate)) matching = candidate;
                }
                if (matching == -1) return invalid("configuration_origin");
                requested = matching;
            }
            return new AppleMusicProjectConfig(token, requested == -1 ? DEFAULT_PORT : requested, expires, null);
        } catch (RuntimeException invalid) {
            return invalid("configuration_invalid");
        }
    }

    private static int loopbackPort(String origin) {
        if (!origin.startsWith(LOOPBACK)) return -1;
        String port = origin.substring(LOOPBACK.length());
        if (!port.matches("[1-9][0-9]{0,4}")) return -1;
        int number = Integer.parseInt(port);
        return number <= 65535 ? number : -1;
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static Properties read(Path path) throws IOException {
        var properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            byte[] data = input.readNBytes(MAX_FILE_BYTES + 1);
            if (data.length > MAX_FILE_BYTES) throw new IOException("Apple Music configuration is too large");
            properties.load(new StringReader(new String(data, StandardCharsets.UTF_8)));
        } catch (NoSuchFileException missing) {
            return properties;
        }
        return properties;
    }

    private static AppleMusicProjectConfig invalid(String issue) {
        return new AppleMusicProjectConfig("", -1, 0, issue);
    }
}
