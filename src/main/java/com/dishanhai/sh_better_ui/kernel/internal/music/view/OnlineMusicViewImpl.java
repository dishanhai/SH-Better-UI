// Author: dishanhai. Layout adapted from MuoniumPlayer NCMScreen, NavigateBar,
// ControlsBar, MusicLyricsPanel and AccountManagerOverlay (Apache-2.0).
// See META-INF/third-party/muonium-player/SOURCE for revision and modifications.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Complete UUK player page; all widgets remain native and all asynchronous work belongs to the session. */
public final class OnlineMusicViewImpl implements OnlineMusicView {
    private enum Page { SEARCH, LIBRARY, LYRICS, QUEUE, ACCOUNTS }
    private boolean immersive, stageFailure;
    private net.minecraft.client.gui.components.events.GuiEventListener shellBack;
    private Page stageReturnPage = Page.SEARCH, lyricsReturnPage = Page.SEARCH;
    private FullscreenLyricLayout stage;
    private final List<AbstractWidget> stageControls = new ArrayList<>();
    private enum StagePage { ARTWORK, LYRICS, QUEUE }
    private StagePage stagePage=StagePage.LYRICS;
    private final MusicArtworkStage artworkStage=new MusicArtworkStage();
    private final Map<MusicTextControl,Object> resultIdentities=new IdentityHashMap<>();
    private MusicSegmentedControl sourceSegments;
    private List<String> searchSourceIds=List.of();
    private final com.dishanhai.sh_better_ui.kernel.api.music.status.MusicStatusView searchStatus = new com.dishanhai.sh_better_ui.kernel.api.music.status.MusicStatusView();
    private final com.dishanhai.sh_better_ui.kernel.api.music.status.MusicStatusView queueStatus = new com.dishanhai.sh_better_ui.kernel.api.music.status.MusicStatusView();
    private MusicTransportControl exitControl;
    private MusicTransportControl stageBack,stagePlay,stagePrevious,stageNext,stageMode,stageQueue;
    private MusicStageButton stageEntry,coverEntry,stageCoverEntry;
    private MusicTextControl stageFollow;
    private MusicSeekControl stageSeek,stageVolume;
    private MusicLyricsView stageLyrics;
    private Object stageLyricKey;
    private OnlineTrack stageLyricTrack;
    private com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline stageTimeline;
    private final List<MusicTextControl> stageQueueRows=new ArrayList<>();
    private OnlineMusicScrollRegion stageQueueScroll;
    private int stageQueueFirst;
    private enum Library { NONE, PLAYLISTS, TRACKS, LIKED }
    private String librarySource="netease";
    private List<String> libraryProviders=List.of();
    private Library libraryGroup=Library.PLAYLISTS;
    private boolean libraryLoginReturn;
    private MusicSegmentedControl librarySources, libraryGroups;
    private MusicTextControl libraryLogin;
    private final List<AbstractWidget> libraryHeaderControls=new ArrayList<>();
    private static final String TEXT = "sh_better_ui.online_music.";
    private final OnlineMusicPlayer player;
    private final List<AbstractWidget> searchControls = new ArrayList<>(), lyricControls = new ArrayList<>(),
            accountControls = new ArrayList<>(), detailControls = new ArrayList<>(), libraryControls = new ArrayList<>();
    private final List<MusicTextControl> resultRows = new ArrayList<>();
    private final List<OnlineLyricButton> lyricRows = new ArrayList<>();
    private final PlayerQueue queue;
    private final List<AbstractWidget> queueControls = new ArrayList<>();
    private final List<MusicTextControl> queueRows = new ArrayList<>(), queueRemoves = new ArrayList<>();
    private OnlineMusicScrollRegion queueScroll;
    private MusicTextControl queuePrevious, queueNext, queueUndo, queueClear, queueLocate;
    private int queueFirst;
    private MusicTextControl queuePlaybackMode, stagePlaybackMode;
    private final LyricViewport lyricViewport = new LyricViewport();
    private final OnlineArtworkTexture artwork = new OnlineArtworkTexture();
    private OnlineTrack lyricTrack;
    private int activeLyric;
    private MusicStageButton followLyrics;
    private OnlineMusicScrollRegion searchScroll, libraryScroll, lyricsScroll, accountScroll;
    private int accountOffset;
    private final Map<AbstractWidget, Integer> accountPositions = new IdentityHashMap<>();
    private final List<MusicTextControl> libraryRows = new ArrayList<>(), searchLikes = new ArrayList<>(), searchUnlikes = new ArrayList<>(),
            libraryLikes = new ArrayList<>(), libraryUnlikes = new ArrayList<>();
    private final List<MusicTextControl> navigation = new ArrayList<>();
    private final Map<String, MusicTextControl> sources = new LinkedHashMap<>(), accounts = new LinkedHashMap<>(), loginSources = new LinkedHashMap<>();
    private PreviewContext host;
    private OnlineMusicLayout layout;
    private UiTextField queryField;
    private MusicTextControl searchButton, previous, next, nowPlaying, back;
    private MusicTransportControl playback, stop, lyricsToggle, miniQueue, miniPrevious, miniNext;
    private MusicTextControl loginRefresh, loginImport, loginSignOut;
    private final Map<String, Boolean> confirmedLikes = new HashMap<>();
    private OnlineTrack likeTrack;
    private boolean requestedLike;
    private MusicTextControl remember, libraryPrevious, libraryNext, libraryRefresh;
    private MusicSeekControl seek;
    private final OnlineMusicViewMotion visualMotion = new OnlineMusicViewMotion();
    private final com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicVinylStage vinylStage = new com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicVinylStage();
    private String musicThemeId() {
        var settings=com.dishanhai.sh_better_ui.config.ClientUiSettings.current().theme();
        return settings.mode()==com.dishanhai.sh_better_ui.config.ThemeSettings.Mode.CUSTOM?settings.registeredThemeId():"";
    }
    private boolean qqStyle() { return com.dishanhai.sh_better_ui.theme.QqMusicThemes.matches(musicThemeId()); }
    private boolean vinylStyle() { return com.dishanhai.sh_better_ui.theme.QqMusicThemes.VINYL.equals(musicThemeId()); }
    private void qqBackground(UiFrame ui,Bounds area) {
        var palette=vinylStyle()
            ? new com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette(0xff55402b,0xff30271e,0xff241a12,0xff453020)
            : new com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette(0xff542630,0xff123f52,0xff41451c,0xff37204e);
        com.dishanhai.sh_better_ui.client.gui.scaled.MusicArtworkStageRender.background(ui.graphics(),area,palette,0,true);
    }
    private Page page = Page.SEARCH;
    private String source = "netease", accountSource = "netease", query = "", submittedQuery = "", searchState = "search_hint";
    private List<OnlineTrack> results = List.of();
    private CompletableFuture<List<OnlineTrack>> pending;
    private CompletableFuture<List<String>> pendingSuggestions;
    private CompletableFuture<List<OnlinePlaylist>> pendingPlaylists;
    private CompletableFuture<List<OnlineTrack>> pendingLibrary;
    private CompletableFuture<OnlineMusicAccount> pendingAccount;
    private CompletableFuture<Void> pendingLike;
    private OnlineMusicLogin login;
    private boolean accountDetail, closed, accountWasSignedIn;
    private int remotePage = 1, first, visibleRows;
    private Library library = Library.NONE;
    private OnlinePlaylist selectedPlaylist;
    private List<OnlinePlaylist> playlists = List.of();
    private List<OnlineTrack> libraryTracks = List.of();
    private List<String> suggestions = List.of();
    private String libraryState = "library.empty", accountState = "", accountName = "", likeState = "", suggestedQuery = "";
    private int libraryPage = 1, libraryFirst, savedPlaylistPage = 1, savedPlaylistFirst;
    private long suggestAfter;
    private final List<LyricSeekTarget> lyricTargets = new ArrayList<>();
    private long lyricOffset;

