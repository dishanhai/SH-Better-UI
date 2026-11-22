// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Entry;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.ReplyPage;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Order.HOT;
import static org.junit.jupiter.api.Assertions.*;

class MusicCommentBrowserTest {
    @Test void revealsPopularRepliesInBatchesBeforeFetchingTheNextPage() {
        var parent = new Entry("parent", "Listener", "Comment", null, 150L, 9L, null, "parent");
        var first = new ReplyPage(List.of(reply("zero", 0L), reply("unknown", null), reply("popular", 20L),
                reply("liked-first", 4L), reply("liked-second", 4L)), "next");
        var last = new ReplyPage(List.of(reply("liked-first", 4L), reply("last", 0L), reply("later", 100L),
                reply("later-second", 8L), reply("later-third", 1L)), null);
        var cursors = new ArrayList<String>();
        var player = (OnlineMusicPlayer) Proxy.newProxyInstance(OnlineMusicPlayer.class.getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "commentOrders" -> List.of(HOT);
                    case "comments" -> CompletableFuture.completedFuture(new OnlineMusicComments(List.of(parent), 1L, false));
                    case "commentReplies" -> {
                        String cursor = (String) args[2];
                        cursors.add(cursor);
                        yield CompletableFuture.completedFuture(cursor == null ? first : last);
                    }
                    default -> throw new AssertionError(method.getName());
                });
        var browser = new MusicCommentBrowser(player);
        browser.open(new OnlineTrack("qq", "123", "123", "Song", "Artist", "", 0));
        browser.poll();
        browser.activate(action(browser, "view_replies_count"));
        browser.poll();
        assertEquals(List.of("popular", "liked-first", "liked-second"), visibleReplies(browser));
        assertEquals(1, cursors.size());
        assertNull(cursors.get(0));

        browser.activate(action(browser, "more_replies"));
        assertEquals(List.of("popular", "liked-first", "liked-second", "zero", "unknown"), visibleReplies(browser));
        assertEquals(1, cursors.size());

        browser.activate(action(browser, "more_replies"));
        browser.poll();
        assertEquals("next", cursors.get(1));
        var beforeLast = List.of("popular", "liked-first", "liked-second", "zero", "unknown", "later",
                "later-second", "later-third");
        assertEquals(beforeLast, visibleReplies(browser));

        browser.activate(action(browser, "more_replies"));
        var allReplies = new ArrayList<>(beforeLast);
        allReplies.add("last");
        assertEquals(allReplies, visibleReplies(browser));
        assertEquals(2, cursors.size());
        assertFalse(browser.lines().stream().anyMatch(line -> "more_replies".equals(line.getAction())));

        browser.activate(action(browser, "hide_replies"));
        assertTrue(visibleReplies(browser).isEmpty());
        browser.activate(action(browser, "view_replies_count"));
        browser.poll();
        assertEquals(List.of("popular", "liked-first", "liked-second"), visibleReplies(browser));
    }

    private static Entry reply(String id, Long likes) {
        return new Entry(id, "Listener", "Reply", null, likes, null);
    }

    private static MusicCommentBrowser.Line action(MusicCommentBrowser browser, String action) {
        return browser.lines().stream().filter(line -> action.equals(line.getAction())).findFirst().orElseThrow();
    }

    private static List<String> visibleReplies(MusicCommentBrowser browser) {
        return browser.lines().stream().filter(MusicCommentBrowser.Line::getNested)
                .map(MusicCommentBrowser.Line::getEntry).filter(entry -> entry != null).map(Entry::getId).toList();
    }
}
