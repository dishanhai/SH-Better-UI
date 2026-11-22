// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.platform.AppleMusicBrowserLogin;
import net.minecraft.client.Minecraft;

import java.awt.Desktop;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owns one browser companion for the lifetime of the visible music screen. No user credentials enter Java. */
public final class AppleMusicAccount implements AutoCloseable {
    static final String TOKEN_PROPERTY = "shbui.appleMusic.developerToken", PORT_PROPERTY = "shbui.appleMusic.loopbackPort";
    static final String TOKEN_ENV = "SHBUI_APPLE_MUSIC_DEVELOPER_TOKEN", PORT_ENV = "SHBUI_APPLE_MUSIC_LOOPBACK_PORT";

    @FunctionalInterface interface Browser { void open(URI uri) throws IOException; }
    private final Supplier<Path> configDirectory;
    private final Function<String, String> properties, environment;
    private final Browser browser;
    private final Supplier<CompletableFuture<String>> managedToken;
    private final Executor ui;
    private CompletableFuture<String> request;
    private AppleMusicProjectConfig configuration;
    private AppleMusicBrowserLogin login;
    private String failure;

    public AppleMusicAccount() {
        this(AppleMusicAccount::instanceConfigDirectory, System::getProperty, System::getenv, uri -> {
            // Avoid logging the session URL/fragment when the OS cannot open the browser.
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IOException("Default browser unavailable");
            }
            Desktop.getDesktop().browse(uri);
        }, AppleMusicManagedToken.bundled(), task -> Minecraft.getInstance().execute(task));
    }

    AppleMusicAccount(Function<String, String> properties, Function<String, String> environment, Browser browser) {
        this(() -> null, properties, environment, browser);
    }

    AppleMusicAccount(Supplier<Path> configDirectory, Function<String, String> properties,
                      Function<String, String> environment, Browser browser) {
        this(configDirectory, properties, environment, browser, null, Runnable::run);
    }

    AppleMusicAccount(Supplier<Path> configDirectory, Function<String, String> properties,
                      Function<String, String> environment, Browser browser,
                      Supplier<CompletableFuture<String>> managedToken, Executor ui) {
        this.configDirectory = configDirectory;
        this.properties = properties;
        this.environment = environment;
        this.browser = browser;
        this.managedToken = managedToken;
        this.ui = ui;
        reloadConfiguration();
    }

    private static Path instanceConfigDirectory() {
        var client = Minecraft.getInstance();
        return client == null || client.gameDirectory == null ? null : client.gameDirectory.toPath().resolve("config");
    }

    private String setting(String property, String env) {
        String value = properties.apply(property);
        if (value == null || value.isBlank()) value = environment.apply(env);
        return value == null ? "" : value.trim();
    }

    private void reloadConfiguration() {
        configuration = AppleMusicProjectConfig.load(configDirectory.get(), setting(TOKEN_PROPERTY, TOKEN_ENV),
                setting(PORT_PROPERTY, PORT_ENV));
    }

    public boolean running() {
        return login != null && login.state() != AppleMusicBrowserLogin.State.EXPIRED
                && login.state() != AppleMusicBrowserLogin.State.CLOSED;
    }

    public boolean configured() { return configuration.issue() == null; }
    public boolean available() { return running() || configured(); }

    /** Opens a companion when needed and sends a selection to the existing session. */
    public boolean openSong(String catalogId) {
        if (catalogId == null || !catalogId.matches("[1-9][0-9]{0,19}")) return false;
        if (running()) {
            try { login.selectSong(catalogId); return true; }
            catch (IllegalStateException expired) { /* Reopen an expired companion below. */ }
        }
        open(catalogId);
        return running();
    }

    public boolean connecting() { return request != null; }
    public boolean primaryAvailable(boolean linksAllowed) { return running() || connecting() || linksAllowed; }

    public String actionKey() {
        if (running()) return "close";
        return connecting() ? "cancel_connection" : "open";
    }

    public void toggleConnection(boolean linksAllowed) {
        if (running()) close();
        else if (connecting()) {
            close();
            failure = "connection_cancelled";
        } else if (linksAllowed) open();
    }

    public boolean authorized() { return login != null && login.state() == AppleMusicBrowserLogin.State.AUTHORIZED; }
    public String storefront() { return login == null ? "" : login.storefront(); }

    public String stateKey(boolean linksAllowed) { return linksAllowed || running() ? stateKey() : "links_disabled"; }

    String stateKey() {
        if (connecting()) return "connecting";
        if (failure != null) return failure;
        String issue = configuration.issue();
        if (!running() && issue != null && managedToken == null) return issue;
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
        return "ready";
    }

    void open() { open(null); }

    private void open(String catalogId) {
        if (running() || connecting()) return;
        close();
        reloadConfiguration();
        if (configured()) startBrowser(configuration, catalogId);
        else if (catalogId == null && managedToken != null) fetchToken();
    }

    private void fetchToken() {
        var next = managedToken.get();
        request = next;
        next.whenCompleteAsync((token, error) -> {
            if (request != next) return;
            request = null;
            if (error == null && token != null) {
                var managed = AppleMusicProjectConfig.load(null, token, "");
                if (managed.issue() == null) {
                    startBrowser(managed, null);
                    return;
                }
            }
            failure = "token_service_failed";
        }, ui);
    }

    private void startBrowser(AppleMusicProjectConfig config, String catalogId) {
        try {
            login = new AppleMusicBrowserLogin(config.token, config.port);
            if (catalogId != null) login.selectSong(catalogId);
        } catch (IllegalArgumentException invalid) {
            failure = "configuration_invalid";
            return;
        } catch (BindException bindFailure) {
            failure = "port_unavailable";
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
        var pending = request;
        request = null;
        if (pending != null) pending.cancel(false);
        if (login != null) login.close();
        login = null;
        failure = null;
    }
}
