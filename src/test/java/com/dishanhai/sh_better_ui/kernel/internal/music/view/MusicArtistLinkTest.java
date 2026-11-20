// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MusicArtistLinkTest {
    private final OnlineArtist artist = new OnlineArtist("qq", "real-id", "Singer");
    private final OnlineTrack track = new OnlineTrack("qq", "id", "id", "Song", "credit", "", 1000, "", List.of(artist));
    private boolean supported = true;
    private List<String> sources = List.of("qq");
    private final OnlineMusicPlayer player = (OnlineMusicPlayer) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{OnlineMusicPlayer.class}, (p, m, a) -> switch (m.getName()) {
                case "supportsArtistTracks" -> supported;
                case "sources" -> sources;
                default -> throw new AssertionError(m.getName());
            });

    @Test void nativeCreditUsesProviderIdAndReturnsToExactOrigin(){
        var routes = new MusicFrontendRoutes(MusicFrontendRoutes.Page.SEARCH);
        routes.searchDraft("draft");
        var playing = MusicFrontendRoutes.Route.of(MusicFrontendRoutes.Page.NOW_PLAYING);
        assertTrue(routes.open(playing));
        var link = MusicArtistLink.create(player, track, 9L, routes.generation());
        assertNotNull(link);
        assertTrue(link.current(player, track, 9L, routes.generation()));
        assertEquals("real-id", link.route().id());
        assertEquals("qq",link.route().provider());
        assertTrue(routes.open(link.route()));
        assertFalse(link.current(player, track, 9L, routes.generation()));
        assertTrue(routes.back());
        assertSame(playing, routes.current());
        assertEquals("draft", routes.searchDraft());
    }

    @Test void displayNameAloneIsNotAnIdAndStaleActionsCannotOpen(){
        var raw = new OnlineTrack("qq","raw","raw","Song","Singer","",1000);
        assertNull(MusicArtistLink.create(player, raw, 1L, 2L));
        var link = MusicArtistLink.create(player, track, 1L, 2L);
        assertFalse(link.current(player, raw, 1L, 2L));
        assertFalse(link.current(player, track, 2L, 2L));
        supported = false;
        assertFalse(link.current(player, track, 1L, 2L));
        supported = true;
        sources=List.of("netease");
        assertFalse(link.current(player, track, 1L, 2L));
    }
}
