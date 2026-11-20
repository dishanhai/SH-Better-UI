package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLogin;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        void press() { account.primary(account.display().primary()); }
    }

    @Test void progressAndExplicitCancelDoNotStartAnotherAttempt() {
        var f = new Fixture();
        f.account.activate("qq");
        assertEquals("login.idle", f.status());
        f.press();
        assertEquals("login.creating", f.status());
        f.attempts.get(0).state = OnlineMusicLogin.State.SCANNED;
        assertEquals("login.scanned", f.status());
        f.account.toggleRemember();
        assertEquals("login.scanned", f.status());
        f.press();
        assertEquals("login.cancelled", f.status());
        assertNull(f.account.snapshot());
        assertEquals(List.of("qq"), f.starts);
        assertEquals(1, f.attempts.get(0).closes);
    }

    @Test void switchingRoutesDropsOldAttemptAndNeverUsesAppleAsNativeProvider() {
        var f = new Fixture();
        f.account.activate("qq");
        f.press();
        f.account.activate("qq"); // same logical route or relayout leaves current attempt alone
        assertEquals(0, f.attempts.get(0).closes);
        f.account.activate("netease");
        assertEquals(1, f.attempts.get(0).closes);
        assertNull(f.account.snapshot());
        f.press();
        f.account.activate("apple");
        assertEquals(1, f.attempts.get(1).closes);
        f.press();
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
        f.press();
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
        f.press();
        f.attempts.get(0).state = OnlineMusicLogin.State.EXPIRED;
        assertEquals("login.expired", f.status());
        f.press();
        f.signed.add("qq");
        f.attempts.get(1).state = OnlineMusicLogin.State.CONFIRMED;
        f.account.reset();
        assertNull(f.account.snapshot());
        assertEquals(Set.of("qq"), f.signed);
        assertEquals(List.of("qq", "qq"), f.starts);
    }
    private static OnlineMusicPlayer nativeSession(OnlineMusicSource source) throws Exception {
        var constructor = Class.forName("com.dishanhai.sh_better_ui.kernel.internal.music.OnlinePlayerSession")
                .getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return (OnlineMusicPlayer) constructor.newInstance(List.of(source));
    }

    @Test void cancelRenderedBeforeRealQrConfirmationCannotSignOutNewSession() throws Exception {
        var confirm = new CountDownLatch(1);
        var provider = new OnlineMusicSource() {
            @Override public String id() { return "qq"; }
            @Override public void credentials(String cookie) {}
            @Override public List<OnlineTrack> search(String query, int page, int limit) { return List.of(); }
            @Override public Stream resolve(OnlineTrack track) { return null; }
            @Override public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
            @Override public QrChallenge qrLogin() {
                return new QrChallenge() {
                    @Override public OnlineMusicLogin.Image image() { return null; }
                    @Override public Poll poll() throws InterruptedException {
                        confirm.await();
                        return new Poll(OnlineMusicLogin.State.CONFIRMED, "fixture");
                    }
                    @Override public void close() {}
                };
            }
        };
        try (var player = nativeSession(provider)) {
            var account = new MusicFrontendAccount(player);
            account.activate("qq");
            account.primary(account.display().primary());
            var cancel = account.display().primary();
            assertEquals(MusicFrontendAccount.Action.CANCEL, cancel.action());
            var operations = SessionMusicOperations.forPlayer(player).orElseThrow();
            var operation = operations.latest(SessionMusicOperations.Kind.LOGIN, "qq").orElseThrow();
            confirm.countDown();
            operation.observe().get(8, TimeUnit.SECONDS);
            assertTrue(player.authenticated("qq"));
            account.primary(cancel);
            assertTrue(player.authenticated("qq"), "stale Cancel must not become Log out");
            var signOut = account.display().primary();
            assertEquals(MusicFrontendAccount.Action.SIGN_OUT, signOut.action());
            player.credentials("qq", "replacement");
            // Label/provider/action are still identical, but this is a different session.
            assertEquals(MusicFrontendAccount.Action.SIGN_OUT, account.display().primary().action());
            account.primary(signOut);
            assertTrue(player.authenticated("qq"), "stale Log out must not revoke replacement credentials");
            var previousEpoch = account.display().primary();
            assertNull(previousEpoch.attempt());
            player.credentials("qq", "another-replacement");
            // No QR is present; only the provider account epoch changed. Verify the atomic
            // backend check also rejects a replacement that wins after UI validation.
            assertFalse(operations.signOutIfCurrent("qq", previousEpoch.accountVersion(), previousEpoch.attempt()));
            account.primary(previousEpoch);
            assertTrue(player.authenticated("qq"));
            account.primary(account.display().primary());
            assertFalse(player.authenticated("qq"));
        } finally { confirm.countDown(); }
    }

    @Test void staleCancelDoesNotCancelAReplacementAttemptOnTheSameRoute() {
        var f = new Fixture();
        f.account.activate("qq");
        f.press();
        var oldCancel = f.account.display().primary();
        f.attempts.get(0).state = OnlineMusicLogin.State.EXPIRED;
        f.press();
        var replacement = f.attempts.get(1);
        f.account.primary(oldCancel);
        assertEquals(0, replacement.closes);
        assertEquals("login.creating", f.status());
        f.press();
        assertEquals(1, replacement.closes);
    }

    @Test void staleStartIsInvalidAfterLeavingAndReenteringSameProvider() {
        var f = new Fixture();
        f.account.activate("qq");
        var oldStart = f.account.display().primary();
        f.account.activate("netease");
        f.account.activate("qq");
        f.account.primary(oldStart);
        assertTrue(f.starts.isEmpty());
    }

}
