// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicLogin.State.*;

class QrLoginSessionTest {
    private static final OnlineMusicLogin.Image IMAGE = new OnlineMusicLogin.Image(21, 21, new BitSet());
    private static class Source implements OnlineMusicSource {
        volatile String credentials = "";
        @Override public String id() { return "test"; }
        @Override public List<OnlineTrack> search(String query, int page, int limit) { return List.of(); }
        @Override public Stream resolve(OnlineTrack track) { return null; }
        @Override public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
        @Override public void credentials(String value) { credentials = value; }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "Login state did not settle");
    }
    private static void uninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        for (;;) { try { latch.await(); break; } catch (InterruptedException ignored) { interrupted = true; } }
        if (interrupted) Thread.currentThread().interrupt();
    }
    @Test void confirmationIsPublishedOnlyAfterCredentialsHaveBeenAppliedAndPollingStops() throws Exception {
        var polls = new AtomicInteger(); var closed = new AtomicBoolean(); var applied = new AtomicBoolean();
        var source = new Source() {
            @Override public QrChallenge qrLogin() {
                return new QrChallenge() {
                    @Override public OnlineMusicLogin.Image image() { return IMAGE; }
                    @Override public Poll poll() { return new Poll(polls.incrementAndGet() == 1 ? SCANNED : CONFIRMED, "fixture"); }
                    @Override public void close() { closed.set(true); }
                };
            }
        };
        try (var login = new QrLoginSession(source, (attempt, cookie) -> attempt.confirm(() -> applied.set(true)), 10, 5000)) {
            login.start(); await(() -> login.snapshot().state() == CONFIRMED);
            assertTrue(applied.get()); assertTrue(closed.get()); assertNull(login.snapshot().image());
            int count = polls.get(); Thread.sleep(40); assertEquals(count, polls.get());
            login.close(); assertEquals(CONFIRMED, login.snapshot().state());
        }
    }
    @Test void cancellationRejectsAnInFlightConfirmationEvenWhenProviderIgnoresInterruption() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var ended = new CountDownLatch(1);
        var applied = new AtomicBoolean(); var closed = new AtomicBoolean();
        var source = new Source() {
            @Override public QrChallenge qrLogin() {
                return new QrChallenge() {
                    @Override public OnlineMusicLogin.Image image() { return IMAGE; }
                    @Override public Poll poll() { entered.countDown(); uninterruptibly(release); return new Poll(CONFIRMED, "late"); }
                    @Override public void close() { closed.set(true); }
                };
            }
        };
        try (var login = new QrLoginSession(source, (attempt, cookie) -> {
            attempt.confirm(() -> applied.set(true)); ended.countDown();
        }, 1, 5000)) {
            login.start(); assertTrue(entered.await(3, TimeUnit.SECONDS)); login.close();
            assertEquals(CANCELLED, login.snapshot().state()); assertNull(login.snapshot().image());
            release.countDown(); assertTrue(ended.await(3, TimeUnit.SECONDS));
            assertFalse(applied.get()); assertTrue(closed.get());
        } finally { release.countDown(); }
    }
    @Test void timeoutRejectsConfirmationAndStopsAnOtherwiseWaitingAttempt() throws Exception {
        var applied = new AtomicBoolean();
        var source = new Source() {
            @Override public QrChallenge qrLogin() {
                return new QrChallenge() {
                    @Override public OnlineMusicLogin.Image image() { return IMAGE; }
                    @Override public Poll poll() throws Exception { Thread.sleep(70); return new Poll(CONFIRMED, "late"); }
                };
            }
        };
        try (var login = new QrLoginSession(source, (attempt, cookie) -> attempt.confirm(() -> applied.set(true)), 1, 50)) {
            login.start(); await(() -> login.snapshot().state() == EXPIRED); assertFalse(applied.get());
        }
    }
    @Test void missingCredentialsAndCreationFailureBecomeFailedWithoutExposingTheError() throws Exception {
        var source = new Source() {
            @Override public QrChallenge qrLogin() {
                return new QrChallenge() {
                    @Override public OnlineMusicLogin.Image image() { return IMAGE; }
                    @Override public Poll poll() { return new Poll(CONFIRMED, ""); }
                };
            }
        };
        try (var login = new QrLoginSession(source, (attempt, cookie) -> fail("Missing credential committed"), 1, 5000)) {
            login.start(); await(() -> login.snapshot().state() == FAILED);
        }
        try (var login = new QrLoginSession(new Source() {
            @Override public QrChallenge qrLogin() { throw new IllegalStateException("secret material"); }
        }, (attempt, cookie) -> fail("Creation failure committed"), 1, 5000)) {
            login.start(); await(() -> login.snapshot().state() == FAILED);
            assertFalse(login.snapshot().toString().contains("secret"));
        }
    }
    @Test void replacingLoginManualCredentialsAndPlayerCloseCancelOldAttemptsAndClearCredentials() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1); var discarded = new AtomicBoolean();
        var created = new AtomicInteger();
        var source = new Source() {
            @Override public QrChallenge qrLogin() {
                int index = created.getAndIncrement();
                if (index == 0) { started.countDown(); uninterruptibly(release); }
                return new QrChallenge() {
                    @Override public OnlineMusicLogin.Image image() { return IMAGE; }
                    @Override public Poll poll() { return new Poll(CONFIRMED, "fixture-account"); }
                    @Override public void close() { if (index == 0) discarded.set(true); }
                };
            }
        };
        var player = new OnlinePlayerSession(List.of(source));
        try {
            var old = player.login("test"); assertTrue(started.await(3, TimeUnit.SECONDS));
            // Rapid refresh reuses one bounded worker; cancelled queued attempts must not start requests.
            for (int i = 0; i < 20; i++) player.login("test");
            var current = player.login("test"); release.countDown(); await(discarded::get);
            assertEquals(CANCELLED, old.snapshot().state());
            await(() -> current.snapshot().state() == CONFIRMED);
            assertEquals(2, created.get());
            assertTrue(player.authenticated("test")); assertEquals("fixture-account", source.credentials);
            var interrupted = player.login("test"); player.credentials("test", "manual");
            assertEquals(CANCELLED, interrupted.snapshot().state()); assertEquals("manual", source.credentials);
            var closing = player.login("test"); player.close();
            assertEquals(CANCELLED, closing.snapshot().state()); assertEquals("", source.credentials);
        } finally { release.countDown(); player.close(); }
    }
    @Test void signingOutOnePlatformPreservesTheOtherAndCloseClearsBoth() {
        var first = new Source();
        var second = new Source() { @Override public String id() { return "other"; } };
        try (var player = new OnlinePlayerSession(List.of(first, second))) {
            player.credentials("test", "first"); player.credentials("other", "second");
            assertTrue(player.authenticated("test")); assertTrue(player.authenticated("other"));
            player.credentials("test", "");
            assertFalse(player.authenticated("test")); assertTrue(player.authenticated("other"));
            assertEquals("second", second.credentials);
        }
        assertEquals("", first.credentials); assertEquals("", second.credentials);
    }
}
