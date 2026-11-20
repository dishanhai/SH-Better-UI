// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendSearchTest {
    record Call(String source, String text, int page, CompletableFuture<List<OnlineTrack>> result) {
        void finish(int size) {
            result.complete(IntStream.range(0, size)
                    .mapToObj(i -> new OnlineTrack(source, page + "-" + i, null, "Same title", "", "", 1)).toList());
        }
    }
    final MusicFrontendRoutes routes = new MusicFrontendRoutes();
    final List<Call> calls = new ArrayList<>();
    List<String> sources = List.of("netease", "qq");
    final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(OnlineMusicPlayer.class.getClassLoader(),
            new Class<?>[]{OnlineMusicPlayer.class}, (proxy, method, args) -> switch (method.getName()) {
                case "sources" -> sources;
                case "search" -> {
                    var result = new CompletableFuture<List<OnlineTrack>>() {
                        @Override public boolean cancel(boolean interrupt) { return false; }
                    };
                    calls.add(new Call((String) args[0], (String) args[1], (int) args[2], result));
                    yield result;
                }
                default -> throw new AssertionError(method);
            });
    final MusicFrontendSearch search = new MusicFrontendSearch(player, routes);
    MusicFrontendSearchTest() { routes.select(SEARCH); routes.searchDraft("  track "); }

    @Test void sourceSwitchDiscardsOlderCompletionEvenIfCancellationIsIgnored() {
        search.poll();
        assertTrue(calls.isEmpty());
        search.submit();
        search.cycleSource();
        calls.get(0).finish(1);
        search.poll();
        assertTrue(search.tracks().isEmpty());
        calls.get(1).finish(2);
        search.poll();
        assertEquals("qq", search.tracks().get(0).source());
        assertEquals("track", calls.get(1).text());
        assertEquals(1, calls.get(1).page());
    }

    @Test void visibleWindowsDoNotFetchUntilEveryResultWasReachableAndRetryKeepsCurrentPage() {
        search.submit();
        search.submit();
        assertEquals(1, calls.size());
        calls.get(0).finish(20);
        search.poll();
        for (int i = 0; i < 3; i++) search.next(5);
        assertEquals(15, search.first());
        assertEquals(1, calls.size());
        search.next(5);
        assertEquals(2, calls.get(1).page());
        calls.get(1).result().completeExceptionally(new IllegalStateException());
        search.poll();
        assertEquals(15, search.first());
        assertTrue(search.canRetry());
        search.retry();
        assertEquals(2, calls.get(2).page());
        calls.get(2).finish(0);
        search.poll();
        assertEquals(MusicFrontendSearch.State.END, search.state());
        assertFalse(search.hasNext(5));
        assertTrue(search.hasPrevious());
        assertEquals(1, search.page());
    }

    @Test void backRestoresSuccessfulSearchButInvalidatesUnfinishedPagination() {
        search.submit();
        calls.get(0).finish(20);
        search.poll();
        var tracks = search.tracks();
        search.next(10);
        search.next(10);
        routes.open(MusicFrontendRoutes.Route.of(NOW_PLAYING));
        search.cancel();
        calls.get(1).finish(20);
        routes.back();
        search.poll();
        assertSame(tracks, search.tracks());
        assertEquals(10, search.first());
        assertEquals("  track ", routes.searchDraft());
        assertEquals(2, calls.size());
    }

    @Test void sourceDisappearingDoesNotTriggerNetworkAndCannotDisplayItsLateResults() {
        search.submit();
        sources = List.of();
        calls.get(0).finish(2);
        search.poll();
        assertTrue(search.tracks().isEmpty());
        assertEquals(MusicFrontendSearch.State.UNAVAILABLE, search.state());
        sources = List.of("qq");
        search.poll();
        assertEquals("qq", search.source());
        assertEquals(MusicFrontendSearch.State.IDLE, search.state());
        assertEquals(1, calls.size());
    }
}
