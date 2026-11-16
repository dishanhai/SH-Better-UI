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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Complete UUK player page; all widgets remain native and all asynchronous work belongs to the session. */
public final class OnlineMusicViewImpl implements OnlineMusicView {
    private enum Page { SEARCH, LYRICS, ACCOUNTS }
    private enum Library { NONE, PLAYLISTS, TRACKS, LIKED }
    private static final String TEXT = "sh_better_ui.online_music.";
    private final OnlineMusicPlayer player;
    private final List<AbstractWidget> searchControls = new ArrayList<>(), lyricControls = new ArrayList<>(),
            accountControls = new ArrayList<>(), detailControls = new ArrayList<>(), libraryControls = new ArrayList<>();
    private final List<UiButton> resultRows = new ArrayList<>(), lyricRows = new ArrayList<>();
    private final List<UiButton> libraryRows = new ArrayList<>(), searchLikes = new ArrayList<>(), searchUnlikes = new ArrayList<>(),
            libraryLikes = new ArrayList<>(), libraryUnlikes = new ArrayList<>();
    private UiNavigationRail navigation;
    private final Map<String, UiButton> sources = new LinkedHashMap<>(), accounts = new LinkedHashMap<>(), loginSources = new LinkedHashMap<>();
    private PreviewContext host;
    private OnlineMusicLayout layout;
    private UiTextField queryField;
    private UiButton searchButton, previous, next, playback, stop, nowPlaying, lyricsToggle, back;
    private UiButton loginRefresh, loginImport, loginSignOut;
    private UiButton remember, openPlaylists, openLiked, libraryPrevious, libraryNext;
    private UiSlider seek;
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
    private final List<Integer> lyricIndices = new ArrayList<>();
    private long lyricOffset;

