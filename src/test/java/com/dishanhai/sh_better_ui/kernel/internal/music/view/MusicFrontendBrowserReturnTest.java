// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import org.junit.jupiter.api.Test;
import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page.*;
import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Route;
import static org.junit.jupiter.api.Assertions.*;

class MusicFrontendBrowserReturnTest {
    private static final Route PROVIDER = Route.forProvider(DISCOVERY, "qq");
    private static final Route OFFICIAL = new Route(DISCOVERY, "qq", "official");
    private static final String URL = "https://y.qq.com/n/ryqq_v2/playlist/9770623443";

    @Test void directDiscoverReturnDoesNotInventAProviderAncestor() {
        var origin = new MusicFrontendRoutes();
        origin.remember("official-qq", 1);
        origin.open(OFFICIAL);
        origin.remember("browse", 0);
        origin.searchDraft("search draft");
        var handoff = new MusicFrontendBrowserReturn();
        var ticket = handoff.begin(origin, URL);
        var replacement = new MusicFrontendRoutes();
        assertTrue(handoff.restore(ticket, origin, URL, replacement));
        assertEquals(URL, ticket.draft);
        assertEquals(DISCOVER, replacement.rootPage());
        assertEquals(OFFICIAL, replacement.current());
        assertEquals("browse", replacement.memory().focusId());
        assertEquals("search draft", replacement.searchDraft());
        assertTrue(replacement.back());
        assertEquals(Route.of(DISCOVER), replacement.current());
        assertEquals(new MusicFrontendRoutes.Memory("official-qq", 1), replacement.memory());
        assertFalse(replacement.back());
    }

    @Test void nestedLibraryReturnPreservesBothBackDestinationsAndConsumesCallbackOnlyOnce() {
        var origin = new MusicFrontendRoutes(LIBRARY);
        origin.open(PROVIDER);
        origin.remember("discover", 2);
        origin.open(OFFICIAL);
        var handoff = new MusicFrontendBrowserReturn();
        var ticket = handoff.begin(origin, URL);
        // Closing the old screen cancels view-local work but does not change navigation.
        var oldRequest = origin.request();
        origin.invalidateRequests();
        var replacement = new MusicFrontendRoutes();
        var replacementRequest = replacement.request();
        assertTrue(handoff.restore(ticket, origin, URL, replacement));
        assertFalse(replacement.complete(oldRequest));
        assertFalse(replacement.complete(replacementRequest));
        assertFalse(handoff.restore(ticket, origin, URL, replacement));
        assertEquals(OFFICIAL, replacement.current());
        assertTrue(replacement.back());
        assertEquals(PROVIDER, replacement.current());
        assertEquals(new MusicFrontendRoutes.Memory("discover", 2), replacement.memory());
        assertTrue(replacement.back());
        assertEquals(Route.of(LIBRARY), replacement.current());
        assertFalse(replacement.back());
    }

    @Test void olderCallbackCannotOverrideAnotherBrowserOpenOrNewerNavigation() {
        var origin = new MusicFrontendRoutes();
        origin.open(OFFICIAL);
        var handoff = new MusicFrontendBrowserReturn();
        var a = handoff.begin(origin, URL);
        var b = handoff.begin(origin, URL);
        assertFalse(handoff.restore(a, origin, URL, new MusicFrontendRoutes()));
        assertTrue(origin.back());
        assertTrue(origin.open(OFFICIAL));  // Back then return to the same route is still newer navigation.
        assertFalse(handoff.restore(b, origin, URL, new MusicFrontendRoutes()));
    }

    @Test void newerDraftAndNewerReplacementNavigationAreNotOverwritten() {
        var origin = new MusicFrontendRoutes();
        origin.open(OFFICIAL);
        var handoff = new MusicFrontendBrowserReturn();
        var oldDraft = handoff.begin(origin, URL);
        assertFalse(handoff.restore(oldDraft, origin, "changed", origin));
        var ticket = handoff.begin(origin, "changed");
        var replacement = new MusicFrontendRoutes();
        replacement.select(SEARCH);
        replacement.select(DISCOVER);
        assertFalse(handoff.restore(ticket, origin, "changed", replacement));
        assertEquals(Route.of(DISCOVER), replacement.current());
        assertFalse(replacement.back());
    }

    @Test void sameViewReturnKeepsStackAndFailedBrowserOpenCanBeDiscarded() {
        var origin = new MusicFrontendRoutes();
        origin.open(OFFICIAL);
        var handoff = new MusicFrontendBrowserReturn();
        var ticket = handoff.begin(origin, URL);
        long generation = origin.generation();
        assertTrue(handoff.restore(ticket, origin, URL, origin));
        assertEquals(generation, origin.generation());
        assertEquals(OFFICIAL, origin.current());
        var rejected = handoff.begin(origin, URL);
        handoff.discard(rejected);
        assertFalse(handoff.restore(rejected, origin, URL, origin));
    }
}
