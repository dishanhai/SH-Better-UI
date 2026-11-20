// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page;
import com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Route;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendRoutesTest {
    @Test void threePrimaryDestinationsAndSecondaryDeepLinks() {
        var routes = new MusicFrontendRoutes();
        assertEquals(Route.of(Page.DISCOVER), routes.current());
        assertFalse(routes.back());
        assertThrows(IllegalArgumentException.class, () -> routes.select(Page.QUEUE));
        assertTrue(routes.select(Page.LIBRARY));
        assertTrue(routes.open(Route.of(Page.NOW_PLAYING)));
        assertTrue(routes.open(Route.of(Page.QUEUE)));
        assertEquals(Page.LIBRARY, routes.rootPage());
        assertTrue(routes.back());
        assertEquals(Route.of(Page.NOW_PLAYING), routes.current());
        assertTrue(routes.back());
        assertEquals(Route.of(Page.LIBRARY), routes.current());
        assertFalse(routes.canGoBack());
    }

    @Test void backRestoresOuterScrollFocusAndSearchDraftAcrossNestedRoutes() {
        var routes = new MusicFrontendRoutes(Page.SEARCH);
        routes.searchDraft("周杰伦 圆游会");
        routes.remember("search/result:25", 391);
        assertTrue(routes.open(new Route(Page.ARTIST, "qq", "123")));
        routes.remember("artist/release:9", 80);
        assertTrue(routes.open(new Route(Page.PLAYLIST, "qq", "901")));
        routes.remember("playlist/song:6", 44);
        assertTrue(routes.back());
        assertEquals("artist/release:9", routes.memory().focusId());
        assertEquals(80, routes.memory().scroll());
        assertTrue(routes.back());
        assertEquals("search/result:25", routes.memory().focusId());
        assertEquals(391, routes.memory().scroll());
        assertEquals("周杰伦 圆游会", routes.searchDraft());
    }

    @Test void providerIdentitySeparatesAccountAndPlaylistMemories() {
        var routes = new MusicFrontendRoutes(Page.LIBRARY);
        var qq = new Route(Page.PLAYLIST, "qq", "12");
        var ne = new Route(Page.PLAYLIST, "netease", "12");
        routes.open(qq);
        routes.remember("row", 80);
        routes.back();
        routes.open(ne);
        assertEquals(MusicFrontendRoutes.Memory.EMPTY, routes.memory());
        routes.back();
        routes.open(qq);
        assertEquals(80, routes.memory().scroll());
        assertEquals("row", routes.memory().focusId());
        assertThrows(IllegalArgumentException.class, () -> Route.of(Page.ACCOUNT));
        assertEquals("apple", Route.forProvider(Page.ACCOUNT, "apple").provider());
        assertThrows(IllegalArgumentException.class, () -> Route.of(Page.PLAYLIST));
    }

    @Test void reselectingTabClosesDetoursAndRemountsOnlyOnActualRouteChange() {
        var routes = new MusicFrontendRoutes(Page.SEARCH);
        var request = routes.request();
        assertFalse(routes.select(Page.SEARCH));
        assertEquals(0, routes.generation());
        assertTrue(routes.isCurrent(request));
        routes.remember("input", -5);
        routes.open(Route.of(Page.SETTINGS));
        routes.open(Route.forProvider(Page.ACCOUNT, "apple"));
        assertTrue(routes.select(Page.SEARCH));
        assertFalse(routes.canGoBack());
        assertEquals(3, routes.generation());
        assertEquals(new MusicFrontendRoutes.Memory("input", 0), routes.memory());
        assertFalse(routes.complete(request));
    }

    @Test void latestRequestWinsPerLaneAndCannotCrossOwners() {
        var routes = new MusicFrontendRoutes();
        var result = routes.request("search");
        var first = routes.request("typeahead");
        var second = routes.request("typeahead");
        var other = new MusicFrontendRoutes().request("typeahead");
        assertFalse(routes.complete(first));
        assertFalse(routes.complete(other));
        assertTrue(routes.complete(second));
        assertFalse(routes.complete(second));
        assertTrue(routes.complete(result));
    }

    @Test void backThenReopenDoesNotAcceptOldCompletion() {
        var routes = new MusicFrontendRoutes();
        var provider = Route.forProvider(Page.DISCOVERY, "qq");
        routes.open(provider);
        var stale = routes.request();
        routes.back();
        routes.open(provider);
        var fresh = routes.request();
        assertFalse(routes.complete(stale));
        assertTrue(routes.isCurrent(fresh));
        routes.invalidateRequests();
        assertFalse(routes.complete(fresh));
    }

    @Test void switchingTabRestoresItsPriorPositionWithoutStaleCallbacks() {
        var routes = new MusicFrontendRoutes(Page.SEARCH);
        var request = routes.request();
        routes.remember("query", 300);
        routes.searchDraft("青花瓷");
        routes.select(Page.LIBRARY);
        routes.remember("saved", 22);
        routes.select(Page.SEARCH);
        assertFalse(routes.isCurrent(request));
        assertEquals("query", routes.memory().focusId());
        assertEquals(300, routes.memory().scroll());
        assertEquals("青花瓷", routes.searchDraft());
        routes.select(Page.LIBRARY);
        assertEquals(22, routes.memory().scroll());
    }

    @Test void browsingManyPlaylistsRemembersPrimaryPageWithoutGrowingUnbounded() {
        var routes = new MusicFrontendRoutes(Page.LIBRARY);
        routes.remember("list", 100);
        for (int i = 0; i < 90; i++) {
            routes.open(new Route(Page.PLAYLIST, "qq", String.valueOf(i)));
            routes.remember("track", i + 1);
            routes.back();
        }
        assertEquals(new MusicFrontendRoutes.Memory("list", 100), routes.memory());
        routes.open(new Route(Page.PLAYLIST, "qq", "0"));
        assertEquals(MusicFrontendRoutes.Memory.EMPTY, routes.memory());
        routes.back();
        routes.open(new Route(Page.PLAYLIST, "qq", "89"));
        assertEquals(90, routes.memory().scroll());
    }
}
