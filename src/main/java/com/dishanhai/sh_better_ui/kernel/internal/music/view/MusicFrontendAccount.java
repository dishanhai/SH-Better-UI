// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLogin;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;

import java.util.Objects;

/** Foreground QR attempt ownership for the rebuilt workspace. Never handles providers' credential contents. */
final class MusicFrontendAccount {
    static final String APPLE = "apple";
    private final OnlineMusicPlayer player;
    private final SessionMusicOperations operations;
    private String source = "", message = "";
    private OnlineMusicLogin attempt;

    MusicFrontendAccount(OnlineMusicPlayer player) {
        this.player = Objects.requireNonNull(player);
        operations = SessionMusicOperations.forPlayer(player).orElse(null);
    }

    boolean available(String id) { return ("qq".equals(id) || "netease".equals(id)) && player.sources().contains(id); }
    boolean authenticated(String id) { return available(id) && player.authenticated(id); }
    boolean remembers() { return available(source) && player.remembersCredentials(source); }

    void activate(String id) {
        if (source.equals(id)) return;
        reset();
        source = id;
    }

    void reset() {
        stopAttempt();
        source = "";
        message = "";
    }

    private void stopAttempt() {
        if (attempt != null) { attempt.close(); attempt = null; }
    }

    OnlineMusicLogin.Snapshot snapshot() {
        if (attempt == null) return null;
        if (!available(source)) {
            stopAttempt();
            return null;
        }
        if (operations != null && operations.currentLogin(source).orElse(null) != attempt) {
            attempt = null;
            return null;
        }
        return attempt.snapshot();
    }

    static boolean busy(OnlineMusicLogin.Snapshot snapshot) {
        if (snapshot == null) return false;
        return switch (snapshot.state()) { case CREATING, WAITING, SCANNED -> true; default -> false; };
    }

    String status(OnlineMusicLogin.Snapshot snapshot) {
        if (!available(source)) return "view.unavailable";
        if (!message.isEmpty()) return message;
        if (snapshot != null && snapshot.state() != OnlineMusicLogin.State.CONFIRMED)
            return "login." + snapshot.state().name().toLowerCase(java.util.Locale.ROOT);
        return authenticated(source) ? "view.signed_in" : "login.idle";
    }

    void primary() {
        if (!available(source)) return;
        var state = snapshot();
        try {
            if (busy(state)) {
                stopAttempt();
                message = "login.cancelled";
            } else if (authenticated(source)) {
                stopAttempt();
                player.credentials(source, "");
                message = "login.signed_out";
            } else {
                stopAttempt();
                attempt = operations == null ? player.login(source) : operations.startLogin(source);
                message = "";
            }
        } catch (RuntimeException rejected) {
            message = authenticated(source) ? "login.storage_failed" : "login.failed";
        }
    }

    void toggleRemember() {
        if (!available(source)) return;
        try {
            player.rememberCredentials(source, !remembers());
            // Do not mask QR progress while the user changes the storage preference.
            message = busy(snapshot()) ? "" : "login.preference_saved";
        } catch (RuntimeException rejected) {
            message = "login.storage_failed";
        }
    }
}
