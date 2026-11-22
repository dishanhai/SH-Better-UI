// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNavigation;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page;

/** Opt-in vertical slice of the rebuilt workspace. The stock view remains the release default. */
public final class MusicFrontendWorkspace implements OnlineMusicView {
    public static final String PREVIEW_FLAG = "shbui.music.previewRedesign";
    private static final int INK = 0xfff5f5f7, MUTED = 0xffb4b4ba, ACCENT = 0xffff4866;
    private final OnlineMusicPlayer player;
    private final boolean ownsPlayer;
    private final MusicFrontendTransport transport;
    private final MusicFrontendQueue playingNext;
    private MusicFrontendQueuePanel queuePanel;
    private final MusicFrontendRoutes routes = new MusicFrontendRoutes();
    private final MusicFrontendRouteMotion motion = new MusicFrontendRouteMotion();
    private final MusicFrontendBrowserReturn browserReturn = new MusicFrontendBrowserReturn();
    private final MusicFrontendSearch catalog;
    private final MusicFrontendAccountPanel accounts;
    private final MusicFrontendLibraryPanel library;
    private final MusicFrontendHistoryPanel history;
    private final MusicFrontendSoundPanel sound;
    private final ArtistDetailPanel artistDetail;
    private final MusicCommentsPanel comments;
    private final List<MusicTextControl> searchComments = new ArrayList<>();
    private final List<MusicTrackCredits> searchCredits = new ArrayList<>();
    private MusicTextControl detailReturnFocus;
    private MusicTextControl nowComments;
    private boolean restoreNowComments;
    private final SessionMusicOperations musicOperations;
    private MusicTextControl artistCredit;
    private MusicArtistLink artistLink;
    private boolean restoreArtistCredit;
    // Route instances correspond to distinct history entries even when their destinations are equal.
    private final IdentityHashMap<MusicFrontendRoutes.Route, MusicFrontendSoundPanel.ViewState> settingsReturns = new IdentityHashMap<>();
    private final IdentityHashMap<MusicFrontendRoutes.Route, MusicCommentsPanel.Bookmark> commentReturns = new IdentityHashMap<>();
    private MusicFrontendSoundPanel.ViewState settingsResize;
    private MusicTextControl historyShortcut, followLyrics;
    private final List<MusicTextControl> nav = new ArrayList<>(), rows = new ArrayList<>();
    private PreviewContext host;
    private MusicFrontendLayout layout;
    private MusicFrontendMiniPlayer mini;
    private UiTextField search;
    private MusicTextControl back, utility, submit;
    private MusicTransportControl nowLyrics, nowQueue;
    private MusicFrontendNowPlayingLayout nowPlaying, playerDetail;
    private MusicLyricsView lyric;
    private MusicTextControl source, retry;
    private OnlineMusicScrollRegion searchScroll;
    private Bounds searchList;
    private int searchRows, searchRowHeight;
    private List<OnlineTrack> paintedMatches = List.of();
    private int paintedFirst = -1;
    private String searchFocus = "";
    private int searchFocusIndex = -1;
    private record SearchAnchor(Object feed, double offset) {}
    private SearchAnchor searchAnchor;
    private String status = "";
    private MusicFrontendTransport.LyricContent lyricContent;
    private boolean closed;
    private int contentFade;

    /** Independent previews and tests may still explicitly supply an owned player. */
    public MusicFrontendWorkspace(OnlineMusicPlayer player) { this(player, true); }
    private MusicFrontendWorkspace(OnlineMusicPlayer player, boolean ownsPlayer) {
        this.player = Objects.requireNonNull(player);
        this.ownsPlayer = ownsPlayer;
        transport = new MusicFrontendTransport(player);
        artistDetail = new ArtistDetailPanel(player);
        comments = new MusicCommentsPanel(player);
        musicOperations = SessionMusicOperations.forPlayer(player).orElse(null);
        catalog = new MusicFrontendSearch(player, routes);
        playingNext = new MusicFrontendQueue(player);
        history = new MusicFrontendHistoryPanel(
                new MusicFrontendHistory(musicOperations), routes, this::navigate);
        sound = new MusicFrontendSoundPanel(player, this::navigate, history::available);
        library = new MusicFrontendLibraryPanel(player,routes,this::navigate,this::browseLibrary);
        accounts = new MusicFrontendAccountPanel(new MusicFrontendAccount(player), new AppleMusicAccount(),
                () -> Minecraft.getInstance() != null && Minecraft.getInstance().options.chatLinks().get(), this::navigate);
    }
    public static MusicFrontendWorkspace borrowed(OnlineMusicPlayer player) {
        return new MusicFrontendWorkspace(player, false);
    }
    public void requestOperationHistory(MusicOperationNavigation.Target target) {
        history.notification(Objects.requireNonNull(target));
    }
    @Override public OnlineMusicPlayer player() { return player; }

