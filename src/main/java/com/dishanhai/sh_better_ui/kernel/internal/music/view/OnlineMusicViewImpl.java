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
    private static final String TEXT = "sh_better_ui.online_music.";
    private final OnlineMusicPlayer player;
    private final List<AbstractWidget> searchControls = new ArrayList<>(), lyricControls = new ArrayList<>(),
            accountControls = new ArrayList<>(), detailControls = new ArrayList<>();
    private final List<UiButton> resultRows = new ArrayList<>(), lyricRows = new ArrayList<>();
    private UiNavigationRail navigation;
    private final Map<String, UiButton> sources = new LinkedHashMap<>(), accounts = new LinkedHashMap<>(), loginSources = new LinkedHashMap<>();
    private PreviewContext host;
    private OnlineMusicLayout layout;
    private UiTextField queryField;
    private UiButton searchButton, previous, next, playback, stop, nowPlaying, lyricsToggle, back;
    private UiButton loginRefresh, loginImport, loginSignOut;
    private UiSlider seek;
    private Page page = Page.SEARCH;
    private String source = "netease", accountSource = "netease", query = "", submittedQuery = "", searchState = "search_hint";
    private List<OnlineTrack> results = List.of();
    private CompletableFuture<List<OnlineTrack>> pending;
    private OnlineMusicLogin login;
    private boolean accountDetail, closed;
    private int remotePage = 1, first, visibleRows;
    private final List<Integer> lyricIndices = new ArrayList<>();
    private long lyricOffset;

    public OnlineMusicViewImpl(OnlineMusicPlayer player) { this.player = Objects.requireNonNull(player); }
    @Override public OnlineMusicPlayer player() { return player; }
    @Override public void layout(PreviewContext context) {
        if (closed) throw new IllegalStateException("Music view closed");
        if (navigation != null) { navigation.close(); navigation = null; }
        host = context; layout = OnlineMusicLayout.of(context.viewport()); queryField = null;
        searchControls.clear(); lyricControls.clear(); accountControls.clear(); detailControls.clear();
        resultRows.clear(); lyricRows.clear(); lyricIndices.clear(); sources.clear(); accounts.clear(); loginSources.clear();
        if (!layout.supported()) return;
        buildNavigation(); buildSearch(); buildLyrics(); buildAccounts(); buildTransport();
        var h = layout.header();
        back = button(new Bounds(h.x(), h.y() + 2, Math.min(88, h.width() / 2), 22), label("view.back"), () -> {
            if (page == Page.ACCOUNTS && accountDetail) { cancelLogin(); accountDetail = false; updateVisibility(); }
            else show(Page.SEARCH);
        }, null);
        back.setLeadingIcon(GraphicIcon.BACK);
        updateRows(); updateVisibility();
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
        queryField.setValue(query); queryField.setHint(label("query")); queryField.setOnChange(value -> query = value);
        queryField.setOnSubmit(value -> { query = value; search(1, true); });
        searchButton = button(new Bounds(h.right() - 24, h.y() + 2, 24, 22), label("search"), () -> {
            if (pending != null) { cancelSearch(); searchState = "cancelled"; updateRows(); }
            else search(1, true);
        }, searchControls);
        searchButton.setIcon(GraphicIcon.SEARCH);
        visibleRows = layout.rows().size(); first = first / visibleRows * visibleRows;
        for (var row : layout.rows()) {
            int slot = resultRows.size();
            int titleWidth = row.width() >= 320 ? row.width() * 54 / 100 : row.width() - 36;
            var button = button(new Bounds(row.x(), row.y(), titleWidth, row.height()), Component.empty(), () -> {
                int i = first + slot; if (i < results.size()) player.play(results.get(i));
            }, searchControls);
            resultRows.add(button);
        }
        var pager = layout.pager();
        previous = button(new Bounds(pager.x(), pager.y(), 24, 20), label("previous"), () -> {
            if (first > 0) { first = Math.max(0, first - visibleRows); updateRows(); }
            else if (remotePage > 1) search(remotePage - 1, false);
        }, searchControls); previous.setIcon(GraphicIcon.PREVIOUS);
        next = button(new Bounds(pager.right() - 24, pager.y(), 24, 20), label("next"), () -> {
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
                accountSource = selected; accountDetail = true;
                if (!player.authenticated(selected)) startLogin();
                updateVisibility(); host.screen().setFocused(back);
            }, accountControls);
            card.setLeadingIcon(GraphicIcon.USER); accounts.put(id, card);
            var tab = button(new Bounds(c.x() + i * (c.width() / 2 + 2), c.y(), (c.width() - 4) / 2, 22), label("source." + id), () -> {
                if (!accountSource.equals(selected)) { cancelLogin(); accountSource = selected; if (!player.authenticated(selected)) startLogin(); }
                updateVisibility();
            }, detailControls);
            loginSources.put(id, tab);
        }
        loginRefresh = button(new Bounds(c.x(), c.bottom() - 44, (c.width() - 4) / 2, 20), label("login.refresh"), this::startLogin, detailControls);
        loginSignOut = button(new Bounds(c.x() + c.width() / 2 + 2, c.bottom() - 44, (c.width() - 4) / 2, 20), label("login.sign_out"), () -> {
            cancelLogin(); player.credentials(accountSource, ""); updateVisibility();
        }, detailControls);
        loginImport = button(new Bounds(c.x(), c.bottom() - 20, c.width(), 20), label("login.import"), () -> {
            try { player.credentials(accountSource, OnlineMusicViewPaint.clipboard().strip()); searchState = "credentials_loaded"; }
            catch (IllegalArgumentException failure) { searchState = "credentials_invalid"; }
            cancelLogin(); show(Page.SEARCH);
        }, detailControls);
        loginImport.setPreview(label("login.import_hint"));
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
        if (page == Page.ACCOUNTS && next != page) { cancelLogin(); accountDetail = false; }
        page = next; updateVisibility();
        if (next == Page.SEARCH) host.screen().setFocused(queryField); else host.screen().setFocused(back);
    }
    private void selectSource(String next) {
        if (!source.equals(next)) {
            cancelSearch(); source = next; results = List.of(); first = 0; remotePage = 1; searchState = "search_hint";
        }
        show(Page.SEARCH); updateRows();
    }
    private void updateVisibility() {
        if (queryField == null) return;
        searchControls.forEach(w -> w.visible = page == Page.SEARCH);
        lyricControls.forEach(w -> w.visible = page == Page.LYRICS);
        accountControls.forEach(w -> w.visible = page == Page.ACCOUNTS && !accountDetail);
        detailControls.forEach(w -> w.visible = page == Page.ACCOUNTS && accountDetail);
        back.visible = page != Page.SEARCH;
        if (navigation != null) navigation.state().select(page.ordinal());
        sources.forEach((id, button) -> {
            if (id.equals("toggle")) button.setMessage(label("source." + source)); else button.setSelected(id.equals(source));
        });
        loginSources.forEach((id, button) -> button.setSelected(id.equals(accountSource)));
    }
    private void search(int pageNumber, boolean fresh) {
        String text = fresh ? query.strip() : submittedQuery;
        if (text.isEmpty()) { searchState = "search_hint"; return; }
        cancelSearch(); if (fresh) submittedQuery = text;
        remotePage = pageNumber; first = 0; results = List.of(); searchState = "searching";
        pending = player.search(source, text, pageNumber); updateRows();
    }
    private void cancelSearch() {
        if (pending != null) { pending.cancel(true); searchState = "cancelled"; pending = null; }
    }
    private void startLogin() { cancelLogin(); login = player.login(accountSource); }
    private void cancelLogin() { if (login != null) { login.close(); login = null; } }
    private void updateRows() {
        if (queryField == null) return;
        var current = player.snapshot().track();
        for (int slot = 0; slot < resultRows.size(); slot++) {
            int i = first + slot; var row = resultRows.get(slot); row.active = i < results.size();
            row.setMessage(row.active ? Component.literal(results.get(i).title()) : Component.empty());
            row.setSelected(row.active && results.get(i).equals(current));
            if (row.active) row.setPreview(label("play_track", results.get(i).title(), results.get(i).artist())
                    .append("\n").append(Component.literal(results.get(i).album())));
        }
        previous.active = pending == null && (first > 0 || remotePage > 1);
        next.active = pending == null && (first + visibleRows < results.size() || results.size() == 20);
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
            text(ui, label("view." + page.name().toLowerCase(Locale.ROOT)), h.x() + back.getWidth() + 8, h.y() + 8, h.width() - back.getWidth() - 8);
            if (page == Page.LYRICS) renderLyrics(ui, current); else renderAccounts(ui);
        }
    }
    private void renderResults(UiFrame ui, OnlineMusicPlayer.Snapshot current) {
        var c = layout.content();
        for (int slot = 0; slot < resultRows.size(); slot++) resultRows.get(slot).visible = first + slot < results.size();
        muted(ui, label("view.song"), c.x() + 6, c.y() + 4, c.width() / 2);
        if (c.width() >= 320) muted(ui, label("view.artist"), c.x() + c.width() * 56 / 100, c.y() + 4, c.width() / 3);
        muted(ui, label("view.duration"), c.right() - 32, c.y() + 4, 32);
        for (int slot = 0; slot < resultRows.size(); slot++) {
            int i = first + slot; if (i >= results.size()) break;
            var track = results.get(i); var row = layout.rows().get(slot);
            resultRows.get(slot).setSelected(track.equals(current.track()));
            if (c.width() >= 320) muted(ui, Component.literal(track.artist()), c.x() + c.width() * 56 / 100, row.y() + 8, c.width() * 44 / 100 - 44);
            muted(ui, Component.literal(time(track.durationMs())), c.right() - 32, row.y() + 8, 32);
        }
        var pager = layout.pager();
        var status = label(searchState, remotePage, results.isEmpty() ? 0 : first + 1, Math.min(first + visibleRows, results.size()));
        text(ui, status, pager.x() + 30, pager.y() + 6, pager.width() - 60);
        previous.setPreview(status.copy().append("\n").append(label("previous")));
        next.setPreview(status.copy().append("\n").append(label("next")));
        if (results.isEmpty()) OnlineMusicViewPaint.paragraph(ui, host.font(), label(searchState),
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
        String state = snapshot == null ? player.authenticated(accountSource) ? "confirmed" : "idle" : snapshot.state().name().toLowerCase(Locale.ROOT);
        var status = label("login." + state);
        loginSignOut.active = player.authenticated(accountSource);
        boolean busy = snapshot != null && (snapshot.state() == OnlineMusicLogin.State.CREATING || snapshot.state() == OnlineMusicLogin.State.WAITING || snapshot.state() == OnlineMusicLogin.State.SCANNED);
        loginRefresh.setMessage(label(busy ? "login.restart" : "login.refresh"));
        var details = status.copy().append("\n").append(label("login.scan." + accountSource)).append("\n").append(label("login.session_only"));
        loginRefresh.setPreview(details); back.setPreview(details);
        boolean wide = c.width() >= 360;
        int imageX = wide ? c.x() + c.width() / 2 + 4 : c.x();
        var imageArea = new Bounds(imageX, c.y() + 42, wide ? c.width() / 2 - 4 : c.width(), Math.max(0, c.height() - 90));
        boolean fitsImage = snapshot == null || snapshot.image() == null ||
                (imageArea.width() >= snapshot.image().width() && imageArea.height() >= snapshot.image().height());
        text(ui, fitsImage ? status : label("login.enlarge"), c.x() + 4, c.y() + 28, c.width() - 8);
        if (wide) OnlineMusicViewPaint.paragraph(ui, host.font(), label("login.scan." + accountSource).append("\n\n").append(label("login.session_only")),
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
    @Override public void suspend() { if (navigation != null) navigation.cancelInteraction(); cancelSearch(); cancelLogin(); accountDetail = false; player.pause(); }
    @Override public void close() { if (!closed) { suspend(); if (navigation != null) navigation.close(); player.close(); closed = true; } }
}
