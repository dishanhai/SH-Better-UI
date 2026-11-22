// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Navigation and return state of the new music screen. Confined to the client UI thread. */
final class MusicFrontendRoutes {
    private static final String DEFAULT_REQUEST = "route";
    private static final int MAX_BOOKMARKS = 64;
    private static final int MAX_DEPTH = 24;

    enum Page {
        DISCOVER, LIBRARY, SEARCH, NOW_PLAYING, LYRICS, QUEUE, SETTINGS, ACCOUNTS, ACCOUNT, DISCOVERY,
        ARTIST, ALBUM, PLAYLIST, HISTORY, COMMENTS;

        boolean detail() { return this == ARTIST || this == ALBUM; }

        boolean primary() {
            return this == DISCOVER || this == LIBRARY || this == SEARCH;
        }
    }

    /** The provider is part of the identity, so the same ID on two providers never shares scroll state. */
    record Route(Page page, String provider, String id) {
        Route {
            Objects.requireNonNull(page, "page");
            provider = provider == null ? "" : provider;
            id = id == null ? "" : id;
            if (page.primary() && (!provider.isEmpty() || !id.isEmpty())) {
                throw new IllegalArgumentException("Primary pages do not take a provider or resource ID");
            }
            if ((page.detail() || page == Page.PLAYLIST || page == Page.COMMENTS) && (provider.isBlank() || id.isBlank())) {
                throw new IllegalArgumentException("Resource routes require a provider and ID");
            }
            if (page == Page.ACCOUNT && provider.isBlank()) {
                throw new IllegalArgumentException("An account route requires a provider");
            }
        }

        static Route of(Page page) {
            return new Route(page, "", "");
        }

        static Route forProvider(Page page, String provider) {
            return new Route(page, provider, "");
        }
    }

    /** A logical focus ID is stable across window resize and widget recreation. */
    record Memory(String focusId, int scroll) {
        static final Memory EMPTY = new Memory("", 0);

        Memory {
            focusId = focusId == null ? "" : focusId;
            scroll = Math.max(0, scroll);
        }
    }

    /** Immutable UI-only checkpoint; the first ancestor is the immediate Back destination. */
    record Bookmark(Page root, Route current, List<Route> ancestors, Map<Route, Memory> memories,
                    String searchDraft, long generation) {}

    /** Opaque, valid once, and local to its controller. Compare by identity. */
    static final class Request {
        private final String lane;

        private Request(String lane) {
            this.lane = lane;
        }
    }

    private final ArrayDeque<Route> history = new ArrayDeque<>();
    private final LinkedHashMap<Route, Memory> memories = new LinkedHashMap<>(16, .75f, true);
    private final Map<String, Request> requests = new HashMap<>();
    private Page rootPage;
    private Route current;
    private long generation;
    private String searchDraft = "";

    MusicFrontendRoutes() {
        this(Page.DISCOVER);
    }

    MusicFrontendRoutes(Page start) {
        if (!start.primary()) throw new IllegalArgumentException("Expected a primary page");
        rootPage = start;
        current = Route.of(start);
    }

    Route current() { return current; }
    Page rootPage() { return rootPage; }
    boolean canGoBack() { return !history.isEmpty(); }
    long generation() { return generation; }

    Bookmark bookmark() {
        return new Bookmark(rootPage, current, List.copyOf(history),
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(memories)), searchDraft, generation);
    }

    /** Never overwrite newer navigation in a replacement view or revive its old in-flight requests. */
    boolean restoreFresh(Bookmark bookmark) {
        if (generation != 0) return false;
        rootPage = bookmark.root();
        history.clear();
        history.addAll(bookmark.ancestors());
        memories.clear();
        memories.putAll(bookmark.memories());
        searchDraft = bookmark.searchDraft();
        move(bookmark.current());
        return true;
    }

    /** A primary tab takes the user to its root, with that tab's prior list position. */
    boolean select(Page page) {
        Objects.requireNonNull(page, "page");
        if (!page.primary()) throw new IllegalArgumentException("Expected a primary page");
        if (rootPage == page && history.isEmpty()) return false;
        history.clear();
        rootPage = page;
        move(Route.of(page));
        return true;
    }

    /** Do not start audio, change accounts, or invoke platform operations for navigation. */
    boolean open(Route route) {
        Objects.requireNonNull(route, "route");
        if (route.page().primary()) return select(route.page());
        if (current.equals(route) || history.size() >= MAX_DEPTH) return false;
        history.push(current);
        move(route);
        return true;
    }

    /** The host decides whether Back at a primary page exits the player. */
    boolean back() {
        if (history.isEmpty()) return false;
        move(history.pop());
        return true;
    }

    private void move(Route route) {
        current = route;
        generation++;
        invalidateRequests();
    }

    Memory memory() { return memories.getOrDefault(current, Memory.EMPTY); }

    void remember(String focusId, int scroll) {
        var memory = new Memory(focusId, scroll);
        if (memory.equals(Memory.EMPTY)) {
            memories.remove(current);
            return;
        }
        memories.put(current, memory);
        if (memories.size() > MAX_BOOKMARKS) {
            var entries = memories.keySet().iterator();
            while (entries.hasNext()) {
                var recent = entries.next();
                if (!recent.page().primary() && !history.contains(recent)) {
                    entries.remove();
                    break;
                }
            }
        }
    }

    String searchDraft() { return searchDraft; }
    void searchDraft(String text) { searchDraft = Objects.requireNonNullElse(text, ""); }

    /** Each named lane is independent (for example submitted search and suggestions). */
    Request request() { return request(DEFAULT_REQUEST); }

    Request request(String lane) {
        Objects.requireNonNull(lane, "lane");
        if (lane.isBlank()) throw new IllegalArgumentException("Request lane must be named");
        var token = new Request(lane);
        requests.put(lane, token);
        return token;
    }

    boolean isCurrent(Request token) {
        return token != null && requests.get(token.lane) == token;
    }

    /** Apply a result on the UI thread only after consuming its current request. */
    boolean complete(Request token) {
        if (!isCurrent(token)) return false;
        requests.remove(token.lane);
        return true;
    }

    /** Call on sign-out/source changes or teardown when no navigation occurred. */
    void invalidateRequests() {
        requests.clear();
    }
}