    @Override public void layout(PreviewContext ctx) {
        if (closed) return;
        motion.reset();
        contentFade = 0;
        if (routes.current().page() == Page.SEARCH) rememberSearchFocus();
        if (layout != null && layout.supported()) settingsResize = sound.resizeBookmark();
        host = ctx;
        if (mini != null) { mini.close(); mini = null; }
        layout = MusicFrontendLayout.compute(ctx.viewport(), MusicFrontendLayout.Route.valueOf(routes.current().page().name()), false);
        nav.clear(); rows.clear(); searchComments.clear(); searchCredits.clear();
        var fallbackExit = OnlineMusicScreen.exitControl(ctx);
        if (fallbackExit != null) {
            fallbackExit.visible = !layout.supported();
            fallbackExit.setAvailable(fallbackExit.visible);
            fallbackExit.setInk(INK);
        }
        if (!layout.supported()) return;
        back = control(layout.back(), Component.translatable("gui.back"), () -> { if (!backFromRoute()) ctx.screen().onClose(); });
        back.setGlyphOnly(MusicTransportControl.Glyph.BACK);
        utility = control(layout.utility(), heading(Page.SETTINGS), () -> navigate(Page.SETTINGS));
        utility.setGlyphOnly(MusicControlRenderer.Icon.SETTINGS);
        for (var place : MusicFrontendLayout.DESTINATIONS) {
            var page = Page.valueOf(place.name());
            var tab = control(layout.tab(place), heading(page), () -> {
                if (routes.current().page() == Page.SEARCH) rememberSearchFocus();
                selectPrimary(page);
            });
            tab.setVariant(layout.mode() == MusicFrontendLayout.Mode.COMPACT
                    ? MusicTextControl.Variant.STACKED_NAVIGATION : MusicTextControl.Variant.NAVIGATION);
            tab.setLeadingIcon(switch (page) {
                case DISCOVER -> MusicControlRenderer.Icon.HOME;
                case LIBRARY -> MusicControlRenderer.Icon.LIBRARY;
                case SEARCH -> MusicControlRenderer.Icon.SEARCH;
                default -> throw new IllegalStateException("Unexpected primary destination: " + page);
            });
            nav.add(tab);
        }

        var area = layout.body();
        artistDetail.build(ctx, layout.content());
        comments.mount(ctx, area);
        var header = layout.header();
        history.mount(ctx, area, INK, MUTED);
        historyShortcut = control(new Bounds(header.right() - 68, header.y(), 64, 22),
                Component.translatable("sh_better_ui.online_music.operations.short_title"), () -> navigate(Page.HISTORY));
        historyShortcut.setInk(INK, MUTED);
        accounts.layout(ctx, area);
        sound.mount(ctx, area, back, utility);
        if (ctx.screen() instanceof UiScreen screen) screen.onFocusChanged(sound::rememberFocus);
        library.mount(ctx,area);
        nowPlaying = MusicFrontendNowPlayingLayout.fit(layout.frame(), false);
        playerDetail = MusicFrontendNowPlayingLayout.fit(layout.frame(), true);
        artistCredit = control(nowPlaying.subtitle(), Component.empty(), this::openArtistCredit);
        artistCredit.setTrailingGlyph(MusicTransportControl.Glyph.NEXT);
        artistCredit.setVisible(false);
        nowLyrics = ctx.add(new MusicTransportControl(nowPlaying.lyrics(), heading(Page.LYRICS),
                MusicTransportControl.Glyph.LYRICS, ctx.preferences(), () -> selectPlayerPane(Page.LYRICS)));
        nowQueue = ctx.add(new MusicTransportControl(nowPlaying.queue(), heading(Page.QUEUE),
                MusicTransportControl.Glyph.QUEUE, ctx.preferences(), () -> selectPlayerPane(Page.QUEUE)));
        nowComments = control(nowPlaying.comments(), MusicCommentsPanel.label("title"),
                () -> openComments(player.snapshot().track()));
        nowComments.setGlyphOnly(MusicControlRenderer.Icon.COMMENTS);
        nowComments.setPreview(nowComments.getMessage());

        int inset = 4, providerWidth = Math.min(104, area.width() / 2 - 8);
        int queryWidth = area.width() - 34 - providerWidth;
        search = ctx.add(new UiTextField(ctx.font(), new Bounds(area.x() + inset, area.y(), queryWidth, 22),
                heading(Page.SEARCH), 100, ctx.preferences()));
        MusicInputAppearance.apply(search);
        search.setHint(label("search_hint"));
        search.setValue(routes.searchDraft());
        search.setOnChange(routes::searchDraft);
        search.setOnSubmit(ignored -> startSearch());
        submit = control(new Bounds(area.x() + queryWidth + 6, area.y(), 22, 22), heading(Page.SEARCH), this::startSearch);
        submit.setGlyphOnly(MusicTransportControl.Glyph.SEARCH);
        source = control(new Bounds(area.right() - providerWidth, area.y(), providerWidth - 4, 22),
                label("search_source"), () -> { searchScroll.cancelDrag(); catalog.cycleSource(); refreshRows(); });
        int footer = area.bottom() - 22;
        retry = control(new Bounds(area.right() - 60, footer, 56, 22), label("search_retry"),
                () -> { catalog.retry(); refreshRows(); });
        int listHeight = Math.max(0, footer - area.y() - 24);
        searchRowHeight = listHeight >= 60 ? 30 : 24;
        searchRows = Math.min(12, listHeight / searchRowHeight);
        searchList = new Bounds(area.x()+inset, area.y()+24, area.width()-2*inset, searchRows*searchRowHeight);
        searchScroll = ctx.add(new OnlineMusicScrollRegion(searchList.x(), searchList.y(), searchList.width(),
                searchList.height(), label("search_results"),
                offset -> { catalog.scrollTo(offset, searchRows); refreshRows(); }));
        searchScroll.setOnBrowse(() -> ctx.screen().setFocused(searchScroll));
        searchScroll.onEnd(catalog::canExtend, () -> { catalog.extend(); refreshRows(); });
        int spill = MusicScrollRows.spill(ctx, searchRows);
        if (spill > 0) searchScroll.pixelRows(searchRowHeight, shift -> positionSearchRows());
        for (int i=0; i<Math.max(searchRows + spill, Math.min(12, area.height()/23)); i++) {
            final int index = i;
            var row = MusicScrollRows.add(ctx, new MusicTextControl(
                    new Bounds(area.x()+inset, area.y()+i*23, area.width()-2*inset, 22),
                    Component.empty(), ctx.preferences(), () -> selectRow(index)),
                    () -> routes.current().page() == Page.SEARCH ? searchList : layout.body());
            row.setInk(INK, MUTED);
            row.setVariant(MusicTextControl.Variant.ROW);
            rows.add(row);
            var comment = MusicScrollRows.add(ctx, new MusicTextControl(
                    new Bounds(searchList.right() - 24, searchList.y(), 24, searchRowHeight),
                    MusicCommentsPanel.label("title"), ctx.preferences(), () -> {
                        int ordinal = paintedFirst + index;
                        if (searchComments.get(index).visible && ordinal >= 0 && ordinal < paintedMatches.size())
                            openComments(paintedMatches.get(ordinal));
                    }), () -> searchList);
            comment.setInk(INK, MUTED);
            comment.setGlyphOnly(MusicControlRenderer.Icon.COMMENTS);
            comment.setPreview(comment.getMessage());
            searchComments.add(comment);
            searchCredits.add(new MusicTrackCredits(ctx, () -> searchList, this::openArtist, this::openAlbum));
        }
        lyric = ctx.add(new MusicLyricsView(playerDetail.detail(), heading(Page.LYRICS), ctx.preferences(), this::seekLyric));
        lyric.scale(playerDetail.split() ? 2.3f : 2f);
        lyric.immersiveReading(true);
        followLyrics = control(new Bounds(layout.frame().right() - 108, layout.frame().y() + 8, 96, 22),
                Component.translatable("sh_better_ui.online_music.view.follow_lyrics"), () -> {
                    if (!host.interactive() || !Minecraft.getInstance().isWindowActive()
                            || routes.current().page() != Page.LYRICS) return;
                    lyric.follow();
                    followLyrics.setVisible(false);
                    host.screen().setFocused(lyric);
                });
        followLyrics.setVisible(false);
        lyricContent = null;
        queuePanel = new MusicFrontendQueuePanel(ctx, playerDetail.detail(), playingNext);
        var controls = layout.transportControls();
        mini = new MusicFrontendMiniPlayer(ctx, new MusicFrontendMiniPlayer.Placement(layout.transport(), controls.track(),
                controls.previous(),controls.playPause(),controls.next(),controls.progress()),transport,
                () -> navigate(Page.NOW_PLAYING));
        refreshRows();
        restoreSearchAnchor();
        restoreSearchFocus();
        sound.restore(settingsResize);
        settingsResize = null;
    }

