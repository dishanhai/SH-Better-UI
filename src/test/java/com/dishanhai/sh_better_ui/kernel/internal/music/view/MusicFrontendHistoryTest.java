// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationFixtures;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNavigation;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendHistoryTest {
    private final List<CompletableFuture<List<OnlineTrack>>> requests = new ArrayList<>();
    private final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(
            OnlineMusicPlayer.class.getClassLoader(), new Class<?>[] {OnlineMusicPlayer.class}, (proxy, method, args) -> {
                if (!method.getName().equals("search")) throw new AssertionError(method);
                var future = new CompletableFuture<List<OnlineTrack>>();
                requests.add(future);
                return future;
            });
    private final SessionMusicOperations registry = MusicOperationFixtures.registry(player);
    private final MusicFrontendHistory history = new MusicFrontendHistory(registry);

    @Test void noticeAndDetailNeverStartAnOperationOrSelectNewestInstead() {
        var target = registry.startSearch("qq", "old query", 1);
        target.detach();
        var later = registry.startSearch("qq", "later query", 1);
        var notice = MusicOperationNavigation.target(registry, target.id()).orElseThrow();

        var selected = history.notification(notice);
        assertEquals(later.id(), history.entries().get(0).key().id());
        assertEquals(target.id(), selected.id());
        var route = MusicFrontendHistory.detail(selected);
        assertEquals(selected, MusicFrontendHistory.parse(route));
        assertEquals(selected, history.lookup(selected).key());
        assertEquals("old query", history.details(selected).caption());
        assertTrue(target.snapshot().background());
        assertFalse(requests.get(0).isDone());
        assertEquals(2, requests.size());
    }

    @Test void supersededAndWrongSessionCannotOpenAnotherRow() {
        var item = registry.startSearch("qq", "query", 1);
        item.detach();
        var notice = MusicOperationNavigation.target(registry, item.id()).orElseThrow();
        var key = history.notification(notice);
        var other = new MusicFrontendHistory(MusicOperationFixtures.registry(player));
        assertNull(other.notification(notice));
        assertNull(other.lookup(key));
        item.cancel();
        assertNull(history.notification(notice));
        assertNull(history.lookup(key));
        assertNull(history.details(key));
        assertEquals(1, requests.size());
    }

    @Test void staleEpochAndEvictedItemDoNotResolveToReplacement() {
        var item = registry.startSearch("qq", "query", 1);
        var key = history.entries().get(0).key();
        assertNull(history.lookup(new MusicFrontendHistory.Key(key.id(), key.epoch() + 1)));
        requests.get(0).complete(List.of());
        for (int i = 0; i < 33; i++) {
            registry.startSearch("qq", "query", 1);
            requests.get(i + 1).complete(List.of());
        }
        assertNull(history.lookup(key));
        assertNull(history.details(key));
        assertEquals(32, history.entries().size());
        assertEquals(item.id(), key.id());
        assertEquals(34, requests.size());
    }
}
