// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class NativePlaylistDiscoveryTest {
    private static final String URL = "https://y.qq.com/n/ryqq_v2/playlist/9770623443";
    private static final String ID = "9770623443";
    private static final OnlineTrack A = track("a"), B = track("b");
    private static OnlineTrack track(String id) { return new OnlineTrack("qq", id, id, "", "", "", 0); }

    @Test void onlyExactOfficialProviderDetailLinksAreAcceptedAsReferences() {
        assertEquals(ID, NativePlaylistDiscovery.playlistId("qq", URL + "?from=web"));
        assertEquals(ID, NativePlaylistDiscovery.playlistId("qq", URL.replace("_v2", "")));
        assertEquals("42", NativePlaylistDiscovery.playlistId("netease", "https://music.163.com/playlist?id=42"));
        assertEquals("42", NativePlaylistDiscovery.playlistId("netease", "https://music.163.com/#/playlist?id=42&from=web"));
        assertNull(NativePlaylistDiscovery.playlistId("netease", URL));
        for (String unsafe : List.of(URL.replace("y.qq.com", "y.qq.com.example"), URL.replace("https", "http"),
                URL.replace("y.qq.com", "y.qq.com:443"), URL.replace("y.qq.com", "secret@y.qq.com"),
                URL.replace(ID, "../" + ID), URL.replace(ID, "%34%32"), "https://c6.y.qq.com/short", URL + "#anything")) {
            assertNull(NativePlaylistDiscovery.playlistId("qq", unsafe), unsafe);
        }
        assertNull(NativePlaylistDiscovery.playlistId("netease", "https://music.163.com/#/playlist?id=42&id=43"));
    }

    @Test void selectingDuplicateStartsExactPositionAndChangesOrderAtomically() {
        var rig = new Rig();
        rig.open(URL); rig.model.play(0); assertEquals(-1, rig.selected);
        rig.pending.complete(detail(ID));
        assertTrue(rig.model.changed());
        rig.model.play(2);
        assertEquals(List.of(A, B, A), rig.queued);
        assertEquals(2, rig.selected);
    }

    @Test void signOutAccountSwitchCancelOrMismatchedResponseNeverQueues() {
        var rig = new Rig();
        rig.authenticated = false; rig.open(URL);
        assertNull(rig.pending);
        assertEquals(LibraryAccessFeedback.SIGN_IN, rig.model.status);
        rig.authenticated = true; rig.open(URL);
        var stale = rig.pending;
        ++rig.epoch;
        assertTrue(rig.model.changed());
        assertTrue(stale.isCancelled());
        rig.open(URL);
        rig.pending.complete(detail("wrong"));
        assertTrue(rig.model.changed());
        rig.model.play(0);
        assertNull(rig.model.detail);
        assertEquals(-1, rig.selected);
        rig.open(URL);
        var back = rig.pending;
        rig.model.clear();
        assertTrue(back.isCancelled());
    }

    private static OnlinePlaylistDetail detail(String id) {
        return new OnlinePlaylistDetail(new OnlinePlaylist("qq", id, "List", 3), List.of(A, B, A));
    }

    private static final class Rig {
        boolean authenticated = true;
        long epoch;
        CompletableFuture<OnlinePlaylistDetail> pending;
        List<?> queued;
        int selected = -1;
        final OnlineMusicQueue queue = (OnlineMusicQueue) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{OnlineMusicQueue.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "supportsModeReplacement" -> true;
                    case "replace" -> {
                        assertEquals(OnlineMusicQueue.Mode.SEQUENTIAL, args[2]);
                        queued = (List<?>) args[0]; selected = (Integer) args[1]; yield null;
                    }
                    default -> throw new AssertionError(method.getName());
                });
        final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{OnlineMusicPlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "supportsPlaylistDetail" -> true;
                    case "authenticated" -> authenticated;
                    case "playlistDetail" -> pending = new CompletableFuture<>();
                    case "queue" -> queue;
                    default -> throw new AssertionError(method.getName());
                });
        final NativePlaylistDiscovery model = new NativePlaylistDiscovery(player);
        void open(String link) { model.open("qq", link, () -> epoch); }
    }
}