    private MusicTextControl control(Bounds b, Component label, Runnable action) {
        var control = host.add(new MusicTextControl(b, label, host.preferences(), action));
        control.setInk(INK, MUTED);
        return control;
    }
    private static Component label(String key, Object... args) { return Component.translatable("sh_better_ui.online_music.redesign." + key, args); }
    private static Component heading(Page page) { return label("page." + page.name().toLowerCase(java.util.Locale.ROOT)); }
    private void navigate(Page page) { navigate(MusicFrontendRoutes.Route.of(page)); }
    private void navigate(MusicFrontendRoutes.Route route) {
        if (routes.current().page() == Page.SEARCH) rememberSearchFocus();
        if (route.page().primary()) { selectPrimary(route.page()); return; }
        var origin = routes.current();
        var state = origin.page() != Page.SETTINGS ? null
                : (layout != null && layout.supported() ? sound.bookmark() : settingsResize);
        var discussion = origin.page() == Page.COMMENTS ? comments.bookmark() : null;
        if (!routes.open(route)) return;
        if (state != null) settingsReturns.put(origin, state);
        if (discussion != null) commentReturns.put(origin, discussion);
        changed();
        if (layout != null && layout.supported()) sound.focusEntry();
    }
    private static boolean playerPage(Page page) {
        return page == Page.NOW_PLAYING || page == Page.LYRICS || page == Page.QUEUE;
    }
    private void selectPlayerPane(Page page) {
        var current = routes.current().page();
        if (current == Page.LYRICS || current == Page.QUEUE) {
            backFromRoute();
            if (current == page) return;
        }
        navigate(page);
    }
    private void selectPrimary(Page page) {
        if (routes.current().page() == Page.SEARCH) rememberSearchFocus();
        if (!routes.select(page)) return;
        settingsReturns.clear();
        commentReturns.clear();
        changed();
    }
    private long artistEpoch(String source) {
        return musicOperations == null ? 0L : musicOperations.accountVersion(source);
    }

