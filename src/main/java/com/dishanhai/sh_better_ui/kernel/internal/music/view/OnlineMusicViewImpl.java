// Author: dishanhai. Layout adapted from MuoniumPlayer NCMScreen, NavigateBar,
// ControlsBar, MusicLyricsPanel and AccountManagerOverlay (Apache-2.0).
// See META-INF/third-party/muonium-player/SOURCE for revision and modifications.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
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
import com.dishanhai.sh_better_ui.kernel.internal.music.Listen1Backup;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNavigation;
import java.io.IOException;
import java.nio.file.Path;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;
import java.util.concurrent.CompletableFuture;

/** Complete UUK player page; all widgets remain native and all asynchronous work belongs to the session. */
public final class OnlineMusicViewImpl implements OnlineMusicView {
    private enum Page { SEARCH, LIBRARY, LYRICS, QUEUE, ACCOUNTS, HISTORY }
    private final OnlineMusicRouteMotion routeMotion = new OnlineMusicRouteMotion();
    private record SearchRoute(String source, long rowsRevision) {}
    private record LibraryRoute(String source, Library group, String playlist, long rowsRevision) {}
    private long searchRowsRevision, libraryRowsRevision;
    private boolean immersive, stageFailure;
    private net.minecraft.client.gui.components.events.GuiEventListener shellBack;
    private Page stageReturnPage = Page.SEARCH, lyricsReturnPage = Page.SEARCH;
    private FullscreenLyricLayout stage;
    private final List<AbstractWidget> stageControls = new ArrayList<>();
    private enum StagePage { ARTWORK, LYRICS, QUEUE }
    private StagePage stagePage=StagePage.LYRICS;
    private final MusicArtworkStage artworkStage=new MusicArtworkStage();
    private final OnlineCoverReveal coverReveal=new OnlineCoverReveal();
    private final Map<MusicTextControl,Object> resultIdentities=new IdentityHashMap<>();
    private MusicSegmentedControl sourceSegments;
    private List<String> searchSourceIds=List.of();
    private final com.dishanhai.sh_better_ui.kernel.api.music.status.MusicStatusView searchStatus = new com.dishanhai.sh_better_ui.kernel.api.music.status.MusicStatusView();
    private final com.dishanhai.sh_better_ui.kernel.api.music.status.MusicStatusView queueStatus = new com.dishanhai.sh_better_ui.kernel.api.music.status.MusicStatusView();
    private MusicTransportControl exitControl;
    private UiButton nowPlayingMore;
    private NowPlayingMenuHost nowPlayingMenus;
    private void closeNowPlayingMenu() { if(nowPlayingMenus!=null)nowPlayingMenus.interrupt(); }
    private void openNowPlayingMenu() {
        var track=player.snapshot().track();
        if(track==null||!immersive)return;
        boolean liked=Boolean.TRUE.equals(knownLike(track));
        var actions=new ArrayList<NowPlayingMenu.Entry>();
        actions.add(new NowPlayingMenu.Entry(label(liked?"menu.unlike":"menu.like"),MusicControlRenderer.Icon.FAVORITE,
                player.supportsLikes(track.source())&&player.authenticated(track.source())&&pendingLike==null,
                ()->changeLike(track,!liked)));
        actions.add(new NowPlayingMenu.Entry(label("menu.queue"),MusicControlRenderer.Icon.PLAYLIST,true,()->{if(stage.lyrics().height()<56)show(Page.QUEUE);else setStagePage(StagePage.QUEUE);}));
        actions.add(new NowPlayingMenu.Entry(label("menu.playlists"),MusicControlRenderer.Icon.LIBRARY,
                libraryProviders.contains(track.source()),()->{
                    librarySource=track.source();libraryGroup=Library.PLAYLISTS;library=Library.NONE;enterLibrary();
                }));
        actions.add(new NowPlayingMenu.Entry(label("menu.search_album"),MusicControlRenderer.Icon.SEARCH,
                !track.album().isBlank()&&searchSourceIds.contains(track.source()),()->searchFromMenu(track,track.album())));
        actions.add(new NowPlayingMenu.Entry(label("menu.search_artist"),MusicControlRenderer.Icon.USER,
                !track.artist().isBlank()&&searchSourceIds.contains(track.source()),()->searchFromMenu(track,track.artist())));
        if(player.supportsSoundEffects())actions.add(new NowPlayingMenu.Entry(label("menu.qq_effects"),
                MusicControlRenderer.Icon.SETTINGS,true,()->openQqEffects(track)));
        if(player.supportsEqualizer())actions.add(new NowPlayingMenu.Entry(label("menu.equalizer"),
                MusicControlRenderer.Icon.SETTINGS,true,()->openEqualizer(track)));
        nowPlayingMenus.open(nowPlayingMore,layout.frame(),label("menu.title"),actions,NowPlayingMenu.Layout.QUICK_ACTIONS,
                ()->!closed&&immersive&&Objects.equals(player.snapshot().track(),track));
    }
    private void openEqualizer(OnlineTrack track) {
        if(!player.supportsEqualizer()||!Objects.equals(player.snapshot().track(),track))return;
        closeNowPlayingMenu();
        var selected=player.equalizerPreset();
        var entries=new ArrayList<NowPlayingMenu.Entry>();
        for(var preset:OnlineMusicEqualizerPreset.values()){
            var name=label("menu.eq."+preset.name().toLowerCase(Locale.ROOT));
            entries.add(new NowPlayingMenu.Entry(preset==selected?label("menu.eq.current",name):name,null,true,
                    ()->perform(name,()->player.equalizerPreset(preset))));
        }
        nowPlayingMenus.open(nowPlayingMore,layout.frame(),label("menu.equalizer"),entries,NowPlayingMenu.Layout.LIST,
                ()->!closed&&immersive&&Objects.equals(player.snapshot().track(),track)&&player.supportsEqualizer());
    }
    private void openQqEffects(OnlineTrack track) {
        if(!player.supportsSoundEffects()||!Objects.equals(player.snapshot().track(),track))return;
        closeNowPlayingMenu();
        var selected=player.soundEffect();
        var entries=new ArrayList<NowPlayingMenu.Entry>();
        for(var effect:OnlineMusicSoundEffect.values()){
            var name=label("menu.qq_effect."+effect.name().toLowerCase(Locale.ROOT));
            entries.add(new NowPlayingMenu.Entry(effect==selected?label("menu.eq.current",name):name,null,true,
                    ()->perform(name,()->player.soundEffect(effect)),effect==selected));
        }
        nowPlayingMenus.open(nowPlayingMore,layout.frame(),label("menu.qq_effects"),entries,NowPlayingMenu.Layout.EFFECT_TILES,
                ()->!closed&&immersive&&Objects.equals(player.snapshot().track(),track)&&player.supportsSoundEffects());
    }
    private void searchFromMenu(OnlineTrack track,String value) {
        selectSource(track.source());query=value;queryField.setValue(value);search(1,true);
    }

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
    private List<String> libraryProviders=List.of(), librarySourceIds=List.of();
    private final Listen1Backup.Session listen1Session;
    private Listen1Backup.Imported listen1Backup() { return listen1Session.imported(); }
    private CompletableFuture<Listen1Backup.Imported> pendingListen1;
    private UiTextField listen1Path;
    private MusicTextControl listen1ImportButton;
    private String listen1File = "";
    private Component listen1Status = label("listen1.hint");
    private boolean listen1Editing;
    private boolean listen1Selected() { return Listen1Backup.ID.equals(librarySource); }
    private boolean listen1EditorVisible() { return listen1Selected()&&listen1Editing&&page==Page.LIBRARY&&!immersive; }
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
    private final List<MusicTextControl> queueRows = new ArrayList<>(), queueRemoves = new ArrayList<>(), queueUps = new ArrayList<>(), queueDowns = new ArrayList<>();
    private MusicTextControl queueAppend;
    private OnlineTrack queueAppendTrack;
    private List<OnlineTrack> boundQueueTracks = List.of();
    private int boundQueueFirst = -1;
    private OnlineMusicScrollRegion queueScroll;
    private MusicTextControl queueUndo, queueClear, queueLocate, queueEdit;
    private boolean queueEditing;
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
    private MusicTextControl searchButton, spotifySearch, previous, next, nowPlaying, back;
    private MusicTransportControl playback, stop, lyricsToggle, miniQueue, miniPrevious, miniNext;
    private MusicTextControl loginRefresh, loginImport, loginSignOut;
    private final Map<String, Boolean> confirmedLikes = new HashMap<>();
    private final Map<String, Long> confirmedLikeEpochs = new HashMap<>();
    private long observedWrites;
    private long accountVersion(String source) { return operations()==null ? 0 : operations().accountVersion(source); }
    private Boolean knownLike(OnlineTrack track) {
        var saved = confirmedLikes.get(trackKey(track));
        return saved != null && confirmedLikeEpochs.getOrDefault(trackKey(track),0L)==accountVersion(track.source()) ? saved : null;
    }
    private void forgetLike(OnlineTrack track) {
        confirmedLikes.remove(trackKey(track)); confirmedLikeEpochs.remove(trackKey(track));
    }
    private void rememberLike(OnlineTrack track, boolean liked, SessionMusicOperations.Handle<?> handle) {
        confirmedLikes.put(trackKey(track),liked);
        confirmedLikeEpochs.put(trackKey(track),handle==null ? accountVersion(track.source()) : handle.accountVersion());
    }

