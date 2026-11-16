// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicCancellationTest {
    @TempDir Path directory;

    private static class Source implements OnlineMusicSource {
        @Override public String id() { return "test"; }
        @Override public void credentials(String value) { }
        @Override public List<OnlineTrack> search(String query, int page, int limit) { return List.of(); }
        @Override public Stream resolve(OnlineTrack track) { return null; }
        @Override public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
        @Override public OnlineMusicAccount account() throws Exception { return new OnlineMusicAccount("test", "2", "Account"); }
    }

    @Test void cancellationCallbackCanAwaitAnotherAccountRequest() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var callbackFinished = new CountDownLatch(1);
        var callbackFailure = new AtomicReference<Throwable>();
        var observed = new AtomicReference<OnlineMusicAccount>();
        var calls = new AtomicInteger();
        var source = new Source() {
            @Override public OnlineMusicAccount account() throws Exception {
                if (calls.getAndIncrement() == 0) {
                    started.countDown();
                    release.await(3, TimeUnit.SECONDS);
                }
                return super.account();
            }
        };
        try (var session = new OnlinePlayerSession(List.of(source))) {
            session.credentials("test", "first");
            var old = session.account("test");
            assertTrue(started.await(2, TimeUnit.SECONDS), "first account request did not start");
            old.whenComplete((value, error) -> {
                try { observed.set(session.account("test").get(500, TimeUnit.MILLISECONDS)); }
                catch (Exception failure) { callbackFailure.set(failure); }
                finally { callbackFinished.countDown(); }
            });
            session.credentials("test", "second");
            assertTrue(callbackFinished.await(2, TimeUnit.SECONDS), "cancellation callback did not finish");
            assertNull(callbackFailure.get(), () -> "replacement account request failed: " + callbackFailure.get());
            assertNotNull(observed.get());
            assertEquals("2", observed.get().id());
            assertTrue(old.isCancelled());
        } finally { release.countDown(); }
    }

    @Test void sessionOnlyLoginDoesNotRevokeAnotherSessionsSavedCredentials() throws Exception {
        var firstStore = new MusicCredentialStore(directory.resolve("accounts"));
        var secondStore = new MusicCredentialStore(directory.resolve("accounts"));
        try (var first = new OnlinePlayerSession(List.of(new Source()), firstStore);
             var second = new OnlinePlayerSession(List.of(new Source()), secondStore)) {
            first.rememberCredentials("test", true);
            first.credentials("test", "first-cookie");
            assertEquals("first-cookie", firstStore.load("test"));
            second.credentials("test", "session-only-cookie");
            assertEquals("first-cookie", firstStore.load("test"));
            assertTrue(first.remembersCredentials("test"));
            assertFalse(second.remembersCredentials("test"));
        }
    }
}