    private void openArtistCredit() {
        synchronized (player) {
            var link = artistLink;
            if (closed || routes.current().page() != Page.NOW_PLAYING || link == null
                    || !link.current(player, player.snapshot().track(), artistEpoch(link.artist().source()), routes.generation())) return;
            if (!routes.open(link.route())) return;
            artistDetail.open(link.artist());
        }
        changed();
        host.screen().setFocused(back);
    }

    private void openArtist(OnlineArtist artist, MusicTextControl origin) {
        if (!openDetail(new MusicFrontendRoutes.Route(Page.ARTIST, artist.source(), artist.id()), origin)) return;
        artistDetail.open(artist);
    }

    private void openAlbum(OnlineAlbum album, MusicTextControl origin) {
        if (!openDetail(new MusicFrontendRoutes.Route(Page.ALBUM, album.source(), album.id()), origin)) return;
        artistDetail.open(album);
    }

    private boolean openDetail(MusicFrontendRoutes.Route route, MusicTextControl origin) {
        navigate(route);
        if (routes.current() != route) return false;
        detailReturnFocus = origin;
        host.screen().setFocused(back);
        return true;
    }

    private boolean backFromRoute() {
        boolean fromArtist = routes.current().page().detail();
        if (fromArtist && artistDetail.back()) return true;
        boolean fromComments = routes.current().page() == Page.COMMENTS;
        if (!routes.back()) return false;
        if (fromComments) restoreNowComments = playerPage(routes.current().page());
        if (fromArtist && routes.current().page() == Page.NOW_PLAYING) restoreArtistCredit = true;
        if (fromArtist && back != null) host.screen().setFocused(back);
        changed();
        if (fromArtist) {
            var focus = detailReturnFocus;
            detailReturnFocus = null;
            if (focus != null && focus.visible && focus.active && host.screen().children().contains(focus))
                host.screen().setFocused(focus);
        }
        var discussion = commentReturns.remove(routes.current());
        if (discussion != null) {
            comments.restore(discussion);
            if (back != null && layout.supported()) host.screen().setFocused(back);
        }
        var state = settingsReturns.remove(routes.current());
        if (layout != null && layout.supported()) sound.restore(state);
        else settingsResize = state;
        return true;
    }
    private void browseLibrary(String url) {
        var context = host;
        var ticket = browserReturn.begin(routes, library.draft());
        if (!ShBetterUiApi.links().open(context.screen(), url,
                () -> OnlineMusicScreen.restoreRebuiltLibrary(context, this, ticket))) {
            browserReturn.discard(ticket);
            library.browserFailed();
        }
    }
    MusicTextControl restoreRebuiltLibrary(MusicFrontendWorkspace origin, MusicFrontendBrowserReturn.Ticket ticket) {
        if (closed || !origin.browserReturn.restore(ticket,origin.routes,origin.library.draft(),routes)) return null;
        if (origin != this) {
            if (search != null) search.setValue(routes.searchDraft());
            changed();
        }
        return library.restoreDraft(ticket.draft);
    }
    private List<String> discoveryProviders() {
        return player.sources().stream().filter(source -> source.equals("qq") || source.equals("netease")).distinct().toList();
    }
    private void changed() {
        if (routes.current().page() != Page.COMMENTS) comments.clear();
        var artistRoute = routes.current().page().detail();
        if (!artistRoute) artistDetail.cancel();
        else artistDetail.visible(true);
        artistLink = null;
        if (artistCredit != null) artistCredit.setVisible(false);
        settingsResize = null;
        catalog.detach();
        library.routeChanged();
        history.routeChanged();
        sound.cancel();
        status=""; lyricContent=null;
        if (mini != null) {
            mini.cancelInteraction();
            if (mini.trackFocused()) host.screen().setFocused(back);
        }
        if (nowLyrics != null) nowLyrics.cancelInteraction();
        if (nowQueue != null) nowQueue.cancelInteraction();
        if (nowComments != null) nowComments.cancelInteraction();
        if (searchScroll != null) searchScroll.cancelInteraction();
        rows.forEach(MusicTextControl::cancelInteraction);
        searchComments.forEach(MusicTextControl::cancelInteraction);
        refreshRows();
        restoreSearchAnchor();
        restoreSearchFocus();
        accounts.route(routes.current(), true);
    }
    private void rememberSearchFocus() {
        if (search == null || host == null || layout == null || !layout.supported()) return;
        Object feed = catalog.feedIdentity();
        searchAnchor = feed == null ? null : new SearchAnchor(feed, searchScroll.viewportOffset());
        var focused = host.screen().getFocused();
        if (focused == search) searchFocus = "query";
        else if (focused == submit) searchFocus = "submit";
        else if (focused == source) searchFocus = "source";
        else if (focused == retry) searchFocus = "retry";
        else if (focused == searchScroll) searchFocus = "scroll";
        else for (int i = 0; i < rows.size(); i++) {
            int ordinal = paintedFirst + i;
            if ((focused == rows.get(i) || focused == searchComments.get(i)) && paintedFirst >= 0 && ordinal < paintedMatches.size()) {
                searchFocus = focused == rows.get(i) ? "row" : "comments";
                searchFocusIndex = ordinal;
            }
        }
    }
    private void restoreSearchAnchor() {
        if (routes.current().page() != Page.SEARCH || searchScroll == null || !layout.supported()
                || searchAnchor == null) return;
        if (searchAnchor.feed() == catalog.feedIdentity()) searchScroll.restoreViewportOffset(searchAnchor.offset());
        else if (searchFocus.equals("row") || searchFocus.equals("comments")) searchFocus = "query";
        searchAnchor = null;
    }