    private OnlineTrack likeTrack;
    private boolean requestedLike;
    private MusicTextControl remember, libraryPrevious, libraryNext, libraryRefresh;
    private MusicSeekControl seek;
    private final OnlineMusicFeedback interactionFeedback = new OnlineMusicFeedback();
    private String observedLoginState = "";
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
    private SessionMusicOperations.Handle<List<OnlineTrack>> searchOperation, trackOperation;
    private SessionMusicOperations.Handle<?> displayedSearchOperation, displayedLibraryOperation;
    private SessionMusicOperations.Handle<List<OnlinePlaylist>> playlistsOperation;
    private MusicTextControl searchHistory, historyPrevious, historyNext;
    private Page historyDestination;
    private long historyReturnId;
    private final List<AbstractWidget> historyControls = new ArrayList<>();
    private final MusicLoadingSkeleton searchLoading = new MusicLoadingSkeleton();
    private final MusicLoadingSkeleton libraryLoading = new MusicLoadingSkeleton();
    private List<UiSkeleton.Block> searchSkeleton = List.of(), librarySkeleton = List.of();
    private Bounds skeletonClip;
    private final List<MusicTextControl> historyRows = new ArrayList<>();
    private final Map<MusicTextControl,Long> historyBindings = new IdentityHashMap<>();
    private int historyFirst;
    private MusicOperationNavigation.Target pendingHistory;
    private List<SessionMusicOperations.Snapshot> history = List.of();
    private SessionMusicOperations operations() { return SessionMusicOperations.forPlayer(player).orElse(null); }
    private CompletableFuture<List<String>> pendingSuggestions;
    private CompletableFuture<List<OnlinePlaylist>> pendingPlaylists;
    private CompletableFuture<List<OnlineTrack>> pendingLibrary;
    private CompletableFuture<OnlineMusicAccount> pendingAccount;
    private CompletableFuture<Void> pendingLike;
    private SessionMusicOperations.Handle<Void> likeOperation;
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
    public OnlineMusicViewImpl(OnlineMusicPlayer player, boolean ownsPlayer) {
        this(player, ownsPlayer, new Listen1Backup.Session());
    }
    public OnlineMusicViewImpl(OnlineMusicPlayer player, boolean ownsPlayer, Listen1Backup.Session listen1Session) {
        this.player = Objects.requireNonNull(player); this.queue = new PlayerQueue(player); this.ownsPlayer = ownsPlayer;
        this.listen1Session = Objects.requireNonNull(listen1Session);
    }
    @Override public OnlineMusicPlayer player() { return player; }
    @Override public void layout(PreviewContext context) {
        if (closed) throw new IllegalStateException("Music view closed");
        closeNowPlayingMenu();
        if(stagePlaybackMode!=null)stagePlaybackMode.cancelInteraction();
        if(queuePlaybackMode!=null)queuePlaybackMode.cancelInteraction();
        navigation.forEach(MusicTextControl::cancelInteraction); if(sourceSegments!=null)sourceSegments.cancelInteraction();if(librarySources!=null)librarySources.cancelInteraction();if(libraryGroups!=null)libraryGroups.cancelInteraction(); navigation.clear();
        routeMotion.reset();
        host = context; layout = OnlineMusicLayout.of(context.viewport()); stage = null; queryField = null;
        stageControls.clear(); stageQueueRows.clear(); stageLyricKey=null;
        queueControls.clear(); queueRows.clear(); queueRemoves.clear(); queueUps.clear(); queueDowns.clear(); boundQueueFirst = -1;
        sourceSegments=null;resultIdentities.clear();
        libraryHeaderControls.clear();librarySources=null;libraryGroups=null;listen1Path=null;listen1ImportButton=null;
        searchControls.clear(); lyricControls.clear(); accountControls.clear(); detailControls.clear(); libraryControls.clear();
        resultRows.clear(); lyricRows.clear(); libraryRows.clear(); searchLikes.clear(); searchUnlikes.clear(); libraryLikes.clear(); libraryUnlikes.clear(); lyricTargets.clear(); sources.clear(); accounts.clear(); loginSources.clear();
        exitControl = OnlineMusicScreen.exitControl(context);
        if (!layout.supported()) {
            searchLoading.reset(); libraryLoading.reset();
            return;
        }
        shellBack = OnlineMusicScreen.shellBack(context);
        stageFailure = player.snapshot().state()==OnlineMusicPlayer.State.FAILED;
        stage = FullscreenLyricLayout.of(context.viewport(),shellBack==null,stageFailure,stagePage!=StagePage.ARTWORK);
        nowPlayingMenus=new NowPlayingMenuHost(context);
        buildNavigation(); buildScrollRegions(); buildSearch(); buildLyrics(); buildAccounts(); buildLibrary(); buildQueue(); buildTransport(); buildImmersive(); buildHistory();
        skeletonClip = listScrollArea();
        searchSkeleton = loadingBlocks(resultRows, true);
        librarySkeleton = loadingBlocks(libraryRows, false);
        var h = layout.searchBar();
        back = button(new Bounds(h.x(), h.y() + 2, Math.min(88, h.width() / 2), 22), label("view.back"), () -> {
            if(returnFromLibraryLogin()||returnFromHistoryDestination()) return;
            if(page==Page.HISTORY) { leaveHistory();
            } else if(page==Page.QUEUE&&queueEditing) { setQueueEditing(false);
            } else if (dismissListen1Editor()) { return;
            } else if(page==Page.LIBRARY&&library==Library.TRACKS) {
                returnToPlaylistList();
            } else if(page==Page.ACCOUNTS&&accountDetail){returnToAccountList();}
            else show(page == Page.LYRICS ? lyricsReturnPage : Page.SEARCH);
        }, null);
        back.setLeadingIcon(GraphicIcon.BACK);
        updateRows(); updateLibraryRows(); updateVisibility();
        if (pendingHistory != null) {
            var target = pendingHistory;
            pendingHistory = null;
            if (target.validFor(operations())) {
                show(Page.HISTORY);
                focusHistoryRow(target.id());
            }
        }
    }
    private void buildNavigation() {
        var n = layout.navigation();
        if (exitControl != null) { exitControl.setX(n.x()); exitControl.setY(n.y()); }
        int offset=exitControl==null?0:30;
        int tabWidth=Math.max(1,(n.width()-offset)/5);
        for(var target:List.of(Page.SEARCH,Page.LIBRARY,Page.LYRICS,Page.QUEUE,Page.ACCOUNTS)) {
            var box=layout.sidebar()?new Bounds(n.x(),n.y()+34+target.ordinal()*Math.min(30,(n.height()-34)/5),n.width(),Math.min(28,(n.height()-34)/5-2))
                    :new Bounds(n.x()+offset+target.ordinal()*tabWidth,n.y(),tabWidth,26);
            var item=button(box,label("view."+target.name().toLowerCase(Locale.ROOT)),()->navigate(target),null);
            item.setVariant(MusicTextControl.Variant.NAVIGATION);item.setPreview(item.getMessage());
            if(!layout.sidebar()&&tabWidth<60) item.setIcon(switch(target){case SEARCH->GraphicIcon.SEARCH;case LYRICS->GraphicIcon.MUSIC;case ACCOUNTS,HISTORY->GraphicIcon.USER;case QUEUE->GraphicIcon.QUEUE;case LIBRARY->GraphicIcon.FAVORITE;});
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
        scrollVisibility(libraryScroll, page == Page.LIBRARY&&!immersive&&!listen1EditorVisible());
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
        int minimum = accountDetail ? 340 : Math.max(320, 214 + host.font().split(OnlineMusicViewPaint.wrapContent(label("login.session_only")), Math.max(1, c.width() - 28)).size() * (MusicTypography.LINE_HEIGHT + 3));
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
        return switch(id){case "netease","qq",Listen1Backup.ID->label("source."+id);case "apple"->MusicTypography.text("Apple Music");default->MusicTypography.text(id);};
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
            var provider=button(layout.sourceBar(),searchSourceIds.isEmpty()?label("spotify.no_native_sources"):sourceLabel(source),()->{},searchControls);
            provider.active=false;
        }
        boolean hasHistory=operations()!=null;
        int historyWidth=hasHistory?(h.width()>=260?Math.min(100,Math.max(64,host.font().width(label("operations.short_title"))+18)):24):0;
        var spotifyLabel=label("spotify.link");
        int spotifyWidth=Math.min(84,Math.max(58,host.font().width(spotifyLabel)+18));
        int spotifyRight=h.right()-(hasHistory?historyWidth+4:0);
        int searchRight=spotifyRight-spotifyWidth-4;
        queryField = host.add(new UiTextField(host.font(), new Bounds(h.x(), h.y() + 2, Math.max(1,searchRight-h.x()-28), 22), label("query"), 200, host.preferences()));
        MusicInputAppearance.apply(queryField);
        searchControls.add(queryField);
        queryField.setValue(query); queryField.setHint(label("query")); queryField.setOnChange(this::queryChanged);
        queryField.setOnSubmit(value -> { query = value; search(1, true); });
        searchButton = button(new Bounds(searchRight - 24, h.y() + 2, 24, 22), label("search"), () -> {
            if (pending != null) { cancelSearch(); searchState = "cancelled"; updateRows(); }
            else search(1, true);
        }, searchControls);
        searchButton.setIcon(GraphicIcon.SEARCH);
        spotifySearch=button(new Bounds(spotifyRight-spotifyWidth,h.y()+2,spotifyWidth,22),
                spotifyLabel,this::openSpotifySearch,searchControls);
        spotifySearch.setPreview(label("spotify.hint"));
        searchHistory=null;
        if(hasHistory){
            searchHistory=button(new Bounds(h.right()-historyWidth,h.y()+2,historyWidth,22),historyWidth==24?label("operations.title"):label("operations.short_title"),()->{if(!returnFromHistoryDestination())show(Page.HISTORY);},searchControls);
            if(historyWidth==24)searchHistory.setGlyphOnly(MusicControlRenderer.Icon.HISTORY);
            searchHistory.setPreview(label("operations.title"));
        }
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
    record ExternalSearchBookmark(OnlineMusicPlayer player, String source, String query, String submittedQuery,
                                  int remotePage, int first, List<OnlineTrack> results, String state,
                                  long accountVersion, SessionMusicOperations.Handle<?> displayed) {}

    private void openSpotifySearch() {
        if (closed || immersive || page != Page.SEARCH) return;
        var url = SpotifySearchLink.forQuery(queryField.getValue());
        var bookmark = new ExternalSearchBookmark(player, source, queryField.getValue(), submittedQuery,
                remotePage, first, List.copyOf(results),
                pending == null ? searchState : searchOperation != null ? "operations.background" : "cancelled",
                accountVersion(source), pending == null ? displayedSearchOperation : null);
        var origin = host;
        if (!ShBetterUiApi.links().open(origin.screen(), url,
                () -> OnlineMusicScreen.restoreExternalSearch(origin, this, bookmark))) {
            feedback(label("spotify.unavailable"));
        }
    }

    MusicTextControl restoreExternalSearch(ExternalSearchBookmark bookmark) {
        // A vanilla confirmation replaces the Screen and can remount its player view on return.
        // Do not re-submit the provider query or retain rows from a replaced account/session.
        if (closed || page != Page.SEARCH || immersive) return null;
        query = bookmark.query();
        if (queryField != null) {
            queryField.setValue(query);
            cancelSuggestions();
        }
        if (searchSourceIds.contains(bookmark.source())) {
            source = bookmark.source();
            if (bookmark.player() == player && accountVersion(source) == bookmark.accountVersion()
                    && (bookmark.displayed() == null || bookmark.displayed().displayValid())) {
                submittedQuery = bookmark.submittedQuery();
                remotePage = bookmark.remotePage();
                first = Math.min(bookmark.first(), Math.max(0, bookmark.results().size() - visibleRows));
                results = bookmark.results();
                searchState = bookmark.state();
                displayedSearchOperation = bookmark.displayed();
            }
        }
        if (queryField == null) return null; // Window may have been resized below the player minimum.
        updateVisibility();
        return spotifySearch;
    }

    MusicTextControl spotifySearchControl() {
        return !closed && queryField != null && spotifySearch.visible ? spotifySearch : null;
    }
    private void buildHistory() {
        historyControls.clear();historyRows.clear();historyBindings.clear();
        for(var box:musicRows()) {
            final MusicTextControl[] holder=new MusicTextControl[1];
            var row=button(box,Component.empty(),()->{var id=historyBindings.get(holder[0]);if(id!=null)restoreOperation(id);},historyControls);holder[0]=row;
            row.setVariant(MusicTextControl.Variant.ROW);historyRows.add(row);
        }
        var pager=layout.pager();
        historyPrevious=button(new Bounds(pager.x(),pager.y(),24,20),label("previous"),()->{historyFirst=Math.max(0,historyFirst-historyRows.size());updateHistory();},historyControls);
        historyPrevious.setIcon(GraphicIcon.PREVIOUS);
        historyNext=button(new Bounds(pager.right()-24,pager.y(),24,20),label("next"),()->{if(historyFirst+historyRows.size()<history.size())historyFirst+=historyRows.size();updateHistory();},historyControls);
        historyNext.setIcon(GraphicIcon.NEXT);
    }
    private String historyAction(SessionMusicOperations.Snapshot item) {
        if(item.state()==SessionMusicOperations.State.CANCELLED||item.state()==SessionMusicOperations.State.SUPERSEDED)return "unavailable";
        if(item.kind().mutation())return libraryProviders.contains(item.source())?"liked":"unavailable";
        if(item.kind()==SessionMusicOperations.Kind.LOGIN)return accounts.containsKey(item.source())&&player.sources().contains(item.source())?"account":"unavailable";
        return "restore";
    }
    private void updateHistory() {
        history=operations()==null?List.of():new ArrayList<>(operations().snapshots());
        if(!history.isEmpty())Collections.reverse(history);
        int pageSize=Math.max(1,historyRows.size());
        historyFirst=Math.min(historyFirst,Math.max(0,(history.size()-1)/pageSize*pageSize));
        updateHistoryPager(historyPrevious,historyFirst>0);
        updateHistoryPager(historyNext,historyFirst+pageSize<history.size());
        for(int i=0;i<historyRows.size();i++) {
            var row=historyRows.get(i);int at=historyFirst+i;
            row.visible=!immersive&&page==Page.HISTORY&&at<history.size();
            if(at>=history.size()){row.active=false;row.cancelInteraction();historyBindings.remove(row);if(host.screen().getFocused()==row)host.screen().setFocused(back);continue;}
            var item=history.get(at);
            if(!Objects.equals(historyBindings.get(row),item.id())){
                row.cancelInteraction();
                if(host.screen().getFocused()==row)host.screen().setFocused(back);
                historyBindings.put(row,item.id());
            }
            var action=historyAction(item);
            row.active=!action.equals("unavailable");
            if(!row.visible||!row.active)row.cancelInteraction();
            var title=label("operations.kind."+item.kind().name().toLowerCase(Locale.ROOT));
            var state=label(item.error()==SessionMusicOperations.Error.EXPIRED ? "operations.state.expired" : item.error()==SessionMusicOperations.Error.UNKNOWN ? "operations.state.unknown" : item.error()==SessionMusicOperations.Error.REJECTED ? "operations.state.rejected" : "operations.state."+item.state().name().toLowerCase(Locale.ROOT));
            var nextStep=label("operations.action."+action);
            row.setMessage(title.copy().append(" · ").append(state));
            row.setSecondary(nextStep.copy().append(" · ").append(sourceLabel(item.source())));
            row.setPreview(title.copy().append(" · ").append(sourceLabel(item.source())).append(" · ").append(state)
                    .append("\n").append(nextStep)
                    .append(item.error()==SessionMusicOperations.Error.UNKNOWN ? Component.literal("\n").append(label("library.like_unconfirmed")) : Component.empty()));
            if(!row.active&&row.isFocused())host.screen().setFocused(back);
        }
    }
    private void updateHistoryPager(MusicTextControl control,boolean active) {
        if(control==null)return;
        control.active=active;
        if(!active){control.cancelInteraction();if(control.isFocused())host.screen().setFocused(back);}
    }
    private void leaveHistory() {
        show(Page.SEARCH);
        if(searchHistory!=null&&searchHistory.visible)host.screen().setFocused(searchHistory);
    }
    /** Called before the dedicated route mounts; never executes or restores the operation itself. */
    public void requestOperationHistory(MusicOperationNavigation.Target target) {
        this.pendingHistory = Objects.requireNonNull(target);
    }

    private boolean returnFromHistoryDestination() {
        if(immersive||page!=historyDestination)return false;
        long id=historyReturnId;
        show(Page.HISTORY);
        focusHistoryRow(id);
        return true;
    }
    private void focusHistoryRow(long id) {
        for(int i=0;i<history.size();i++)if(history.get(i).id()==id){
            int size=Math.max(1,historyRows.size());historyFirst=i/size*size;updateHistory();break;
        }
        for(var row:historyRows)if(row.active&&row.visible&&Objects.equals(historyBindings.get(row),id)){host.screen().setFocused(row);break;}
    }
    @SuppressWarnings("unchecked")
    private void restoreOperation(long id) {
        var registry=operations();if(registry==null)return;
        var optional=registry.find(id);if(optional.isEmpty()) {feedback(label("operations.expired"));updateHistory();return;}
        var handle=optional.get();if(!handle.valid()){feedback(label("operations.expired"));updateHistory();return;}
        var request=handle.request();
        String action=historyAction(handle.snapshot());
        if(action.equals("unavailable"))return;
        boolean fromHistory=page==Page.HISTORY;
        if (request.kind().mutation()) {
            show(Page.LIBRARY);
            displayedLibraryOperation=null; librarySource=request.source();
            selectedPlaylist=null; playlists=List.of(); libraryTracks=List.of();
            openLibraryGroup(Library.LIKED);
            if(fromHistory){historyDestination=page;historyReturnId=id;}
            return;
        }
        if (request.kind()==SessionMusicOperations.Kind.LOGIN) {
            show(Page.ACCOUNTS);openAccount(request.source());updateVisibility();
            if(fromHistory){historyDestination=page;historyReturnId=id;}
            return;
        }
        if(request.kind()==SessionMusicOperations.Kind.SEARCH) {
            detachSearch();show(Page.SEARCH);source=request.source();submittedQuery=query=request.query();remotePage=request.page();first=0;results=List.of();
            searchOperation=(SessionMusicOperations.Handle<List<OnlineTrack>>)handle;searchOperation.attach();displayedSearchOperation=searchOperation;pending=searchOperation.observe();
            searchState="searching";queryField.setValue(query);cancelSuggestions();updateRows();
        } else {
            detachLibrary();show(Page.LIBRARY);librarySource=request.source();libraryPage=request.page();libraryFirst=0;libraryState="library.loading";
            playlists=List.of();libraryTracks=List.of();selectedPlaylist=request.playlist();
            library=switch(request.kind()){case PLAYLISTS->Library.PLAYLISTS;case PLAYLIST->Library.TRACKS;default->Library.LIKED;};
            libraryGroup=library==Library.LIKED?Library.LIKED:Library.PLAYLISTS;
            handle.attach();
            if(library==Library.PLAYLISTS){playlistsOperation=(SessionMusicOperations.Handle<List<OnlinePlaylist>>)handle;displayedLibraryOperation=playlistsOperation;pendingPlaylists=playlistsOperation.observe();}
            else {trackOperation=(SessionMusicOperations.Handle<List<OnlineTrack>>)handle;displayedLibraryOperation=trackOperation;pendingLibrary=trackOperation.observe();}
        }
        if(fromHistory){historyDestination=page;historyReturnId=id;}
        feedback(label("operations.restored"));updateVisibility();
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
            feedback(label(accountState.isEmpty()?"feedback.account_ready":accountState)); updateVisibility();
        }, detailControls);
        remember.setPreview(label("login.remember_hint"));
        int half = (c.width() - 4) / 2, third = (c.width() - 8) / 3;
        loginRefresh = button(new Bounds(c.x(), c.y() + 48, third, 20), label("login.refresh"), () -> { if (player.authenticated(accountSource) && !accountState.equals("library.login_required") && !loginBusy(login == null ? null : login.snapshot())) loadAccount(); else startLogin(); }, detailControls);
        loginSignOut = button(new Bounds(c.x() + (third + 4) * 2, c.y() + 48, c.width() - (third + 4) * 2, 20), label("login.sign_out"), () -> {
            try { cancelLogin(); cancelLibrary(); clearAccountFeedback(); player.credentials(accountSource, "");invalidateLibrarySource(accountSource); accountName = ""; accountState = "login.signed_out"; }
            catch (RuntimeException failure) { accountState = "login.storage_failed"; }
            feedback(label(accountState.isEmpty()?"feedback.account_ready":accountState)); updateVisibility();
        }, detailControls);
        loginImport = button(new Bounds(c.x() + third + 4, c.y() + 48, third, 20), label("login.import"), () -> {
            try { cancelLibrary(); clearAccountFeedback(); player.credentials(accountSource, OnlineMusicViewPaint.clipboard().strip());invalidateLibrarySource(accountSource); accountState = "credentials_loaded"; cancelLogin(); loadAccount(); }
            catch (IllegalArgumentException failure) { accountState = "credentials_invalid"; }
            catch (RuntimeException failure) { accountState = "login.storage_failed"; }
            feedback(label(accountState.isEmpty()?"feedback.account_ready":accountState)); updateVisibility();
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
        libraryProviders=player.sources().stream().filter(id->id.equals("netease")||id.equals("qq")).distinct().toList();
        var ids=new ArrayList<>(libraryProviders);
        if(!libraryProviders.isEmpty())ids.add(Listen1Backup.ID);
        librarySourceIds=List.copyOf(ids);
        if(!ids.contains(librarySource))librarySource=ids.isEmpty()?"":ids.get(0);
        if(ids.size()>1) librarySources=host.add(new MusicSegmentedControl(layout.sourceBar(),label("view.sources"),ids.stream().map(this::sourceLabel).toList(),ids.indexOf(librarySource),host.preferences(),index->{
            historyDestination=null; cancelLibrary(); cancelListen1();librarySource=ids.get(index);library=Library.NONE;
            if(listen1Selected())showListen1Library();else openLibraryGroup(libraryGroup);
            feedback(sourceLabel(librarySource)); host.screen().setFocused(librarySources);
        }));
        if(librarySources!=null)libraryHeaderControls.add(librarySources);
        if(!libraryProviders.isEmpty()) {
            int inputWidth=c.width()-82;
            listen1Path=host.add(new UiTextField(host.font(),new Bounds(c.x()+4,c.y()+2,inputWidth,22),
                    label("listen1.path"),2048,host.preferences()));
            MusicInputAppearance.apply(listen1Path);
            listen1Path.setHint(label("listen1.path"));
            listen1Path.setValue(listen1File);
            listen1Path.setOnChange(value->{listen1File=value;updateListen1Editor();});
            listen1Path.setOnSubmit(ignored->submitListen1());
            listen1ImportButton=button(new Bounds(c.x()+inputWidth+8,c.y()+2,70,22),label("listen1.import"),
                    this::submitListen1,libraryControls);
        }
        else {var sourceLabel=button(layout.sourceBar(),ids.isEmpty()?label("view.unavailable"):sourceLabel(librarySource),()->{},libraryHeaderControls);sourceLabel.active=false;}
        var h=layout.searchBar();
        libraryGroups=host.add(new MusicSegmentedControl(new Bounds(h.x()+92,h.y()+2,Math.max(48,h.width()-120),22),label("view.library"),List.of(label("library.playlists"),label("library.liked")),libraryGroup==Library.LIKED?1:0,host.preferences(),index->{historyDestination=null;openLibraryGroup(index==0?Library.PLAYLISTS:Library.LIKED);host.screen().setFocused(libraryGroups);}));libraryHeaderControls.add(libraryGroups);
        libraryLogin=button(new Bounds(c.x()+8,c.bottom()-44,Math.max(24,c.width()-16),24),label("library.sign_in"),()->{
            boolean fromHistory=historyDestination==Page.LIBRARY;
            show(Page.ACCOUNTS);libraryLoginReturn=true;
            if(fromHistory)historyDestination=Page.LIBRARY;
            openAccount(librarySource);updateVisibility();host.screen().setFocused(back);
        },null);

        libraryRefresh = button(new Bounds(layout.searchBar().right()-24,layout.searchBar().y()+2,24,22), label("library.retry"),
                () -> {
                    if(listen1Selected()){listen1Editing=true;listen1Status=label("listen1.hint");updateVisibility();host.screen().setFocused(listen1Path);}
                    else if(pendingPlaylists!=null||pendingLibrary!=null){cancelLibrary();libraryState="cancelled";feedback(label("cancelled"));updateVisibility();}
                    else loadLibraryPage(libraryPage);
                }, libraryControls);
        libraryRefresh.setIcon(GraphicIcon.SEARCH);
        for (var row : musicRows()) {
            int slot = libraryRows.size();
            libraryRows.add(button(new Bounds(row.x(), row.y(), row.width() - 52, row.height()), Component.empty(), () -> {
                int i = libraryFirst + slot;
                if (library == Library.PLAYLISTS && i < playlists.size()) {historyDestination=null;loadPlaylist(playlists.get(i), 1);}
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
            else if (!listen1Selected() && librarySize() == 20) loadLibraryPage(libraryPage + 1);
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
        queueAppend = button(new Bounds(layout.sourceBar().right() - 60, layout.sourceBar().y(), 24, 20), label("queue.append_current"), () -> {
            if (queueAppendTrack != null) queue.append(queueAppendTrack);
            updateQueueRows(); updateScrollRegions();
        }, queueControls);
        queueAppend.setIcon(GraphicIcon.ADD);
        queueLocate = button(new Bounds(layout.sourceBar().right() - 24, layout.sourceBar().y(), 24, 20), label("queue.locate"), () -> {
            queueFirst = Math.max(0, queue.index() - visibleRows / 2); updateQueueRows();
        }, queueControls);
        queueLocate.setIcon(GraphicIcon.SEARCH); queueLocate.setPreview(label("queue.locate"));
        for (var row : musicRows()) {
            int slot = queueRows.size();
            int editWidth = queue.supportsEditing() ? 84 : 28;
            var play = button(new Bounds(row.x(), row.y(), row.width() - editWidth, row.height()), Component.empty(),
                    () -> { queue.select(queueFirst + slot); updateQueueRows(); }, queueControls);
            play.setLeadingIcon(GraphicIcon.RUN); play.setVariant(MusicTextControl.Variant.ROW);queueRows.add(play);
            var up = button(new Bounds(row.right() - 80, row.y(), 24, row.height()), Component.literal("↑"),
                    () -> moveQueueRow(slot, -1), queueControls);
            var down = button(new Bounds(row.right() - 52, row.y(), 24, row.height()), Component.literal("↓"),
                    () -> moveQueueRow(slot, 1), queueControls);
            queueUps.add(up); queueDowns.add(down);
            var remove = button(new Bounds(row.right() - 24, row.y(), 24, row.height()), label("queue.remove"),
                    () -> { queue.remove(queueFirst + slot); updateQueueRows(); updateScrollRegions(); }, queueControls);
            remove.setIcon(GraphicIcon.REMOVE); queueRemoves.add(remove);
        }
        var p = layout.pager();
        queueEdit = button(new Bounds(p.x(), p.y(), Math.min(88, p.width()), 20), label("queue.edit"),
                () -> setQueueEditing(!queueEditing), queueControls);
        queueEdit.setPreview(label("queue.session_hint"));
        int actionWidth = Math.min(68, Math.max(36, (p.width() - 96) / 2));
        queueUndo = button(new Bounds(p.right() - actionWidth * 2 - 4, p.y(), actionWidth, 20), label("queue.undo"),
                () -> { queue.undo(); updateQueueRows(); updateScrollRegions(); }, queueControls);
        queueClear = button(new Bounds(p.right() - actionWidth, p.y(), actionWidth, 20), label("queue.clear"),
                () -> { queue.clear(); updateQueueRows(); updateScrollRegions(); }, queueControls);
        queueClear.setPreview(label("queue.clear_hint"));
    }
    private void setQueueEditing(boolean editing) {
        queueEditing = editing;
        for (var control : queueControls) if (control instanceof MusicTextControl text) text.cancelInteraction();
        updateQueueRows();
        host.screen().setFocused(queueEdit);
    }
    private void moveQueueRow(int slot, int direction) {
        int from = queueFirst + slot;
        int to = from + direction;
        if (boundQueueFirst != queueFirst || !boundQueueTracks.equals(queue.tracks()) || to < 0 || to >= queue.tracks().size()) {
            updateQueueRows(); return;
        }
        queue.move(from, to);
        if (to < queueFirst) queueFirst = to;
        else if (to >= queueFirst + visibleRows) queueFirst = to - visibleRows + 1;
        updateQueueRows(); updateScrollRegions();
        int targetSlot = to - queueFirst;
        var target = (direction < 0 ? queueUps : queueDowns).get(targetSlot);
        host.screen().setFocused(target.active ? target : queueRows.get(targetSlot));
    }
    private void updateQueueRows() {
        if (queueEdit == null) return;
        queueFirst = Math.min(queueFirst, Math.max(0, queue.tracks().size() - visibleRows));
        var tracks = queue.tracks();
        if (boundQueueFirst != queueFirst || !boundQueueTracks.equals(tracks)) {
            for (var control : queueControls) if (control instanceof MusicTextControl text) text.cancelInteraction();
            boundQueueTracks = tracks; boundQueueFirst = queueFirst;
        }
        var trackToAppend = player.snapshot().track();
        if (!java.util.Objects.equals(queueAppendTrack, trackToAppend)) queueAppend.cancelInteraction();
        queueAppendTrack = trackToAppend;
        queueAppend.setVisible(!immersive && page == Page.QUEUE && queueEditing && queue.supportsEditing());
        queueAppend.setAvailable(queueAppendTrack != null);
        queueAppend.setPreview(queueAppendTrack == null ? label("queue.append_current") : label("queue.append_track", queueAppendTrack.title()));
        var rowBounds = musicRows();
        for (int slot = 0; slot < queueRows.size(); slot++) {
            int i = queueFirst + slot;
            var row = queueRows.get(slot); var remove = queueRemoves.get(slot);
            row.visible = !immersive && page == Page.QUEUE && i < queue.tracks().size();
            remove.visible = row.visible && queueEditing;
            row.setWidth(rowBounds.get(slot).width() - (queueEditing ? (queue.supportsEditing() ? 84 : 28) : 0));
            row.active = remove.active = row.visible;
            var up = queueUps.get(slot); var down = queueDowns.get(slot);
            up.setVisible(row.visible && queueEditing && queue.supportsEditing()); down.setVisible(up.visible);
            up.setAvailable(i > 0); down.setAvailable(i + 1 < tracks.size());
            if (i >= queue.tracks().size()) continue;
            up.setPreview(label("queue.move_up", tracks.get(i).title()));
            up.setSecondary(label("queue.move_up", tracks.get(i).title()));
            down.setPreview(label("queue.move_down", tracks.get(i).title()));
            down.setSecondary(label("queue.move_down", tracks.get(i).title()));
            var track = queue.tracks().get(i);
            var caption=MusicTypography.text((i + 1) + " · " + track.title());if(!row.getMessage().equals(caption))row.rebind(caption);
            row.setSecondary(MusicTypography.text(track.artist()+" · "+time(track.durationMs())));
            row.setPreview(label("play_track", track.title(), track.artist()).append("\n").append(label("queue.session_hint")));
            row.setSelected(i == queue.index());
            remove.setPreview(label("queue.remove_track", track.title()));
        }
        boolean queuePage = !immersive && page == Page.QUEUE;
        queueEdit.setVisible(queuePage);
        queueEdit.setMessage(label(queueEditing ? "queue.done" : "queue.edit"));
        queueUndo.setVisible(queuePage && queueEditing); queueClear.setVisible(queuePage && queueEditing);
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
        if (queueEditing || !layout.expanded()) text(ui, label(queueEditing ? "queue.edit" : "view.queue"), h.x() + back.getWidth() + 8, h.y() + 8, h.width() - back.getWidth() - 8);
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
        var control = new MusicTransportControl[1];
        control[0] = host.add(new MusicTransportControl(box,label(name),glyph,host.preferences(),()->perform(control[0].getMessage(),action)));
        return control[0];
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
            var current=player.snapshot();if(current.track()!=null&&trackKey(current.track()).equals(edit.trackKey()))perform(label("feedback.seek",time(edit.positionMs())),()->player.seek(edit.positionMs()));
        },ignored->{}));
        seek.setTimePreviewEnabled(true);
    }
    private MusicTextControl button(Bounds b, Component name, Runnable action, List<AbstractWidget> group) {
        var control = new MusicTextControl[1];
        var result = host.add(new MusicTextControl(b, name, host.preferences(), ()->perform(control[0].getMessage(),action)));
        control[0] = result;

        if (group != null) group.add(result);
        return result;
    }
    private void feedback(Component message) {
        long now = System.nanoTime() / 1_000_000L;
        interactionFeedback.show(message, now);
        visualMotion.pulse(now);
    }
    private void perform(Component actionName, Runnable action) {
        feedback(label("feedback.requested", actionName));
        try { action.run(); }
        catch (RuntimeException failure) { interactionFeedback.show(label("feedback.failed"),System.nanoTime()/1_000_000L,true); visualMotion.pulse(System.nanoTime()/1_000_000L); }
    }
    private Component feedbackMessage() { return interactionFeedback.current(System.nanoTime() / 1_000_000L); }
    private net.minecraft.client.gui.components.events.GuiEventListener stageReturnControl() {
        return shellBack==null?stageBack:shellBack;
    }
    private void enterImmersive() {
        cancelListen1(); enterImmersive(StagePage.LYRICS); }
    private void enterImmersive(StagePage mode) {
        if (!immersive) {historyDestination=null;stageReturnPage=page;if(page==Page.SEARCH)detachSearch();if(page==Page.LIBRARY)detachLibrary();}
        immersive=true;page=Page.LYRICS;setStagePage(mode);cancelSuggestions();
        updateVisibility();host.screen().setFocused(stageReturnControl());
    }
    private void setStagePage(StagePage mode) {
        stagePage=mode;
        stage=FullscreenLyricLayout.of(layout.frame(),shellBack==null,stageFailure,mode!=StagePage.ARTWORK);
        updateStageGeometry();updateVisibility();
    }
    boolean permitsScreenClose() {
        if(returnFromLibraryLogin()||returnFromHistoryDestination())return false;
        if(dismissListen1Editor())return false;
        if(!immersive&&page==Page.LIBRARY&&library==Library.TRACKS){returnToPlaylistList();return false;}
        if(!immersive&&page==Page.HISTORY){leaveHistory();return false;}
        if(!immersive&&page==Page.ACCOUNTS&&accountDetail){returnToAccountList();return false;}
        if (!immersive && page == Page.QUEUE && queueEditing) { setQueueEditing(false); return false; }
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
        closeNowPlayingMenu();
        if (!immersive) return;
        if(stageSeek!=null)stageSeek.cancelInteraction(); if(stageVolume!=null)stageVolume.cancelInteraction();
        immersive = false; page = stageReturnPage;
        updateVisibility(); host.screen().setFocused(page == Page.SEARCH ? queryField : back);
    }
    private MusicTransportControl stageButton(Bounds box,String name,MusicTransportControl.Glyph glyph,Runnable action) {
        var result=miniControl(box,name,glyph,action);
        stageControls.add(result);return result;
    }
    private void buildImmersive() {
        var moreBox=NowPlayingMenu.anchor(layout.frame(),stage.wide());
        nowPlayingMore=host.add(new UiButton(moreBox.x(),moreBox.y(),24,24,Component.literal("…"),
                button->openNowPlayingMenu(),host.preferences(),false));
        nowPlayingMore.setPreview(label("menu.title"));nowPlayingMore.setCornerRadius(12);

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
            if(now.track()!=null&&trackKey(now.track()).equals(edit.trackKey()))perform(label("feedback.seek",time(edit.positionMs())),()->player.seek(edit.positionMs()));
        },edit->{}));
        stageSeek.setTimePreviewEnabled(true);
        stageVolume=host.add(new MusicSeekControl(stage.volume(),label("stage.volume",100),host.preferences(),edit->{},edit->{
            if(player.supportsVolume())perform(label("stage.volume",Math.round(edit.positionMs()/10f)),()->player.volume(Math.max(0,Math.min(1,edit.positionMs()/1000f))));
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
                var track=queue.tracks().get(index);var caption=MusicTypography.text(track.title()+" · "+track.artist());if(!row.getMessage().equals(caption))row.rebind(caption);row.setPreview(caption);row.setSelected(index==queue.index());
            }
        }
        stageQueueScroll.visible=showingStageQueue()&&count>0;stageQueueScroll.configure(stageQueueFirst,queue.tracks().size(),count);
    }
    /** User-facing routes always use the new stage; show(LYRICS) remains a compatibility path. */
    private void navigate(Page next) {
        historyDestination=null;
        if (next == Page.LYRICS) enterImmersive(); else if(next==Page.LIBRARY)enterLibrary();else show(next);
    }
    private void show(Page next) {
        closeNowPlayingMenu();
        if (immersive) { immersive = false; }
        if (next == Page.LYRICS && page != Page.LYRICS) lyricsReturnPage = page;
        for (var scroll : new OnlineMusicScrollRegion[]{searchScroll, libraryScroll, lyricsScroll, accountScroll, queueScroll}) if (scroll != null) scroll.cancelDrag();
        if (page != next) { historyDestination=null; cancelLike(); queueEditing=false; }
        if (page == Page.ACCOUNTS && next != page){leaveAccountDetail();libraryLoginReturn=false;}
        if(page==Page.LIBRARY&&next!=page){boolean loading=pendingPlaylists!=null||pendingLibrary!=null;detachLibrary();cancelListen1();if(loading)library=Library.NONE;}
        if (page == Page.SEARCH && next != page) { detachSearch(); cancelSuggestions(); }
        if (page != next) visualMotion.pulse(System.nanoTime() / 1_000_000L);
        page = next; if (back != null) back.setY(layout.searchBar().y() + 2); updateVisibility();
        if (next == Page.SEARCH) host.screen().setFocused(queryField); else host.screen().setFocused(back);
    }
    private void selectSource(String next) {
        historyDestination=null;
        if (!searchSourceIds.contains(next)) return;
        feedback(sourceLabel(next));
        if (!source.equals(next)) {
            cancelLike(); detachSearch(); cancelSuggestions(); source = next; results = List.of(); first = 0; remotePage = 1; searchState = "search_hint";
        }
        show(Page.SEARCH); updateRows();
        if(sourceSegments!=null&&sourceSegments.visible)host.screen().setFocused(sourceSegments);
    }
    private void updateVisibility() {
        if (queryField == null) return;
        if (exitControl != null) { exitControl.visible = !immersive; if (immersive) exitControl.cancelInteraction(); }
        historyControls.forEach(w->w.visible=!immersive&&page==Page.HISTORY);
        if(page==Page.HISTORY)updateHistory();
        searchControls.forEach(w -> w.visible = page == Page.SEARCH);
        lyricControls.forEach(w -> w.visible = page == Page.LYRICS);
        queueControls.forEach(w -> w.visible = !immersive && page == Page.QUEUE);
        accountControls.forEach(w -> w.visible = page == Page.ACCOUNTS && !accountDetail);
        detailControls.forEach(w -> w.visible = page == Page.ACCOUNTS && accountDetail);
        libraryControls.forEach(w -> w.visible = page == Page.LIBRARY);
        libraryHeaderControls.forEach(w->w.visible=page==Page.LIBRARY&&!immersive);
        if(librarySources!=null){librarySources.setVisible(page==Page.LIBRARY&&!immersive);librarySources.setSelectedIndex(librarySourceIds.indexOf(librarySource));}
        if(libraryGroups!=null){libraryGroups.setVisible(page==Page.LIBRARY&&!immersive&&!listen1Selected());libraryGroups.setSelectedIndex(libraryGroup==Library.LIKED?1:0);libraryGroups.setAvailable(!libraryProviders.isEmpty());}
        updateListen1Editor();
        libraryLogin.setVisible(page==Page.LIBRARY&&!immersive&&!listen1Selected()&&!libraryProviders.isEmpty()&&(!player.authenticated(librarySource)||libraryState.equals("library.login_required")));

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
        if(nowPlayingMore!=null){nowPlayingMore.visible=immersive&&nowPlayingMenus.supported();nowPlayingMore.active=player.snapshot().track()!=null;}
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
        for(var group:List.of(searchControls,lyricControls,queueControls,accountControls,detailControls,libraryControls,historyControls))
            for(var widget:group)if(!widget.visible&&widget instanceof MusicTextControl text)text.cancelInteraction();
        if(immersive){nowPlaying.cancelInteraction();back.cancelInteraction();seek.cancelInteraction();for(var control:new MusicTransportControl[]{playback,stop,lyricsToggle,miniQueue,miniPrevious,miniNext})control.cancelInteraction();}
        var lyricsBox = lyricArea();
        lyricsScroll.setBounds(lyricsBox);
        followLyrics.setX(lyricsBox.x()); followLyrics.setY(lyricsBox.bottom()+2);
    }
    private void queryChanged(String value) {
        query = value; cancelSuggestions();
        if (!searchSourceIds.isEmpty() && !value.isBlank()) { suggestAfter = System.nanoTime() + 300_000_000L; suggestedQuery = value; }
        first = 0; updateRows();
    }
    private boolean showSuggestions() { return !suggestions.isEmpty() && query.equals(suggestedQuery); }
    private void cancelSuggestions() {
        if (pendingSuggestions != null) pendingSuggestions.cancel(true);
        pendingSuggestions = null; suggestions = List.of(); suggestedQuery = ""; suggestAfter = 0;
    }
    private void search(int pageNumber, boolean fresh) {
        historyDestination=null;
        if(searchSourceIds.isEmpty())return;
        String text = fresh ? query.strip() : submittedQuery;
        if (text.isEmpty()) { searchState = "search_hint"; return; }
        detachSearch(); cancelSuggestions(); likeState = ""; if (fresh) submittedQuery = text;
        remotePage = pageNumber; first = 0; results = List.of(); searchState = "searching";
        try { if (operations()==null) pending = player.search(source, text, pageNumber);
            else {searchOperation=operations().startSearch(source,text,pageNumber);displayedSearchOperation=searchOperation;pending=searchOperation.observe();} }
        catch (RuntimeException failure) { searchState = "search_failed"; }
        feedback(label(searchState)); updateRows();
    }
    private void detachSearch() {
        searchLoading.reset();
        if(searchOperation!=null){if(pending!=null){searchOperation.detach();searchState="operations.background";}searchOperation=null;pending=null;}
        else cancelSearch();
    }
    private void detachLibrary() {
        libraryLoading.reset();
        if(playlistsOperation!=null&&pendingPlaylists!=null)playlistsOperation.detach();
        if(trackOperation!=null&&pendingLibrary!=null)trackOperation.detach();
        if(playlistsOperation==null&&trackOperation==null){cancelLibrary();return;}
        playlistsOperation=null;trackOperation=null;pendingPlaylists=null;pendingLibrary=null;
        cancelLike();cancelAccountRequest();
    }
    private void cancelSearch() {
        searchLoading.reset();
        if(searchOperation!=null){searchOperation.cancel();searchOperation=null;}
        if (pending != null) { pending.cancel(true); searchState = "cancelled"; pending = null; feedback(label("cancelled")); }
    }
    private void cancelLike() {
        // Cancellation cannot prove the provider did not accept the write.
        if (pendingLike != null && likeTrack != null) forgetLike(likeTrack);
        if (likeOperation != null) likeOperation.detach();
        else if (pendingLike != null) { pendingLike.cancel(true); feedback(label("feedback.like_cancelled")); }
        likeOperation = null;
        pendingLike = null; likeTrack = null; likeState = "";
    }
    private void clearAccountFeedback() { cancelLike(); confirmedLikes.clear(); confirmedLikeEpochs.clear(); accountState = ""; accountName = ""; }
    private void returnToAccountList() {
        var card=accounts.get(accountSource);
        leaveAccountDetail();updateVisibility();
        host.screen().setFocused(card!=null&&card.visible&&card.active?card:back);
    }
    private void leaveAccountDetail() {
        detachLogin();cancelAccountRequest();clearAccountFeedback();
        accountDetail = false; accountOffset = 0;
    }
    private void openAccount(String selected) {
        detachLogin();cancelAccountRequest();clearAccountFeedback();
        accountSource = selected; accountDetail = true; accountOffset = 0;
        if (operations() != null) {
            login = operations().currentLogin(selected).orElse(null);
            operations().setLoginBackground(selected, login, false);
        }
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
        libraryLoading.reset();
        if(playlistsOperation!=null)playlistsOperation.cancel();
        if(trackOperation!=null)trackOperation.cancel();
        playlistsOperation=null;trackOperation=null;
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
            accountState = "account.loading"; feedback(label(accountState));
            try { pendingAccount = player.account(accountSource); }
            catch (RuntimeException failure) { accountState = operationFailure(failure).equals("library.login_required") ? "library.login_required" : "account.failed"; feedback(label(accountState)); }
        }
    }
    private boolean returnFromLibraryLogin() {
        if(immersive||page!=Page.ACCOUNTS||!libraryLoginReturn)return false;
        boolean fromHistory=historyDestination==Page.LIBRARY;
        libraryLoginReturn=false;
        enterLibrary();
        if(fromHistory)historyDestination=Page.LIBRARY;
        return true;
    }
    private void returnToPlaylistList() {
        detachLibrary();library=Library.PLAYLISTS;libraryPage=savedPlaylistPage;libraryFirst=savedPlaylistFirst;
        libraryState=playlists.isEmpty()?"library.empty":"library.results";updateVisibility();
        host.screen().setFocused(back);
    }
    private void enterLibrary() {
        show(Page.LIBRARY);
        if(listen1Selected()) {
            if(library==Library.NONE)showListen1Library();
            return;
        }
        if(library==Library.NONE||libraryState.equals(LibraryAccessFeedback.AUTHORIZATION_REQUIRED)||libraryState.equals(LibraryAccessFeedback.SIGN_IN)||!player.authenticated(librarySource)) {
            if(library==Library.TRACKS&&selectedPlaylist!=null)loadPlaylist(selectedPlaylist,libraryPage);else openLibraryGroup(libraryGroup);
        }
    }
    private void openLibraryGroup(Library group) {
        libraryGroup=group;
        if(group==Library.LIKED)loadLiked(1);else loadPlaylists(1);
    }
    private void loadPlaylists(int number) {
        detachLibrary(); detachLogin(); library = Library.PLAYLISTS; libraryPage = number; libraryFirst = 0;
        likeState = "";
        playlists = List.of(); libraryTracks = List.of(); libraryState = "library.loading";
        if(libraryProviders.isEmpty()){libraryState="view.unavailable";updateVisibility();return;}
        if(!player.authenticated(librarySource)){libraryState=LibraryAccessFeedback.SIGN_IN;updateVisibility();return;}
        try{if(operations()==null)pendingPlaylists = player.playlists(librarySource, number);else {playlistsOperation=operations().startPlaylists(librarySource,number);displayedLibraryOperation=playlistsOperation;pendingPlaylists=playlistsOperation.observe();}}catch(RuntimeException failure){libraryState=operationFailure(failure);}
        feedback(label(libraryState)); updateVisibility(); host.screen().setFocused(back);
    }
    private void loadPlaylist(OnlinePlaylist playlist, int number) {
        if (library == Library.PLAYLISTS) { savedPlaylistPage = libraryPage; savedPlaylistFirst = libraryFirst; }
        detachLibrary(); library = Library.TRACKS; selectedPlaylist = playlist; libraryPage = number; libraryFirst = 0;
        likeState = "";
        libraryTracks = List.of(); libraryState = "library.loading";
        if(listen1Selected()&&listen1Backup()!=null) {
            libraryTracks=listen1Backup().tracks(playlist);
            libraryState=libraryTracks.isEmpty()?"listen1.no_tracks":"library.results";
            libraryRowsRevision++;
            updateVisibility();host.screen().setFocused(back);return;
        }
        try{if(operations()==null)pendingLibrary = player.playlist(playlist, number);else {trackOperation=operations().startPlaylist(playlist,number);displayedLibraryOperation=trackOperation;pendingLibrary=trackOperation.observe();}}catch(RuntimeException failure){libraryState=operationFailure(failure);}
        feedback(label(libraryState)); updateVisibility(); host.screen().setFocused(back);
    }
    private void loadLiked(int number) {
        detachLibrary(); detachLogin(); library = Library.LIKED; libraryPage = number; libraryFirst = 0;
        likeState = "";
        libraryTracks = List.of(); libraryState = "library.loading";
        if(libraryProviders.isEmpty()){libraryState="view.unavailable";updateVisibility();return;}
        if(!player.authenticated(librarySource)){libraryState=LibraryAccessFeedback.SIGN_IN;updateVisibility();return;}
        try{if(operations()==null)pendingLibrary = player.liked(librarySource, number);else {trackOperation=operations().startLiked(librarySource,number);displayedLibraryOperation=trackOperation;pendingLibrary=trackOperation.observe();}}catch(RuntimeException failure){libraryState=operationFailure(failure);}
        feedback(label(libraryState)); updateVisibility(); host.screen().setFocused(back);
    }
    private void loadLibraryPage(int number) {
        if (library == Library.PLAYLISTS) loadPlaylists(number);
        else if (library == Library.TRACKS && selectedPlaylist != null) loadPlaylist(selectedPlaylist, number);
        else if (library == Library.LIKED) loadLiked(number);
        else openLibraryGroup(libraryGroup);
    }
    private int librarySize() { return library == Library.PLAYLISTS ? playlists.size() : libraryTracks.size(); }

    private void showListen1Library() {
        detachLibrary(); displayedLibraryOperation=null;
        library=Library.PLAYLISTS; libraryPage=1; libraryFirst=0; selectedPlaylist=null;
        playlists=listen1Backup()==null?List.of():listen1Backup().playlists();
        libraryTracks=List.of(); likeState="";
        libraryState=playlists.isEmpty()?"listen1.no_playlists":"library.results";
        listen1Editing=listen1Backup()==null;
        libraryRowsRevision++;
        updateVisibility();
    }
    private void updateListen1Editor() {
        if(listen1Path==null||listen1ImportButton==null)return;
        boolean visible=listen1EditorVisible();
        listen1Path.visible=visible;listen1Path.active=visible&&pendingListen1==null;
        listen1ImportButton.setVisible(visible);
        listen1ImportButton.setAvailable(visible&&pendingListen1==null&&!listen1File.isBlank());
    }
    private void submitListen1() {
        if(!listen1EditorVisible()||pendingListen1!=null||listen1File.isBlank())return;
        Path file;
        String candidate=listen1File.strip();
        if(candidate.length()>=2&&candidate.charAt(0)=='"'&&candidate.charAt(candidate.length()-1)=='"')
            candidate=candidate.substring(1,candidate.length()-1);
        try {file=Path.of(candidate);}
        catch(IllegalArgumentException invalid){listen1Status=label("listen1.failed");feedback(listen1Status);return;}
        var sources=Set.copyOf(libraryProviders);
        pendingListen1=CompletableFuture.supplyAsync(()->{
            try {return Listen1Backup.read(file,sources);}
            catch(IOException | SecurityException ignored) {return null;}
        });
        listen1Status=label("listen1.loading");
        updateListen1Editor();feedback(listen1Status);
    }
    private boolean dismissListen1Editor() {
        if(!listen1EditorVisible()||(listen1Backup()==null&&pendingListen1==null))return false;
        cancelListen1();
        listen1Editing=listen1Backup()==null;
        listen1Status=label("listen1.hint");
        updateVisibility();host.screen().setFocused(listen1Editing?listen1Path:libraryRefresh);return true;
    }
    private void cancelListen1() {
        if(pendingListen1!=null){pendingListen1.cancel(true);pendingListen1=null;listen1Status=label("listen1.hint");}
    }
    private void finishListen1Import() {
        if(pendingListen1==null||!pendingListen1.isDone()||!listen1EditorVisible())return;
        Listen1Backup.Imported imported;
        try {imported=pendingListen1.join();}
        catch(RuntimeException failure) {imported=null;}
        pendingListen1=null;
        if(imported==null){listen1Status=label("listen1.failed");feedback(listen1Status);updateListen1Editor();return;}
        listen1Session.replace(imported);
        listen1File="";
        listen1Path.setValue("");
        listen1Status=label("listen1.hint");
        showListen1Library();
        feedback(label("listen1.loaded",imported.playlists().size(),imported.supportedTracks(),imported.skippedTracks()));
        host.screen().setFocused(libraryRefresh);
    }
    private void changeLike(OnlineTrack track, boolean like) {
        if (pendingLike != null || (operations()!=null && operations().likePending(track))) {
            feedback(label("library.updating")); return;
        }
        likeState = "library.updating";
        likeTrack = track; requestedLike = like;
        try {
            if (operations() == null) pendingLike = player.like(track, like);
            else { likeOperation = operations().startLike(track, like); pendingLike = likeOperation.observe(); }
        }
        catch (RuntimeException failure) { forgetLike(track); likeTrack = null; likeState = operationFailure(failure); }
        feedback(label(likeState)); updateRows(); updateLibraryRows();
    }
    private MutableComponent likeDescription(OnlineTrack track, boolean like) {
        Boolean known = knownLike(track);
        return label(like ? "library.like_track" : "library.unlike_track", track.title()).append("\n")
                .append(label(operations()!=null && operations().likePending(track) ? "library.updating"
                        : known == null ? "library.like_unknown" : known ? "library.is_liked" : "library.not_liked"));
    }
    private void applyLikeState(MusicTextControl add, MusicTextControl remove, OnlineTrack track, boolean enabled) {
        Boolean known = track == null ? null : knownLike(track);
        if (track!=null && operations()!=null && operations().likePending(track)) known=null;
        add.active = enabled && !Boolean.TRUE.equals(known); remove.active = enabled && !Boolean.FALSE.equals(known);
        add.setSelected(Boolean.TRUE.equals(known)); remove.setSelected(Boolean.FALSE.equals(known));
        add.setMessage(label(Boolean.TRUE.equals(known) ? "library.is_liked" : "library.like"));
    }
    private void updateLibraryRows() {
        if (libraryPrevious == null) return;
        for (int slot = 0; slot < libraryRows.size(); slot++) {
            int i = libraryFirst + slot; var row = libraryRows.get(slot);
            boolean available = page == Page.LIBRARY && !immersive && !listen1EditorVisible() && !libraryLogin.visible && i < librarySize();
            row.setVisible(available); row.active = available;
            Object identity=available?(library==Library.PLAYLISTS?playlists.get(i):libraryTracks.get(i)):null;
            if(!Objects.equals(resultIdentities.put(row,identity),identity)){row.cancelInteraction();libraryLikes.get(slot).cancelInteraction();libraryUnlikes.get(slot).cancelInteraction();}
            if (available) {
                if (library == Library.PLAYLISTS) {
                    var playlist = playlists.get(i); row.setMessage(MusicTypography.text(playlist.name()));
                    row.setSecondary(label("library.track_count", playlist.trackCount()));
                    row.setPreview(label("library.playlist_details", playlist.name(), playlist.trackCount()));
                } else {
                    var track = libraryTracks.get(i); row.setMessage(MusicTypography.text(track.title()));
                    row.setSecondary(MusicTypography.text(track.artist()+" · "+time(track.durationMs())));
                    row.setPreview(label("play_track", track.title(), track.artist()).append("\n").append(MusicTypography.text(track.album())));
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
        libraryRefresh.setVisible(page == Page.LIBRARY&&!immersive&&!listen1EditorVisible());
        if (listen1Selected()) {
            libraryRefresh.setMessage(label("listen1.import"));
            libraryRefresh.setPreview(listen1Backup()==null?label("listen1.import"):label("listen1.import").append("\n")
                    .append(label("listen1.loaded",listen1Backup().playlists().size(),listen1Backup().supportedTracks(),listen1Backup().skippedTracks())));
            libraryRefresh.setGlyphOnly(MusicControlRenderer.Icon.ADD);
            libraryRefresh.setAvailable(libraryRefresh.visible);
        } else {
            libraryRefresh.setMessage(label(ready?"library.retry":"cancel"));
            libraryRefresh.setPreview(ready ? label("library.retry") : label("cancel").append("\n").append(label(libraryState)));
            libraryRefresh.setGlyphOnly(ready?MusicControlRenderer.Icon.REFRESH:MusicControlRenderer.Icon.CLOSE);
            libraryRefresh.setAvailable(!libraryProviders.isEmpty()&&player.authenticated(librarySource));
        }

        if(libraryLogin.visible||listen1EditorVisible()){libraryPrevious.setVisible(false);libraryNext.setVisible(false);}
        libraryPrevious.active = ready && !listen1EditorVisible() && (libraryFirst > 0 || libraryPage > 1);
        libraryNext.active = ready && !listen1EditorVisible() && (libraryFirst + visibleRows < librarySize() || !listen1Selected() && librarySize() == 20);
    }
    private static String operationFailure(Throwable failure) {
        return LibraryAccessFeedback.failure(failure);
    }
    private void pollOperations() {
        finishListen1Import();
        if (operations()!=null && (page==Page.SEARCH || page==Page.LIBRARY)) {
            long activeWrites = operations().likeLaneRevision();
            if (observedWrites != activeWrites) {
                observedWrites = activeWrites;
                updateRows(); updateLibraryRows();
            }
        }

        if(displayedSearchOperation!=null&&!displayedSearchOperation.displayValid()){pending=null;searchOperation=null;displayedSearchOperation=null;results=List.of();searchState="cancelled";updateRows();}
        if(displayedLibraryOperation!=null&&!displayedLibraryOperation.displayValid()){
            pendingPlaylists=null;pendingLibrary=null;playlistsOperation=null;trackOperation=null;displayedLibraryOperation=null;
            playlists=List.of();libraryTracks=List.of();selectedPlaylist=null;library=Library.NONE;libraryFirst=0;libraryPage=1;libraryState="cancelled";
            clearAccountFeedback();updateRows();updateLibraryRows();updateVisibility();
        }
        if(page==Page.HISTORY)updateHistory();
        if (page == Page.SEARCH && suggestAfter != 0 && System.nanoTime() >= suggestAfter) {
            suggestAfter = 0;
            if (query.equals(suggestedQuery) && !query.isBlank()) {
                try { pendingSuggestions = player.suggest(source, query.strip()); }
                catch (RuntimeException ignored) { suggestions = List.of(); }
            }
        }
        if (pendingSuggestions != null && pendingSuggestions.isDone()) {
            try { suggestions = pendingSuggestions.join(); first = 0; } catch (RuntimeException ignored) { suggestions = List.of(); }
            pendingSuggestions = null; updateRows();
        }
        if (pendingAccount != null && pendingAccount.isDone()) {
            try { var account = pendingAccount.join(); accountState = ""; accountName = account.name(); }
            catch (RuntimeException failure) { accountState = operationFailure(failure).equals("library.login_required") ? "library.login_required" : "account.failed"; }
            pendingAccount = null; feedback(label(accountState.isEmpty()?"feedback.account_ready":accountState));
        }
        if (pendingPlaylists != null && pendingPlaylists.isDone()) {
            try { playlists = pendingPlaylists.join(); libraryState = playlists.isEmpty() ? "library.empty" : "library.results";
                if (!playlists.isEmpty()) libraryRowsRevision++;
            }
            catch (RuntimeException failure) { libraryState = operationFailure(failure); }
            pendingPlaylists = null; feedback(label(libraryState.equals("library.results")?"feedback.library_ready":libraryState)); updateLibraryRows();updateVisibility();
        }
        if (pendingLibrary != null && pendingLibrary.isDone()) {
            try { libraryTracks = pendingLibrary.join(); libraryState = libraryTracks.isEmpty() ? "library.empty" : "library.results";
                if (!libraryTracks.isEmpty()) libraryRowsRevision++;
                if (library == Library.LIKED) for (var track : libraryTracks) rememberLike(track,true,displayedLibraryOperation);
            }
            catch (RuntimeException failure) { libraryState = operationFailure(failure); likeState = ""; }
            pendingLibrary = null; feedback(label(libraryState.equals("library.results")?"feedback.library_ready":libraryState)); updateLibraryRows();updateVisibility();
        }
        if (likeOperation != null && !likeOperation.displayValid()) {
            cancelLike(); confirmedLikes.clear(); confirmedLikeEpochs.clear(); updateRows(); updateLibraryRows();
        }
        if (pendingLike != null && pendingLike.isDone()) {
            boolean updated = false;
            try { pendingLike.join(); likeState = "library.updated"; updated = true;
                if (likeTrack != null) rememberLike(likeTrack,requestedLike,likeOperation);
            }
            catch (RuntimeException failure) { if (likeTrack != null) forgetLike(likeTrack); likeState = likeOperation == null ? operationFailure(failure) : switch (SessionMusicOperations.classify(likeOperation.request().kind(),failure)) {
                    case UNKNOWN, NONE -> "library.like_unconfirmed";
                    case REJECTED -> "library.like_rejected";
                    case BUSY -> "library.updating";
                    default -> operationFailure(failure);
                };
            }
            likeOperation = null; pendingLike = null; likeTrack = null; feedback(label(likeState));
            if (updated && page == Page.LIBRARY && library == Library.LIKED) {
                loadLiked(libraryPage); likeState = "library.updated";
            }
            updateRows(); updateLibraryRows();
        }
    }
    private static boolean loginBusy(OnlineMusicLogin.Snapshot snapshot) {
        return snapshot != null && (snapshot.state() == OnlineMusicLogin.State.CREATING
                || snapshot.state() == OnlineMusicLogin.State.WAITING || snapshot.state() == OnlineMusicLogin.State.SCANNED);
    }
    private void startLogin() {
        cancelLogin(); cancelLibrary(); clearAccountFeedback();
        try { login = operations()==null ? player.login(accountSource) : operations().startLogin(accountSource); feedback(label("login.creating")); }
        catch (RuntimeException failure) { accountState="login.failed"; feedback(label("login.failed")); }
    }
    private void detachLogin() {
        if (login != null && operations() != null && ownsPlayer == false) {
            var state=login.snapshot().state();
            boolean waiting = state == OnlineMusicLogin.State.CREATING || state == OnlineMusicLogin.State.WAITING || state == OnlineMusicLogin.State.SCANNED;
            if (waiting || !state.name().equalsIgnoreCase(observedLoginState)) operations().setLoginBackground(accountSource, login, true);
            login=null; observedLoginState="";
        } else cancelLogin();
    }
    private void cancelLogin() { if (login != null) { login.close(); login = null; } observedLoginState=""; }
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
            var nextLabel=row.active ? MusicTypography.text(suggesting ? suggestions.get(i) : results.get(i).title()) : Component.empty();
            if(!row.getMessage().equals(nextLabel))row.rebind(nextLabel);
            row.setSecondary(row.active&&!suggesting?MusicTypography.text(results.get(i).artist()+" · "+time(results.get(i).durationMs())):Component.empty());
            row.setSelected(!suggesting && row.active && results.get(i).equals(current));
            if (row.active && !suggesting) row.setPreview(label("play_track", results.get(i).title(), results.get(i).artist())
                    .append("\n").append(MusicTypography.text(results.get(i).album())));
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
            var detail=submittedQuery.isBlank()?Component.empty():sourceLabel(source).copy().append(" · ").append(MusicTypography.text(submittedQuery));
            searchStatus.setContent(label(searchSourceIds.isEmpty()?"spotify.hint":searchState),detail,pending!=null,null);
            searchButton.setPreview(label(pending==null?"search":"cancel").append("\n").append(searchStatus.narration()));
        } else searchButton.setPreview(label(pending==null?"search":"cancel"));
    }
    @Override public void render(UiFrame ui) {
        if (closed || layout == null) return;
        if(seek!=null&&!net.minecraft.client.Minecraft.getInstance().isWindowActive()) {
            closeNowPlayingMenu();
            seek.cancelInteraction();
            if(stagePlaybackMode!=null)stagePlaybackMode.cancelInteraction();
            for(var control:new MusicTransportControl[]{playback,stop,lyricsToggle,miniQueue,miniPrevious,miniNext})control.cancelInteraction();
            navigation.forEach(MusicTextControl::cancelInteraction); if(sourceSegments!=null)sourceSegments.cancelInteraction();if(librarySources!=null)librarySources.cancelInteraction();if(libraryGroups!=null)libraryGroups.cancelInteraction();
            for(var group:List.of(searchControls,lyricControls,queueControls,accountControls,detailControls,libraryControls,historyControls))
                for(var widget:group)if(widget instanceof MusicTextControl text)text.cancelInteraction();
        }
        if (!layout.supported()) { text(ui, label("space"), layout.frame().x() + 4, layout.frame().y() + 4, Math.max(0, layout.frame().width() - 8)); return; }
        if(nowPlayingMore!=null)nowPlayingMore.active=immersive&&player.snapshot().track()!=null;
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
            try { results = pending.join(); searchState = results.isEmpty() ? "empty" : "results";
                if (!results.isEmpty()) searchRowsRevision++;
            }
            catch (RuntimeException failure) { searchState = "search_failed"; }
            pending = null; feedback(label(searchState.equals("results")?"feedback.search_ready":searchState)); updateRows();
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
        OnlineMusicViewPaint.transportSurface(ui, layout.transport(), Math.max(visual.activity(),visual.emphasis()));
        if (layout.expanded()) {
            if (page != Page.LYRICS) OnlineMusicViewPaint.hero(ui, host.font(), layout.hero(), label("view.brand"),
                    page==Page.LIBRARY&&library==Library.TRACKS&&selectedPlaylist!=null?MusicTypography.text(selectedPlaylist.name()):label("refresh." + page.name().toLowerCase(Locale.ROOT)), Component.empty(), visual.emphasis());
            paintArtwork(ui, layout.cover(), current.track(), visual.activity());
        }
        renderTransport(ui, current);
        if (page == Page.SEARCH) renderResults(ui, current);
        else if (page == Page.QUEUE) renderQueue(ui, current);
        else if (page == Page.HISTORY) {
            if(history.isEmpty())muted(ui,label("operations.empty"),layout.content().x()+8,layout.content().y()+8,layout.content().width()-16);
            muted(ui,label("operations.hint"),layout.searchBar().x()+back.getWidth()+8,layout.searchBar().y()+8,Math.max(1,layout.searchBar().width()-back.getWidth()-8));
        }
        else {
            var h = layout.searchBar();
            Component title = page == Page.LIBRARY
                    ? (library == Library.TRACKS && selectedPlaylist != null ? MusicTypography.text(selectedPlaylist.name()) : label(library == Library.PLAYLISTS ? "library.playlists" : "library.liked"))
                    : page == Page.ACCOUNTS && accountDetail && !accountState.isEmpty() ? label(accountState)
                    : page == Page.ACCOUNTS && accountDetail && !accountName.isBlank() ? MusicTypography.text(accountName)
                    : label("view." + page.name().toLowerCase(Locale.ROOT));
            if (page == Page.ACCOUNTS && accountDetail && login != null && accountState.isEmpty() && !player.authenticated(accountSource)) {
                var loginSnapshot = login.snapshot();
                title = loginSnapshot.image() != null && layout.content().height() - 96 < loginSnapshot.image().height()
                        ? label("login.enlarge") : label("login." + loginSnapshot.state().name().toLowerCase(Locale.ROOT));
            }
            if (page != Page.LYRICS&&page!=Page.LIBRARY && (!layout.expanded() || accountDetail)) text(ui, title, h.x() + back.getWidth() + 8, h.y() + 8, h.width() - back.getWidth() - 8);
            if (page == Page.LYRICS) renderLyrics(ui, current); else if (page == Page.LIBRARY) renderLibrary(ui); else {
                positionAccounts();
                OnlineMusicViewPaint.clip(ui, layout.content());
                try { renderAccounts(ui); } finally { OnlineMusicViewPaint.unclip(ui); }
            }
        }
    }
    /**
     * Draws a short transition over the already-current route. Old widgets are never drawn again,
     * so their focus, clicks and operation callbacks cannot survive a route change.
     */
    void renderRouteHandoff(UiFrame ui) {
        if(nowPlayingMenus!=null)nowPlayingMenus.render(ui);
        if (closed || layout == null || !layout.supported()) {
            routeMotion.reset();
            return;
        }
        Object detail = immersive ? null : switch (page) {
            case SEARCH -> new SearchRoute(source, searchRowsRevision);
            case LIBRARY -> new LibraryRoute(librarySource, libraryGroup,
                    library == Library.TRACKS && selectedPlaylist != null ? selectedPlaylist.id() : null, libraryRowsRevision);
            case ACCOUNTS -> accountDetail ? accountSource : null;
            case QUEUE -> queueEditing;
            default -> null;
        };
        var next = new OnlineMusicRouteMotion.Route(page, detail, immersive ? stagePage : null);
        var motion = routeMotion.sample(next, ui.nowMs(), ui.preferences().reducedMotion());
        if (motion.strength() <= 0f) return;
        var box = OnlineMusicRouteMotion.bounds(motion.surface(), layout, stage);
        int opacity = motion.opacity(ui.preferences().highContrast());
        if (opacity == 0 || box.width() <= 0 || box.height() <= 0) return;
        int rgb = UiTheme.current().background() & 0x00ffffff;
        int tint = (opacity << 24) | rgb;
        int feather = Math.min(8, box.height() / 3);
        if (feather == 0) {
            ui.graphics().fill(box.x(), box.y(), box.right(), box.bottom(), tint);
        } else {
            ui.graphics().fillGradient(box.x(), box.y(), box.right(), box.y() + feather, rgb, tint);
            ui.graphics().fill(box.x(), box.y() + feather, box.right(), box.bottom() - feather, tint);
            ui.graphics().fillGradient(box.x(), box.bottom() - feather, box.right(), box.bottom(), tint, rgb);
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
        if(!results.isEmpty()||showSuggestions())
            text(ui, status, pager.x() + 30, pager.y() + 6, pager.width() - 60);
        previous.setPreview(status.copy().append("\n").append(label("previous")));
        next.setPreview(status.copy().append("\n").append(label("next")));
        boolean loading = pending != null && results.isEmpty() && !showSuggestions() && !searchSkeleton.isEmpty();
        paintLoading(ui, searchLoading, loading ? pending : null,
                !showSuggestions() && !results.isEmpty() && searchState.equals("results"), searchSkeleton);
        if (results.isEmpty() && !showSuggestions()) {
            updateSearchFeedback();
            previous.setPreview(searchStatus.narration());next.setPreview(searchStatus.narration());
            if (loading) text(ui, label(searchState), pager.x() + 30, pager.y() + 6, pager.width() - 60);
            else searchStatus.render(ui,host.font(),new Bounds(c.x()+8,c.y()+layout.listHeadingHeight(),c.width()-16,
                    Math.max(0,layout.pager().y()-c.y()-layout.listHeadingHeight()-4)));
        }

    }
    private void renderLibrary(UiFrame ui) {
        var c = layout.content();
        if(listen1EditorVisible()) {
            OnlineMusicViewPaint.paragraph(ui,host.font(),listen1Status,new Bounds(c.x()+4,c.y()+29,c.width()-8,
                    Math.max(0,c.height()-29)),UiTheme.current().text());
            return;
        }
        if(!libraryLogin.visible&&layout.listHeadingHeight()>=20)muted(ui, label(library == Library.PLAYLISTS ? "library.playlists" : "view.song"), c.x() + 6, c.y() + 4, Math.max(0, c.width() - 116));
        var pager = layout.pager();
        var status = label(!likeState.isEmpty() ? likeState : libraryState, libraryPage, librarySize() == 0 ? 0 : libraryFirst + 1, Math.min(libraryFirst + visibleRows, librarySize()));
        if(!libraryLogin.visible && librarySize()>0)text(ui, status, pager.x() + 30, pager.y() + 6, pager.width() - 60);
        var request = pendingPlaylists != null ? pendingPlaylists : pendingLibrary;
        boolean loading = request != null && librarySize() == 0 && !libraryLogin.visible && !librarySkeleton.isEmpty();
        paintLoading(ui, libraryLoading, loading ? request : null,
                !libraryLogin.visible && librarySize() > 0 && libraryState.equals("library.results"), librarySkeleton);
        if (loading) text(ui, label(libraryState), pager.x() + 30, pager.y() + 6, pager.width() - 60);
        else if (librarySize() == 0||libraryLogin.visible) {
            OnlineMusicViewPaint.paragraph(ui,host.font(),label(libraryState),new Bounds(c.x()+8,c.y()+4,c.width()-16,Math.max(0,c.height()-(libraryLogin.visible?48:24))),UiTheme.current().text());
            libraryLogin.setPreview(label(LibraryAccessFeedback.signInHint(libraryState)).append("\n").append(sourceLabel(librarySource)));
        }
    }
    private List<UiSkeleton.Block> loadingBlocks(List<MusicTextControl> rows, boolean leadingIcon) {
        var bounds = rows.stream().map(row -> new Bounds(row.getX(), row.getY(), row.getWidth(), row.getHeight())).toList();
        return MusicLoadingSkeleton.blocks(bounds, skeletonClip, leadingIcon);
    }

    private void paintLoading(UiFrame ui, MusicLoadingSkeleton motion, CompletableFuture<?> pending,
                              boolean populated, List<UiSkeleton.Block> blocks) {
        long now = System.nanoTime() / 1_000_000L;
        float alpha = motion.sample(pending, populated, now, ui.preferences().reducedMotion());
        if (alpha > 0 && !blocks.isEmpty())
            UiSkeleton.draw(ui.graphics(), skeletonClip, blocks, now, ui.preferences(), alpha * .75f);
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
        muted(ui, MusicTypography.text(time(seek.previewPositionMs())), t.x(), seek.getY() + 4, 30);
        muted(ui, MusicTypography.text("−" + time(Math.max(0, current.durationMs() - seek.previewPositionMs()))), t.right() - 32, seek.getY() + 4, 32);
        var status = state == OnlineMusicPlayer.State.FAILED ? label("failure." + current.failure().name().toLowerCase(Locale.ROOT))
                : label("state." + state.name().toLowerCase(Locale.ROOT));
        if (current.preview()) status = label("preview").append(" · ").append(status);
        var operation = feedbackMessage();
        if(interactionFeedback.isError(System.nanoTime()/1_000_000L) && state!=OnlineMusicPlayer.State.FAILED) status=operation.copy().append(" · ").append(status);
        boolean important = state==OnlineMusicPlayer.State.FAILED || state==OnlineMusicPlayer.State.LOADING || current.preview();
        if (!MusicMiniPlayerLayout.stacked(t.width()) && (important || !operation.getString().isEmpty()))
            muted(ui,important?status:operation,t.x()+4,t.bottom()-12,t.width()-8);
        playback.setTooltip(net.minecraft.client.gui.components.Tooltip.create(MusicTypography.text(status))); stop.setTooltip(net.minecraft.client.gui.components.Tooltip.create(MusicTypography.text(status.copy().append("\n").append(label("stop_hint")))));
    }
    private void updateMiniMetadata(OnlineMusicPlayer.Snapshot current) {
        var title=current.track()==null?label("view.now_playing"):MusicTypography.text(current.track().title());
        var operation=feedbackMessage();
        Component visibleTitle=title;
        if(MusicMiniPlayerLayout.stacked(layout.transport().width())) {
            if(current.state()==OnlineMusicPlayer.State.FAILED) visibleTitle=label("failure."+current.failure().name().toLowerCase(Locale.ROOT));
            else if(interactionFeedback.isError(System.nanoTime()/1_000_000L)) visibleTitle=current.preview()?operation.copy().append(" · ").append(label("preview")):operation;
            else if(current.state()==OnlineMusicPlayer.State.LOADING) visibleTitle=label("state.loading");
            else if(current.preview()) visibleTitle=label("preview");
            else if(!operation.getString().isEmpty()) visibleTitle=operation;
        }
        nowPlaying.setMessage(visibleTitle);
        var description=current.track()==null?title.copy():label("track",current.track().title(),current.track().artist());
        if(!visibleTitle.equals(title))description.append("\n").append(visibleTitle);
        else if(!operation.getString().isEmpty())description.append("\n").append(operation);
        nowPlaying.setPreview(description);
        nowPlaying.setSecondary(current.track()==null?label("refresh.choose_track"):MusicTypography.text(current.track().artist()));
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
        else artworkStage.renderAudioBackground(ui,stage.frame(),loaded.map(OnlineArtworkTexture.Texture::palette).orElse(null),ui.nowMs(),ui.preferences().reducedMotion(),
                current.state()==OnlineMusicPlayer.State.PLAYING?player.audioActivity():0f);
        int ink=ui.preferences().highContrast()?UiTheme.current().text():MusicArtworkStage.FOREGROUND;
        int secondary=ui.preferences().highContrast()?ink:MusicArtworkStage.SECONDARY;
        var cover=loaded.orElse(null);
        float opacity=coverOpacity(ui,cover);
        if(vinylStyle()&&!ui.preferences().highContrast()) {
            vinylStage.render(ui,stage.cover(),cover==null?null:cover.id(),cover==null?0:cover.width(),
                    cover==null?0:cover.height(),current.state()==OnlineMusicPlayer.State.PLAYING,opacity);
        } else paintArtwork(ui,stage.cover(),cover,0f,opacity);
        var title=current.track()==null?label("stage.empty_title"):MusicTypography.bold(current.track().title());
        var subtitle=current.track()==null?label("stage.empty_detail"):MusicTypography.text(current.track().artist()
                +(current.track().album().isBlank()?"":" · "+current.track().album()));
        var trackDetails=title.copy().append("\n").append(subtitle).append("\n").append(label("stage.toggle_lyrics"));
        stageCoverEntry.setMessage(trackDetails);stageCoverEntry.setTooltip(net.minecraft.client.gui.components.Tooltip.create(MusicTypography.text(trackDetails)));
        OnlineMusicViewPaint.scaledLine(ui,host.font(),MusicTypography.bold(title),stage.info().x(),stage.info().y(),stage.info().width(),ink,stage.wide()?1.35f:1.1f);
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
        OnlineMusicViewPaint.line(ui,host.font(),MusicTypography.text(time(position)),stage.seek().x(),stage.seek().bottom()+2,36,secondary);
        OnlineMusicViewPaint.line(ui,host.font(),MusicTypography.text("−"+time(Math.max(0,current.durationMs()-position))),stage.seek().right()-40,stage.seek().bottom()+2,40,secondary);
        var operation=feedbackMessage();
        if(!failed && (current.preview() || !operation.getString().isEmpty()))
            OnlineMusicViewPaint.line(ui,host.font(),current.preview()?(operation.getString().isEmpty()?label("preview"):operation.copy().append(" · ").append(label("preview"))):operation,stage.seek().x()+42,stage.seek().bottom()+2,Math.max(0,stage.seek().width()-86),secondary);
        stagePlay.setTooltip(net.minecraft.client.gui.components.Tooltip.create(MusicTypography.text(operation.getString().isEmpty()?stagePlay.getMessage():operation)));
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
        var texture=artwork.texture(track).orElse(null);
        paintArtwork(ui,bounds,texture,activity,coverOpacity(ui,texture));
    }
    private float coverOpacity(UiFrame ui, OnlineArtworkTexture.Texture texture) {
        return coverReveal.sample(artwork.revision(),texture!=null,ui.nowMs(),
                ui.preferences().reducedMotion()||ui.preferences().highContrast());
    }
    private void paintArtwork(UiFrame ui, Bounds bounds, OnlineArtworkTexture.Texture texture, float activity, float opacity) {
        if(opacity<1f)OnlineMusicViewPaint.artwork(ui,bounds,activity,1f-opacity);
        if(texture!=null&&opacity>0f)artworkStage.renderCover(ui,bounds,texture.id(),texture.width(),texture.height(),opacity);
    }
    private void renderLyrics(UiFrame ui, OnlineMusicPlayer.Snapshot current) {
        var c = lyricContent(); var lines = lyricArea();
        back.setY(layout.expanded() ? layout.header().y() + 2 : layout.searchBar().y() + 2);
        var title = current.track() == null ? label("view.now_playing") : MusicTypography.text(current.track().title());
        if (!immersive && c.width() >= 360) {
            int column = c.width() * 44 / 100;
            int size = Math.max(16, Math.min(column - 32, c.height() - 46));
            var cover = new Bounds(c.x() + (column - size) / 2, c.y() + Math.max(0, (c.height() - size - 38) / 2), size, size);
            paintArtwork(ui, cover, current.track(), 0f);
            text(ui, title, cover.x(), cover.bottom() + 10, size);
            if (current.track() != null) muted(ui, MusicTypography.text(current.track().artist()), cover.x(), cover.bottom() + 23, size);
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
                    ? MusicTypography.text(word.text()).withStyle(style -> style.withColor(
                        word.startMs() <= position ? palette.active() : palette.text()))
                    : MusicTypography.text(word.text()));
            row.setMessage(words); row.setSelected(i == index); row.setDistance((float)(i - center));
            row.setPreview(label("view.seek_line", time(line.startMs()), words).append("\n").append(MusicTypography.text(line.translation())));
        }
        followLyrics.visible = lyricViewport.browsing(now); followLyrics.active = followLyrics.visible;
        if (index >= 0 && index < timeline.size())
            muted(ui, MusicTypography.text(timeline.line(index).translation()), lines.x() + 12, lines.bottom() + 25, lines.width() - 24);
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
        if (login != null && operations() != null && operations().currentLogin(accountSource).orElse(null) != login) {
            login = null; observedLoginState = "";
        }
        var c = accountContent(accountOffset); var snapshot = login == null ? null : login.snapshot();
        boolean signedIn = player.authenticated(accountSource);
        if (signedIn != accountWasSignedIn) { accountWasSignedIn = signedIn; invalidateLibrarySource(accountSource); clearAccountFeedback(); if (signedIn) loadAccount(); updateVisibility(); }
        String state = snapshot == null ? player.authenticated(accountSource) ? "confirmed" : "idle" : snapshot.state().name().toLowerCase(Locale.ROOT);
        var status = label("login." + state);
        if (login != null && !state.equals(observedLoginState)) { observedLoginState=state; feedback(status); }
        loginSignOut.active = player.authenticated(accountSource);
        boolean busy = loginBusy(snapshot);
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
        int instructionsY = statusY + MusicTypography.LINE_HEIGHT + 6;
        if (!wide) imageArea = new Bounds(imageArea.x(), instructionsY, imageArea.width(), Math.max(0, bodyBottom - instructionsY));
        boolean fitsImage = snapshot == null || snapshot.image() == null ||
                (imageArea.width() >= snapshot.image().width() && imageArea.height() >= snapshot.image().height()
                    && layout.content().height() >= snapshot.image().height());
        if (bodyBottom - statusY >= MusicTypography.LINE_HEIGHT) text(ui, fitsImage ? status : label("login.enlarge"), c.x() + 4, statusY, wide ? c.width() / 2 - 12 : c.width() - 8);
        if (wide && bodyBottom - instructionsY >= MusicTypography.LINE_HEIGHT) OnlineMusicViewPaint.paragraph(ui, host.font(), label(signedIn ? "account.library_hint" : "login.scan." + accountSource),
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
    private static MutableComponent label(String key, Object... args) { return MusicTypography.text(Component.translatable(TEXT + key, args)); }
    @Override public void suspend() { cancelListen1(); closeNowPlayingMenu(); routeMotion.reset(); historyDestination=null; interactionFeedback.clear(); for(var control:historyControls)if(control instanceof MusicTextControl text)text.cancelInteraction(); for(var control:queueControls) if(control instanceof MusicTextControl text)text.cancelInteraction(); if(stagePlaybackMode!=null)stagePlaybackMode.cancelInteraction(); if(seek!=null)seek.cancelInteraction(); for(var c:new MusicTransportControl[]{playback,stop,lyricsToggle,miniQueue,miniPrevious,miniNext})if(c!=null)c.cancelInteraction(); if(stageSeek!=null)stageSeek.cancelInteraction(); if(stageVolume!=null)stageVolume.cancelInteraction(); queue.suspend(); if(stageQueueScroll!=null)stageQueueScroll.cancelDrag(); for(var control:new MusicTransportControl[]{stageBack,stagePlay,stagePrevious,stageNext,stageMode,stageQueue})if(control!=null)control.cancelInteraction(); for (var scroll : new OnlineMusicScrollRegion[]{searchScroll, libraryScroll, lyricsScroll, accountScroll, queueScroll}) if (scroll != null) scroll.cancelDrag(); navigation.forEach(MusicTextControl::cancelInteraction); if(sourceSegments!=null)sourceSegments.cancelInteraction();if(librarySources!=null)librarySources.cancelInteraction();if(libraryGroups!=null)libraryGroups.cancelInteraction(); detachSearch(); cancelSuggestions(); detachLogin(); detachLibrary(); clearAccountFeedback(); library = Library.NONE; accountDetail = false; interactionFeedback.clear(); if (ownsPlayer) player.pause(); }
    @Override public void close() { if (!closed) { suspend(); navigation.forEach(MusicTextControl::cancelInteraction); if(sourceSegments!=null)sourceSegments.cancelInteraction();if(librarySources!=null)librarySources.cancelInteraction();if(libraryGroups!=null)libraryGroups.cancelInteraction(); visualMotion.close(); artwork.close(); coverReveal.close(); artworkStage.close(); if (ownsPlayer) player.close(); closed = true; } }
}
