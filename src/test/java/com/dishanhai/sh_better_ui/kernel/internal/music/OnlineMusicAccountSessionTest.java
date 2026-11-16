// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicAccountSessionTest {
    @TempDir Path directory;
    private static final OnlineTrack SONG = new OnlineTrack("test", "123", "123", "青花瓷", "周杰伦", "我很忙", 10000);
    private static class Source implements OnlineMusicSource {
        volatile String cookie = "";
        volatile boolean like;
        @Override public String id() { return "test"; }
        @Override public void credentials(String value) { cookie = value; }
        @Override public List<OnlineTrack> search(String query, int page, int limit) { return query.equals("中文") ? List.of(SONG) : List.of(); }
        @Override public Stream resolve(OnlineTrack track) { return null; }
        @Override public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
        @Override public OnlineMusicAccount account() throws Exception { return new OnlineMusicAccount(id(), "42", "昵称"); }
        @Override public List<OnlinePlaylist> playlists(int page, int limit) { return List.of(new OnlinePlaylist(id(), "7", "歌单", 1)); }
        @Override public List<OnlineTrack> playlist(OnlinePlaylist list, int page, int limit) { return List.of(SONG); }
        @Override public List<OnlineTrack> liked(int page, int limit) { return like ? List.of(SONG) : List.of(); }
        @Override public void like(OnlineTrack song, boolean value) { like = value; }
        @Override public boolean supportsLikes() { return true; }
        @Override public List<String> suggest(String query, int limit) { return List.of("远端候选"); }
    }
    @Test void credentialsAreOptInAndDisablingSaveKeepsCurrentAccount() throws Exception {
        var store = new MusicCredentialStore(directory.resolve("accounts"));
        var source = new Source();
        try (var session = new OnlinePlayerSession(List.of(source), store)) {
            session.credentials("test", "session-cookie"); assertTrue(store.load().isEmpty());
            session.rememberCredentials("test", true); assertEquals("session-cookie", store.load().get("test"));
        }
        var restored = new Source();
        try (var session = new OnlinePlayerSession(List.of(restored), store)) {
            session.restoreSavedCredentials(); assertTrue(session.authenticated("test"));
            assertTrue(session.remembersCredentials("test")); assertEquals("session-cookie", restored.cookie);
            session.rememberCredentials("test", false);
            assertTrue(session.authenticated("test")); assertTrue(store.load().isEmpty());
            session.credentials("test", "replacement"); assertTrue(store.load().isEmpty());
        }
        assertEquals("", restored.cookie);
    }
    @Test void signingOutDeletesSavedCredentialsAndReopenDoesNotLogIn() throws Exception {
        var store = new MusicCredentialStore(directory);
        try (var session = new OnlinePlayerSession(List.of(new Source()), store)) {
            session.rememberCredentials("test", true); session.credentials("test", "saved-cookie");
            session.credentials("test", ""); assertTrue(store.load().isEmpty());
        }
        try (var session = new OnlinePlayerSession(List.of(new Source()), store)) {
            session.restoreSavedCredentials(); assertFalse(session.authenticated("test"));
        }
    }
    @Test void damagedSavedSourceDoesNotPreventAnotherSourceFromRestoring() throws Exception {
        var store = new MusicCredentialStore(directory);
        store.put("test", "first"); store.put("other", "second");
        java.nio.file.Files.write(directory.resolve("test.credential"), new byte[]{1, 2, 3});
        var second = new Source() { @Override public String id() { return "other"; } };
        try (var session = new OnlinePlayerSession(List.of(new Source(), second), store)) {
            session.restoreSavedCredentials(); assertFalse(session.authenticated("test"));
            assertTrue(session.authenticated("other")); assertEquals("second", second.cookie);
        }
    }
    @Test void signingOutAnotherPlatformDoesNotCancelCurrentQrAttempt() {
        var other = new Source() { @Override public String id() { return "other"; } };
        try (var session = new OnlinePlayerSession(List.of(new Source(), other))) {
            var login = session.login("test");
            session.credentials("other", "");
            assertNotEquals(OnlineMusicLogin.State.CANCELLED, login.snapshot().state());
        }
    }
    @Test void namespacedCustomSourceCanUseSessionCredentialsWithoutFileStorage() {
        var source = new Source() { @Override public String id() { return "example:music"; } };
        try (var session = new OnlinePlayerSession(List.of(source), new MusicCredentialStore(directory))) {
            session.credentials(source.id(), "fixture"); assertTrue(session.authenticated(source.id()));
            session.credentials(source.id(), ""); assertFalse(session.authenticated(source.id()));
        }
    }
    @Test void accountPlaylistsLikesAndPinyinCandidatesShareTheSession() throws Exception {
        var source = new Source();
        try (var session = new OnlinePlayerSession(List.of(source))) {
            var denied = assertThrows(ExecutionException.class, () -> session.account("test").get(2, TimeUnit.SECONDS));
            assertInstanceOf(OnlineMusicSource.LoginRequired.class, denied.getCause());
            session.credentials("test", "fixture");
            assertEquals("42", session.account("test").get(2, TimeUnit.SECONDS).id());
            var list = session.playlists("test", 1).get(2, TimeUnit.SECONDS).get(0);
            assertEquals(List.of(SONG), session.playlist(list, 1).get(2, TimeUnit.SECONDS));
            assertEquals(List.of(SONG), session.search("test", "qhc", 1).get(2, TimeUnit.SECONDS));
            assertEquals(List.of("青花瓷", "远端候选"), session.suggest("test", "qinghuaci").get(2, TimeUnit.SECONDS));
            session.like(SONG, true).get(2, TimeUnit.SECONDS);
            assertEquals(List.of(SONG), session.liked("test", 1).get(2, TimeUnit.SECONDS));
            session.like(SONG, false).get(2, TimeUnit.SECONDS);
            assertTrue(session.liked("test", 1).get(2, TimeUnit.SECONDS).isEmpty());
            session.credentials("test", "");
            assertEquals(List.of("远端候选"), session.suggest("test", "qhc").get(2, TimeUnit.SECONDS));
        }
    }
    @Test void changingAccountCancelsAndRejectsLateAccountResults() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        var source = new Source() {
            @Override public OnlineMusicAccount account() throws Exception {
                started.countDown();
                boolean done = false;
                while (!done) { try { release.await(); done = true; } catch (InterruptedException ignored) {} }
                return super.account();
            }
        };
        try (var session = new OnlinePlayerSession(List.of(source))) {
            session.credentials("test", "first"); var old = session.account("test");
            assertTrue(started.await(2, TimeUnit.SECONDS)); session.credentials("test", "second");
            release.countDown(); assertTrue(old.isCancelled());
        } finally { release.countDown(); }
    }
    @Test void serverExpiredCredentialsClearAccountAndSavedCopy() throws Exception {
        var store = new MusicCredentialStore(directory);
        var source = new Source() { @Override public OnlineMusicAccount account() throws Exception { throw new LoginRequired(); } };
        try (var session = new OnlinePlayerSession(List.of(source), store)) {
            session.rememberCredentials("test", true); session.credentials("test", "expired");
            assertThrows(ExecutionException.class, () -> session.account("test").get(2, TimeUnit.SECONDS));
            // Synchronizes with the completion worker's invalidation.
            assertFalse(session.authenticated("test")); assertTrue(store.load().isEmpty()); assertEquals("", source.cookie);
        }
    }
    @Test void expiredRequestCannotSignOutCredentialsInstalledByItsCompletionCallback() throws Exception {
        var source = new Source() { @Override public OnlineMusicAccount account() throws Exception { throw new LoginRequired(); } };
        try (var session = new OnlinePlayerSession(List.of(source))) {
            session.credentials("test", "expired");
            session.account("test").handle((value, error) -> { session.credentials("test", "new-cookie"); return null; }).get(2, TimeUnit.SECONDS);
            assertTrue(session.authenticated("test")); assertEquals("new-cookie", source.cookie);
        }
    }
    @Test void completionCallbacksCanWaitForAnotherRequestWithoutHoldingTheSessionLock() throws Exception {
        try (var session = new OnlinePlayerSession(List.of(new Source()))) {
            session.credentials("test", "fixture");
            var chained = session.account("test").thenApply(value -> {
                try { return session.playlists("test", 1).get(2, TimeUnit.SECONDS); }
                catch (Exception error) { throw new CompletionException(error); }
            });
            assertEquals(1, chained.get(3, TimeUnit.SECONDS).size());
        }
    }
    @Test void disablingSaveInAnotherSessionRevokesOldWriters() throws Exception {
        var store = new MusicCredentialStore(directory);
        try (var first = new OnlinePlayerSession(List.of(new Source()), store);
             var second = new OnlinePlayerSession(List.of(new Source()), new MusicCredentialStore(directory))) {
            first.rememberCredentials("test", true); first.credentials("test", "first");
            second.restoreSavedCredentials(); second.rememberCredentials("test", false);
            assertFalse(first.remembersCredentials("test"));
            first.credentials("test", "updated"); assertTrue(store.load().isEmpty());
            assertTrue(first.authenticated("test")); assertTrue(second.authenticated("test"));
        }
    }
    @Test void savedCredentialFailureRollsBackProviderAndLeavesOldAccountActive() throws Exception {
        var source = new Source();
        var folder = directory.resolve("storage");
        try (var session = new OnlinePlayerSession(List.of(source), new MusicCredentialStore(folder))) {
            session.rememberCredentials("test", true); session.credentials("test", "old-cookie");
            java.nio.file.Files.delete(folder.resolve("test.credential"));
            // POSIX encryption creates a local key; remove this test fixture before blocking its directory.
            java.nio.file.Files.deleteIfExists(folder.resolve(".key"));
            java.nio.file.Files.delete(folder);
            java.nio.file.Files.writeString(folder, "blocks-directory");
            assertThrows(java.io.UncheckedIOException.class, () -> session.credentials("test", "new-cookie"));
            assertTrue(session.authenticated("test")); assertEquals("old-cookie", source.cookie);
            assertThrows(IllegalArgumentException.class, () -> session.credentials("test", "字".repeat(6000)));
            assertEquals("old-cookie", source.cookie);
        }
    }
    @Test void queuedMutationUsesTheCredentialsCapturedAtSubmission() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        var used = new java.util.concurrent.atomic.AtomicReference<String>();
        var source = new Source() {
            @Override public OnlineMusicSource requestSnapshot() {
                String captured = cookie;
                return new Source() {
                    @Override public void like(OnlineTrack track, boolean value) {
                        started.countDown();
                        for (;;) { try { release.await(); break; } catch (InterruptedException ignored) {} }
                        used.set(captured);
                    }
                };
            }
        };
        try (var session = new OnlinePlayerSession(List.of(source))) {
            session.credentials("test", "first"); var action = session.like(SONG, true);
            assertTrue(started.await(2, TimeUnit.SECONDS)); session.credentials("test", "second"); release.countDown();
            assertTrue(action.isCancelled());
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (used.get() == null && System.nanoTime() < until) Thread.sleep(5);
            assertEquals("first", used.get());
        } finally { release.countDown(); }
    }
}