    private void restoreSearchFocus() {
        if (routes.current().page() != Page.SEARCH || search == null || !layout.supported() || searchFocus.isEmpty()) return;
        var target = switch (searchFocus) {
            case "submit" -> submit;
            case "source" -> source;
            case "retry" -> retry;
            case "scroll" -> searchScroll;
            case "row", "comments" -> {
                int row = searchFocusIndex - paintedFirst;
                yield searchFocusIndex >= 0 && searchFocusIndex < paintedMatches.size()
                        && row >= 0 && row < rows.size() ? (searchFocus.equals("row") ? rows.get(row) : searchComments.get(row)) : search;
            }
            default -> search;
        };
        host.screen().setFocused(target.visible && target.active ? target : search);
    }
    private void startSearch() {
        status = "";
        searchScroll.cancelDrag();
        catalog.submit();
        refreshRows();
    }

    private void selectRow(int index) {
        var page=routes.current().page();
        switch (page) {
            case DISCOVER -> {
                var providers = discoveryProviders();
                if (index < providers.size()) navigate(new MusicFrontendRoutes.Route(Page.DISCOVERY, providers.get(index), "official"));
                else if (index == providers.size()) selectPrimary(Page.SEARCH);
            }
            case SEARCH -> {
                if (!rows.get(index).visible) return;
                int ordinal = paintedFirst + index;
                if (ordinal < 0 || ordinal >= paintedMatches.size()) return;
                var track = paintedMatches.get(ordinal);
                if (!player.supportsPlayback(track.source())) { openComments(track); return; }
                try {
                    if (catalog.play(paintedMatches, paintedFirst, index, searchRows + 1)) status="";
                } catch (OnlineMusicQueue.TransitionInProgress busy) { status="busy"; }
            }
            default -> { }
        }
    }
    private void seekLyric(long songPosition) {
        var content = lyricContent;
        if (!closed && routes.current().page() == Page.LYRICS && content != null && lyric.contentKey() == content
                && host.interactive() && Minecraft.getInstance().isWindowActive())
            transport.seekLyric(content, songPosition);
    }

    private void openComments(OnlineTrack track) {
        if (!comments.supports(track)) return;
        var route = new MusicFrontendRoutes.Route(Page.COMMENTS, track.source(), track.id());
        navigate(route);
        if (routes.current() != route) return;
        comments.open(track);
        host.screen().setFocused(back);
    }

    private void positionSearchRows() {
        if (routes.current().page() != Page.SEARCH) return;
        for (int slot = 0; slot < rows.size(); slot++) {
            var row = rows.get(slot);
            int y = searchList.y() + slot * searchRowHeight - searchScroll.rowShift();
            row.setY(y);
            row.setVisible(paintedFirst + slot >= 0 && paintedFirst + slot < paintedMatches.size()
                    && MusicScrollRows.intersects(y, searchRowHeight, searchList));
            var comment = searchComments.get(slot);
            comment.setY(y);
            int ordinal = paintedFirst + slot;
            var track = row.visible && ordinal >= 0 ? paintedMatches.get(ordinal) : null;
            boolean playable = track == null || player.supportsPlayback(track.source());
            boolean showComment = playable && comments.supports(track);
            row.setLeadingIcon(playable ? (MusicControlRenderer.Icon) null : MusicControlRenderer.Icon.COMMENTS);
            int right = searchList.right() - (searchScroll.active ? 8 : 0);
            row.setWidth(right - row.getX() - (showComment ? 24 : 0));
            boolean linked = searchCredits.get(slot).bind(player, searchRowHeight >= 28 ? track : null,
                    row.getX() + 6, y + searchRowHeight - 16, row.getWidth() - 8);
            row.setHeight(linked ? searchRowHeight - 16 : searchRowHeight);
            row.setSecondary(linked || track == null ? Component.empty() : MusicTypography.text(track.artist()));
            comment.setX(right - 24);
            comment.setVisible(showComment);
            comment.setAvailable(showComment);
        }
    }