    private final boolean ownsPlayer;
    public OnlineMusicViewImpl(OnlineMusicPlayer player) { this(player, true); }
    /** Borrowed sessions are released by their kernel service, never by screen removal. */
    public OnlineMusicViewImpl(OnlineMusicPlayer player, boolean ownsPlayer) { this.player = Objects.requireNonNull(player); this.queue = new PlayerQueue(player); this.ownsPlayer = ownsPlayer; }
    @Override public OnlineMusicPlayer player() { return player; }
    @Override public void layout(PreviewContext context) {
        if (closed) throw new IllegalStateException("Music view closed");
        if(stagePlaybackMode!=null)stagePlaybackMode.cancelInteraction();
        if(queuePlaybackMode!=null)queuePlaybackMode.cancelInteraction();
        navigation.forEach(MusicTextControl::cancelInteraction); if(sourceSegments!=null)sourceSegments.cancelInteraction();if(librarySources!=null)librarySources.cancelInteraction();if(libraryGroups!=null)libraryGroups.cancelInteraction(); navigation.clear();
        host = context; layout = OnlineMusicLayout.of(context.viewport()); stage = null; queryField = null;
        stageControls.clear(); stageQueueRows.clear(); stageLyricKey=null;
        queueControls.clear(); queueRows.clear(); queueRemoves.clear();
        sourceSegments=null;resultIdentities.clear();
        libraryHeaderControls.clear();librarySources=null;libraryGroups=null;
        searchControls.clear(); lyricControls.clear(); accountControls.clear(); detailControls.clear(); libraryControls.clear();
        resultRows.clear(); lyricRows.clear(); libraryRows.clear(); searchLikes.clear(); searchUnlikes.clear(); libraryLikes.clear(); libraryUnlikes.clear(); lyricTargets.clear(); sources.clear(); accounts.clear(); loginSources.clear();
        exitControl = OnlineMusicScreen.exitControl(context);
        if (!layout.supported()) return;
        shellBack = OnlineMusicScreen.shellBack(context);
        stageFailure = player.snapshot().state()==OnlineMusicPlayer.State.FAILED;
        stage = FullscreenLyricLayout.of(context.viewport(),shellBack==null,stageFailure,stagePage!=StagePage.ARTWORK);
        buildNavigation(); buildScrollRegions(); buildSearch(); buildLyrics(); buildAccounts(); buildLibrary(); buildQueue(); buildTransport(); buildImmersive();
        var h = layout.searchBar();
        back = button(new Bounds(h.x(), h.y() + 2, Math.min(88, h.width() / 2), 22), label("view.back"), () -> {
            if(page==Page.LIBRARY&&library==Library.TRACKS) {
                cancelLibrary();library=Library.PLAYLISTS;libraryPage=savedPlaylistPage;libraryFirst=savedPlaylistFirst;
                libraryState=playlists.isEmpty()?"library.empty":"library.results";updateVisibility();
            } else if(page==Page.ACCOUNTS&&libraryLoginReturn) { libraryLoginReturn=false;enterLibrary(); }
            else if(page==Page.ACCOUNTS&&accountDetail){leaveAccountDetail();updateVisibility();}
            else show(page == Page.LYRICS ? lyricsReturnPage : Page.SEARCH);
        }, null);
        back.setLeadingIcon(GraphicIcon.BACK);
        updateRows(); updateLibraryRows(); updateVisibility();
    }
    private void buildNavigation() {
        var n = layout.navigation();
        if (exitControl != null) { exitControl.setX(n.x()); exitControl.setY(n.y()); }
        int offset=exitControl==null?0:30;
        int tabWidth=Math.max(1,(n.width()-offset)/Page.values().length);
        for(var target:Page.values()) {
            var box=layout.sidebar()?new Bounds(n.x(),n.y()+34+target.ordinal()*Math.min(30,(n.height()-34)/Page.values().length),n.width(),Math.min(28,(n.height()-34)/Page.values().length-2))
                    :new Bounds(n.x()+offset+target.ordinal()*tabWidth,n.y(),tabWidth,26);
            var item=button(box,label("view."+target.name().toLowerCase(Locale.ROOT)),()->navigate(target),null);
            item.setVariant(MusicTextControl.Variant.NAVIGATION);item.setPreview(item.getMessage());
            if(!layout.sidebar()&&tabWidth<60) item.setIcon(switch(target){case SEARCH->GraphicIcon.SEARCH;case LYRICS->GraphicIcon.MUSIC;case ACCOUNTS->GraphicIcon.USER;case QUEUE->GraphicIcon.QUEUE;case LIBRARY->GraphicIcon.FAVORITE;});
            if(target==Page.LIBRARY){if(!layout.sidebar()&&tabWidth<60)item.setGlyphOnly(MusicControlRenderer.Icon.LIBRARY);else item.setLeadingIcon(MusicControlRenderer.Icon.LIBRARY);}
            if(target==Page.ACCOUNTS){if(!layout.sidebar()&&tabWidth<60)item.setGlyphOnly(MusicControlRenderer.Icon.USER);else item.setLeadingIcon(MusicControlRenderer.Icon.USER);}
            navigation.add(item);
        }
    }
    private List<Bounds> musicRows() {
        return layout.rows().stream().map(row -> new Bounds(row.x(), row.y(), Math.max(1, row.width() - 10), row.height())).toList();
    }
    private Bounds listScrollArea() {
        var c = layout.content();
        return new Bounds(c.x(), c.y() + layout.listHeadingHeight(), c.width(), Math.max(1, layout.pager().y() - c.y() - layout.listHeadingHeight()-4));
    }
    private void buildScrollRegions() {
        var area = listScrollArea();
        searchScroll = host.add(new OnlineMusicScrollRegion(area.x(), area.y(), area.width(), area.height(), label("view.scroll_search"), offset -> {
            first = offset; updateRows();
        }));
        libraryScroll = host.add(new OnlineMusicScrollRegion(area.x(), area.y(), area.width(), area.height(), label("view.scroll_library"), offset -> {
            libraryFirst = offset; updateLibraryRows();
        }));
        queueScroll = host.add(new OnlineMusicScrollRegion(area.x(), area.y(), area.width(), area.height(), label("queue.scroll"), offset -> {
            queueFirst = offset; updateQueueRows();
        }));
        var lyrics = lyricContent();
        lyricsScroll = host.add(new OnlineMusicScrollRegion(lyrics.x(), lyrics.y(), lyrics.width(), Math.max(1, lyricArea().bottom() - lyrics.y()), label("view.scroll_lyrics"), offset -> {
            lyricViewport.browseTo(offset, player.snapshot().lyrics().size(), System.nanoTime() / 1_000_000L);
        }));
        var c = layout.content();
        accountScroll = host.add(new OnlineMusicScrollRegion(c.x(), c.y(), c.width(), c.height(), label("view.scroll_account"), offset -> {
            accountOffset = offset; positionAccounts();
        }));
        accountScroll.setStep(24);
        for (var scroll : new OnlineMusicScrollRegion[]{searchScroll, libraryScroll, lyricsScroll, accountScroll, queueScroll})
            scroll.setOnBrowse(() -> { if (host.screen().getFocused() != scroll) host.screen().setFocused(scroll); });
    }
    private void scrollVisibility(OnlineMusicScrollRegion scroll, boolean visible) {
        if (!visible && scroll.visible) scroll.cancelDrag();
        scroll.visible = visible;
    }
    private void updateScrollRegions() {
        if (searchScroll == null) return;
        scrollVisibility(searchScroll, page == Page.SEARCH);
        int size = showSuggestions() ? suggestions.size() : results.size();
        int clamped = Math.min(first, Math.max(0, size - visibleRows));
        if (first != clamped) { first = clamped; updateRows(); }
        searchScroll.configure(first, size, visibleRows);
        scrollVisibility(libraryScroll, page == Page.LIBRARY);
        clamped = Math.min(libraryFirst, Math.max(0, librarySize() - visibleRows));
        if (libraryFirst != clamped) { libraryFirst = clamped; updateLibraryRows(); }
        libraryScroll.configure(libraryFirst, librarySize(), visibleRows);
        scrollVisibility(queueScroll, page == Page.QUEUE);
        queueFirst = Math.min(queueFirst, Math.max(0, queue.tracks().size() - visibleRows));
        queueScroll.configure(queueFirst, queue.tracks().size(), visibleRows);
        scrollVisibility(lyricsScroll, page == Page.LYRICS);
        scrollVisibility(accountScroll, page == Page.ACCOUNTS);
        accountOffset = Math.min(accountOffset, Math.max(0, accountContent(0).height() - layout.content().height()));
        accountScroll.configure(accountOffset, accountContent(0).height(), layout.content().height());
    }
    private Bounds accountContent(int offset) {
        var c = layout.content();
        // Fixed controls and provider QR content remain reachable even in the smallest window.
        int minimum = accountDetail ? 340 : Math.max(320, 214 + host.font().split(label("login.session_only"), Math.max(1, c.width() - 28)).size() * (host.font().lineHeight + 3));
        return accountBounds(offset, minimum);
    }
    private Bounds accountBounds(int offset, int minimum) {
        var c = layout.content();
        return new Bounds(c.x(), c.y() - offset, Math.max(1, c.width() - 12), Math.max(c.height(), minimum));
    }
    private void positionAccounts() {
        var clip = layout.content();
        for (var entry : accountPositions.entrySet()) {
            var widget = entry.getKey(); widget.setY(entry.getValue() - accountOffset);
            boolean relevant = accountControls.contains(widget) ? page == Page.ACCOUNTS && !accountDetail
                    : page == Page.ACCOUNTS && accountDetail;
            widget.visible = relevant && widget.getY() >= clip.y() && widget.getY() + widget.getHeight() <= clip.bottom();
            if (!widget.visible && widget.isFocused() && !accountScroll.dragging()) host.screen().setFocused(accountScroll);
        }
    }
    private Component sourceLabel(String id) {
        return switch(id){case "netease","qq"->label("source."+id);case "apple"->MpTypography.text("Apple Music");default->MpTypography.text(id);};
    }
    private void buildSearch() {
        var h = layout.searchBar();
        searchSourceIds=List.copyOf(player.sources());
        if(!searchSourceIds.isEmpty()&&!searchSourceIds.contains(source))source=searchSourceIds.get(0);
        if(searchSourceIds.size()>=2&&searchSourceIds.size()<=5) {
            sourceSegments=host.add(new MusicSegmentedControl(layout.sourceBar(),label("view.sources"),
                    searchSourceIds.stream().map(this::sourceLabel).toList(),searchSourceIds.indexOf(source),host.preferences(),
                    index->selectSource(searchSourceIds.get(index))));
            searchControls.add(sourceSegments);
        } else {
            var provider=button(layout.sourceBar(),searchSourceIds.isEmpty()?label("view.unavailable"):sourceLabel(source),()->{},searchControls);
            provider.active=false;
        }
        queryField = host.add(new UiTextField(host.font(), new Bounds(h.x(), h.y() + 2, h.width() - 28, 22), label("query"), 200, host.preferences()));
        MusicInputAppearance.apply(queryField);queryField.active=!searchSourceIds.isEmpty();
        searchControls.add(queryField);
        queryField.setValue(query); queryField.setHint(label("query")); queryField.setOnChange(this::queryChanged);
        queryField.setOnSubmit(value -> { query = value; search(1, true); });
        searchButton = button(new Bounds(h.right() - 24, h.y() + 2, 24, 22), label("search"), () -> {
            if (pending != null) { cancelSearch(); searchState = "cancelled"; updateRows(); }
            else search(1, true);
        }, searchControls);
        searchButton.setIcon(GraphicIcon.SEARCH);
        visibleRows = layout.rows().size();
        for (var row : musicRows()) {
            int slot = resultRows.size();
            int titleWidth = resultTitleWidth(row);
            var button = button(new Bounds(row.x(), row.y(), titleWidth, row.height()), Component.empty(), () -> {
                int i = first + slot;
                if (showSuggestions()) { if (i < suggestions.size()) { String selected = suggestions.get(i); queryField.setValue(selected); query = selected; search(1, true); } }
                else if (i < results.size()) playCollection(results, i);
            }, searchControls);
            button.setLeadingIcon(GraphicIcon.RUN);
            button.setVariant(MusicTextControl.Variant.ROW); resultRows.add(button);
            searchLikes.add(trackAction(row, slot, false, true));
            searchUnlikes.add(trackAction(row, slot, false, false));
        }
        var pager = layout.pager();
        previous = button(new Bounds(pager.x(), pager.y(), 24, 20), label("previous"), () -> {
            if (showSuggestions()) { first = Math.max(0, first - visibleRows); updateRows(); return; }
            if (first > 0) { first = Math.max(0, first - visibleRows); updateRows(); }
            else if (remotePage > 1) search(remotePage - 1, false);
        }, searchControls); previous.setIcon(GraphicIcon.PREVIOUS);
        next = button(new Bounds(pager.right() - 24, pager.y(), 24, 20), label("next"), () -> {
            if (showSuggestions()) { if (first + visibleRows < suggestions.size()) first += visibleRows; updateRows(); return; }
            if (first + visibleRows < results.size()) { first += visibleRows; updateRows(); }
            else if (results.size() == 20) search(remotePage + 1, false);
        }, searchControls); next.setIcon(GraphicIcon.NEXT);
    }
    private void buildLyrics() {
        var lines = lyricArea();
        int count = Math.max(1, Math.min(25, Math.max(lines.height(), stage.lyrics().height()) / 24 + 3));
        for (int i = 0; i < count; i++) {
            int slot = i; lyricTargets.add(null);
            var row = host.add(new OnlineLyricButton(lines.x(), lines.y(), Math.max(1, lines.width() - 10), 24, () -> {
                var target = lyricTargets.get(slot);
                var position = target == null ? java.util.OptionalLong.empty() : target.position(player.snapshot());
                if (position.isPresent()) {
                    player.seek(position.getAsLong());
                    lyricViewport.follow();

                }
            }, host.preferences(), direction -> {
                host.screen().setFocused(immersive ? stageReturnControl() : back);
                lyricViewport.scroll(direction, activeLyric, player.snapshot().lyrics().size(), System.nanoTime() / 1_000_000L);
            }));
            lyricRows.add(row); lyricControls.add(row);
        }
        followLyrics = host.add(new MusicStageButton(new Bounds(lines.x(), lines.bottom() + 2, Math.min(112, lines.width()), 20),
                label("view.follow_lyrics"),null,lyricViewport::follow,host.preferences(),false,false));
        lyricControls.add(followLyrics);
    }
    private void buildAccounts() {
        accountPositions.clear();
        var c = accountBounds(0, 320); int width = c.width();
        int left = c.x();
        for (String id : List.of("netease", "qq")) {
            final String selected = id; int i = accounts.size();
            var card = button(new Bounds(left, c.y() + 4 + i * 56, width, 44), label("source." + id), () -> {
                openAccount(selected);
                updateVisibility(); host.screen().setFocused(back);
            }, accountControls);
            card.setVariant(MusicTextControl.Variant.ROW); card.setLeadingIcon(GraphicIcon.USER); card.setMessage(label("account.manage", label("source." + id))); accounts.put(id, card);
            var tab = button(new Bounds(c.x() + i * (c.width() / 2 + 2), c.y(), (c.width() - 4) / 2, 22), label("source." + id), () -> {
                if (!accountSource.equals(selected)) { openAccount(selected); }
                updateVisibility();
            }, detailControls);
            loginSources.put(id, tab);
        }
        remember = button(new Bounds(c.x(), c.y() + 24, c.width(), 20), label("login.remember_off"), () -> {
            try { player.rememberCredentials(accountSource, !player.remembersCredentials(accountSource)); accountState = "login.preference_saved"; }
            catch (RuntimeException failure) { accountState = "login.storage_failed"; }
            updateVisibility();
        }, detailControls);
        remember.setPreview(label("login.remember_hint"));
        int half = (c.width() - 4) / 2, third = (c.width() - 8) / 3;
        loginRefresh = button(new Bounds(c.x(), c.y() + 48, third, 20), label("login.refresh"), () -> { if (player.authenticated(accountSource) && !accountState.equals("library.login_required") && login == null) loadAccount(); else startLogin(); }, detailControls);
        loginSignOut = button(new Bounds(c.x() + (third + 4) * 2, c.y() + 48, c.width() - (third + 4) * 2, 20), label("login.sign_out"), () -> {
            try { cancelLogin(); cancelLibrary(); clearAccountFeedback(); player.credentials(accountSource, "");invalidateLibrarySource(accountSource); accountName = ""; accountState = "login.signed_out"; }
            catch (RuntimeException failure) { accountState = "login.storage_failed"; }
            updateVisibility();
        }, detailControls);
        loginImport = button(new Bounds(c.x() + third + 4, c.y() + 48, third, 20), label("login.import"), () -> {
            try { cancelLibrary(); clearAccountFeedback(); player.credentials(accountSource, OnlineMusicViewPaint.clipboard().strip());invalidateLibrarySource(accountSource); accountState = "credentials_loaded"; cancelLogin(); loadAccount(); }
            catch (IllegalArgumentException failure) { accountState = "credentials_invalid"; }
            catch (RuntimeException failure) { accountState = "login.storage_failed"; }
            updateVisibility();
        }, detailControls);
        loginImport.setPreview(label("login.import_hint"));
        if(c.width()<360) {
            loginRefresh.setWidth(half); loginRefresh.setHeight(24);
            loginImport.setX(c.x()+half+4);loginImport.setWidth(half);loginImport.setHeight(24);
            loginSignOut.setX(c.x());loginSignOut.setY(c.y()+76);loginSignOut.setWidth(c.width());loginSignOut.setHeight(24);
        }
        accountControls.forEach(w -> accountPositions.put(w, w.getY()));
        detailControls.forEach(w -> accountPositions.put(w, w.getY()));
    }
    private MusicTextControl trackAction(Bounds row, int slot, boolean inLibrary, boolean like) {
        var button = button(new Bounds(row.right() - (like ? 48 : 24), row.y(), 24, row.height()),
                label(like ? "library.like" : "library.unlike"), () -> {
                    int i = (inLibrary ? libraryFirst : first) + slot;
                    var list = inLibrary ? libraryTracks : results;
                    if (i < list.size()) changeLike(list.get(i), like);
                }, inLibrary ? libraryControls : searchControls);
        button.setIcon(like ? GraphicIcon.FAVORITE : GraphicIcon.REMOVE);
        return button;
    }
    private void buildLibrary() {
        var c = layout.content();
        var ids=player.sources().stream().filter(id->id.equals("netease")||id.equals("qq")).distinct().toList();libraryProviders=ids;
        if(!ids.contains(librarySource))librarySource=ids.isEmpty()?"":ids.get(0);
        if(ids.size()>1) librarySources=host.add(new MusicSegmentedControl(layout.sourceBar(),label("view.sources"),ids.stream().map(this::sourceLabel).toList(),ids.indexOf(librarySource),host.preferences(),index->{
            cancelLibrary();librarySource=ids.get(index);library=Library.NONE;openLibraryGroup(libraryGroup);
            host.screen().setFocused(librarySources);
        }));
        if(librarySources!=null)libraryHeaderControls.add(librarySources);
        else {var sourceLabel=button(layout.sourceBar(),ids.isEmpty()?label("view.unavailable"):sourceLabel(librarySource),()->{},libraryHeaderControls);sourceLabel.active=false;}
        var h=layout.searchBar();
        libraryGroups=host.add(new MusicSegmentedControl(new Bounds(h.x()+92,h.y()+2,Math.max(48,h.width()-120),22),label("view.library"),List.of(label("library.playlists"),label("library.liked")),libraryGroup==Library.LIKED?1:0,host.preferences(),index->{openLibraryGroup(index==0?Library.PLAYLISTS:Library.LIKED);host.screen().setFocused(libraryGroups);}));libraryHeaderControls.add(libraryGroups);
        libraryLogin=button(new Bounds(c.x()+8,c.bottom()-44,Math.max(24,c.width()-16),24),label("library.sign_in"),()->{
            libraryLoginReturn=true;show(Page.ACCOUNTS);openAccount(librarySource);updateVisibility();host.screen().setFocused(back);
        },null);

        libraryRefresh = button(new Bounds(layout.searchBar().right()-24,layout.searchBar().y()+2,24,22), label("library.retry"),
                () -> {if(pendingPlaylists!=null||pendingLibrary!=null){cancelLibrary();libraryState="cancelled";updateVisibility();}else loadLibraryPage(libraryPage);}, libraryControls);
        libraryRefresh.setIcon(GraphicIcon.SEARCH);
        for (var row : musicRows()) {
            int slot = libraryRows.size();
            libraryRows.add(button(new Bounds(row.x(), row.y(), row.width() - 52, row.height()), Component.empty(), () -> {
                int i = libraryFirst + slot;
                if (library == Library.PLAYLISTS && i < playlists.size()) loadPlaylist(playlists.get(i), 1);
                else if (i < libraryTracks.size()) playCollection(libraryTracks, i);
            }, libraryControls));
            libraryRows.get(slot).setVariant(MusicTextControl.Variant.ROW);
            libraryLikes.add(trackAction(row, slot, true, true));
            libraryUnlikes.add(trackAction(row, slot, true, false));
        }
        var pager = layout.pager();
        libraryPrevious = button(new Bounds(pager.x(), pager.y(), 24, 20), label("previous"), () -> {
            if (libraryFirst > 0) { libraryFirst = Math.max(0, libraryFirst - visibleRows); updateLibraryRows(); }
            else if (libraryPage > 1) loadLibraryPage(libraryPage - 1);
        }, libraryControls); libraryPrevious.setIcon(GraphicIcon.PREVIOUS);
        libraryNext = button(new Bounds(pager.right() - 24, pager.y(), 24, 20), label("next"), () -> {
            if (libraryFirst + visibleRows < librarySize()) { libraryFirst += visibleRows; updateLibraryRows(); }
            else if (librarySize() == 20) loadLibraryPage(libraryPage + 1);
        }, libraryControls); libraryNext.setIcon(GraphicIcon.NEXT);
    }
    private void playCollection(List<OnlineTrack> tracks, int selected) {
        queue.replace(tracks, selected);
        queueFirst = Math.max(0, selected - visibleRows / 2);
        updateQueueRows();
    }
    private void buildQueue() {
        var c = layout.content();
        var modes = layout.sourceBar();
        queuePlaybackMode = button(new Bounds(modes.x(), modes.y(), Math.max(12, modes.width()-96), 20),
                Component.empty(), this::cyclePlaybackMode, queueControls);
        queueLocate = button(new Bounds(layout.sourceBar().right() - 88, layout.sourceBar().y(), 88, 20), label("queue.locate"), () -> {
            queueFirst = Math.max(0, queue.index() - visibleRows / 2); updateQueueRows();
        }, queueControls);
        for (var row : musicRows()) {
            int slot = queueRows.size();
            var play = button(new Bounds(row.x(), row.y(), row.width() - 28, row.height()), Component.empty(),
                    () -> { queue.select(queueFirst + slot); updateQueueRows(); }, queueControls);
            play.setLeadingIcon(GraphicIcon.RUN); play.setVariant(MusicTextControl.Variant.ROW);queueRows.add(play);
            var remove = button(new Bounds(row.right() - 24, row.y(), 24, row.height()), label("queue.remove"),
                    () -> { queue.remove(queueFirst + slot); updateQueueRows(); updateScrollRegions(); }, queueControls);
            remove.setIcon(GraphicIcon.REMOVE); queueRemoves.add(remove);
        }
        var p = layout.pager();
        queuePrevious = button(new Bounds(p.x(), p.y(), 24, 20), label("queue.previous"),
                () -> { queue.previous(); updateQueueRows(); }, queueControls);
        queuePrevious.setIcon(GraphicIcon.PREVIOUS);
        queueNext = button(new Bounds(p.x() + 28, p.y(), 24, 20), label("queue.next"),
                () -> { queue.next(); updateQueueRows(); }, queueControls);
        queueNext.setIcon(GraphicIcon.NEXT);
        int actionWidth = Math.min(68, Math.max(36, (p.width() - 64) / 2));
        queueUndo = button(new Bounds(p.right() - actionWidth * 2 - 4, p.y(), actionWidth, 20), label("queue.undo"),
                () -> { queue.undo(); updateQueueRows(); updateScrollRegions(); }, queueControls);
        queueClear = button(new Bounds(p.right() - actionWidth, p.y(), actionWidth, 20), label("queue.clear"),
                () -> { queue.clear(); updateQueueRows(); updateScrollRegions(); }, queueControls);
        queueClear.setPreview(label("queue.clear_hint"));
    }
    private void updateQueueRows() {
        if (queuePrevious == null) return;
        queueFirst = Math.min(queueFirst, Math.max(0, queue.tracks().size() - visibleRows));
        for (int slot = 0; slot < queueRows.size(); slot++) {
            int i = queueFirst + slot;
            var row = queueRows.get(slot); var remove = queueRemoves.get(slot);
            row.visible = remove.visible = page == Page.QUEUE && i < queue.tracks().size();
            row.active = remove.active = row.visible;
            if (i >= queue.tracks().size()) continue;
            var track = queue.tracks().get(i);
            var caption=MpTypography.text((i + 1) + " · " + track.title());if(!row.getMessage().equals(caption))row.rebind(caption);
            row.setSecondary(MpTypography.text(track.artist()+" · "+time(track.durationMs())));
            row.setPreview(label("play_track", track.title(), track.artist()).append("\n").append(label("queue.session_hint")));
            row.setSelected(i == queue.index());
            remove.setPreview(label("queue.remove_track", track.title()));
        }
        queuePrevious.active = queue.canPrevious(); queueNext.active = queue.canNext();
        queueClear.active = !queue.tracks().isEmpty(); queueUndo.active = queue.canUndo();
        queueLocate.active = queue.index() >= 0;
        updatePlaybackModeControls();
        if(!navigation.isEmpty()) {
            var entry=navigation.get(Page.QUEUE.ordinal());
            var details=queue.tracks().isEmpty()?label("queue.empty").append("\n").append(label("queue.empty_hint")):label("queue.count",queue.tracks().size());
            entry.setSecondary(details);entry.setPreview(details);
        }
    }
    private void cyclePlaybackMode() {
        queue.cycleMode(); updateQueueRows(); updateStageQueueRows();
    }
    private void updatePlaybackModeControls() {
        var modeName = label("queue.mode." + queue.mode().name().toLowerCase(java.util.Locale.ROOT));
        var caption = label("queue.mode", modeName);
        var preview = queue.supportsModes()
                ? label("queue.mode.next", label("queue.mode." + queue.nextMode().name().toLowerCase(java.util.Locale.ROOT)))
                    .append("\n").append(label("queue.mode.hint"))
                : label("queue.mode.unsupported");
        for (var control : new MusicTextControl[]{queuePlaybackMode, stagePlaybackMode}) {
            if (control == null) continue;
            var visibleCaption = control == queuePlaybackMode ? modeName : caption;
            if (!control.getMessage().equals(visibleCaption)) control.setMessage(visibleCaption);
            control.setPreview(caption.copy().append("\n").append(preview)); control.setAvailable(queue.supportsModes());
            control.setSelected(queue.mode() != OnlineMusicQueue.Mode.SEQUENTIAL);
        }
    }
    private void renderQueue(UiFrame ui, OnlineMusicPlayer.Snapshot current) {
        var h = layout.searchBar(); var c = layout.content();
        text(ui, label("queue.session_hint"), h.x() + back.getWidth() + 8, h.y() + 8, h.width() - back.getWidth() - 8);
        if(layout.listHeadingHeight()>=20)muted(ui, label("queue.count", queue.tracks().size()), c.x() + 6, c.y() + 4, Math.max(0, c.width() - 110));
        for (int slot = 0; slot < queueRows.size(); slot++) {
            int i = queueFirst + slot; if (i >= queue.tracks().size()) break;
            var row = queueRows.get(slot);
            OnlineMusicViewPaint.resultSurface(ui, new Bounds(c.x(), row.getY(), c.width(), row.getHeight()),
                    i == queue.index() && queue.tracks().get(i).equals(current.track()), row.isHoveredOrFocused());
        }
        if(queue.tracks().isEmpty()) {
            queueStatus.setContent(label("queue.empty"),label("queue.empty_hint"),false,null);
            queueStatus.render(ui,host.font(),new Bounds(c.x()+8,c.y()+layout.listHeadingHeight(),c.width()-16,Math.max(0,layout.pager().y()-c.y()-layout.listHeadingHeight()-4)));
        }
    }
    private int resultTitleWidth(Bounds row) {
        return row.width() - 52;
    }
    private MusicTransportControl miniControl(Bounds box,String name,MusicTransportControl.Glyph glyph,Runnable action) {
        return host.add(new MusicTransportControl(box,label(name),glyph,host.preferences(),action));
    }
    private void buildTransport() {
        var mini = MusicMiniPlayerLayout.of(layout.transport(), layout.expanded());
        nowPlaying=button(mini.metadata(),label("view.now_playing"),this::enterImmersive,null);
        nowPlaying.setVariant(MusicTextControl.Variant.ROW);
        // Register in visual order so keyboard traversal follows previous / play / next.
        miniPrevious=miniControl(mini.previous(),"queue.previous",MusicTransportControl.Glyph.PREVIOUS,()->queue.previous());
        playback=miniControl(mini.play(),"play",MusicTransportControl.Glyph.PLAY,()->{
            if(player.snapshot().state()==OnlineMusicPlayer.State.PLAYING)player.pause();else player.resume();
        });
        miniNext=miniControl(mini.next(),"queue.next",MusicTransportControl.Glyph.NEXT,()->queue.next());
        stop=miniControl(mini.stop(),"stop",MusicTransportControl.Glyph.STOP,player::stop);
        lyricsToggle=miniControl(mini.lyrics(),"view.lyrics",MusicTransportControl.Glyph.LYRICS,this::enterImmersive);
        miniQueue=miniControl(mini.queue(),"view.queue",MusicTransportControl.Glyph.QUEUE,()->show(Page.QUEUE));
        seek=host.add(new MusicSeekControl(layout.seekBar(),label("seek",0),host.preferences(),ignored->{},edit->{
            var current=player.snapshot();if(current.track()!=null&&trackKey(current.track()).equals(edit.trackKey()))player.seek(edit.positionMs());
        },ignored->{}));
        seek.setTimePreviewEnabled(true);
    }
    private MusicTextControl button(Bounds b, Component name, Runnable action, List<AbstractWidget> group) {
        var result = host.add(new MusicTextControl(b, name, host.preferences(), action));

        if (group != null) group.add(result);
        return result;
    }
    private net.minecraft.client.gui.components.events.GuiEventListener stageReturnControl() {
        return shellBack==null?stageBack:shellBack;
    }
    private void enterImmersive() { enterImmersive(StagePage.LYRICS); }
    private void enterImmersive(StagePage mode) {
        if (!immersive) stageReturnPage=page;
        immersive=true;page=Page.LYRICS;setStagePage(mode);cancelSuggestions();
        updateVisibility();host.screen().setFocused(stageReturnControl());
    }
    private void setStagePage(StagePage mode) {
        stagePage=mode;
        stage=FullscreenLyricLayout.of(layout.frame(),shellBack==null,stageFailure,mode!=StagePage.ARTWORK);
        updateStageGeometry();updateVisibility();
    }
    boolean permitsScreenClose() {
        if (!immersive) return true;
        if(stageSeek!=null&&stageSeek.isEditing()){stageSeek.cancelInteraction();return false;}
        if(stageVolume!=null&&stageVolume.isEditing()){stageVolume.cancelInteraction();return false;}
        boolean cancelled=false;
        for(var control:new MusicTransportControl[]{stageBack,stagePlay,stagePrevious,stageNext,stageMode,stageQueue})
            if(control!=null)cancelled|=control.keyPressed(256,0,0);
        if(cancelled)return false;
        if(stageLyrics!=null&&stageLyrics.browsing()){stageLyrics.follow();return false;}
        leaveImmersive(); return false;
    }
    private void leaveImmersive() {
        if (!immersive) return;
        if(stageSeek!=null)stageSeek.cancelInteraction(); if(stageVolume!=null)stageVolume.cancelInteraction();
        immersive = false; page = stageReturnPage;
        updateVisibility(); host.screen().setFocused(page == Page.SEARCH ? queryField : back);
    }
    private MusicTransportControl stageButton(Bounds box,String name,MusicTransportControl.Glyph glyph,Runnable action) {
        var result=host.add(new MusicTransportControl(box,label(name),glyph,host.preferences(),action));
        stageControls.add(result);return result;
    }
    private void buildImmersive() {
        stageBack=stageButton(stage.back(),"stage.back",MusicTransportControl.Glyph.BACK,this::leaveImmersive);
        stagePlay=stageButton(stage.play(),"play",MusicTransportControl.Glyph.PLAY,()->{
            if(player.snapshot().state()==OnlineMusicPlayer.State.PLAYING)player.pause();else player.resume();
        });
        stagePrevious=stageButton(stage.previous(),"queue.previous",MusicTransportControl.Glyph.PREVIOUS,()->queue.previous());
        stageNext=stageButton(stage.next(),"queue.next",MusicTransportControl.Glyph.NEXT,()->queue.next());
        stageMode=stageButton(stage.mode(),"stage.toggle_lyrics",MusicTransportControl.Glyph.LYRICS,
                ()->setStagePage(stagePage==StagePage.LYRICS?StagePage.ARTWORK:StagePage.LYRICS));
        stageQueue=stageButton(stage.queue(),"view.queue",MusicTransportControl.Glyph.QUEUE,()->{if(stage.lyrics().height()<56)show(Page.QUEUE);else setStagePage(StagePage.QUEUE);});
        stageSeek=host.add(new MusicSeekControl(stage.seek(),label("seek",0),host.preferences(),edit->{},edit->{
            var now=player.snapshot();
            if(now.track()!=null&&trackKey(now.track()).equals(edit.trackKey()))player.seek(edit.positionMs());
        },edit->{}));
        stageSeek.setTimePreviewEnabled(true);
        stageVolume=host.add(new MusicSeekControl(stage.volume(),label("stage.volume",100),host.preferences(),edit->{},edit->{
            if(player.supportsVolume())player.volume(Math.max(0,Math.min(1,edit.positionMs()/1000f)));
        },edit->{}));
        stageVolume.setValueFormatter((v,max)->label("stage.volume",max<=0?0:Math.round(v*100.0/max)));
        stageVolume.setKeyboardStep(50,10);
        stageControls.add(stageSeek);stageControls.add(stageVolume);
        stageLyrics=OnlineMusicScreen.addLyricsLayer(host,new MusicLyricsView(stage.lyrics(),label("lyrics.empty"),host.preferences(),position->{
            var now=player.snapshot();
            if(OnlineLyricPresentation.seekable(now)&&position>=0&&position<=now.durationMs()&&Objects.equals(now.track(),stageLyricTrack)&&now.lyrics()==stageTimeline&&stageLyrics.contentKey()==stageLyricKey)
                player.seek(position);
        }),()->stage.lyrics());
        stageFollow=OnlineMusicScreen.addLyricsLayer(host,new MusicTextControl(new Bounds(stage.lyrics().x(),stage.lyrics().y()+Math.max(0,stage.lyrics().height()-20),Math.min(108,stage.lyrics().width()),20),label("view.follow_lyrics"),
                host.preferences(),stageLyrics::follow),()->stage.lyrics());
        stageCoverEntry=host.add(new MusicStageButton(stage.cover(),label("stage.toggle_lyrics"),null,
                ()->setStagePage(stagePage==StagePage.LYRICS?StagePage.ARTWORK:StagePage.LYRICS),host.preferences(),false,true));
        stagePlaybackMode=host.add(new MusicTextControl(new Bounds(stage.lyrics().x(),stage.lyrics().y(),
                Math.max(12,stage.lyrics().width()),20),Component.empty(),host.preferences(),this::cyclePlaybackMode));
        stageControls.add(stagePlaybackMode);
        stageQueueScroll=host.add(new OnlineMusicScrollRegion(stage.lyrics().x(),stage.lyrics().y()+Math.min(24,stage.lyrics().height()),stage.lyrics().width(),Math.max(1,stage.lyrics().height()-24),
                label("queue.scroll"),offset->{stageQueueFirst=offset;updateStageQueueRows();}));
        stageQueueScroll.setOnBrowse(()->host.screen().setFocused(stageQueueScroll));
        int count=Math.max(1,Math.min(20,(stage.lyrics().height()-24)/32));
        for(int i=0;i<count;i++) {
            int slot=i;
            stageQueueRows.add(host.add(new MusicTextControl(new Bounds(stage.lyrics().x(),stage.lyrics().y()+24+i*32,Math.max(1,stage.lyrics().width()-10),30),
                    Component.empty(),host.preferences(),()->{queue.select(stageQueueFirst+slot);updateStageQueueRows();})));
        }
        var h=layout.header();
        stageEntry=host.add(new MusicStageButton(new Bounds(h.right()-100,h.y()+2,100,22),label("stage.open"),null,this::enterImmersive,host.preferences(),false,false));
        coverEntry=host.add(new MusicStageButton(layout.cover(),label("stage.now_playing"),null,()->enterImmersive(StagePage.ARTWORK),host.preferences(),false,true));
    }
    private void updateStageGeometry() {
        if(stageLyrics==null)return;
        stageLyrics.bounds(stage.lyrics());stageLyrics.scale(stage.lyricScale());
        var cover=stage.cover();stageCoverEntry.setX(cover.x());stageCoverEntry.setY(cover.y());stageCoverEntry.setWidth(cover.width());stageCoverEntry.setHeight(cover.height());
        stageFollow.setWidth(Math.min(108,stage.lyrics().width()));
        stageFollow.setX(stage.lyrics().x());stageFollow.setY(stage.lyrics().y()+Math.max(0,stage.lyrics().height()-20));
        stageQueueScroll.setBounds(new Bounds(stage.lyrics().x(),stage.lyrics().y()+Math.min(24,stage.lyrics().height()),stage.lyrics().width(),Math.max(1,stage.lyrics().height()-24)));
        for(int i=0;i<stageQueueRows.size();i++) {
            var row=stageQueueRows.get(i);row.setX(stage.lyrics().x());row.setY(stage.lyrics().y()+24+i*32);row.setWidth(Math.max(1,stage.lyrics().width()-10));
        }
    }
    private boolean showingStageQueue(){return immersive&&(stagePage==StagePage.QUEUE||stage.wide()&&stagePage==StagePage.ARTWORK);}
    private void updateStageQueueRows() {
        if(stageQueueScroll==null)return;
        stagePlaybackMode.setVisible(showingStageQueue()&&stage.lyrics().height()>=24);
        updatePlaybackModeControls();
        int count=Math.max(0,Math.min(stageQueueRows.size(),(stage.lyrics().height()-24)/32));
        stageQueueFirst=Math.min(stageQueueFirst,Math.max(0,queue.tracks().size()-count));
        for(int slot=0;slot<stageQueueRows.size();slot++) {
            var row=stageQueueRows.get(slot);int index=stageQueueFirst+slot;
            row.setVisible(showingStageQueue()&&slot<count&&index<queue.tracks().size());
            row.active=row.visible;
            if(index<queue.tracks().size()){
                var track=queue.tracks().get(index);var caption=MpTypography.text(track.title()+" · "+track.artist());if(!row.getMessage().equals(caption))row.rebind(caption);row.setPreview(caption);row.setSelected(index==queue.index());
            }
        }
        stageQueueScroll.visible=showingStageQueue()&&count>0;stageQueueScroll.configure(stageQueueFirst,queue.tracks().size(),count);
    }
    /** User-facing routes always use the new stage; show(LYRICS) remains a compatibility path. */
    private void navigate(Page next) {
        if (next == Page.LYRICS) enterImmersive(); else if(next==Page.LIBRARY)enterLibrary();else show(next);
    }
    private void show(Page next) {
        if (immersive) { immersive = false; }
        if (next == Page.LYRICS && page != Page.LYRICS) lyricsReturnPage = page;
        for (var scroll : new OnlineMusicScrollRegion[]{searchScroll, libraryScroll, lyricsScroll, accountScroll, queueScroll}) if (scroll != null) scroll.cancelDrag();
        if (page != next) cancelLike();
        if (page == Page.ACCOUNTS && next != page){leaveAccountDetail();libraryLoginReturn=false;}
        if(page==Page.LIBRARY&&next!=page){boolean loading=pendingPlaylists!=null||pendingLibrary!=null;cancelLibrary();if(loading)library=Library.NONE;}
        if (page == Page.SEARCH && next != page) cancelSuggestions();
        if (page != next) visualMotion.pulse(System.nanoTime() / 1_000_000L);
        page = next; if (back != null) back.setY(layout.searchBar().y() + 2); updateVisibility();
        if (next == Page.SEARCH) host.screen().setFocused(queryField); else host.screen().setFocused(back);
    }
    private void selectSource(String next) {
        if (!searchSourceIds.contains(next)) return;
        if (!source.equals(next)) {
            cancelLike(); cancelSearch(); cancelSuggestions(); source = next; results = List.of(); first = 0; remotePage = 1; searchState = "search_hint";
        }
        show(Page.SEARCH); updateRows();
        if(sourceSegments!=null&&sourceSegments.visible)host.screen().setFocused(sourceSegments);
    }
    private void updateVisibility() {
        if (queryField == null) return;
        if (exitControl != null) { exitControl.visible = !immersive; if (immersive) exitControl.cancelInteraction(); }
        searchControls.forEach(w -> w.visible = page == Page.SEARCH);
        lyricControls.forEach(w -> w.visible = page == Page.LYRICS);
        queueControls.forEach(w -> w.visible = page == Page.QUEUE);
        accountControls.forEach(w -> w.visible = page == Page.ACCOUNTS && !accountDetail);
        detailControls.forEach(w -> w.visible = page == Page.ACCOUNTS && accountDetail);
        libraryControls.forEach(w -> w.visible = page == Page.LIBRARY);
        libraryHeaderControls.forEach(w->w.visible=page==Page.LIBRARY&&!immersive);
        if(librarySources!=null){librarySources.setVisible(page==Page.LIBRARY&&!immersive);librarySources.setSelectedIndex(libraryProviders.indexOf(librarySource));}
        if(libraryGroups!=null){libraryGroups.setVisible(page==Page.LIBRARY&&!immersive);libraryGroups.setSelectedIndex(libraryGroup==Library.LIKED?1:0);libraryGroups.setAvailable(!libraryProviders.isEmpty());}
        libraryLogin.setVisible(page==Page.LIBRARY&&!immersive&&!libraryProviders.isEmpty()&&(!player.authenticated(librarySource)||libraryState.equals("library.login_required")));

        back.visible = page != Page.SEARCH;
        for(int i=0;i<navigation.size();i++)navigation.get(i).setSelected(i==page.ordinal());
        sources.forEach((id, button) -> {
            if (id.equals("toggle")) button.setMessage(label("source." + source)); else button.setSelected(id.equals(source));
        });
        if(sourceSegments!=null){sourceSegments.setSelectedIndex(searchSourceIds.indexOf(source));sourceSegments.setVisible(!immersive&&page==Page.SEARCH);}
        loginSources.forEach((id, button) -> button.setSelected(id.equals(accountSource)));
        boolean signedIn = player.authenticated(accountSource);
        remember.setMessage(label(player.remembersCredentials(accountSource) ? "login.remember_on" : "login.remember_off"));
        remember.setSelected(player.remembersCredentials(accountSource));
        updateRows(); updateLibraryRows(); updateQueueRows();
        libraryPrevious.visible&= !libraryLogin.visible;libraryNext.visible&= !libraryLogin.visible;
        updateScrollRegions();
        positionAccounts();
        navigation.forEach(w->w.setVisible(!immersive));
        sources.values().forEach(w -> w.setVisible(!immersive && page == Page.SEARCH));
        for (var w : new AbstractWidget[]{nowPlaying, playback, stop, lyricsToggle, miniQueue, miniPrevious, miniNext, seek, back})
            if (immersive) w.visible = false;
        if (!immersive) { nowPlaying.visible = playback.visible = stop.visible = lyricsToggle.visible = miniQueue.visible = seek.visible = true; miniPrevious.visible=miniNext.visible=true; }
        stageControls.forEach(w -> w.visible = immersive);
        stageBack.visible = immersive && shellBack==null;
        if(stageLyrics!=null) {
            stageLyrics.visible=immersive&&stagePage==StagePage.LYRICS;
            stageCoverEntry.visible=immersive;
            stageFollow.setVisible(stageLyrics.visible&&stage.lyrics().height()>=40&&stageLyrics.browsing());
            stageVolume.visible=immersive&&player.supportsVolume();
            for(var control:new MusicTransportControl[]{stageBack,stagePlay,stagePrevious,stageNext,stageMode,stageQueue})if(!control.visible)control.cancelInteraction();
            if(!immersive){stageSeek.cancelInteraction();stageVolume.cancelInteraction();stageQueueScroll.cancelDrag();}
            updateStageQueueRows();
        }
        if(immersive){lyricRows.forEach(w->w.visible=false);followLyrics.visible=false;lyricsScroll.visible=false;}
        stageEntry.visible = !immersive && page == Page.LYRICS;
        coverEntry.visible = !immersive && layout.expanded();
        for(var group:List.of(searchControls,lyricControls,queueControls,accountControls,detailControls,libraryControls))
            for(var widget:group)if(!widget.visible&&widget instanceof MusicTextControl text)text.cancelInteraction();
        if(immersive){nowPlaying.cancelInteraction();back.cancelInteraction();seek.cancelInteraction();for(var control:new MusicTransportControl[]{playback,stop,lyricsToggle,miniQueue,miniPrevious,miniNext})control.cancelInteraction();}
        var lyricsBox = lyricArea();
        lyricsScroll.setBounds(lyricsBox);
        followLyrics.setX(lyricsBox.x()); followLyrics.setY(lyricsBox.bottom()+2);
    }
    private void queryChanged(String value) {
        query = value; cancelSuggestions();
        if (!value.isBlank()) { suggestAfter = System.nanoTime() + 300_000_000L; suggestedQuery = value; }
        first = 0; updateRows();
    }
    private boolean showSuggestions() { return !suggestions.isEmpty() && query.equals(suggestedQuery); }
    private void cancelSuggestions() {
        if (pendingSuggestions != null) pendingSuggestions.cancel(true);
        pendingSuggestions = null; suggestions = List.of(); suggestedQuery = ""; suggestAfter = 0;
    }
    private void search(int pageNumber, boolean fresh) {
        if(searchSourceIds.isEmpty())return;
        String text = fresh ? query.strip() : submittedQuery;
        if (text.isEmpty()) { searchState = "search_hint"; return; }
        cancelSearch(); cancelSuggestions(); likeState = ""; if (fresh) submittedQuery = text;
        remotePage = pageNumber; first = 0; results = List.of(); searchState = "searching";
        pending = player.search(source, text, pageNumber); updateRows();
    }
    private void cancelSearch() {
        if (pending != null) { pending.cancel(true); searchState = "cancelled"; pending = null; }
    }
    private void cancelLike() {
        // Cancellation cannot prove the provider did not accept the write.
        if (pendingLike != null && likeTrack != null) confirmedLikes.remove(trackKey(likeTrack));
        if (pendingLike != null) pendingLike.cancel(true);
        pendingLike = null; likeTrack = null; likeState = "";
    }
    private void clearAccountFeedback() { cancelLike(); confirmedLikes.clear(); accountState = ""; accountName = ""; }
    private void leaveAccountDetail() {
        cancelLogin();cancelAccountRequest();clearAccountFeedback();
        accountDetail = false; accountOffset = 0;
    }
    private void openAccount(String selected) {
        cancelLogin();cancelAccountRequest();clearAccountFeedback();
        accountSource = selected; accountDetail = true; accountOffset = 0;
        accountWasSignedIn = player.authenticated(selected);
        if (accountWasSignedIn) loadAccount();
    }
    private void cancelAccountRequest(){if(pendingAccount!=null)pendingAccount.cancel(true);pendingAccount=null;}
    private void invalidateLibrarySource(String changedSource) {
        if(!librarySource.equals(changedSource))return;
        cancelLibrary();library=Library.NONE;playlists=List.of();libraryTracks=List.of();selectedPlaylist=null;libraryFirst=0;libraryPage=1;
    }
    private static String trackKey(OnlineTrack track) { return track.source() + ":" + track.id(); }
    private void cancelLibrary() {
        cancelLike();
        if (pendingPlaylists != null) pendingPlaylists.cancel(true);
        if (pendingLibrary != null) pendingLibrary.cancel(true);
        if (pendingAccount != null) { pendingAccount.cancel(true); accountState = ""; }
        pendingPlaylists = null; pendingLibrary = null; pendingAccount = null;
    }
    private void loadAccount() {
        if (pendingAccount != null) pendingAccount.cancel(true);
        pendingAccount = null; accountState = "";
        if (player.authenticated(accountSource)) {
            accountState = "account.loading";
            try { pendingAccount = player.account(accountSource); }
            catch (RuntimeException failure) { accountState = operationFailure(failure).equals("library.login_required") ? "library.login_required" : "account.failed"; }
        }
    }
    private void enterLibrary() {
        show(Page.LIBRARY);
        if(library==Library.NONE||libraryState.equals(LibraryAccessFeedback.AUTHORIZATION_REQUIRED)||libraryState.equals(LibraryAccessFeedback.SIGN_IN)||!player.authenticated(librarySource)) {
            if(library==Library.TRACKS&&selectedPlaylist!=null)loadPlaylist(selectedPlaylist,libraryPage);else openLibraryGroup(libraryGroup);
        }
    }
    private void openLibraryGroup(Library group) {
        libraryGroup=group;
        if(group==Library.LIKED)loadLiked(1);else loadPlaylists(1);
    }
    private void loadPlaylists(int number) {
        cancelLibrary(); cancelLogin(); library = Library.PLAYLISTS; libraryPage = number; libraryFirst = 0;
        likeState = "";
        playlists = List.of(); libraryTracks = List.of(); libraryState = "library.loading";
        if(libraryProviders.isEmpty()){libraryState="view.unavailable";updateVisibility();return;}
        if(!player.authenticated(librarySource)){libraryState=LibraryAccessFeedback.SIGN_IN;updateVisibility();return;}
        try{pendingPlaylists = player.playlists(librarySource, number);}catch(RuntimeException failure){libraryState=operationFailure(failure);}
        updateVisibility(); host.screen().setFocused(back);
    }
    private void loadPlaylist(OnlinePlaylist playlist, int number) {
        if (library == Library.PLAYLISTS) { savedPlaylistPage = libraryPage; savedPlaylistFirst = libraryFirst; }
        cancelLibrary(); library = Library.TRACKS; selectedPlaylist = playlist; libraryPage = number; libraryFirst = 0;
        likeState = "";
        libraryTracks = List.of(); libraryState = "library.loading";
        try{pendingLibrary = player.playlist(playlist, number);}catch(RuntimeException failure){libraryState=operationFailure(failure);}
        updateVisibility(); host.screen().setFocused(back);
    }
    private void loadLiked(int number) {
        cancelLibrary(); cancelLogin(); library = Library.LIKED; libraryPage = number; libraryFirst = 0;
        likeState = "";
        libraryTracks = List.of(); libraryState = "library.loading";
        if(libraryProviders.isEmpty()){libraryState="view.unavailable";updateVisibility();return;}
        if(!player.authenticated(librarySource)){libraryState=LibraryAccessFeedback.SIGN_IN;updateVisibility();return;}
        try{pendingLibrary = player.liked(librarySource, number);}catch(RuntimeException failure){libraryState=operationFailure(failure);}
        updateVisibility(); host.screen().setFocused(back);
    }
    private void loadLibraryPage(int number) {
        if (library == Library.PLAYLISTS) loadPlaylists(number);
        else if (library == Library.TRACKS) loadPlaylist(selectedPlaylist, number);
        else if (library == Library.LIKED) loadLiked(number);
    }
    private int librarySize() { return library == Library.PLAYLISTS ? playlists.size() : libraryTracks.size(); }
    private void changeLike(OnlineTrack track, boolean like) {
        if (pendingLike != null) return;
        likeState = "library.updating";
        likeTrack = track; requestedLike = like;
        try { pendingLike = player.like(track, like); }
        catch (RuntimeException failure) { confirmedLikes.remove(trackKey(track)); likeTrack = null; likeState = operationFailure(failure); }
        updateRows(); updateLibraryRows();
    }
    private MutableComponent likeDescription(OnlineTrack track, boolean like) {
        Boolean known = confirmedLikes.get(trackKey(track));
        return label(like ? "library.like_track" : "library.unlike_track", track.title()).append("\n")
                .append(label(known == null ? "library.like_unknown" : known ? "library.is_liked" : "library.not_liked"));
    }
    private void applyLikeState(MusicTextControl add, MusicTextControl remove, OnlineTrack track, boolean enabled) {
        Boolean known = track == null ? null : confirmedLikes.get(trackKey(track));
        add.active = enabled && !Boolean.TRUE.equals(known); remove.active = enabled && !Boolean.FALSE.equals(known);
        add.setSelected(Boolean.TRUE.equals(known)); remove.setSelected(Boolean.FALSE.equals(known));
        add.setMessage(label(Boolean.TRUE.equals(known) ? "library.is_liked" : "library.like"));
    }
    private void updateLibraryRows() {
        if (libraryPrevious == null) return;
        for (int slot = 0; slot < libraryRows.size(); slot++) {
            int i = libraryFirst + slot; var row = libraryRows.get(slot);
            boolean available = page == Page.LIBRARY && !libraryLogin.visible && i < librarySize();
            row.setVisible(available); row.active = available;
            Object identity=available?(library==Library.PLAYLISTS?playlists.get(i):libraryTracks.get(i)):null;
            if(!Objects.equals(resultIdentities.put(row,identity),identity)){row.cancelInteraction();libraryLikes.get(slot).cancelInteraction();libraryUnlikes.get(slot).cancelInteraction();}
            if (available) {
                if (library == Library.PLAYLISTS) {
                    var playlist = playlists.get(i); row.setMessage(MpTypography.text(playlist.name()));
                    row.setSecondary(label("library.playlist_details", playlist.name(), playlist.trackCount()));
                    row.setPreview(label("library.playlist_details", playlist.name(), playlist.trackCount()));
                } else {
                    var track = libraryTracks.get(i); row.setMessage(MpTypography.text(track.title()));
                    row.setSecondary(MpTypography.text(track.artist()+" · "+time(track.durationMs())));
                    row.setPreview(label("play_track", track.title(), track.artist()).append("\n").append(MpTypography.text(track.album())));
                }
            }
            boolean actions = available && library != Library.PLAYLISTS;
            libraryLikes.get(slot).visible = actions; libraryUnlikes.get(slot).visible = actions;
            boolean supported = actions && player.supportsLikes(libraryTracks.get(i).source());
            boolean enabled = supported && pendingLike == null && player.authenticated(libraryTracks.get(i).source());
            applyLikeState(libraryLikes.get(slot), libraryUnlikes.get(slot), actions ? libraryTracks.get(i) : null, enabled);
            if (actions) { libraryLikes.get(slot).setPreview(supported ? likeDescription(libraryTracks.get(i), true) : label("library.like_unsupported")); libraryUnlikes.get(slot).setPreview(supported ? likeDescription(libraryTracks.get(i), false) : label("library.like_unsupported")); }
        }
        boolean ready = pendingPlaylists == null && pendingLibrary == null;
        libraryRefresh.visible = page == Page.LIBRARY&&!immersive;
        libraryRefresh.setMessage(label(pendingPlaylists!=null||pendingLibrary!=null?"cancel":"library.retry"));
        libraryRefresh.setGlyphOnly(pendingPlaylists!=null||pendingLibrary!=null?MusicControlRenderer.Icon.CLOSE:MusicControlRenderer.Icon.REFRESH);
        libraryRefresh.active=!libraryProviders.isEmpty()&&player.authenticated(librarySource);

        if(libraryLogin.visible){libraryPrevious.setVisible(false);libraryNext.setVisible(false);}
        libraryPrevious.active = ready && (libraryFirst > 0 || libraryPage > 1);
        libraryNext.active = ready && (libraryFirst + visibleRows < librarySize() || librarySize() == 20);
    }
    private static String operationFailure(Throwable failure) {
        return LibraryAccessFeedback.failure(failure);
    }
    private void pollOperations() {
        if (page == Page.SEARCH && suggestAfter != 0 && System.nanoTime() >= suggestAfter) {
            suggestAfter = 0;
            if (query.equals(suggestedQuery) && !query.isBlank()) pendingSuggestions = player.suggest(source, query.strip());
        }
        if (pendingSuggestions != null && pendingSuggestions.isDone()) {
            try { suggestions = pendingSuggestions.join(); first = 0; } catch (RuntimeException ignored) { suggestions = List.of(); }
            pendingSuggestions = null; updateRows();
        }
        if (pendingAccount != null && pendingAccount.isDone()) {
            try { var account = pendingAccount.join(); accountState = ""; accountName = account.name(); }
            catch (RuntimeException failure) { accountState = operationFailure(failure).equals("library.login_required") ? "library.login_required" : "account.failed"; }
            pendingAccount = null;
        }
        if (pendingPlaylists != null && pendingPlaylists.isDone()) {
            try { playlists = pendingPlaylists.join(); libraryState = playlists.isEmpty() ? "library.empty" : "library.results"; }
            catch (RuntimeException failure) { libraryState = operationFailure(failure); }
            pendingPlaylists = null; updateLibraryRows();updateVisibility();
        }
        if (pendingLibrary != null && pendingLibrary.isDone()) {
            try { libraryTracks = pendingLibrary.join(); libraryState = libraryTracks.isEmpty() ? "library.empty" : "library.results";
                if (library == Library.LIKED) for (var track : libraryTracks) confirmedLikes.put(trackKey(track), true);
            }
            catch (RuntimeException failure) { libraryState = operationFailure(failure); likeState = ""; }
            pendingLibrary = null; updateLibraryRows();updateVisibility();
        }
        if (pendingLike != null && pendingLike.isDone()) {
            boolean updated = false;
            try { pendingLike.join(); likeState = "library.updated"; updated = true;
                if (likeTrack != null) confirmedLikes.put(trackKey(likeTrack), requestedLike);
            }
            catch (RuntimeException failure) { if (likeTrack != null) confirmedLikes.remove(trackKey(likeTrack)); likeState = operationFailure(failure); }
            pendingLike = null; likeTrack = null;
            if (updated && page == Page.LIBRARY && library == Library.LIKED) {
                loadLiked(libraryPage); likeState = "library.updated";
            }
            updateRows(); updateLibraryRows();
        }
    }
    private void startLogin() { cancelLogin(); cancelLibrary(); clearAccountFeedback(); login = player.login(accountSource); }
    private void cancelLogin() { if (login != null) { login.close(); login = null; } }
    private void updateRows() {
        if (queryField == null) return;
        var current = player.snapshot().track();
        for (int slot = 0; slot < resultRows.size(); slot++) {
            int i = first + slot; var row = resultRows.get(slot); boolean suggesting = showSuggestions();
            row.active = i < (suggesting ? suggestions.size() : results.size());
            Object identity=row.active?(suggesting?suggestions.get(i):results.get(i)):null;
            if(!Objects.equals(resultIdentities.put(row,identity),identity)) {
                row.cancelInteraction();searchLikes.get(slot).cancelInteraction();searchUnlikes.get(slot).cancelInteraction();
            }
            var nextLabel=row.active ? MpTypography.text(suggesting ? suggestions.get(i) : results.get(i).title()) : Component.empty();
            if(!row.getMessage().equals(nextLabel))row.rebind(nextLabel);
            row.setSecondary(row.active&&!suggesting?MpTypography.text(results.get(i).artist()+" · "+time(results.get(i).durationMs())):Component.empty());
            row.setSelected(!suggesting && row.active && results.get(i).equals(current));
            if (row.active && !suggesting) row.setPreview(label("play_track", results.get(i).title(), results.get(i).artist())
                    .append("\n").append(MpTypography.text(results.get(i).album())));
            else if (row.active) row.setPreview(label("suggestion_pick", suggestions.get(i)));
            boolean actions = page == Page.SEARCH && !suggesting && i < results.size();
            searchLikes.get(slot).visible = actions; searchUnlikes.get(slot).visible = actions;
            boolean supported = actions && player.supportsLikes(results.get(i).source());
            boolean enabled = supported && pendingLike == null && player.authenticated(results.get(i).source());
            applyLikeState(searchLikes.get(slot), searchUnlikes.get(slot), actions ? results.get(i) : null, enabled);
            if (actions) { searchLikes.get(slot).setPreview(supported ? likeDescription(results.get(i), true) : label("library.like_unsupported")); searchUnlikes.get(slot).setPreview(supported ? likeDescription(results.get(i), false) : label("library.like_unsupported")); }
        }
        previous.active = showSuggestions() ? first > 0 : pending == null && (first > 0 || remotePage > 1);
        next.active = showSuggestions() ? first + visibleRows < suggestions.size() : pending == null && (first + visibleRows < results.size() || results.size() == 20);
        searchButton.setMessage(label(pending == null ? "search" : "cancel"));
        searchButton.setIcon(pending == null ? GraphicIcon.SEARCH : GraphicIcon.CLOSE);
        searchButton.setAvailable(!searchSourceIds.isEmpty());
        updateSearchFeedback();
    }
    private void updateSearchFeedback() {
        if(results.isEmpty()&&!showSuggestions()) {
            var detail=submittedQuery.isBlank()?Component.empty():sourceLabel(source).copy().append(" · ").append(MpTypography.text(submittedQuery));
            searchStatus.setContent(label(searchSourceIds.isEmpty()?"view.unavailable":searchState),detail,pending!=null,null);
            searchButton.setPreview(label(pending==null?"search":"cancel").append("\n").append(searchStatus.narration()));
        } else searchButton.setPreview(label(pending==null?"search":"cancel"));
    }
    @Override public void render(UiFrame ui) {
        if (closed || layout == null) return;
        if(seek!=null&&!net.minecraft.client.Minecraft.getInstance().isWindowActive()) {
            seek.cancelInteraction();
            if(stagePlaybackMode!=null)stagePlaybackMode.cancelInteraction();
            for(var control:new MusicTransportControl[]{playback,stop,lyricsToggle,miniQueue,miniPrevious,miniNext})control.cancelInteraction();
            navigation.forEach(MusicTextControl::cancelInteraction); if(sourceSegments!=null)sourceSegments.cancelInteraction();if(librarySources!=null)librarySources.cancelInteraction();if(libraryGroups!=null)libraryGroups.cancelInteraction();
            for(var group:List.of(searchControls,lyricControls,queueControls,accountControls,detailControls,libraryControls))
                for(var widget:group)if(widget instanceof MusicTextControl text)text.cancelInteraction();
        }
        if (!layout.supported()) { text(ui, label("space"), layout.frame().x() + 4, layout.frame().y() + 4, Math.max(0, layout.frame().width() - 8)); return; }
        var theme = UiTheme.current();
        if (exitControl != null) {
            exitControl.setInk(theme.text()); exitControl.setPreferences(host.preferences());
            exitControl.setAvailable(!immersive && net.minecraft.client.Minecraft.getInstance().isWindowActive());
        }
        if(qqStyle()&&!ui.preferences().highContrast()) qqBackground(ui,layout.frame());
        else OnlineMusicViewPaint.panel(ui, layout.frame(), theme.background() | 0xff000000, false);

        if (layout.sidebar() && !immersive) {
            OnlineMusicViewPaint.panel(ui, layout.navigation(), theme.surface(), false);
            text(ui, label("view.brand"), layout.navigation().x() + (exitControl == null ? 6 : 30), layout.navigation().y() + 7, layout.navigation().width() - (exitControl == null ? 12 : 36));

        }
        pollOperations();
        if (pending != null && pending.isDone()) {
            try { results = pending.join(); searchState = results.isEmpty() ? "empty" : "results"; }
            catch (RuntimeException failure) { searchState = "search_failed"; }
            pending = null; updateRows(); visualMotion.pulse(System.nanoTime() / 1_000_000L);
        }
        updateScrollRegions();
        var current = player.snapshot();
        queue.observe(current);
        current = player.snapshot();
        updateQueueRows();
        var visual = visualMotion.sample(System.nanoTime() / 1_000_000L, ui.preferences().reducedMotion(),
                current.state() == OnlineMusicPlayer.State.PLAYING);
        if (immersive) { seek.cancelInteraction(); renderImmersive(ui, current); return; }
        stageSeek.sync(current.track()==null?"":trackKey(current.track()),current.positionMs(),current.durationMs(),false);
        stageVolume.sync("volume",0,1000,false);
        OnlineMusicViewPaint.transportSurface(ui, layout.transport(), visual.activity());
        if (layout.expanded()) {
            if (page != Page.LYRICS) OnlineMusicViewPaint.hero(ui, host.font(), layout.hero(), label("view.brand"),
                    page==Page.LIBRARY&&library==Library.TRACKS&&selectedPlaylist!=null?MpTypography.text(selectedPlaylist.name()):label("refresh." + page.name().toLowerCase(Locale.ROOT)), label("refresh.subtitle"), visual.emphasis());
            paintArtwork(ui, layout.cover(), current.track(), visual.activity());
        }
        renderTransport(ui, current);
        if (page == Page.SEARCH) renderResults(ui, current);
        else if (page == Page.QUEUE) renderQueue(ui, current);
        else {
            var h = layout.searchBar();
            Component title = page == Page.LIBRARY
                    ? (library == Library.TRACKS && selectedPlaylist != null ? MpTypography.text(selectedPlaylist.name()) : label(library == Library.PLAYLISTS ? "library.playlists" : "library.liked"))
                    : page == Page.ACCOUNTS && accountDetail && !accountState.isEmpty() ? label(accountState)
                    : page == Page.ACCOUNTS && accountDetail && !accountName.isBlank() ? MpTypography.text(accountName)
                    : label("view." + page.name().toLowerCase(Locale.ROOT));
            if (page == Page.ACCOUNTS && accountDetail && login != null && accountState.isEmpty() && !player.authenticated(accountSource)) {
                var loginSnapshot = login.snapshot();
                title = loginSnapshot.image() != null && layout.content().height() - 96 < loginSnapshot.image().height()
                        ? label("login.enlarge") : label("login." + loginSnapshot.state().name().toLowerCase(Locale.ROOT));
            }
            if (page != Page.LYRICS&&page!=Page.LIBRARY) text(ui, title, h.x() + back.getWidth() + 8, h.y() + 8, h.width() - back.getWidth() - 8);
            if (page == Page.LYRICS) renderLyrics(ui, current); else if (page == Page.LIBRARY) renderLibrary(ui); else {
                positionAccounts();
                OnlineMusicViewPaint.clip(ui, layout.content());
                try { renderAccounts(ui); } finally { OnlineMusicViewPaint.unclip(ui); }
            }
        }
    }
    private void renderResults(UiFrame ui, OnlineMusicPlayer.Snapshot current) {
        var c = layout.content();
        for (int slot = 0; slot < resultRows.size(); slot++) resultRows.get(slot).visible = first + slot < (showSuggestions() ? suggestions.size() : results.size());
        if(layout.listHeadingHeight()>=20)muted(ui, label(showSuggestions() ? "suggestions" : "view.song"), c.x() + 6, c.y() + 4, c.width() - 12);
        for (int slot = 0; slot < resultRows.size(); slot++) {
            int i = first + slot; if (showSuggestions() || i >= results.size()) break;
            var track = results.get(i);
            var row = resultRows.get(slot);

            row.setSelected(track.equals(current.track()));

        }
        var pager = layout.pager();
        var status = label(!likeState.isEmpty() ? likeState : showSuggestions() ? "suggestions" : searchState, remotePage, results.isEmpty() ? 0 : first + 1, Math.min(first + visibleRows, results.size()));
        if(!"search_hint".equals(searchState)||!results.isEmpty()||showSuggestions())
            text(ui, status, pager.x() + 30, pager.y() + 6, pager.width() - 60);
        previous.setPreview(status.copy().append("\n").append(label("previous")));
        next.setPreview(status.copy().append("\n").append(label("next")));
        if (results.isEmpty() && !showSuggestions()) {
            updateSearchFeedback();
            previous.setPreview(searchStatus.narration());next.setPreview(searchStatus.narration());
            searchStatus.render(ui,host.font(),new Bounds(c.x()+8,c.y()+layout.listHeadingHeight(),c.width()-16,
                    Math.max(0,layout.pager().y()-c.y()-layout.listHeadingHeight()-4)));
        }

    }
    private void renderLibrary(UiFrame ui) {
        var c = layout.content();
        if(!libraryLogin.visible&&layout.listHeadingHeight()>=20)muted(ui, label(library == Library.PLAYLISTS ? "library.playlists" : "view.song"), c.x() + 6, c.y() + 4, Math.max(0, c.width() - 116));
        var pager = layout.pager();
        var status = label(!likeState.isEmpty() ? likeState : libraryState, libraryPage, librarySize() == 0 ? 0 : libraryFirst + 1, Math.min(libraryFirst + visibleRows, librarySize()));
        if(!libraryLogin.visible)text(ui, status, pager.x() + 30, pager.y() + 6, pager.width() - 60);
        if (librarySize() == 0||libraryLogin.visible) {
            OnlineMusicViewPaint.paragraph(ui,host.font(),label(libraryState),new Bounds(c.x()+8,c.y()+4,c.width()-16,Math.max(0,c.height()-(libraryLogin.visible?48:24))),UiTheme.current().text());
            libraryLogin.setPreview(label(LibraryAccessFeedback.signInHint(libraryState)).append("\n").append(sourceLabel(librarySource)));
        }
    }
    private void renderTransport(UiFrame ui, OnlineMusicPlayer.Snapshot current) {
        var state = current.state(); boolean playing = state == OnlineMusicPlayer.State.PLAYING;
        playback.active = playing || state == OnlineMusicPlayer.State.PAUSED || state == OnlineMusicPlayer.State.ENDED;
        playback.setMessage(label(playing ? "pause" : "play")); playback.setGlyph(playing ? MusicTransportControl.Glyph.PAUSE : MusicTransportControl.Glyph.PLAY);
        stop.active = state != OnlineMusicPlayer.State.IDLE && state != OnlineMusicPlayer.State.CLOSED;
        stop.setMessage(label(state == OnlineMusicPlayer.State.LOADING ? "cancel" : "stop"));
        boolean windowActive=net.minecraft.client.Minecraft.getInstance().isWindowActive();
        seek.active = windowActive && current.durationMs() > 0 && playback.active;
        seek.sync(current.track()==null?"":trackKey(current.track()),current.positionMs(),current.durationMs(),seek.active);
        miniPrevious.active=queue.canPrevious();miniNext.active=queue.canNext();
        for(var control:new MusicTransportControl[]{playback,stop,lyricsToggle,miniQueue,miniPrevious,miniNext}) {
            control.setInk(UiTheme.current().text());control.setPreferences(ui.preferences());control.setAvailable(control.active&&windowActive);
        }
        if(qqStyle()&&!ui.preferences().highContrast())playback.setInk(0xff35d89a);
        miniQueue.active=lyricsToggle.active=windowActive;
        seek.setInk(UiTheme.current().text());seek.setPreferences(ui.preferences());
        if(!windowActive)seek.cancelInteraction();
        updateMiniMetadata(current);
        stop.setGlyph(state==OnlineMusicPlayer.State.LOADING?MusicTransportControl.Glyph.CANCEL:MusicTransportControl.Glyph.STOP);
        var t = layout.transport();
        muted(ui, MpTypography.text(time(seek.previewPositionMs())), t.x(), seek.getY() + 4, 30);
        muted(ui, MpTypography.text("−" + time(Math.max(0, current.durationMs() - seek.previewPositionMs()))), t.right() - 32, seek.getY() + 4, 32);
        var status = state == OnlineMusicPlayer.State.FAILED ? label("failure." + current.failure().name().toLowerCase(Locale.ROOT))
                : label("state." + state.name().toLowerCase(Locale.ROOT));
        if (current.preview()) status = label("preview").append(" · ").append(status);
        if (!MusicMiniPlayerLayout.stacked(t.width())) muted(ui,status,t.x()+4,t.bottom()-12,t.width()-8);
        playback.setTooltip(net.minecraft.client.gui.components.Tooltip.create(status)); stop.setTooltip(net.minecraft.client.gui.components.Tooltip.create(status.copy().append("\n").append(label("stop_hint"))));
    }
    private void updateMiniMetadata(OnlineMusicPlayer.Snapshot current) {
        var title=current.track()==null?label("view.now_playing"):MpTypography.text(current.track().title());
        nowPlaying.setMessage(title);
        nowPlaying.setPreview(current.track()==null?title:label("track",current.track().title(),current.track().artist()));
        nowPlaying.setSecondary(current.track()==null?label("refresh.choose_track"):MpTypography.text(current.track().artist()));
    }
    private void cancelStageInteractions() {
        if(stageSeek!=null)stageSeek.cancelInteraction();if(stageVolume!=null)stageVolume.cancelInteraction();
        if(stageQueueScroll!=null)stageQueueScroll.cancelDrag();
        for(var control:new MusicTransportControl[]{stageBack,stagePlay,stagePrevious,stageNext,stageMode,stageQueue})if(control!=null)control.cancelInteraction();
    }
    private void renderImmersive(UiFrame ui,OnlineMusicPlayer.Snapshot current) {
        boolean failed=current.state()==OnlineMusicPlayer.State.FAILED;
        if(failed!=stageFailure){stageFailure=failed;stage=FullscreenLyricLayout.of(layout.frame(),shellBack==null,failed,stagePage!=StagePage.ARTWORK);updateStageGeometry();}
        var loaded=artwork.texture(current.track());
        if(qqStyle()&&!ui.preferences().highContrast())qqBackground(ui,stage.frame());
        else artworkStage.renderBackground(ui,stage.frame(),loaded.map(OnlineArtworkTexture.Texture::palette).orElse(null),ui.nowMs(),ui.preferences().reducedMotion());
        int ink=ui.preferences().highContrast()?UiTheme.current().text():MusicArtworkStage.FOREGROUND;
        int secondary=ui.preferences().highContrast()?ink:MusicArtworkStage.SECONDARY;
        if(vinylStyle()&&!ui.preferences().highContrast()) {
            var t=loaded.orElse(null);vinylStage.render(ui,stage.cover(),t==null?null:t.id(),t==null?0:t.width(),t==null?0:t.height(),current.state()==OnlineMusicPlayer.State.PLAYING);
        } else if(loaded.isPresent()) { var t=loaded.get();artworkStage.renderCover(ui,stage.cover(),t.id(),t.width(),t.height()); }
        else OnlineMusicViewPaint.artwork(ui,stage.cover(),0);
        var title=current.track()==null?label("stage.empty_title"):MpTypography.bold(current.track().title());
        var subtitle=current.track()==null?label("stage.empty_detail"):MpTypography.text(current.track().artist()
                +(current.track().album().isBlank()?"":" · "+current.track().album()));
        var trackDetails=title.copy().append("\n").append(subtitle).append("\n").append(label("stage.toggle_lyrics"));
        stageCoverEntry.setMessage(trackDetails);stageCoverEntry.setTooltip(net.minecraft.client.gui.components.Tooltip.create(trackDetails));
        OnlineMusicViewPaint.scaledLine(ui,host.font(),MpTypography.bold(title),stage.info().x(),stage.info().y(),stage.info().width(),ink,stage.wide()?1.35f:1.1f);
        OnlineMusicViewPaint.line(ui,host.font(),subtitle,stage.info().x(),stage.info().y()+22,stage.info().width(),secondary);
        boolean windowActive=net.minecraft.client.Minecraft.getInstance().isWindowActive();
        if(!windowActive)cancelStageInteractions();
        for(var control:new MusicTransportControl[]{stageBack,stagePlay,stagePrevious,stageNext,stageMode,stageQueue}){
            control.setInk(ink);control.setPreferences(ui.preferences());if(!windowActive)control.cancelInteraction();
        }
        if(qqStyle()&&!ui.preferences().highContrast())stagePlay.setInk(0xff35d89a);
        stageBack.setAvailable(windowActive);stageMode.setAvailable(windowActive);stageQueue.setAvailable(windowActive);
        stagePlay.setAvailable(windowActive&&(current.state()==OnlineMusicPlayer.State.PLAYING||current.state()==OnlineMusicPlayer.State.PAUSED||current.state()==OnlineMusicPlayer.State.ENDED));
        boolean playing=current.state()==OnlineMusicPlayer.State.PLAYING;
        stagePlay.setMessage(label(playing?"pause":"play"));stagePlay.setGlyph(playing?MusicTransportControl.Glyph.PAUSE:MusicTransportControl.Glyph.PLAY);
        stagePrevious.setAvailable(windowActive&&queue.canPrevious());stageNext.setAvailable(windowActive&&queue.canNext());
        String key=current.track()==null?"":trackKey(current.track());
        stageSeek.setInk(ink);stageSeek.setPreferences(ui.preferences());stageSeek.sync(key,current.positionMs(),current.durationMs(),stagePlay.active&&current.durationMs()>0);
        stageVolume.setInk(secondary);stageVolume.setPreferences(ui.preferences());
        stageVolume.visible=player.supportsVolume();stageVolume.sync("volume",player.supportsVolume()?Math.round(player.volume()*1000):0,1000,windowActive&&player.supportsVolume());
        long position=stageSeek.isEditing()?stageSeek.previewPositionMs():current.positionMs();
        OnlineMusicViewPaint.line(ui,host.font(),MpTypography.text(time(position)),stage.seek().x(),stage.seek().bottom()+2,36,secondary);
        OnlineMusicViewPaint.line(ui,host.font(),MpTypography.text("−"+time(Math.max(0,current.durationMs()-position))),stage.seek().right()-40,stage.seek().bottom()+2,40,secondary);
        syncStageLyrics(current,windowActive);
        stageLyrics.setMessage(label("lyrics."+current.lyricsState().name().toLowerCase(Locale.ROOT)));
        stageLyrics.palette(ink,secondary,ui.preferences().highContrast()?UiTheme.current().background():0xff292229);
        stageLyrics.preferences(ui.preferences());
        stageLyrics.visible=stagePage==StagePage.LYRICS;stageLyrics.active=windowActive;stageLyrics.scale(stage.lyricScale());
        var lyricBounds=stage.lyrics();
        if(!OnlineMusicScreen.supportsLyricsStack(host)&&stageLyrics.browsing()&&lyricBounds.height()>=40)
            lyricBounds=new Bounds(lyricBounds.x(),lyricBounds.y(),lyricBounds.width(),lyricBounds.height()-20);
        stageLyrics.bounds(lyricBounds);
        stageFollow.setVisible(stageLyrics.visible&&stage.lyrics().height()>=40&&stageLyrics.browsing());stageFollow.setInk(ink,secondary);
        stageCoverEntry.ink(ink);
        lyricsScroll.visible=false;lyricRows.forEach(w->w.visible=false);followLyrics.visible=false;
        updateStageQueueRows();for(var row:stageQueueRows)row.setInk(ink,secondary);
        if(showingStageQueue()){
            stagePlaybackMode.setInk(ink,secondary);
            if(queue.tracks().isEmpty())OnlineMusicViewPaint.paragraph(ui,host.font(),label("queue.empty_hint"),new Bounds(stage.lyrics().x(),stage.lyrics().y()+28,stage.lyrics().width(),Math.max(1,stage.lyrics().height()-28)),secondary);
        }
        if(failed)OnlineMusicViewPaint.paragraph(ui,host.font(),label("failure."+current.failure().name().toLowerCase(Locale.ROOT)),stage.message(),ink);
        var selected=stagePage==StagePage.LYRICS?stage.mode():stagePage==StagePage.QUEUE?stage.queue():null;
        if(selected!=null)RoundedCorners.draw(ui.graphics(),selected.x()-3,selected.y()-3,selected.width()+6,selected.height()+6,8,0,0,0x22ffffff);
    }
    private void syncStageLyrics(OnlineMusicPlayer.Snapshot current,boolean windowActive) {
        var timeline=current.lyricsState()==OnlineMusicPlayer.LyricsState.READY&&current.track()!=null
                ?current.lyrics():com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline.empty();
        if(!Objects.equals(stageLyricTrack,current.track())||stageTimeline!=timeline||stageLyricKey==null) {
            stageLyricTrack=current.track();stageTimeline=timeline;stageLyricKey=new Object();
            stageLyrics.setLines(stageLyricKey,OnlineLyricPresentation.lines(timeline));
        }
        stageLyrics.playback(OnlineLyricPresentation.position(current),windowActive&&OnlineLyricPresentation.seekable(current));
    }
    private Bounds lyricContent() {
        if (immersive) return stage.lyrics();
        var c = layout.content();
        int top = layout.expanded() ? layout.header().y() + 30 : c.y();
        return new Bounds(c.x(), top, c.width(), Math.max(0, c.bottom() - top));
    }
    private Bounds lyricArea() {
        if (immersive) return stage.lyrics();
        var c = lyricContent();
        int top = c.y() + (c.width() >= 360 ? 0 : 16);
        return c.width() >= 360 ? new Bounds(c.x() + c.width() * 44 / 100 + 8, top,
                c.width() * 56 / 100 - 8, Math.max(0, c.bottom() - top - 38))
                : new Bounds(c.x(), top, c.width(), Math.max(0, c.bottom() - top - 38));
    }
    private void paintArtwork(UiFrame ui, Bounds bounds, OnlineTrack track, float activity) {
        var texture = artwork.texture(track);
        if (texture.isPresent()) OnlineMusicViewPaint.cover(ui, bounds, texture.get());
        else OnlineMusicViewPaint.artwork(ui, bounds, activity);
    }
    private void renderLyrics(UiFrame ui, OnlineMusicPlayer.Snapshot current) {
        var c = lyricContent(); var lines = lyricArea();
        back.setY(layout.expanded() ? layout.header().y() + 2 : layout.searchBar().y() + 2);
        var title = current.track() == null ? label("view.now_playing") : MpTypography.text(current.track().title());
        if (!immersive && c.width() >= 360) {
            int column = c.width() * 44 / 100;
            int size = Math.max(16, Math.min(column - 32, c.height() - 46));
            var cover = new Bounds(c.x() + (column - size) / 2, c.y() + Math.max(0, (c.height() - size - 38) / 2), size, size);
            paintArtwork(ui, cover, current.track(), 0f);
            text(ui, title, cover.x(), cover.bottom() + 10, size);
            if (current.track() != null) muted(ui, MpTypography.text(current.track().artist()), cover.x(), cover.bottom() + 23, size);
        } else if (!immersive) text(ui, title, c.x() + 4, c.y() + 2, c.width() - 8);
        if (!Objects.equals(lyricTrack, current.track())) { lyricTrack = current.track(); lyricViewport.reset(); }
        var feed = player.lyricFeed().frame();
        long position = feed.alive() ? feed.snapshot().positionMs() : current.positionMs();
        lyricOffset = Math.max(0, position - current.positionMs());
        var palette = LyricTextPalette.sample(UiTheme.current().text(), UiTheme.current().accent(), UiTheme.current().background() | 0xff000000, 0, ui.preferences().highContrast());
        var timeline = current.lyrics(); int index = timeline.indexAt(position); activeLyric = index;
        long now = System.nanoTime() / 1_000_000L;
        boolean focused = lyricRows.stream().anyMatch(row -> row.visible && row.keyboardFocused());
        double center = lyricViewport.sample(Math.max(0, index), timeline.size(), now, ui.preferences().reducedMotion() || !LyricViewport.canAnimate(lines.height()), focused);
        int rowHeight = immersive ? stage.lyricRowHeight() : 24;
        int visibleLines = Math.max(1, lines.height() / rowHeight);
        lyricsScroll.configure(lyricViewport.scrollPosition(index), timeline.size() + visibleLines - 1, visibleLines);
        int firstLine = Math.max(0, (int)Math.floor(center) - lyricRows.size() / 2);
        for (int slot = 0; slot < lyricRows.size(); slot++) {
            int i = firstLine + slot; var row = lyricRows.get(slot);
            int y = lines.y() + lines.height() / 2 - rowHeight / 2 + (int)Math.round((i - center) * rowHeight);
            row.setX(lines.x()); row.setY(y); row.setWidth(Math.max(1,lines.width()-10)); row.setHeight(rowHeight);
            row.setStage(immersive, immersive ? stage.lyricScale() : 1f);
            boolean present = i < timeline.size() && y >= lines.y() && y + rowHeight <= lines.bottom()
                    && current.lyricsState() == OnlineMusicPlayer.LyricsState.READY;
            row.active = present && seek.active; row.visible = present; lyricTargets.set(slot, present ? new LyricSeekTarget(current.track(), timeline, timeline.line(i).startMs(), lyricOffset) : null);
            if (!present) continue;
            var line = timeline.line(i); var words = Component.empty();
            // Keep provider word timing and text intact. Only the current line receives karaoke color.
            for (var word : line.words()) words.append(i == index
                    ? MpTypography.text(word.text()).withStyle(style -> style.withColor(
                        word.startMs() <= position ? palette.active() : palette.text()))
                    : MpTypography.text(word.text()));
            row.setMessage(words); row.setSelected(i == index); row.setDistance((float)(i - center));
            row.setPreview(label("view.seek_line", time(line.startMs()), words).append("\n").append(MpTypography.text(line.translation())));
        }
        followLyrics.visible = lyricViewport.browsing(now); followLyrics.active = followLyrics.visible;
        if (index >= 0 && index < timeline.size())
            muted(ui, MpTypography.text(timeline.line(index).translation()), lines.x() + 12, lines.bottom() + 25, lines.width() - 24);
        if (current.lyricsState() != OnlineMusicPlayer.LyricsState.READY)
            text(ui, label("lyrics." + current.lyricsState().name().toLowerCase(Locale.ROOT)), lines.x() + 12, lines.y() + lines.height() / 2, lines.width() - 24);
    }
    private void renderAccounts(UiFrame ui) {
        if (!accountDetail) {
            for (var entry : accounts.entrySet()) {
                var card = entry.getValue(); boolean signedIn = player.authenticated(entry.getKey());
                card.setSelected(signedIn);
                var status = label(signedIn ? "view.signed_in" : "view.guest");
                var saved = label(player.remembersCredentials(entry.getKey()) ? "account.saved" : "account.session");
                card.setPreview(status.copy().append("\n").append(saved).append("\n").append(label("account.manage_hint")));
                card.setSecondary(status.copy().append(" · ").append(saved));
            }
            var c = accountContent(accountOffset);
            OnlineMusicViewPaint.paragraph(ui, host.font(), label("login.session_only"), new Bounds(c.x() + 8, c.y() + 124, c.width() - 16, Math.max(0, c.height() - 126)), UiTheme.current().text());
            return;
        }
        var c = accountContent(accountOffset); var snapshot = login == null ? null : login.snapshot();
        boolean signedIn = player.authenticated(accountSource);
        if (signedIn != accountWasSignedIn) { accountWasSignedIn = signedIn; invalidateLibrarySource(accountSource); clearAccountFeedback(); if (signedIn) loadAccount(); updateVisibility(); }
        String state = snapshot == null ? player.authenticated(accountSource) ? "confirmed" : "idle" : snapshot.state().name().toLowerCase(Locale.ROOT);
        var status = label("login." + state);
        loginSignOut.active = player.authenticated(accountSource);
        boolean busy = snapshot != null && (snapshot.state() == OnlineMusicLogin.State.CREATING || snapshot.state() == OnlineMusicLogin.State.WAITING || snapshot.state() == OnlineMusicLogin.State.SCANNED);
        loginRefresh.setMessage(label(busy ? "login.restart" : signedIn && !accountState.equals("library.login_required") ? "account.retry" : "login.refresh"));
        loginRefresh.active = pendingAccount == null;
        var details = status.copy().append("\n").append(label(signedIn ? "account.library_hint" : "login.scan." + accountSource)).append("\n").append(label("login.remember_hint"));
        loginRefresh.setPreview(details); back.setPreview(details); remember.setPreview(details);
        boolean wide = c.width() >= 360;
        int imageX = wide ? c.x() + c.width() / 2 + 4 : c.x();
        int bodyStart=wide?76:108;
        var imageArea = new Bounds(imageX, c.y() + bodyStart, wide ? c.width() / 2 - 4 : c.width(), Math.max(0, c.height() - bodyStart));
        int bodyBottom = imageArea.bottom();
        int statusY = imageArea.y() + 2;
        int instructionsY = statusY + host.font().lineHeight + 6;
        if (!wide) imageArea = new Bounds(imageArea.x(), instructionsY, imageArea.width(), Math.max(0, bodyBottom - instructionsY));
        boolean fitsImage = snapshot == null || snapshot.image() == null ||
                (imageArea.width() >= snapshot.image().width() && imageArea.height() >= snapshot.image().height()
                    && layout.content().height() >= snapshot.image().height());
        if (bodyBottom - statusY >= host.font().lineHeight) text(ui, fitsImage ? status : label("login.enlarge"), c.x() + 4, statusY, wide ? c.width() / 2 - 12 : c.width() - 8);
        if (wide && bodyBottom - instructionsY >= host.font().lineHeight) OnlineMusicViewPaint.paragraph(ui, host.font(), label(signedIn ? "account.library_hint" : "login.scan." + accountSource),
                new Bounds(c.x() + 4, instructionsY, c.width() / 2 - 16, bodyBottom - instructionsY), UiTheme.current().text());
        if (snapshot != null && snapshot.image() != null) {
            var image = snapshot.image(); int scale = Math.min(layout.content().height() / image.height(), Math.min(3, Math.min(imageArea.width() / image.width(), imageArea.height() / image.height())));
            if (scale < 1) OnlineMusicViewPaint.paragraph(ui, host.font(), label("login.enlarge"), imageArea, UiTheme.current().text());
            else {
                int left = imageArea.x() + (imageArea.width() - image.width() * scale) / 2, top = imageArea.y() + (imageArea.height() - image.height() * scale) / 2;
                ui.graphics().fill(left, top, left + image.width() * scale, top + image.height() * scale, 0xffffffff);
                for (int y = 0; y < image.height(); y++) for (int start = 0; start < image.width();) {
                    if (!image.dark(start, y)) { start++; continue; }
                    int end = start + 1; while (end < image.width() && image.dark(end, y)) end++;
                    ui.graphics().fill(left + start * scale, top + y * scale, left + end * scale, top + (y + 1) * scale, 0xff000000); start = end;
                }
            }
        } else if (!wide) OnlineMusicViewPaint.paragraph(ui, host.font(), label(signedIn ? "account.library_hint" : "login.scan." + accountSource), imageArea, UiTheme.current().text());
    }
    private void text(UiFrame ui, Component text, int x, int y, int width) { OnlineMusicViewPaint.line(ui, host.font(), text, x, y, width, UiTheme.current().text()); }
    private void muted(UiFrame ui, Component text, int x, int y, int width) { OnlineMusicViewPaint.line(ui, host.font(), text, x, y, width, UiTheme.current().secondaryText(0xff94a3b8, .25f)); }
    private static String time(long millis) { long seconds = Math.max(0, millis) / 1000; return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    private static MutableComponent label(String key, Object... args) { return MpTypography.text(Component.translatable(TEXT + key, args)); }
    @Override public void suspend() { if(queuePlaybackMode!=null)queuePlaybackMode.cancelInteraction(); if(stagePlaybackMode!=null)stagePlaybackMode.cancelInteraction(); if(seek!=null)seek.cancelInteraction(); for(var c:new MusicTransportControl[]{playback,stop,lyricsToggle,miniQueue,miniPrevious,miniNext})if(c!=null)c.cancelInteraction(); if(stageSeek!=null)stageSeek.cancelInteraction(); if(stageVolume!=null)stageVolume.cancelInteraction(); queue.suspend(); if(stageQueueScroll!=null)stageQueueScroll.cancelDrag(); for(var control:new MusicTransportControl[]{stageBack,stagePlay,stagePrevious,stageNext,stageMode,stageQueue})if(control!=null)control.cancelInteraction(); for (var scroll : new OnlineMusicScrollRegion[]{searchScroll, libraryScroll, lyricsScroll, accountScroll, queueScroll}) if (scroll != null) scroll.cancelDrag(); navigation.forEach(MusicTextControl::cancelInteraction); if(sourceSegments!=null)sourceSegments.cancelInteraction();if(librarySources!=null)librarySources.cancelInteraction();if(libraryGroups!=null)libraryGroups.cancelInteraction(); cancelSearch(); cancelSuggestions(); cancelLogin(); cancelLibrary(); clearAccountFeedback(); library = Library.NONE; accountDetail = false; if (ownsPlayer) player.pause(); }
    @Override public void close() { if (!closed) { suspend(); navigation.forEach(MusicTextControl::cancelInteraction); if(sourceSegments!=null)sourceSegments.cancelInteraction();if(librarySources!=null)librarySources.cancelInteraction();if(libraryGroups!=null)libraryGroups.cancelInteraction(); visualMotion.close(); artwork.close(); artworkStage.close(); if (ownsPlayer) player.close(); closed = true; } }
}
