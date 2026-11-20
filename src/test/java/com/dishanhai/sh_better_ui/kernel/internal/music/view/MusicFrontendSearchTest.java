// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationFixtures;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
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
    final List<OnlineTrack> played = new ArrayList<>();
    List<String> sources = List.of("netease", "qq");
    final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(OnlineMusicPlayer.class.getClassLoader(),
            new Class<?>[]{OnlineMusicPlayer.class}, (proxy, method, args) -> switch (method.getName()) {
                case "sources" -> sources;
                case "queue" -> {
                    assertTrue(Thread.holdsLock(proxy));
                    yield null;
                }
                case "play" -> {
                    assertTrue(Thread.holdsLock(proxy));
                    played.add((OnlineTrack) args[0]);
                    yield null;
                }
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

    @Test void playUsesOnlyTheDisplayedSlotAndRejectsAReboundOrHiddenRow() {
        search.submit();
        calls.get(0).finish(8);
        search.poll();
        var shown = search.tracks();
        search.scrollTo(2, 3);
        assertFalse(search.play(shown, 0, 0, 3));
        assertFalse(search.play(shown, 2, 3, 3));
        assertTrue(search.play(shown, 2, 1, 3));
        assertEquals(List.of(shown.get(3)), played);
        routes.open(MusicFrontendRoutes.Route.of(NOW_PLAYING));
        assertFalse(search.play(shown, 2, 1, 3));
        assertEquals(1, played.size());
    }

    @Test void logoutHoldingThePlayerMonitorWinsBeforeAQueuedClickCanUseTheOldPage() throws Exception {
        var song = new OnlineTrack("qq", "1", "1", "Old account result", "", "", 1000);
        OnlineMusicSource source = new OnlineMusicSource() {
            @Override public String id() { return "qq"; }
            @Override public void credentials(String cookie) {}
            @Override public List<OnlineTrack> search(String query, int page, int limit) { return List.of(song); }
            @Override public Stream resolve(OnlineTrack track) { throw new AssertionError("Unexpected play"); }
            @Override public LyricTimeline lyrics(OnlineTrack track) { return LyricTimeline.empty(); }
        };
        try (var session = MusicOperationFixtures.searchSession(source)) {
            session.credentials("qq", "sample=1");
            var control = new MusicFrontendSearch(session, routes);
            control.submit();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (control.tracks().isEmpty() && System.nanoTime() < deadline) {
                control.poll();
                Thread.sleep(2);
            }
            var painted = control.tracks();
            assertEquals(List.of(song), painted);
            var click = new FutureTask<>(() -> control.play(painted, 0, 0, 1));
            Thread worker = new Thread(click, "acceptance-stale-search-click");
            worker.setDaemon(true);
            synchronized (session) {
                worker.start();
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (worker.getState() != Thread.State.BLOCKED && !click.isDone() && System.nanoTime() < deadline)
                    Thread.sleep(2);
                assertEquals(Thread.State.BLOCKED, worker.getState());
                session.credentials("qq", "");
            }
            assertFalse(click.get(3, TimeUnit.SECONDS));
            assertTrue(session.queue().snapshot().tracks().isEmpty());
        }
    }

}