    private void refreshRows() {
        if (layout==null || !layout.supported() || search==null) return;
        var page=routes.current().page();
        artistDetail.visible(page.detail());
        if (page != Page.NOW_PLAYING && artistCredit != null) artistCredit.setVisible(false);
        accounts.route(routes.current(), false);
        sound.update(routes.current());
        boolean searching = page == Page.SEARCH;
        historyShortcut.setVisible(searching && history.available());
        history.refresh();
        search.visible = searching; submit.setVisible(searching); source.setVisible(searching);
        searchScroll.visible = searching;
        retry.setVisible(searching && catalog.canRetry());
        if (searching) {
            var sourceId = catalog.source();
            var sourceName = switch (sourceId) {
                case "" -> label("search_no_sources");
                case "netease", "qq", "kugou", "listen1" -> Component.translatable("sh_better_ui.online_music.source." + sourceId);
                case "apple" -> MusicTypography.text("Apple Music");
                default -> MusicTypography.text(sourceId);
            };
            source.active = player.sources().size() > 1;
            var caption = source.active ? label("search_provider", sourceName) : sourceName;
            if (!caption.equals(source.getMessage())) source.rebind(caption);
            submit.active = !sourceId.isEmpty();
            catalog.scrollTo(catalog.first(), searchRows);
            searchScroll.configure(catalog.first(), catalog.tracks().size(), searchRows);
            if (paintedMatches != catalog.tracks() || paintedFirst != catalog.first()) {
                rows.forEach(MusicTextControl::cancelInteraction);
                searchComments.forEach(MusicTextControl::cancelInteraction);
                paintedMatches = catalog.tracks();
                paintedFirst = catalog.first();
            }
        }
        lyric.visible=page==Page.LYRICS;
        if (!lyric.visible) followLyrics.setVisible(false);
        boolean immersive = playerPage(page);
        var playerLayout = !immersive ? null : page == Page.NOW_PLAYING ? nowPlaying : playerDetail;
        var focused = host.screen().getFocused();
        if (immersive && (focused == utility || nav.contains(focused))) host.screen().setFocused(back);
        if (!immersive && (focused == nowLyrics || focused == nowQueue || focused == lyric)) host.screen().setFocused(back);
        utility.setVisible(!immersive);
        for (int i = 0; i < nav.size(); i++) {
            nav.get(i).setVisible(!immersive);
            nav.get(i).setSelected(MusicFrontendLayout.DESTINATIONS.get(i).name().equals(routes.rootPage().name()));
        }
        if (immersive) {
            nowLyrics.setX(playerLayout.lyrics().x()); nowLyrics.setY(playerLayout.lyrics().y());
            nowQueue.setX(playerLayout.queue().x()); nowQueue.setY(playerLayout.queue().y());
            nowComments.setX(playerLayout.comments().x()); nowComments.setY(playerLayout.comments().y());
        }
        nowLyrics.visible = immersive; nowQueue.visible = immersive;
        boolean engaged = immersive && Minecraft.getInstance().isWindowActive();
        nowLyrics.setAvailable(engaged); nowQueue.setAvailable(engaged);
        if (!immersive) nowComments.setVisible(false);
        mini.immersive(playerLayout);
        queuePanel.updateVisible(page == Page.QUEUE);
        List<OnlineTrack> shown=searching?paintedMatches:List.of();
        for(int i=0;i<rows.size();i++) {
            var item=rows.get(i);
            var body=layout.body();
            item.setWidth(body.width() - 8 - (searching && searchScroll.active ? 8 : 0));
            if (!searching) {
                item.setY(body.y()+i*23);
                item.setLeadingIcon((MusicControlRenderer.Icon) null);
                searchComments.get(i).setVisible(false);
                searchCredits.get(i).bind(player, null, 0, 0, 0);
            }
            item.setHeight(searching ? searchRowHeight : 22);
            Component primary=null, secondary=Component.empty();
            int ordinal = searching ? paintedFirst + i : i;
            if (page==Page.DISCOVER) {
                var providers = discoveryProviders();
                if (i<providers.size()) {
                    primary = Component.translatable("sh_better_ui.online_music.source." + providers.get(i));
                    secondary = heading(Page.DISCOVERY);
                } else if (i == providers.size()) primary = heading(Page.SEARCH);
            } else if (searching && i <= searchRows && ordinal >= 0 && ordinal < shown.size()) {
                primary=MusicTypography.text(shown.get(ordinal).title()); secondary=MusicTypography.text(shown.get(ordinal).artist());
            }
            if(primary!=null&&!primary.equals(item.getMessage())) item.rebind(primary);
            item.setSecondary(secondary); item.setVisible(primary!=null);
        }
        if (searching) positionSearchRows();
    }

    private void paintTabIndicator(UiFrame ui, float tabPosition) {
        int first = Math.max(0, Math.min(nav.size() - 1, (int) tabPosition));
        int second = Math.min(nav.size() - 1, first + 1);
        var from = layout.tab(MusicFrontendLayout.DESTINATIONS.get(first));
        var to = layout.tab(MusicFrontendLayout.DESTINATIONS.get(second));
        var box = MusicFrontendRouteMotion.interpolate(from, to, tabPosition - first);
        var ink = ui.preferences().highContrast() ? INK : ACCENT;
        if (layout.mode() == MusicFrontendLayout.Mode.DESKTOP) {
            ui.graphics().fill(box.x()+2, box.y()+4, box.x()+4, box.bottom()-4, ink);
        } else {
            int span = Math.min(16, box.width()/3), left = box.x()+(box.width()-span)/2;
            ui.graphics().fill(left, box.bottom()-3, left+span, box.bottom()-1, ink);
        }
    }

