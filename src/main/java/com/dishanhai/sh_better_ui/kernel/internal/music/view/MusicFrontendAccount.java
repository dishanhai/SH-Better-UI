// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLogin;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;

import java.util.Objects;

/** Foreground QR attempt ownership for the rebuilt workspace. Never handles providers' credential contents. */
final class MusicFrontendAccount {
    static final String APPLE = "apple";
    enum Action { START, CANCEL, SIGN_OUT }
    record Primary(String source, long routeRevision, Action action, OnlineMusicLogin attempt, long accountVersion) {
        boolean matches(Primary other) {
            return other != null && source.equals(other.source) && routeRevision == other.routeRevision
                    && action == other.action && attempt == other.attempt && accountVersion == other.accountVersion;
        }
    }
    record Display(OnlineMusicLogin.Snapshot snapshot, Primary primary) {}
    private final OnlineMusicPlayer player;
    private final SessionMusicOperations operations;
    private String source = "", message = "";
    private OnlineMusicLogin attempt;
    private long routeRevision;

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
        routeRevision++;
    }

    private void stopAttempt() {
        if (attempt != null) { attempt.close(); attempt = null; }
    }

    OnlineMusicLogin.Snapshot snapshot() { return display().snapshot(); }

    Display display() {
        synchronized (player) { return displayLocked(); }
    }

    private Display displayLocked() {
        var state = snapshotLocked();
        if (!available(source)) return new Display(state, null);
        Action action = busy(state) ? Action.CANCEL : authenticated(source) ? Action.SIGN_OUT : Action.START;
        OnlineMusicLogin providerAttempt = operations == null ? attempt : operations.currentLogin(source).orElse(null);
        return new Display(state, new Primary(source, routeRevision, action, providerAttempt,
                operations == null ? 0L : operations.accountVersion(source)));
    }

    private OnlineMusicLogin.Snapshot snapshotLocked() {
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

    void primary(Primary displayed) {
        if (displayed == null) return;
        // Confirmation and account replacement use the same player lock. A stale press
        // does nothing, even if the current button label would perform a different action.
        synchronized (player) {
            if (!displayed.matches(displayLocked().primary())) return;
            if (displayed.action() != Action.SIGN_OUT || operations == null) {
                primaryLocked(displayed.action());
                return;
            }
        }
        // The native session rechecks account epoch and attempt atomically at commit,
        // then cancels obsolete futures after releasing its monitor.
        try {
            if (operations.signOutIfCurrent(displayed.source(), displayed.accountVersion(), displayed.attempt())) {
                attempt = null;
                message = "login.signed_out";
            }
        } catch (RuntimeException rejected) {
            message = "login.storage_failed";
        }
    }

    private void primaryLocked(Action action) {
        try {
            switch (action) {
                case CANCEL -> {
                    stopAttempt();
                    message = "login.cancelled";
                }
                case SIGN_OUT -> {
                    stopAttempt();
                    player.credentials(source, "");
                    message = "login.signed_out";
                }
                case START -> {
                    stopAttempt();
                    attempt = operations == null ? player.login(source) : operations.startLogin(source);
                    message = "";
                }
            }
        } catch (RuntimeException rejected) {
            message = action == Action.SIGN_OUT ? "login.storage_failed" : "login.failed";
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
