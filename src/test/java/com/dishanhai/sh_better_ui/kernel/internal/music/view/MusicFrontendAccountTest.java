package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLogin;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendAccountTest {
    private static final class Attempt implements OnlineMusicLogin {
        int closes;
        State state = State.CREATING;
        @Override public Snapshot snapshot() { return new Snapshot(state, null); }
        @Override public void close() { if (state != State.CONFIRMED) state = State.CANCELLED; closes++; }
    }

    private static final class Fixture {
        final Set<String> signed = new HashSet<>(), remember = new HashSet<>();
        final List<String> starts = new ArrayList<>();
        final List<Attempt> attempts = new ArrayList<>();
        boolean storageFails;
        final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (p, method, args) -> {
                    assertTrue(args == null || !List.of(args).contains("apple"), "Apple never enters the native account backend");
                    return switch (method.getName()) {
                        case "sources" -> List.of("qq", "netease");
                        case "authenticated" -> signed.contains(args[0]);
                        case "remembersCredentials" -> remember.contains(args[0]);
                        case "rememberCredentials" -> {
                            if (storageFails) throw new IllegalStateException("storage unavailable");
                            if (Boolean.TRUE.equals(args[1])) remember.add((String) args[0]); else remember.remove(args[0]);
                            yield null;
                        }
                        case "credentials" -> {
                            assertEquals("", args[1]);
                            signed.remove(args[0]);
                            yield null;
                        }
                        case "login" -> {
                            starts.add((String) args[0]);
                            var attempt = new Attempt(); attempts.add(attempt); yield attempt;
                        }
                        default -> throw new AssertionError(method.getName());
                    };
                });
        final MusicFrontendAccount account = new MusicFrontendAccount(player);
        String status() { return account.status(account.snapshot()); }
    }

    @Test void progressAndExplicitCancelDoNotStartAnotherAttempt() {
        var f = new Fixture();
        f.account.activate("qq");
        assertEquals("login.idle", f.status());
        f.account.primary();
        assertEquals("login.creating", f.status());
        f.attempts.get(0).state = OnlineMusicLogin.State.SCANNED;
        assertEquals("login.scanned", f.status());
        f.account.toggleRemember();
        assertEquals("login.scanned", f.status());
        f.account.primary();
        assertEquals("login.cancelled", f.status());
        assertNull(f.account.snapshot());
        assertEquals(List.of("qq"), f.starts);
        assertEquals(1, f.attempts.get(0).closes);
    }

    @Test void switchingRoutesDropsOldAttemptAndNeverUsesAppleAsNativeProvider() {
        var f = new Fixture();
        f.account.activate("qq");
        f.account.primary();
        f.account.activate("qq"); // same logical route or relayout leaves current attempt alone
        assertEquals(0, f.attempts.get(0).closes);
        f.account.activate("netease");
        assertEquals(1, f.attempts.get(0).closes);
        assertNull(f.account.snapshot());
        f.account.primary();
        f.account.activate("apple");
        assertEquals(1, f.attempts.get(1).closes);
        f.account.primary();
        f.account.toggleRemember();
        assertFalse(f.account.authenticated("apple"));
        assertEquals(List.of("qq", "netease"), f.starts);
    }

    @Test void signOutAndRememberAreExplicitAndScopedToTheSelectedProvider() {
        var f = new Fixture();
        f.signed.addAll(List.of("qq","netease"));
        f.account.activate("qq");
        assertFalse(f.account.remembers());
        assertEquals("view.signed_in", f.status());
        f.account.toggleRemember();
        assertEquals(Set.of("qq"), f.remember);
        f.account.primary();
        assertEquals(Set.of("netease"), f.signed);
        assertEquals("login.signed_out", f.status());
        assertTrue(f.starts.isEmpty());
        f.account.activate("netease");
        f.storageFails = true;
        f.account.toggleRemember();
        assertEquals("login.storage_failed", f.status());
        assertEquals(Set.of("qq"), f.remember);
    }

    @Test void expiredAttemptCanBeRestartedAndTeardownDoesNotSignOutAConfirmedLogin() {
        var f = new Fixture();
        f.account.activate("qq");
        f.account.primary();
        f.attempts.get(0).state = OnlineMusicLogin.State.EXPIRED;
        assertEquals("login.expired", f.status());
        f.account.primary();
        f.signed.add("qq");
        f.attempts.get(1).state = OnlineMusicLogin.State.CONFIRMED;
        f.account.reset();
        assertNull(f.account.snapshot());
        assertEquals(Set.of("qq"), f.signed);
        assertEquals(List.of("qq", "qq"), f.starts);
    }
}