    @Override public void render(UiFrame ui) {
        contentFade = 0;
        if(closed||layout==null)return;
        var frame=layout.frame();
        boolean glass = MusicGlass.active();
        if (!layout.supported()) {
            paintBrowsingCanvas(ui, frame, glass);
            OnlineMusicViewPaint.line(ui, host.font(), label("small"), frame.x()+4, frame.y()+4, frame.width()-8, INK);
            return;
        }
        catalog.poll();
        refreshRows();
        searchScroll.tick();
        var route = routes.current();
        var page = route.page();
        int selectedTab = MusicFrontendLayout.DESTINATIONS.indexOf(MusicFrontendLayout.Route.valueOf(routes.rootPage().name()));
        var routeFrame = motion.sample(route, selectedTab, ui.nowMs(),
                ui.preferences().reducedMotion() || ui.preferences().highContrast());
        if (playerPage(page)) {
            renderPlayer(ui, page, routeFrame, glass);
            return;
        }
        paintBrowsingCanvas(ui, frame, glass);
        contentFade = routeFrame.contentFade();
        var header=layout.header();
        var panel=layout.navigation();
        if (MusicRainbowTheme.active(ui)) {
            MusicRainbowTheme.chrome(ui, panel, INK);
            MusicRainbowTheme.chrome(ui, layout.transport(), INK);
        } else if (glass) MusicGlass.chrome(ui, panel, layout.transport());
        else RoundedCorners.draw(ui.graphics(), panel.x(), panel.y(), panel.width(), panel.height(),
                layout.mode() == MusicFrontendLayout.Mode.DESKTOP ? 9 : 8, 0, 0, 0xff212124);
        paintTabIndicator(ui, routeFrame.tabPosition());
        for (int i = 0; i < nav.size(); i++) {
            boolean selected = i == selectedTab;
            nav.get(i).setInk(ui.preferences().highContrast() ? INK : selected ? ACCENT : MUTED, MUTED);
        }
        back.setInk(INK, MUTED);
        utility.setInk(INK, MUTED);
        var accountHeading = accounts.heading();
        boolean libraryRoute = MusicFrontendLibraryPanel.controls(page);
        if (libraryRoute) library.render(ui,MUTED);
        var soundHeading = sound.heading();
        if (!page.detail()) {
            var title = accountHeading != null ? accountHeading : libraryRoute ? library.heading()
                    : soundHeading != null ? soundHeading : heading(page);
            int titleInset = historyShortcut.visible ? 76 : page == Page.LYRICS ? 104 : 8;
            OnlineMusicViewPaint.scaledLine(ui,host.font(),title,header.x()+4,
                    header.y()+3+routeFrame.headingOffset(),header.width()-titleInset,INK,1.5f);
        }
        var body=layout.body();
        if (page == Page.SEARCH) {
            var feedback = catalog.loadingMore() ? continuation("loading")
                    : catalog.extensionFailed() ? continuation("error")
                    : switch (catalog.state()) {
                case LOADING -> label("loading");
                case FAILED -> label("failed");
                case EMPTY -> label("empty");
                case END -> continuation("end");
                case UNAVAILABLE -> Component.empty(); // The source control already describes this state.
                case IDLE -> !status.isEmpty() ? label(status) : catalog.tracks().isEmpty() ? Component.empty()
                        : Component.translatable("sh_better_ui.online_music.library.track_count", catalog.tracks().size());
            };
            OnlineMusicViewPaint.line(ui, host.font(), feedback, body.x() + 4, body.bottom() - 16,
                    body.width() - (catalog.canRetry() ? 68 : 8), MUTED);
        } else if (!page.detail() && !status.isEmpty()) OnlineMusicViewPaint.line(ui,host.font(),label(status),body.x()+4,
                body.y()+4,body.width()-8,MUTED);
        if (page.detail()) artistDetail.render(ui);
        if (page == Page.COMMENTS) {
            comments.show(true);
            comments.render(ui, INK, MUTED);
        }
        accounts.render(ui, MUTED);
        sound.render(ui, MUTED);
        history.render(ui, MUTED);
        mini.render(ui, glass || MusicRainbowTheme.active(ui));
    }

    private static Component continuation(String state) {
        return Component.translatable("sh_better_ui.online_music.continuation." + state);
    }

    private static void paintBrowsingCanvas(UiFrame ui, Bounds frame, boolean glass) {
        if (MusicRainbowTheme.active(ui)) MusicRainbowTheme.paint(ui, frame, INK);
        else if (glass && !ui.preferences().highContrast()) MusicGlass.canvas(ui, frame);
        else ui.graphics().fill(frame.x(), frame.y(), frame.right(), frame.bottom(),
                glass ? 0xff000000 : 0xff171719);
    }

