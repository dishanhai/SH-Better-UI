// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One geometry snapshot for paint, clipping and hit targets in the rebuilt music workspace. */
record MusicFrontendLayout(Bounds frame, boolean supported, Mode mode, Bounds back, Bounds utility,
                           Bounds navigation, Bounds content, Bounds header, Bounds body,
                           Bounds transport, Bounds queueRail, Map<Route, Bounds> tabs,
                           Transport transportControls) {
    enum Route { DISCOVER, LIBRARY, SEARCH, NOW_PLAYING, LYRICS, QUEUE, SETTINGS, ACCOUNTS, ACCOUNT, DISCOVERY, ARTIST, PLAYLIST, HISTORY }
    enum Mode { COMPACT, DESKTOP }

    static final int MIN_WIDTH = 208;
    static final int MIN_HEIGHT = 240;
    private static final int SIDEBAR_MIN_WIDTH = 560;
    private static final int QUEUE_RAIL_MIN_WIDTH = 864;
    static final List<Route> DESTINATIONS = List.of(Route.DISCOVER, Route.LIBRARY, Route.SEARCH);
    private static final int PADDING = 8;
    private static final int GAP = 8;

    record Transport(Bounds track, Bounds previous, Bounds playPause, Bounds next, Bounds progress) {}

    MusicFrontendLayout {
        var copy = new EnumMap<Route, Bounds>(Route.class);
        copy.putAll(tabs);
        tabs = Collections.unmodifiableMap(copy);
    }

    Bounds tab(Route route) {
        return tabs.getOrDefault(Objects.requireNonNull(route), emptyAt(frame));
    }

    private static Bounds emptyAt(Bounds frame) { return new Bounds(frame.x(), frame.y(), 0, 0); }

    static MusicFrontendLayout compute(Bounds frame, Route route, boolean requestQueueRail) {
        Objects.requireNonNull(frame);
        Objects.requireNonNull(route);
        var empty = emptyAt(frame);
        var tabs = new EnumMap<Route, Bounds>(Route.class);
        if (frame.width() < MIN_WIDTH || frame.height() < MIN_HEIGHT) {
            for (var item : DESTINATIONS) tabs.put(item, empty);
            return new MusicFrontendLayout(frame, false, Mode.COMPACT, empty, empty, empty, empty, empty,
                    empty, empty, empty, tabs, new Transport(empty, empty, empty, empty, empty));
        }

        int x = frame.x() + PADDING, y = frame.y() + PADDING;
        int width = frame.width() - PADDING * 2;
        boolean desktop = frame.width() >= SIDEBAR_MIN_WIDTH;
        int transportHeight = desktop ? 44 : 48;
        int navHeight = desktop ? 0 : 26;
        int transportBottom = frame.bottom() - PADDING - (desktop ? 0 : navHeight + GAP);
        var transport = new Bounds(x, transportBottom - transportHeight, width, transportHeight);
        int workspaceBottom = transport.y() - GAP;
        boolean withRail = desktop && requestQueueRail && route != Route.QUEUE && route != Route.NOW_PLAYING && route != Route.LYRICS
                && frame.width() >= QUEUE_RAIL_MIN_WIDTH;
        Bounds navigation, content, back, utility;
        Bounds queueRail = empty;
        if (desktop) {
            int sidebarWidth = Math.min(168, Math.max(112, width / 5));
            back = new Bounds(x, y, 24, 24);
            utility = new Bounds(x + sidebarWidth - 24, y, 24, 24);
            navigation = new Bounds(x, back.bottom() + GAP, sidebarWidth, workspaceBottom - back.bottom() - GAP);
            int contentX = navigation.right() + GAP;
            if (withRail) {
                int railWidth = Math.min(200, Math.max(148, width / 5));
                queueRail = new Bounds(x + width - railWidth, y, railWidth, workspaceBottom - y);
            }
            int contentRight = withRail ? queueRail.x() - GAP : x + width;
            content = new Bounds(contentX, y, contentRight - contentX, workspaceBottom - y);
        } else {
            back = new Bounds(x, y, 24, 24);
            utility = new Bounds(x + width - 24, y, 24, 24);
            navigation = new Bounds(x, frame.bottom() - PADDING - navHeight, width, navHeight);
            int contentY = back.bottom() + GAP;
            content = new Bounds(x, contentY, width, workspaceBottom - contentY);
        }

        int index = 0;
        for (var item : DESTINATIONS) {
            if (desktop) {
                tabs.put(item, new Bounds(navigation.x(), navigation.y() + index * 30, navigation.width(), 26));
            } else {
                // Distribute remainders across adjacent tabs rather than introducing dead hit areas.
                int left = (int) ((long) navigation.width() * index / DESTINATIONS.size());
                int right = (int) ((long) navigation.width() * (index + 1) / DESTINATIONS.size());
                tabs.put(item, new Bounds(navigation.x() + left, navigation.y(), right - left, navigation.height()));
            }
            index++;
        }
        int headerHeight = desktop ? 30 : 24;
        var header = new Bounds(content.x(), content.y(), content.width(), headerHeight);
        var body = new Bounds(content.x(), header.bottom() + GAP, content.width(), content.height() - headerHeight - GAP);
        return new MusicFrontendLayout(frame, true, desktop ? Mode.DESKTOP : Mode.COMPACT, back, utility, navigation,
                content, header, body, transport, queueRail, tabs, transportControls(transport, desktop));
    }

    private static Transport transportControls(Bounds bar, boolean desktop) {
        int x = bar.x() + PADDING, y = bar.y();
        int right = bar.right() - PADDING;
        int first = desktop ? bar.x() + (bar.width() - 80) / 2 : right - 80;
        var metadata = new Bounds(x, y + 4, first - GAP - x, 26);
        var previous = new Bounds(first, y + 4, 24, 24);
        var playPause = new Bounds(first + 28, y + 4, 24, 24);
        var next = new Bounds(first + 56, y + 4, 24, 24);
        var progress = new Bounds(x, bar.bottom() - 14, right - x, 10);
        return new Transport(metadata, previous, playPause, next, progress);
    }
}