    public OnlineMusicViewImpl(OnlineMusicPlayer player) { this.player = Objects.requireNonNull(player); }
    @Override public OnlineMusicPlayer player() { return player; }
    @Override public void layout(PreviewContext context) {
        if (closed) throw new IllegalStateException("Music view closed");
        if (navigation != null) { navigation.close(); navigation = null; }
        host = context; layout = OnlineMusicLayout.of(context.viewport()); queryField = null;
        searchControls.clear(); lyricControls.clear(); accountControls.clear(); detailControls.clear(); libraryControls.clear();
        resultRows.clear(); lyricRows.clear(); libraryRows.clear(); searchLikes.clear(); searchUnlikes.clear(); libraryLikes.clear(); libraryUnlikes.clear(); lyricIndices.clear(); sources.clear(); accounts.clear(); loginSources.clear();
        if (!layout.supported()) return;
        buildNavigation(); buildSearch(); buildLyrics(); buildAccounts(); buildLibrary(); buildTransport();
        var h = layout.header();
        back = button(new Bounds(h.x(), h.y() + 2, Math.min(88, h.width() / 2), 22), label("view.back"), () -> {
            if (page == Page.ACCOUNTS && library != Library.NONE) {
                if (library == Library.TRACKS) {
                    cancelLibrary(); library = Library.PLAYLISTS; libraryPage = savedPlaylistPage; libraryFirst = savedPlaylistFirst;
                    libraryState = playlists.isEmpty() ? "library.empty" : "library.results"; updateVisibility();
                } else { cancelLibrary(); library = Library.NONE; updateVisibility(); }
            } else if (page == Page.ACCOUNTS && accountDetail) { cancelLogin(); accountDetail = false; updateVisibility(); }
            else show(Page.SEARCH);
        }, null);
        back.setLeadingIcon(GraphicIcon.BACK);
        updateRows(); updateLibraryRows(); updateVisibility();
    }
    private void buildNavigation() {
        var n = layout.navigation();
        var entries = new ArrayList<UiNavigationRail.Item>();
        for (var target : Page.values()) entries.add(new UiNavigationRail.Item(
                label("view." + target.name().toLowerCase(Locale.ROOT)),
                target == Page.SEARCH ? GraphicIcon.SEARCH : target == Page.LYRICS ? GraphicIcon.MUSIC : GraphicIcon.USER, true));
        var area = layout.sidebar() ? new Bounds(n.x(), n.y() + 26, n.width(), 70)
                : new Bounds(n.x(), n.y(), n.width(), 22);
        navigation = host.add(new UiNavigationRail(host.font(), area, label("view.brand"), entries,
                page.ordinal(), layout.sidebar() ? UiNavigationRail.Orientation.VERTICAL
                        : UiNavigationRail.Orientation.HORIZONTAL,
                host.preferences(), index -> show(Page.values()[index])));
        if (layout.sidebar()) {
            int row = 0;
            for (String id : List.of("netease", "qq")) {
                final String selected = id;
                sources.put(id, button(new Bounds(n.x(), n.y() + 118 + row++ * 24, n.width(), 22), label("source." + id), () -> selectSource(selected), null));
            }
        }
    }
    private void buildSearch() {
        var h = layout.header(); int offset = 0;
        if (!layout.sidebar()) {
            var toggle = button(new Bounds(h.x(), h.y() + 2, 76, 22), label("source." + source),
                    () -> selectSource(source.equals("netease") ? "qq" : "netease"), searchControls);
            toggle.setPreview(label("guest_hint")); sources.put("toggle", toggle); offset = 80;
        }
        queryField = host.add(new UiTextField(host.font(), new Bounds(h.x() + offset, h.y() + 2, h.width() - offset - 28, 22), label("query"), 200, host.preferences()));
        searchControls.add(queryField);
        queryField.setValue(query); queryField.setHint(label("query")); queryField.setOnChange(this::queryChanged);
        queryField.setOnSubmit(value -> { query = value; search(1, true); });
        searchButton = button(new Bounds(h.right() - 24, h.y() + 2, 24, 22), label("search"), () -> {
            if (pending != null) { cancelSearch(); searchState = "cancelled"; updateRows(); }
            else search(1, true);
        }, searchControls);
        searchButton.setIcon(GraphicIcon.SEARCH);
        visibleRows = layout.rows().size(); first = first / visibleRows * visibleRows;
        for (var row : layout.rows()) {
            int slot = resultRows.size();
            int titleWidth = row.width() - 52;
            var button = button(new Bounds(row.x(), row.y(), titleWidth, row.height()), Component.empty(), () -> {
                int i = first + slot;
                if (showSuggestions()) { if (i < suggestions.size()) { String selected = suggestions.get(i); queryField.setValue(selected); query = selected; search(1, true); } }
                else if (i < results.size()) player.play(results.get(i));
            }, searchControls);
            resultRows.add(button);
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
        int count = Math.max(1, Math.min(7, (lines.height() - 22) / 29));
        if (count > 1 && count % 2 == 0) count--;
        int top = lines.y() + Math.max(0, (lines.height() - count * 29 - 14) / 2);
        for (int i = 0; i < count; i++) {
            int slot = i; lyricIndices.add(-1);
            lyricRows.add(button(new Bounds(lines.x(), top + i * 29, lines.width(), 24), Component.empty(), () -> {
                int index = lyricIndices.get(slot);
                if (index >= 0) player.seek(Math.max(0, player.snapshot().lyrics().line(index).startMs() - lyricOffset));
            }, lyricControls));
        }
    }
    private void buildAccounts() {
        var c = layout.content(); int width = Math.min(132, (c.width() - 12) / 2);
        int left = c.x() + (c.width() - width * 2 - 12) / 2;
        for (String id : List.of("netease", "qq")) {
            final String selected = id; int i = accounts.size();
            var card = button(new Bounds(left + i * (width + 12), c.y() + 10, width, 44), label("source." + id), () -> {
                accountSource = selected; accountDetail = true; accountName = ""; accountState = "";
                accountWasSignedIn = player.authenticated(selected);
                if (player.authenticated(selected)) loadAccount();
                updateVisibility(); host.screen().setFocused(back);
            }, accountControls);
            card.setLeadingIcon(GraphicIcon.USER); accounts.put(id, card);
            var tab = button(new Bounds(c.x() + i * (c.width() / 2 + 2), c.y(), (c.width() - 4) / 2, 22), label("source." + id), () -> {
                if (!accountSource.equals(selected)) { cancelLogin(); cancelLibrary(); accountSource = selected; accountState = ""; accountName = ""; accountWasSignedIn = player.authenticated(selected); if (accountWasSignedIn) loadAccount(); }
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
        openPlaylists = button(new Bounds(c.x(), c.bottom() - 44, half, 20), label("library.playlists"), () -> loadPlaylists(1), detailControls);
        openLiked = button(new Bounds(c.x() + half + 4, c.bottom() - 44, half, 20), label("library.liked"), () -> loadLiked(1), detailControls);
        loginRefresh = button(new Bounds(c.x(), c.bottom() - 20, third, 20), label("login.refresh"), this::startLogin, detailControls);
        loginSignOut = button(new Bounds(c.x() + (third + 4) * 2, c.bottom() - 20, c.width() - (third + 4) * 2, 20), label("login.sign_out"), () -> {
            try { cancelLogin(); cancelLibrary(); player.credentials(accountSource, ""); accountName = ""; accountState = "login.signed_out"; }
            catch (RuntimeException failure) { accountState = "login.storage_failed"; }
            updateVisibility();
        }, detailControls);
        loginImport = button(new Bounds(c.x() + third + 4, c.bottom() - 20, third, 20), label("login.import"), () -> {
            try { cancelLibrary(); player.credentials(accountSource, OnlineMusicViewPaint.clipboard().strip()); accountState = "credentials_loaded"; cancelLogin(); loadAccount(); }
            catch (IllegalArgumentException failure) { accountState = "credentials_invalid"; }
            catch (RuntimeException failure) { accountState = "login.storage_failed"; }
            updateVisibility();
        }, detailControls);
        loginImport.setPreview(label("login.import_hint"));
    }
    private UiButton trackAction(Bounds row, int slot, boolean inLibrary, boolean like) {
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
        for (var row : layout.rows()) {
            int slot = libraryRows.size();
            libraryRows.add(button(new Bounds(row.x(), row.y(), row.width() - 52, row.height()), Component.empty(), () -> {
                int i = libraryFirst + slot;
                if (library == Library.PLAYLISTS && i < playlists.size()) loadPlaylist(playlists.get(i), 1);
                else if (i < libraryTracks.size()) player.play(libraryTracks.get(i));
            }, libraryControls));
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
    private void buildTransport() {
        var t = layout.transport(); boolean wide = t.width() >= 480;
        int titleWidth = wide ? Math.min(180, t.width() / 3) : t.width() - 124;
        nowPlaying = button(new Bounds(t.x(), t.y() + 4, titleWidth, 24), label("view.now_playing"), () -> show(Page.LYRICS), null);
        if (titleWidth > 100) nowPlaying.setLeadingIcon(GraphicIcon.MUSIC);
        int playX = wide ? t.x() + t.width() / 2 - 30 : t.x() + titleWidth + 4;
        playback = button(new Bounds(playX, t.y() + 4, 28, 24), label("play"), () -> {
            if (player.snapshot().state() == OnlineMusicPlayer.State.PLAYING) player.pause(); else player.resume();
        }, null); playback.setIcon(GraphicIcon.RUN);
        stop = button(new Bounds(playback.getX() + 32, t.y() + 4, 28, 24), label("stop"), player::stop, null);
        stop.setIcon(GraphicIcon.CLOSE);
        lyricsToggle = button(new Bounds(t.right() - 56, t.y() + 4, 56, 24), label("view.lyrics"), () -> show(page == Page.LYRICS ? Page.SEARCH : Page.LYRICS), null);
        seek = host.add(new UiSlider(new Bounds(t.x() + 32, t.y() + 34, t.width() - 64, 16), 0, 1000, 1, 0, host.preferences(),
                value -> label("seek", value / 10), ignored -> {}));
        seek.setOnCommit(value -> player.seek(player.snapshot().durationMs() * value / 1000), ignored -> {});
    }
    private UiButton button(Bounds b, Component name, Runnable action, List<AbstractWidget> group) {
        var result = host.add(new UiButton(b.x(), b.y(), b.width(), b.height(), name, ignored -> action.run(), host.preferences(), false));
        if (group != null) group.add(result);
        return result;
    }
    private void show(Page next) {
        if (page == Page.ACCOUNTS && next != page) { cancelLogin(); cancelLibrary(); library = Library.NONE; accountDetail = false; }
        if (page == Page.SEARCH && next != page) cancelSuggestions();
        page = next; updateVisibility();
        if (next == Page.SEARCH) host.screen().setFocused(queryField); else host.screen().setFocused(back);
    }
    private void selectSource(String next) {
        if (!source.equals(next)) {
            cancelSearch(); cancelSuggestions(); source = next; results = List.of(); first = 0; remotePage = 1; searchState = "search_hint";
        }
        show(Page.SEARCH); updateRows();
    }
    private void updateVisibility() {
        if (queryField == null) return;
        searchControls.forEach(w -> w.visible = page == Page.SEARCH);
        lyricControls.forEach(w -> w.visible = page == Page.LYRICS);
        accountControls.forEach(w -> w.visible = page == Page.ACCOUNTS && !accountDetail);
        detailControls.forEach(w -> w.visible = page == Page.ACCOUNTS && accountDetail && library == Library.NONE);
        libraryControls.forEach(w -> w.visible = page == Page.ACCOUNTS && accountDetail && library != Library.NONE);
        back.visible = page != Page.SEARCH;
        if (navigation != null) navigation.state().select(page.ordinal());
        sources.forEach((id, button) -> {
            if (id.equals("toggle")) button.setMessage(label("source." + source)); else button.setSelected(id.equals(source));
        });
        loginSources.forEach((id, button) -> button.setSelected(id.equals(accountSource)));
        boolean signedIn = player.authenticated(accountSource);
        remember.setMessage(label(player.remembersCredentials(accountSource) ? "login.remember_on" : "login.remember_off"));
        remember.setSelected(player.remembersCredentials(accountSource));
        openPlaylists.visible &= signedIn; openLiked.visible &= signedIn;
        updateRows(); updateLibraryRows();
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
        String text = fresh ? query.strip() : submittedQuery;
        if (text.isEmpty()) { searchState = "search_hint"; return; }
        cancelSearch(); cancelSuggestions(); likeState = ""; if (fresh) submittedQuery = text;
        remotePage = pageNumber; first = 0; results = List.of(); searchState = "searching";
        pending = player.search(source, text, pageNumber); updateRows();
    }
    private void cancelSearch() {
        if (pending != null) { pending.cancel(true); searchState = "cancelled"; pending = null; }
    }
    private void cancelLibrary() {
        if (pendingPlaylists != null) pendingPlaylists.cancel(true);
        if (pendingLibrary != null) pendingLibrary.cancel(true);
        if (pendingAccount != null) pendingAccount.cancel(true);
        pendingPlaylists = null; pendingLibrary = null; pendingAccount = null;
    }
    private void loadAccount() {
        if (pendingAccount != null) pendingAccount.cancel(true);
        if (player.authenticated(accountSource)) pendingAccount = player.account(accountSource);
    }
    private void loadPlaylists(int number) {
        cancelLibrary(); cancelLogin(); library = Library.PLAYLISTS; libraryPage = number; libraryFirst = 0;
        likeState = "";
        playlists = List.of(); libraryTracks = List.of(); libraryState = "library.loading";
        pendingPlaylists = player.playlists(accountSource, number); updateVisibility(); host.screen().setFocused(back);
    }
    private void loadPlaylist(OnlinePlaylist playlist, int number) {
        if (library == Library.PLAYLISTS) { savedPlaylistPage = libraryPage; savedPlaylistFirst = libraryFirst; }
        cancelLibrary(); library = Library.TRACKS; selectedPlaylist = playlist; libraryPage = number; libraryFirst = 0;
        likeState = "";
        libraryTracks = List.of(); libraryState = "library.loading";
        pendingLibrary = player.playlist(playlist, number); updateVisibility(); host.screen().setFocused(back);
    }
    private void loadLiked(int number) {
        cancelLibrary(); cancelLogin(); library = Library.LIKED; libraryPage = number; libraryFirst = 0;
        likeState = "";
        libraryTracks = List.of(); libraryState = "library.loading";
        pendingLibrary = player.liked(accountSource, number); updateVisibility(); host.screen().setFocused(back);
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
        pendingLike = player.like(track, like); updateRows(); updateLibraryRows();
    }
    private void updateLibraryRows() {
        if (libraryPrevious == null) return;
        for (int slot = 0; slot < libraryRows.size(); slot++) {
            int i = libraryFirst + slot; var row = libraryRows.get(slot);
            boolean available = page == Page.ACCOUNTS && accountDetail && library != Library.NONE && i < librarySize();
            row.visible = available; row.active = available;
            if (available) {
                if (library == Library.PLAYLISTS) {
                    var playlist = playlists.get(i); row.setMessage(Component.literal(playlist.name()));
                    row.setPreview(label("library.playlist_details", playlist.name(), playlist.trackCount()));
                } else {
                    var track = libraryTracks.get(i); row.setMessage(Component.literal(track.title()));
                    row.setPreview(label("play_track", track.title(), track.artist()).append("\n").append(Component.literal(track.album())));
                }
            }
            boolean actions = available && library != Library.PLAYLISTS;
            libraryLikes.get(slot).visible = actions; libraryUnlikes.get(slot).visible = actions;
            boolean supported = actions && player.supportsLikes(libraryTracks.get(i).source());
            boolean enabled = supported && pendingLike == null && player.authenticated(libraryTracks.get(i).source());
            libraryLikes.get(slot).active = enabled; libraryUnlikes.get(slot).active = enabled;
            if (actions) { libraryLikes.get(slot).setPreview(supported ? label("library.like_track", libraryTracks.get(i).title()) : label("library.like_unsupported")); libraryUnlikes.get(slot).setPreview(supported ? label("library.unlike_track", libraryTracks.get(i).title()) : label("library.like_unsupported")); }
        }
        boolean ready = pendingPlaylists == null && pendingLibrary == null;
        libraryPrevious.active = ready && (libraryFirst > 0 || libraryPage > 1);
        libraryNext.active = ready && (libraryFirst + visibleRows < librarySize() || librarySize() == 20);
    }
    private static String operationFailure(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure instanceof OnlineMusicSource.LoginRequired ? "library.login_required" : "library.failed";
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
            catch (RuntimeException failure) { accountState = operationFailure(failure); }
            pendingAccount = null;
        }
        if (pendingPlaylists != null && pendingPlaylists.isDone()) {
            try { playlists = pendingPlaylists.join(); libraryState = playlists.isEmpty() ? "library.empty" : "library.results"; }
            catch (RuntimeException failure) { libraryState = operationFailure(failure); }
            pendingPlaylists = null; updateLibraryRows();
        }
        if (pendingLibrary != null && pendingLibrary.isDone()) {
            try { libraryTracks = pendingLibrary.join(); libraryState = libraryTracks.isEmpty() ? "library.empty" : "library.results"; }
            catch (RuntimeException failure) { libraryState = operationFailure(failure); likeState = ""; }
            pendingLibrary = null; updateLibraryRows();
        }
        if (pendingLike != null && pendingLike.isDone()) {
            boolean updated = false;
            try { pendingLike.join(); likeState = "library.updated"; updated = true; }
            catch (RuntimeException failure) { likeState = operationFailure(failure); }
            pendingLike = null;
            if (updated && page == Page.ACCOUNTS && library == Library.LIKED) {
                loadLiked(libraryPage); likeState = "library.updated";
            }
            updateRows(); updateLibraryRows();
        }
    }
    private void startLogin() { cancelLogin(); accountState = ""; login = player.login(accountSource); }
    private void cancelLogin() { if (login != null) { login.close(); login = null; } }
    private void updateRows() {
        if (queryField == null) return;
        var current = player.snapshot().track();
        for (int slot = 0; slot < resultRows.size(); slot++) {
            int i = first + slot; var row = resultRows.get(slot); boolean suggesting = showSuggestions();
            row.active = i < (suggesting ? suggestions.size() : results.size());
            row.setMessage(row.active ? Component.literal(suggesting ? suggestions.get(i) : results.get(i).title()) : Component.empty());
            row.setSelected(!suggesting && row.active && results.get(i).equals(current));
            if (row.active && !suggesting) row.setPreview(label("play_track", results.get(i).title(), results.get(i).artist())
                    .append("\n").append(Component.literal(results.get(i).album())));
            else if (row.active) row.setPreview(label("suggestion_pick", suggestions.get(i)));
            boolean actions = page == Page.SEARCH && !suggesting && i < results.size();
            searchLikes.get(slot).visible = actions; searchUnlikes.get(slot).visible = actions;
            boolean supported = actions && player.supportsLikes(results.get(i).source());
            boolean enabled = supported && pendingLike == null && player.authenticated(results.get(i).source());
            searchLikes.get(slot).active = enabled; searchUnlikes.get(slot).active = enabled;
            if (actions) { searchLikes.get(slot).setPreview(supported ? label("library.like_track", results.get(i).title()) : label("library.like_unsupported")); searchUnlikes.get(slot).setPreview(supported ? label("library.unlike_track", results.get(i).title()) : label("library.like_unsupported")); }
        }
        previous.active = showSuggestions() ? first > 0 : pending == null && (first > 0 || remotePage > 1);
        next.active = showSuggestions() ? first + visibleRows < suggestions.size() : pending == null && (first + visibleRows < results.size() || results.size() == 20);
        searchButton.setMessage(label(pending == null ? "search" : "cancel"));
        searchButton.setIcon(pending == null ? GraphicIcon.SEARCH : GraphicIcon.CLOSE);
    }
    @Override public void render(UiFrame ui) {
        if (closed || layout == null) return;
        if (!layout.supported()) { text(ui, label("space"), layout.frame().x() + 4, layout.frame().y() + 4, Math.max(0, layout.frame().width() - 8)); return; }
        var theme = UiTheme.current();
        OnlineMusicViewPaint.panel(ui, layout.frame(), theme.background(), true);
        OnlineMusicViewPaint.panel(ui, layout.transport(), theme.surface(), false);
        if (layout.sidebar()) {
            OnlineMusicViewPaint.panel(ui, layout.navigation(), theme.surface(), false);
            text(ui, label("view.brand"), layout.navigation().x() + 6, layout.navigation().y() + 7, layout.navigation().width() - 12);
            muted(ui, label("view.sources"), layout.navigation().x() + 6, layout.navigation().y() + 104, layout.navigation().width() - 12);
        }
        pollOperations();
        if (pending != null && pending.isDone()) {
            try { results = pending.join(); searchState = results.isEmpty() ? "empty" : "results"; }
            catch (RuntimeException failure) { searchState = "search_failed"; }
            pending = null; updateRows();
        }
        var current = player.snapshot();
        renderTransport(ui, current);
        if (page == Page.SEARCH) renderResults(ui, current);
        else {
            var h = layout.header();
            Component title = page == Page.ACCOUNTS && library != Library.NONE
                    ? (library == Library.TRACKS && selectedPlaylist != null ? Component.literal(selectedPlaylist.name()) : label(library == Library.PLAYLISTS ? "library.playlists" : "library.liked"))
                    : page == Page.ACCOUNTS && !accountState.isEmpty() ? label(accountState)
                    : page == Page.ACCOUNTS && accountDetail && !accountName.isBlank() ? Component.literal(accountName)
                    : label("view." + page.name().toLowerCase(Locale.ROOT));
            if (page == Page.ACCOUNTS && accountDetail && library == Library.NONE && login != null && accountState.isEmpty() && !player.authenticated(accountSource)) {
                var loginSnapshot = login.snapshot();
                title = loginSnapshot.image() != null && layout.content().height() - 96 < loginSnapshot.image().height()
                        ? label("login.enlarge") : label("login." + loginSnapshot.state().name().toLowerCase(Locale.ROOT));
            }
            text(ui, title, h.x() + back.getWidth() + 8, h.y() + 8, h.width() - back.getWidth() - 8);
            if (page == Page.LYRICS) renderLyrics(ui, current); else if (library != Library.NONE) renderLibrary(ui); else renderAccounts(ui);
        }
    }
    private void renderResults(UiFrame ui, OnlineMusicPlayer.Snapshot current) {
        var c = layout.content();
        for (int slot = 0; slot < resultRows.size(); slot++) resultRows.get(slot).visible = first + slot < (showSuggestions() ? suggestions.size() : results.size());
        muted(ui, label(showSuggestions() ? "suggestions" : "view.song"), c.x() + 6, c.y() + 4, c.width() - 12);
        for (int slot = 0; slot < resultRows.size(); slot++) {
            int i = first + slot; if (showSuggestions() || i >= results.size()) break;
            var track = results.get(i);
            resultRows.get(slot).setSelected(track.equals(current.track()));
        }
        var pager = layout.pager();
        var status = label(!likeState.isEmpty() ? likeState : showSuggestions() ? "suggestions" : searchState, remotePage, results.isEmpty() ? 0 : first + 1, Math.min(first + visibleRows, results.size()));
        text(ui, status, pager.x() + 30, pager.y() + 6, pager.width() - 60);
        previous.setPreview(status.copy().append("\n").append(label("previous")));
        next.setPreview(status.copy().append("\n").append(label("next")));
        if (results.isEmpty() && !showSuggestions()) OnlineMusicViewPaint.paragraph(ui, host.font(), label(searchState),
                new Bounds(c.x() + 8, c.y() + 28, c.width() - 16, Math.max(0, c.height() - 54)), UiTheme.current().text());
    }
    private void renderLibrary(UiFrame ui) {
        var c = layout.content();
        muted(ui, label(library == Library.PLAYLISTS ? "library.playlists" : "view.song"), c.x() + 6, c.y() + 4, c.width() - 12);
        var pager = layout.pager();
        var status = label(!likeState.isEmpty() ? likeState : libraryState, libraryPage, librarySize() == 0 ? 0 : libraryFirst + 1, Math.min(libraryFirst + visibleRows, librarySize()));
        text(ui, status, pager.x() + 30, pager.y() + 6, pager.width() - 60);
        if (librarySize() == 0) OnlineMusicViewPaint.paragraph(ui, host.font(), label(libraryState),
                new Bounds(c.x() + 8, c.y() + 28, c.width() - 16, Math.max(0, c.height() - 54)), UiTheme.current().text());
    }
    private void renderTransport(UiFrame ui, OnlineMusicPlayer.Snapshot current) {
        var state = current.state(); boolean playing = state == OnlineMusicPlayer.State.PLAYING;
        playback.active = playing || state == OnlineMusicPlayer.State.PAUSED || state == OnlineMusicPlayer.State.ENDED;
        playback.setMessage(label(playing ? "pause" : "play")); playback.setIcon(playing ? GraphicIcon.PAUSE : GraphicIcon.RUN);
        stop.active = state != OnlineMusicPlayer.State.IDLE && state != OnlineMusicPlayer.State.CLOSED;
        stop.setMessage(label(state == OnlineMusicPlayer.State.LOADING ? "cancel" : "stop"));
        seek.active = current.durationMs() > 0 && playback.active;
        if (!OnlineMusicViewPaint.editing(seek)) seek.setValue(current.durationMs() <= 0 ? 0 : (int) (1000 * current.positionMs() / current.durationMs()));
        var title = current.track() == null ? label("view.now_playing") : Component.literal(current.track().title());
        nowPlaying.setMessage(title); nowPlaying.setPreview(current.track() == null ? title : label("track", current.track().title(), current.track().artist()));
        lyricsToggle.setSelected(page == Page.LYRICS);
        var t = layout.transport();
        muted(ui, Component.literal(time(current.positionMs())), t.x(), seek.getY() + 4, 30);
        muted(ui, Component.literal(time(current.durationMs())), t.right() - 28, seek.getY() + 4, 28);
        var status = state == OnlineMusicPlayer.State.FAILED ? label("failure." + current.failure().name().toLowerCase(Locale.ROOT))
                : label("state." + state.name().toLowerCase(Locale.ROOT));
        if (current.preview()) status = label("preview").append(" · ").append(status);
        muted(ui, status, t.x() + 4, t.y() + 55, t.width() - 8);
        playback.setPreview(status); stop.setPreview(status.copy().append("\n").append(label("stop_hint")));
    }
    private Bounds lyricArea() {
        var c = layout.content();
        return c.width() >= 360 ? new Bounds(c.x() + c.width() * 42 / 100 + 8, c.y(), c.width() * 58 / 100 - 8, c.height())
                : new Bounds(c.x(), c.y() + 16, c.width(), c.height() - 16);
    }
    private void renderLyrics(UiFrame ui, OnlineMusicPlayer.Snapshot current) {
        var c = layout.content(); var lines = lyricArea();
        var title = current.track() == null ? label("view.now_playing") : Component.literal(current.track().title());
        if (c.width() >= 360) {
            int size = Math.max(24, Math.min(c.width() * 42 / 100 - 16, c.height() - 40));
            var cover = new Bounds(c.x() + (c.width() * 42 / 100 - size) / 2, c.y() + Math.max(0, (c.height() - size - 34) / 2), size, size);
            OnlineMusicViewPaint.artwork(ui, cover);
            text(ui, title, c.x() + 4, cover.bottom() + 8, c.width() * 42 / 100 - 8);
            if (current.track() != null) muted(ui, Component.literal(current.track().artist()), c.x() + 4, cover.bottom() + 21, c.width() * 42 / 100 - 8);
        } else text(ui, title, c.x() + 4, c.y() + 2, c.width() - 8);
        var feed = player.lyricFeed().frame();
        long position = feed.alive() ? feed.snapshot().positionMs() : current.positionMs();
        lyricOffset = Math.max(0, position - current.positionMs());
        var timeline = current.lyrics(); int index = timeline.indexAt(position);
        int center = Math.max(0, index), firstLine = center - lyricRows.size() / 2;
        for (int slot = 0; slot < lyricRows.size(); slot++) {
            int i = firstLine + slot; var row = lyricRows.get(slot);
            boolean present = i >= 0 && i < timeline.size() && current.lyricsState() == OnlineMusicPlayer.LyricsState.READY;
            row.active = present && seek.active; row.visible = present; lyricIndices.set(slot, present ? i : -1);
            if (!present) continue;
            var line = timeline.line(i); var words = Component.empty();
            for (var word : line.words()) words.append(Component.literal(word.text()).withStyle(style -> style.withColor(
                    i == index && word.startMs() <= position ? UiTheme.current().accent() : UiTheme.current().text())));
            row.setMessage(words); row.setSelected(i == index);
            row.setPreview(label("view.seek_line", time(line.startMs()), words).append("\n").append(Component.literal(line.translation())));
            if (i == index) muted(ui, Component.literal(line.translation()), lines.x() + 4, lines.bottom() - 11, lines.width() - 8);
        }
        if (current.lyricsState() != OnlineMusicPlayer.LyricsState.READY)
            text(ui, label("lyrics." + current.lyricsState().name().toLowerCase(Locale.ROOT)), lines.x() + 4, lines.y() + lines.height() / 2, lines.width() - 8);
    }
    private void renderAccounts(UiFrame ui) {
        if (!accountDetail) {
            for (var entry : accounts.entrySet()) {
                var card = entry.getValue(); boolean signedIn = player.authenticated(entry.getKey());
                card.setSelected(signedIn);
                var status = label(signedIn ? "view.signed_in" : "view.guest");
                card.setPreview(status.copy().append("\n").append(label("credentials_hint")));
                muted(ui, status, card.getX() + 4, card.getY() + card.getHeight() + 7, card.getWidth() - 8);
            }
            var c = layout.content();
            OnlineMusicViewPaint.paragraph(ui, host.font(), label("login.session_only"), new Bounds(c.x() + 8, c.y() + 82, c.width() - 16, Math.max(0, c.height() - 84)), UiTheme.current().text());
            return;
        }
        var c = layout.content(); var snapshot = login == null ? null : login.snapshot();
        boolean signedIn = player.authenticated(accountSource);
        if (signedIn != accountWasSignedIn) { accountWasSignedIn = signedIn; if (signedIn) loadAccount(); updateVisibility(); }
        String state = snapshot == null ? player.authenticated(accountSource) ? "confirmed" : "idle" : snapshot.state().name().toLowerCase(Locale.ROOT);
        var status = label("login." + state);
        loginSignOut.active = player.authenticated(accountSource);
        boolean busy = snapshot != null && (snapshot.state() == OnlineMusicLogin.State.CREATING || snapshot.state() == OnlineMusicLogin.State.WAITING || snapshot.state() == OnlineMusicLogin.State.SCANNED);
        loginRefresh.setMessage(label(busy ? "login.restart" : "login.refresh"));
        var details = status.copy().append("\n").append(label("login.scan." + accountSource)).append("\n").append(label("login.remember_hint"));
        loginRefresh.setPreview(details); back.setPreview(details); remember.setPreview(details);
        boolean wide = c.width() >= 360;
        int imageX = wide ? c.x() + c.width() / 2 + 4 : c.x();
        var imageArea = new Bounds(imageX, c.y() + 48, wide ? c.width() / 2 - 4 : c.width(), Math.max(0, c.height() - 96));
        boolean fitsImage = snapshot == null || snapshot.image() == null ||
                (imageArea.width() >= snapshot.image().width() && imageArea.height() >= snapshot.image().height());
        if (imageArea.height() >= 12 && wide) text(ui, fitsImage ? status : label("login.enlarge"), c.x() + 4, imageArea.y() + 2, c.width() / 2 - 12);
        if (wide && imageArea.height() >= 24) OnlineMusicViewPaint.paragraph(ui, host.font(), label("login.scan." + accountSource),
                new Bounds(c.x() + 4, imageArea.y() + 4, c.width() / 2 - 16, imageArea.height() - 4), UiTheme.current().text());
        if (snapshot != null && snapshot.image() != null) {
            var image = snapshot.image(); int scale = Math.min(3, Math.min(imageArea.width() / image.width(), imageArea.height() / image.height()));
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
        } else if (!wide) OnlineMusicViewPaint.paragraph(ui, host.font(), label("login.scan." + accountSource), imageArea, UiTheme.current().text());
    }
    private void text(UiFrame ui, Component text, int x, int y, int width) { OnlineMusicViewPaint.line(ui, host.font(), text, x, y, width, UiTheme.current().text()); }
    private void muted(UiFrame ui, Component text, int x, int y, int width) { OnlineMusicViewPaint.line(ui, host.font(), text, x, y, width, UiTheme.current().secondaryText(0xff94a3b8, .25f)); }
    private static String time(long millis) { long seconds = Math.max(0, millis) / 1000; return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    private static MutableComponent label(String key, Object... args) { return Component.translatable(TEXT + key, args); }
    @Override public void suspend() { if (navigation != null) navigation.cancelInteraction(); cancelSearch(); cancelSuggestions(); cancelLogin(); cancelLibrary(); if (pendingLike != null) pendingLike.cancel(true); pendingLike = null; library = Library.NONE; accountDetail = false; player.pause(); }
    @Override public void close() { if (!closed) { suspend(); if (navigation != null) navigation.close(); player.close(); closed = true; } }
}