    private void renderPlayer(UiFrame ui, Page page, MusicFrontendRouteMotion.Frame routeFrame, boolean glass) {
        var placement = page == Page.NOW_PLAYING ? nowPlaying : playerDetail;
        boolean engaged = host.interactive() && Minecraft.getInstance().isWindowActive();
        var presentation = page == Page.LYRICS ? transport.presentation(engaged) : null;
        var playback = presentation == null ? transport.frame() : presentation.transport();
        var song = playback.playback().track();
        boolean canRead = comments.supports(song);
        nowComments.setVisible(canRead);
        nowComments.setAvailable(canRead && engaged);
        if (!canRead && host.screen().getFocused() == nowComments) host.screen().setFocused(back);
        if (restoreNowComments) {
            restoreNowComments = false;
            if (canRead && engaged) host.screen().setFocused(nowComments);
        }
        if (MusicRainbowTheme.active(ui)) {
            MusicRainbowTheme.paint(ui, layout.frame(), INK);
            MusicRainbowTheme.chrome(ui, placement.transportChrome(), INK);
        } else {
            mini.renderBackdrop(ui, layout.frame(), song);
            if (glass) MusicGlass.chrome(ui, placement.transportChrome(), null);
        }
        back.setInk(INK, MUTED);
        if (page == Page.LYRICS || page == Page.QUEUE) {
            var selected = page == Page.LYRICS ? placement.lyrics() : placement.queue();
            boolean contrast = ui.preferences().highContrast();
            RoundedCorners.draw(ui.graphics(), selected.x() + 1, selected.y() + 2, selected.width() - 2,
                    selected.height() - 4, 10, contrast ? 1 : 0, INK, contrast ? 0 : 0x34ffffff);
        }
        var art = placement.cover();
        mini.paintCover(ui, page == Page.NOW_PLAYING ? routeFrame.artwork(art, mini.coverBounds()) : art, song);
        var title = placement.title();
        var artist = placement.subtitle();
        OnlineMusicViewPaint.scaledLine(ui, host.font(),
                MusicTypography.bold(song == null ? label("nothing_playing") : MusicTypography.text(song.title())),
                title.x(), title.y(), title.width(), INK, page == Page.NOW_PLAYING ? 1.65f : 1.45f);
        boolean linkedArtist = page == Page.NOW_PLAYING && paintArtistCredit(song);
        if (song != null && !linkedArtist) OnlineMusicViewPaint.line(ui, host.font(), MusicTypography.text(song.artist()),
                artist.x(), artist.y(), artist.width(), MUTED);
        if(page==Page.LYRICS) {
            var lyrics = presentation.lyrics();
            lyric.preferences(ui.preferences());
            lyric.palette(INK, 0xff9b909d, 0xff403642);
            lyric.active = engaged;
            if (!Objects.equals(lyricContent, lyrics.content())) {
                lyricContent = lyrics.content();
                lyric.setLines(lyricContent, OnlineLyricPresentation.lines(lyricContent.timeline()));
            }
            lyric.playback(lyrics.songPositionMs(), lyrics.seekable(), lyrics.preview());
            boolean manualBrowse = lyric.browsing() && !lyrics.preview() && lyric.model().synchronizedLyrics();
            if (!manualBrowse && host.screen().getFocused() == followLyrics) host.screen().setFocused(lyric);
            followLyrics.setVisible(manualBrowse);
            followLyrics.setAvailable(engaged);
        }
        queuePanel.render(ui, MUTED);
        mini.render(ui, glass, playback, engaged);
    }
    private boolean paintArtistCredit(OnlineTrack track) {
        MusicArtistLink next;
        synchronized (player) {
            next = MusicArtistLink.create(player, track, track == null ? 0 : artistEpoch(track.source()), routes.generation());
        }
        if (!Objects.equals(artistLink, next) || next != null && artistLink.track() != track) {
            artistCredit.cancelInteraction();
            artistLink = next;
            if (next != null) {
                artistCredit.rebind(MusicTypography.text(next.artist().name()));
                artistCredit.setPreview(Component.translatable("sh_better_ui.online_music.artist.open", next.artist().name()));
            }
        }
        artistCredit.setVisible(next != null);
        artistCredit.setAvailable(next != null && Minecraft.getInstance().isWindowActive());
        if (restoreArtistCredit) {
            restoreArtistCredit = false;
            if (next != null && host.screen().getFocused() == back) host.screen().setFocused(artistCredit);
        }
        return next != null;
    }

    /** Paint only the destination body; nav, MiniPlayer, keyboard focus and hit testing stay live. */
    void renderRouteHandoff(UiFrame ui) {
        if (closed || layout == null || contentFade <= 0) return;
        var body = layout.body();
        int background = MusicGlass.active() ? MusicGlass.DARK : 0xff171719;
        MusicRouteReveal.paint(ui, body, background, contentFade, contentFade / 52f);
    }

    boolean permitsScreenClose() { return !backFromRoute(); }
    @Override public void suspend() {
        artistDetail.cancel();
        comments.suspend();
        if (artistCredit != null) artistCredit.cancelInteraction();
        motion.reset();
        contentFade = 0;
        if (routes.current().page() == Page.SEARCH) rememberSearchFocus();
        if (ownsPlayer) catalog.cancel(); else catalog.detach();
        routes.invalidateRequests();
        library.close();
        accounts.suspend();
        sound.cancel();
        history.cancelInteraction();
        if (followLyrics != null) followLyrics.cancelInteraction();
        if (nowLyrics != null) nowLyrics.cancelInteraction();
        if (nowQueue != null) nowQueue.cancelInteraction();
        if (mini != null) mini.cancelInteraction();
        if (queuePanel != null) queuePanel.cancelInteraction();
    }
    @Override public void close() { if(!closed){suspend();closed=true;artistDetail.close();if(mini!=null)mini.close(); if(ownsPlayer)player.close();} }
}
