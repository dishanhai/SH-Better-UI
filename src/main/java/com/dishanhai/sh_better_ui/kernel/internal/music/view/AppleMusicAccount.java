// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.platform.AppleMusicBrowserLogin;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.function.Function;

/** Owns one browser companion for the lifetime of the visible music screen. No user credentials enter Java. */
final class AppleMusicAccount implements AutoCloseable {
    static final String TOKEN_PROPERTY = "shbui.appleMusic.developerToken", PORT_PROPERTY = "shbui.appleMusic.loopbackPort";
    static final String TOKEN_ENV = "SHBUI_APPLE_MUSIC_DEVELOPER_TOKEN", PORT_ENV = "SHBUI_APPLE_MUSIC_LOOPBACK_PORT";

    @FunctionalInterface interface Browser { void open(URI uri) throws IOException; }
    private final Function<String, String> properties, environment;
    private final Browser browser;
    private AppleMusicBrowserLogin login;
    private String failure;

    AppleMusicAccount() {
        this(System::getProperty, System::getenv, uri -> {
            // Avoid logging the session URL/fragment when the OS cannot open the browser.
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IOException("Default browser unavailable");
            }
            Desktop.getDesktop().browse(uri);
        });
    }

    AppleMusicAccount(Function<String, String> properties, Function<String, String> environment, Browser browser) {
        this.properties = properties;
        this.environment = environment;
        this.browser = browser;
    }

    private String setting(String property, String env) {
        String value = properties.apply(property);
        if (value == null || value.isBlank()) value = environment.apply(env);
        return value == null ? "" : value.trim();
    }

    private String token() { return setting(TOKEN_PROPERTY, TOKEN_ENV); }
    private String port() { return setting(PORT_PROPERTY, PORT_ENV); }

    boolean running() {
        return login != null && login.state() != AppleMusicBrowserLogin.State.EXPIRED
                && login.state() != AppleMusicBrowserLogin.State.CLOSED;
    }

    String stateKey() {
        if (failure != null) return failure;
        if (login != null) return switch (login.state()) {
            case WAITING -> "browser_open";
            case AUTHORIZING -> "authorizing";
            case AUTHORIZED -> "browser_authorized";
            case SIGNED_OUT -> "signed_out";
            case CANCELLED -> "cancelled";
            case FAILED -> "authorization_failed";
            case EXPIRED -> "expired";
            case CLOSED -> "closed";
        };
        return token().isEmpty() || port().isEmpty() ? "configuration_missing" : "ready";
    }

    void open() {
        if (running()) return;
        close();
        String developerToken = token(), loopbackPort = port();
        if (developerToken.isEmpty() || loopbackPort.isEmpty()) return;
        try {
            login = new AppleMusicBrowserLogin(developerToken, Integer.parseInt(loopbackPort));
        } catch (IllegalArgumentException invalid) {
            failure = "configuration_invalid";
            return;
        } catch (IOException unavailable) {
            failure = "bridge_failed";
            return;
        }
        try { browser.open(login.browserUri()); }
        catch (IOException | RuntimeException unavailable) {
            close();
            failure = "browser_failed";
        }
    }

    @Override public void close() {
        if (login != null) login.close();
        login = null;
        failure = null;
    }
}
