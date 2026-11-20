// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
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
    private final MusicFrontendSearch catalog;
    private final MusicFrontendAccountPanel accounts;
    private final MusicFrontendLibraryPanel library;
    private final List<MusicTextControl> nav = new ArrayList<>(), rows = new ArrayList<>();
    private PreviewContext host;
    private MusicFrontendLayout layout;
    private MusicFrontendMiniPlayer mini;
    private UiTextField search;
    private MusicTextControl back, utility, submit;
    private MusicTransportControl nowLyrics, nowQueue;
    private MusicFrontendNowPlayingLayout nowPlaying;
    private MusicLyricsView lyric;
    private MusicTextControl source, previous, next, retry;
    private OnlineMusicScrollRegion searchScroll;
    private int searchRows;
    private List<OnlineTrack> paintedMatches = List.of();
    private int paintedFirst = -1;
    private String searchFocus = "";
    private OnlineTrack searchFocusTrack;
    private String status = "";
    private MusicFrontendTransport.LyricContent lyricContent;
    private boolean closed;

    /** Independent previews and tests may still explicitly supply an owned player. */
    public MusicFrontendWorkspace(OnlineMusicPlayer player) { this(player, true); }
    private MusicFrontendWorkspace(OnlineMusicPlayer player, boolean ownsPlayer) {
        this.player = Objects.requireNonNull(player);
        this.ownsPlayer = ownsPlayer;
        transport = new MusicFrontendTransport(player);
        catalog = new MusicFrontendSearch(player, routes);
        playingNext = new MusicFrontendQueue(player);
        library = new MusicFrontendLibraryPanel(player,routes,this::navigate,this::browseLibrary);
        accounts = new MusicFrontendAccountPanel(new MusicFrontendAccount(player), new AppleMusicAccount(),
                () -> Minecraft.getInstance() != null && Minecraft.getInstance().options.chatLinks().get(), this::navigate);
    }
    public static MusicFrontendWorkspace borrowed(OnlineMusicPlayer player) {
        return new MusicFrontendWorkspace(player, false);
    }
    @Override public OnlineMusicPlayer player() { return player; }

    @Override public void layout(PreviewContext ctx) {
        if (closed) return;
        if (routes.current().page() == Page.SEARCH) rememberSearchFocus();
        host = ctx;
        if (mini != null) { mini.close(); mini = null; }
        layout = MusicFrontendLayout.compute(ctx.viewport(), MusicFrontendLayout.Route.valueOf(routes.current().page().name()), false);
        nav.clear(); rows.clear();
        if (!layout.supported()) return;
        back = control(layout.back(), Component.translatable("gui.back"), () -> { if (routes.back()) changed(); else ctx.screen().onClose(); });
        back.setGlyphOnly(MusicTransportControl.Glyph.BACK);
        utility = control(layout.utility(), label("accounts"), () -> navigate(Page.SETTINGS));
        utility.setGlyphOnly(MusicTransportControl.Glyph.USER);
        for (var place : MusicFrontendLayout.DESTINATIONS) {
            var page = Page.valueOf(place.name());
            var tab = control(layout.tab(place), heading(page), () -> {
                if (routes.current().page() == Page.SEARCH) rememberSearchFocus();
                if (routes.select(page)) changed();
            });
            tab.setVariant(MusicTextControl.Variant.NAVIGATION);
            nav.add(tab);
        }

        var area = layout.body();
        accounts.layout(ctx, area);
        library.mount(ctx,area);
        nowPlaying = MusicFrontendNowPlayingLayout.fit(area);
        nowLyrics = nowPlaying.usable() ? ctx.add(new MusicTransportControl(nowPlaying.lyrics(), heading(Page.LYRICS),
                MusicTransportControl.Glyph.LYRICS, ctx.preferences(), () -> navigate(Page.LYRICS))) : null;
        nowQueue = nowPlaying.usable() ? ctx.add(new MusicTransportControl(nowPlaying.queue(), heading(Page.QUEUE),
                MusicTransportControl.Glyph.QUEUE, ctx.preferences(), () -> navigate(Page.QUEUE))) : null;

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
                label("search_source"), () -> { catalog.cycleSource(); refreshRows(); });
        int footer = area.bottom() - 22;
        previous = control(new Bounds(area.x() + 4, footer, 22, 22), label("search_previous"),
                () -> { catalog.previous(searchRows); refreshRows(); });
        previous.setGlyphOnly(MusicTransportControl.Glyph.BACK);
        next = control(new Bounds(area.right() - 26, footer, 22, 22), label("search_next"),
                () -> { catalog.next(searchRows); refreshRows(); });
        next.setGlyphOnly(MusicTransportControl.Glyph.NEXT);
        retry = control(new Bounds(area.right() - 74, footer, 44, 22), label("search_retry"),
                () -> { catalog.retry(); refreshRows(); });
        searchRows = Math.min(12, Math.max(0, (area.height() - 44) / 24));
        searchScroll = ctx.add(new OnlineMusicScrollRegion(area.x() + inset, area.y() + 24,
                area.width() - 2 * inset, searchRows * 24, label("search_results"),
                offset -> { catalog.scrollTo(offset, searchRows); refreshRows(); }));
        searchScroll.setOnBrowse(() -> ctx.screen().setFocused(searchScroll));
        for (int i=0; i<Math.min(12, Math.max(0, (area.height()-29)/24)); i++) {
            final int index = i;
            var row = control(new Bounds(area.x()+inset, area.y()+28+i*24, area.width()-2*inset, 22),
                    Component.empty(), () -> selectRow(index));
            row.setVariant(MusicTextControl.Variant.ROW);
            rows.add(row);
        }
        lyric = ctx.add(new MusicLyricsView(area, heading(Page.LYRICS), ctx.preferences(), this::seekLyric));
        lyricContent = null;
        queuePanel = new MusicFrontendQueuePanel(ctx, area, playingNext);
        var controls = layout.transportControls();
        mini = new MusicFrontendMiniPlayer(ctx, new MusicFrontendMiniPlayer.Placement(layout.transport(), controls.track(),
                controls.previous(),controls.playPause(),controls.next(),controls.progress()),transport,
                () -> navigate(Page.NOW_PLAYING));
        refreshRows();
        restoreSearchFocus();
    }

    private MusicTextControl control(Bounds b, Component label, Runnable action) {
        return host.add(new MusicTextControl(b, label, host.preferences(), action));
    }
    private static Component label(String key, Object... args) { return Component.translatable("sh_better_ui.online_music.redesign." + key, args); }
    private static Component heading(Page page) { return label("page." + page.name().toLowerCase(java.util.Locale.ROOT)); }
    private void navigate(Page page) { navigate(MusicFrontendRoutes.Route.of(page)); }
    private void navigate(MusicFrontendRoutes.Route route) {
        if (routes.current().page() == Page.SEARCH) rememberSearchFocus();
        if (routes.open(route)) changed();
    }
    private void browseLibrary(String url) {
        var context = host;
        var route = routes.current();
        var root = routes.rootPage();
        String draft = library.draft();
        if (!ShBetterUiApi.links().open(context.screen(), url,
                () -> OnlineMusicScreen.restoreRebuiltLibrary(context, this, root, route, draft))) library.browserFailed();
    }
    MusicTextControl restoreRebuiltLibrary(Page root, MusicFrontendRoutes.Route route, String draft) {
        if (closed || !MusicFrontendLibraryPanel.controls(route.page())) return null;
        if (!routes.current().equals(route) || routes.rootPage()!=root) {
            routes.select(root);
            if (route.page() != Page.LIBRARY && !route.provider().isEmpty())
                routes.open(MusicFrontendRoutes.Route.forProvider(Page.DISCOVERY, route.provider()));
            if (route.page() != Page.LIBRARY) routes.open(route);
            changed();
        }
        return library.restoreDraft(draft);
    }
    private List<String> discoveryProviders() {
        return player.sources().stream().filter(source -> source.equals("qq") || source.equals("netease")).distinct().toList();
    }
    private void changed() {
        catalog.cancel();
        library.routeChanged();
        status=""; lyricContent=null;
        if (mini!=null) mini.cancelInteraction();
        if (searchScroll != null) searchScroll.cancelInteraction();
        rows.forEach(MusicTextControl::cancelInteraction);
        refreshRows();
        restoreSearchFocus();
        accounts.route(routes.current(), true);
    }
    private void rememberSearchFocus() {
        if (search == null || host == null) return;
        var focused = host.screen().getFocused();
        if (focused == search) searchFocus = "query";
        else if (focused == submit) searchFocus = "submit";
        else if (focused == source) searchFocus = "source";
        else if (focused == previous) searchFocus = "previous";
        else if (focused == next) searchFocus = "next";
        else if (focused == retry) searchFocus = "retry";
        else if (focused == searchScroll) searchFocus = "scroll";
        else for (int i = 0; i < rows.size(); i++) {
            int ordinal = paintedFirst + i;
            if (focused == rows.get(i) && paintedFirst >= 0 && ordinal < paintedMatches.size()) {
                searchFocus = "row";
                searchFocusTrack = paintedMatches.get(ordinal);
            }
        }
    }
    private void restoreSearchFocus() {
        if (routes.current().page() != Page.SEARCH || search == null || !layout.supported() || searchFocus.isEmpty()) return;
        var target = switch (searchFocus) {
            case "submit" -> submit;
            case "source" -> source;
            case "previous" -> previous;
            case "next" -> next;
            case "retry" -> retry;
            case "scroll" -> searchScroll;
            case "row" -> {
                int row = paintedMatches.indexOf(searchFocusTrack) - paintedFirst;
                yield row >= 0 && row < rows.size() ? rows.get(row) : search;
            }
            default -> search;
        };
        host.screen().setFocused(target.visible && target.active ? target : search);
    }
    private void startSearch() {
        status = "";
        catalog.submit();
        refreshRows();
    }

    private void selectRow(int index) {
        var page=routes.current().page();
        switch (page) {
            case DISCOVER -> {
                var providers = discoveryProviders();
                if (index < providers.size()) navigate(new MusicFrontendRoutes.Route(Page.DISCOVERY, providers.get(index), "official"));
                else if (index == providers.size() && routes.select(Page.SEARCH)) changed();
            }
            case SETTINGS -> { if (index==0) navigate(Page.ACCOUNTS); }
            case SEARCH -> {
                if (paintedMatches != catalog.tracks() || paintedFirst != catalog.first()) return;
                var matches = paintedMatches;
                int match = paintedFirst + index;
                if (index >= searchRows || match >= matches.size()) return;
                try {
                    var queue=player.queue();
                    if(queue==null) player.play(matches.get(match)); else queue.replace(matches,match);
                    status="";
                } catch (OnlineMusicQueue.TransitionInProgress busy) { status="busy"; }
            }
            default -> { }
        }
    }
    private void seekLyric(long songPosition) {
        var content = lyricContent;
        if (!closed && routes.current().page() == Page.LYRICS && content != null && lyric.contentKey() == content
                && Minecraft.getInstance().isWindowActive()) transport.seekLyric(content, songPosition);
    }

    private void refreshRows() {
        if (layout==null || !layout.supported() || search==null) return;
        var page=routes.current().page();
        accounts.route(routes.current(), false);
        boolean searching = page == Page.SEARCH;
        search.visible = searching; submit.setVisible(searching); source.setVisible(searching);
        searchScroll.visible = searching;
        previous.setVisible(searching); next.setVisible(searching); retry.setVisible(searching && catalog.canRetry());
        if (searching) {
            var sourceId = catalog.source();
            var sourceName = switch (sourceId) {
                case "" -> label("search_no_sources");
                case "netease", "qq", "listen1" -> Component.translatable("sh_better_ui.online_music.source." + sourceId);
                case "apple" -> MusicTypography.text("Apple Music");
                default -> MusicTypography.text(sourceId);
            };
            source.active = player.sources().size() > 1;
            var caption = source.active ? label("search_provider", sourceName) : sourceName;
            if (!caption.equals(source.getMessage())) source.rebind(caption);
            submit.active = !sourceId.isEmpty();
            catalog.scrollTo(catalog.first(), searchRows);
            searchScroll.configure(catalog.first(), catalog.tracks().size(), searchRows);
            previous.active = catalog.hasPrevious();
            next.active = catalog.hasNext(searchRows);
            if (paintedMatches != catalog.tracks() || paintedFirst != catalog.first()) {
                rows.forEach(MusicTextControl::cancelInteraction);
                paintedMatches = catalog.tracks();
                paintedFirst = catalog.first();
            }
        }
        lyric.visible=page==Page.LYRICS;
        if(nowLyrics!=null) {
            boolean show=page==Page.NOW_PLAYING;
            nowLyrics.visible=show; nowQueue.visible=show;
            nowLyrics.setAvailable(show); nowQueue.setAvailable(show);
        }
        for(int i=0;i<nav.size();i++) nav.get(i).setSelected(MusicFrontendLayout.DESTINATIONS.get(i).name().equals(routes.rootPage().name()));
        queuePanel.updateVisible(page == Page.QUEUE);
        List<OnlineTrack> shown=searching?paintedMatches:List.of();
        for(int i=0;i<rows.size();i++) {
            var item=rows.get(i);
            var body=layout.body();
            item.setWidth(body.width() - 8 - (searching && searchScroll.active ? 8 : 0));
            item.setY(body.y()+(searching?24:28)+i*24);
            Component primary=null, secondary=Component.empty();
            int ordinal = searching ? paintedFirst + i : i;
            if (page==Page.DISCOVER) {
                var providers = discoveryProviders();
                if (i<providers.size()) {
                    primary = Component.translatable("sh_better_ui.online_music.source." + providers.get(i));
                    secondary = heading(Page.DISCOVERY);
                } else if (i == providers.size()) primary = heading(Page.SEARCH);
            } else if ((!searching || i < searchRows) && ordinal < shown.size()) {
                primary=MusicTypography.text(shown.get(ordinal).title()); secondary=MusicTypography.text(shown.get(ordinal).artist());
            } else if(i==0) {
                primary=switch(page) {
                    case SETTINGS -> heading(Page.ACCOUNTS);
                    default -> null;
                };
            }
            if(primary!=null&&!primary.equals(item.getMessage())) item.rebind(primary);
            item.setSecondary(secondary); item.setVisible(primary!=null);
        }
    }

    @Override public void render(UiFrame ui) {
        if(closed||layout==null)return;
        var frame=layout.frame();
        ui.graphics().fill(frame.x(),frame.y(),frame.right(),frame.bottom(),0xff171719);
        if(!layout.supported()) { OnlineMusicViewPaint.line(ui,host.font(),label("small"),frame.x()+4,frame.y()+4,frame.width()-8,INK); return; }
        catalog.poll();
        refreshRows();
        var page=routes.current().page();
        var header=layout.header();
        var panel=layout.navigation();
        if(layout.mode()==MusicFrontendLayout.Mode.DESKTOP) {
            RoundedCorners.draw(ui.graphics(),panel.x(),panel.y(),panel.width(),panel.height(),9,0,0,0xff212124);
        }
        for(var tab:nav) tab.setInk(ui.preferences().highContrast()?INK:ACCENT,MUTED);
        var accountHeading = accounts.heading();
        boolean libraryRoute = MusicFrontendLibraryPanel.controls(page);
        if (libraryRoute) library.render(ui,MUTED);
        var title = accountHeading != null ? accountHeading : libraryRoute ? library.heading() : heading(page);
        OnlineMusicViewPaint.scaledLine(ui,host.font(),title,header.x()+4,header.y()+3,header.width()-8,INK,1.5f);
        var body=layout.body();
        if (page == Page.SEARCH) {
            var feedback = switch (catalog.state()) {
                case LOADING -> label("loading");
                case FAILED -> label("failed");
                case EMPTY -> label("empty");
                case END -> label("search_end");
                case UNAVAILABLE -> Component.empty(); // The source control already describes this state.
                case IDLE -> !status.isEmpty() ? label(status) : catalog.tracks().isEmpty() ? Component.empty()
                        : label("search_position", catalog.page(), (catalog.page() - 1) * 20 + catalog.first() + 1,
                                (catalog.page() - 1) * 20 + Math.min(catalog.first() + searchRows, catalog.tracks().size()));
            };
            OnlineMusicViewPaint.line(ui, host.font(), feedback, body.x() + 31, body.bottom() - 16,
                    body.width() - (catalog.canRetry() ? 107 : 62), MUTED);
        } else if (!status.isEmpty()) OnlineMusicViewPaint.line(ui,host.font(),label(status),body.x()+4,
                body.y()+4,body.width()-8,MUTED);
        if(page==Page.NOW_PLAYING && nowPlaying.usable()) {
            var song=player.snapshot().track();
            mini.paintCover(ui,nowPlaying.cover(),song);
            var trackTitle=nowPlaying.title(); var subtitle=nowPlaying.subtitle();
            OnlineMusicViewPaint.scaledLine(ui,host.font(),song==null?label("nothing_playing"):MusicTypography.text(song.title()),
                    trackTitle.x(),trackTitle.y(),trackTitle.width(),INK,1.4f);
            if(song!=null && subtitle.height()>=MusicTypography.LINE_HEIGHT) OnlineMusicViewPaint.line(ui,host.font(),MusicTypography.text(song.artist()),
                    subtitle.x(),subtitle.y(),subtitle.width(),MUTED);
        }
        accounts.render(ui, MUTED);
        if(page==Page.LYRICS) {
            var lyrics = transport.lyrics(Minecraft.getInstance().isWindowActive());
            if (!Objects.equals(lyricContent, lyrics.content())) {
                lyricContent = lyrics.content();
                lyric.setLines(lyricContent, OnlineLyricPresentation.lines(lyricContent.timeline()));
            }
            lyric.playback(lyrics.songPositionMs(), lyrics.seekable(), lyrics.preview());
        }
        queuePanel.render(ui, MUTED);
        mini.render(ui);
    }
    boolean permitsScreenClose() { if (!routes.back()) return true; changed(); return false; }
    @Override public void suspend() { if (routes.current().page() == Page.SEARCH) rememberSearchFocus(); catalog.cancel(); routes.invalidateRequests();library.close();accounts.suspend();if(mini!=null)mini.cancelInteraction();if(queuePanel!=null)queuePanel.cancelInteraction(); }
    @Override public void close() { if(!closed){suspend();closed=true;if(mini!=null)mini.close(); if(ownsPlayer)player.close();} }
}
