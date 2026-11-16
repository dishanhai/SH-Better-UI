// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.*;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Online playback demo uses only public UUK session APIs and existing themed controls. */
public final class OnlineMusicGalleryPreview implements PreviewScene {
    public static final int HEIGHT = 270;
    private static final String TEXT = "sh_better_ui.online_music.";
    private OnlineMusicPlayer player;
    private PreviewContext host;
    private UiTextField queryField;
    private UiButton sourceButton, searchButton, previous, next, playback, stop, accountButton;
    private UiButton loginBack, loginRefresh, loginImport, loginSignOut;
    private OnlineMusicLogin login;
    private boolean loginPanel;
    private UiSlider seek;
    private final UiButton[] rows = new UiButton[4];
    private List<OnlineTrack> results = List.of();
    private CompletableFuture<List<OnlineTrack>> pending;
    private String source = "netease", query = "", submittedQuery = "", searchState = "search_hint";
    private int page = 1, first, visibleRows = 4;
    private Bounds area;

    @Override public void layout(PreviewContext context) {
        host = context; area = context.viewport(); queryField = null;
        java.util.Arrays.fill(rows, null);
        if (area.width() < 208 || area.height() < 174) return;
        visibleRows = Math.max(1, Math.min(rows.length, (area.height() - 148) / 21));
        first = first / visibleRows * visibleRows;
        if (player == null) player = UnifiedUI.get().require(OnlineMusicService.KEY).open();
        int x = area.x() + 4, width = area.width() - 8, y = area.y() + 4;
        sourceButton = button(x, y, 78, label("source." + source), () -> {
            source = source.equals("netease") ? "qq" : "netease";
            sourceButton.setMessage(label("source." + source));
            cancelSearch(); results = List.of(); first = 0; page = 1; searchState = "search_hint"; updateRows();
        });
        sourceButton.setPreview(label("guest_hint"));
        queryField = host.add(new UiTextField(host.font(), new Bounds(x + 82, y, width - 82, 18), label("query"), 200, host.preferences()));
        queryField.setValue(query); queryField.setHint(label("query"));
        queryField.setOnChange(value -> query = value);
        queryField.setOnSubmit(value -> { query = value; search(1, true); });
        y += 22;
        int slot = width / 4;
        searchButton = button(x, y, slot - 3, label("search"), () -> {
            if (pending != null) { cancelSearch(); searchState = "cancelled"; }
            else search(1, true);
        });
        previous = button(x + slot, y, slot - 3, label("previous"), () -> {
            if (first > 0) { first = Math.max(0, first - visibleRows); updateRows(); }
            else if (page > 1) search(page - 1, false);
        });
        next = button(x + 2 * slot, y, slot - 3, label("next"), () -> {
            if (first + visibleRows < results.size()) { first += visibleRows; updateRows(); }
            else if (results.size() == 20) search(page + 1, false);
        });
        accountButton = button(x + 3 * slot, y, width - 3 * slot, label("credentials"), () -> {
            cancelSearch(); player.pause(); loginPanel = true;
            if (!player.authenticated(source)) startLogin();
            updateVisibility();
            host.screen().setFocused(loginBack);
        });
        accountButton.setPreview(label("credentials_hint"));
        y += 34;
        for (int i = 0; i < visibleRows; i++) {
            final int row = i;
            rows[i] = button(x, y + i * 21, width, Component.empty(), () -> {
                int index = first + row;
                if (index < results.size()) player.play(results.get(index));
            });
        }
        y += visibleRows * 21 + 18;
        playback = button(x, y, width / 2 - 2, label("play"), () -> {
            if (player.snapshot().state() == OnlineMusicPlayer.State.PLAYING) player.pause();
            else player.resume();
        });
        stop = button(x + width / 2 + 2, y, width - width / 2 - 2, label("stop"), player::stop);
        stop.setPreview(label("stop_hint"));
        y += 23;
        seek = host.add(new UiSlider(new Bounds(x, y, width, 18), 0, 1000, 1, 0, host.preferences(),
                value -> label("seek", value / 10), ignored -> {}));
        seek.setOnCommit(value -> player.seek(player.snapshot().durationMs() * value / 1000), ignored -> {});
        int footer = area.bottom() - 22;
        loginBack = button(x, footer, width / 2 - 2, label("login.back"), this::leaveLogin);
        loginRefresh = button(x + width / 2 + 2, footer, width - width / 2 - 2, label("login.refresh"), this::startLogin);
        loginImport = button(x, footer - 22, width / 2 - 2, label("login.import"), () -> {
            try {
                player.credentials(source, OnlineMusicPaint.clipboard().strip());
                leaveLogin(); searchState = "credentials_loaded";
            } catch (IllegalArgumentException failure) { leaveLogin(); searchState = "credentials_invalid"; }
        });
        loginImport.setPreview(label("login.import_hint"));
        loginSignOut = button(x + width / 2 + 2, footer - 22, width - width / 2 - 2, label("login.sign_out"), () -> {
            player.credentials(source, ""); leaveLogin(); searchState = "login.signed_out";
        });
        updateRows();
        updateVisibility();
    }
    private void startLogin() {
        if (login != null) login.close();
        login = player.login(source);
    }
    private void leaveLogin() {
        if (login != null) { login.close(); login = null; }
        loginPanel = false; updateVisibility(); host.screen().setFocused(accountButton);
    }
    private void updateVisibility() {
        for (var widget : List.of(sourceButton, queryField, searchButton, previous, next, accountButton, playback, stop, seek))
            widget.visible = !loginPanel;
        for (var row : rows) if (row != null) row.visible = !loginPanel;
        for (var widget : List.of(loginBack, loginRefresh, loginImport, loginSignOut)) widget.visible = loginPanel;
    }
    private UiButton button(int x, int y, int width, Component text, Runnable action) {
        return host.add(new UiButton(x, y, width, 18, text, ignored -> action.run(), host.preferences(), false));
    }
    private void search(int targetPage, boolean fresh) {
        String text = fresh ? query.strip() : submittedQuery;
        if (text.isEmpty()) { searchState = "search_hint"; return; }
        cancelSearch();
        if (fresh) submittedQuery = text;
        page = targetPage; first = 0; results = List.of(); searchState = "searching";
        pending = player.search(source, text, page);
        updateRows();
    }
    private void cancelSearch() {
        if (pending != null) { pending.cancel(true); searchState = "cancelled"; }
        pending = null;
    }
    private void updateRows() {
        if (queryField == null) return;
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == null) continue;
            int index = first + i;
            boolean present = index < results.size();
            rows[i].active = present;
            rows[i].setMessage(present ? label("track", results.get(index).title(), results.get(index).artist()) : Component.empty());
            if (present) rows[i].setPreview(label("play_track", results.get(index).title(), results.get(index).artist()));
        }
        previous.active = pending == null && (first > 0 || page > 1);
        next.active = pending == null && (first + visibleRows < results.size() || results.size() == 20);
        searchButton.setMessage(label(pending == null ? "search" : "cancel"));
    }
    @Override public void render(UiFrame ui) {
        if (queryField == null) {
            text(ui, label("space"), area.x() + 4, area.y() + 4, Math.max(1, area.width() - 8));
            return;
        }
        if (loginPanel) { renderLogin(ui); return; }
        if (pending != null && pending.isDone()) {
            try { results = pending.join(); searchState = results.isEmpty() ? "empty" : "results"; }
            catch (RuntimeException failure) { searchState = "search_failed"; }
            pending = null; updateRows();
        }
        var current = player.snapshot();
        var state = current.state();
        boolean playing = state == OnlineMusicPlayer.State.PLAYING;
        playback.active = playing || state == OnlineMusicPlayer.State.PAUSED || state == OnlineMusicPlayer.State.ENDED;
        playback.setMessage(label(playing ? "pause" : "play"));
        stop.active = state != OnlineMusicPlayer.State.IDLE && state != OnlineMusicPlayer.State.CLOSED;
        stop.setMessage(label(state == OnlineMusicPlayer.State.LOADING ? "cancel" : "stop"));
        seek.active = current.durationMs() > 0 && playback.active;
        if (!OnlineMusicPaint.editing(seek)) seek.setValue(current.durationMs() <= 0 ? 0 : (int) (1000 * current.positionMs() / current.durationMs()));
        int x = area.x() + 5, width = area.width() - 10;
        text(ui, label(searchState, page, results.isEmpty() ? 0 : first + 1, Math.min(first + visibleRows, results.size())), x, area.y() + 49, width);
        Component status = state == OnlineMusicPlayer.State.FAILED
                ? label("failure." + current.failure().name().toLowerCase(Locale.ROOT))
                : label("state." + state.name().toLowerCase(Locale.ROOT));
        if (current.preview()) status = label("preview").append(" · ").append(status);
        text(ui, status, x, playback.getY() - 12, width);
        Component details = current.track() == null ? status
                : label("track", current.track().title(), current.track().artist()).append("\n").append(status);
        playback.setPreview(details); stop.setPreview(details);
        long position = current.positionMs();
        // The feed applies a platform preview's absolute lyric offset without inventing word timing.
        var feed = player.lyricFeed().frame();
        if (feed.alive()) position = feed.snapshot().positionMs();
        var timeline = current.lyrics();
        int index = timeline.indexAt(position);
        int y = seek.getY() + 24;
        if (current.track() != null && y + 39 <= area.bottom())
            text(ui, label("track", current.track().title(), current.track().artist()), x, y + 27, width);
        if (current.lyricsState() == OnlineMusicPlayer.LyricsState.READY && index >= 0) {
            var line = timeline.line(index);
            var text = Component.empty();
            for (var word : line.words()) text.append(Component.literal(word.text()).withStyle(style ->
                    style.withColor(word.startMs() <= feed.snapshot().positionMs()
                            ? UiTheme.current().accent() : UiTheme.current().text())));
            text(ui, text, x, y, width);
            text(ui, Component.literal(line.translation()), x, y + 13, width);
        } else text(ui, label("lyrics." + current.lyricsState().name().toLowerCase(Locale.ROOT)), x, y, width);
    }
    private void renderLogin(UiFrame ui) {
        int x = area.x() + 5, width = area.width() - 10;
        var current = login == null ? null : login.snapshot();
        String state = current == null ? "confirmed" : current.state().name().toLowerCase(Locale.ROOT);
        text(ui, label("login.title", label("source." + source)), x, area.y() + 5, width);
        var status = label("login." + state);
        text(ui, status, x, area.y() + 18, width);
        text(ui, label("login.scan." + source), x, area.y() + 31, width);
        boolean busy = current != null && (current.state() == OnlineMusicLogin.State.CREATING
                || current.state() == OnlineMusicLogin.State.WAITING || current.state() == OnlineMusicLogin.State.SCANNED);
        loginRefresh.setMessage(label(busy ? "login.restart" : "login.refresh"));
        var details = status.copy().append("\n").append(label("login.scan." + source)).append("\n").append(label("login.session_only"));
        loginRefresh.setPreview(details);
        loginBack.setPreview(details);
        loginSignOut.active = player.authenticated(source);
        if (current != null && current.image() != null) {
            var image = current.image();
            int available = Math.min(width, loginImport.getY() - area.y() - 48);
            int scale = Math.min(3, available / Math.max(image.width(), image.height()));
            if (scale < 1) text(ui, label("login.enlarge"), x, area.y() + 50, width);
            else {
                int left = area.x() + (area.width() - image.width() * scale) / 2, top = area.y() + 44;
                // Integer scaling and a white quiet zone preserve scan readability in every theme.
                ui.graphics().fill(left, top, left + image.width() * scale, top + image.height() * scale, 0xffffffff);
                for (int y = 0; y < image.height(); y++) {
                    for (int start = 0; start < image.width();) {
                        if (!image.dark(start, y)) { start++; continue; }
                        int end = start + 1;
                        while (end < image.width() && image.dark(end, y)) end++;
                        ui.graphics().fill(left + start * scale, top + y * scale, left + end * scale, top + (y + 1) * scale, 0xff000000);
                        start = end;
                    }
                }
            }
        }
    }
    private void text(UiFrame ui, Component text, int x, int y, int width) {
        OnlineMusicPaint.line(ui, host.font(), text, x, y, width);
    }
    @Override public void suspend() {
        cancelSearch();
        if (login != null) { login.close(); login = null; }
        loginPanel = false;
        if (player != null) player.pause();
    }
    @Override public void close() {
        cancelSearch();
        if (login != null) { login.close(); login = null; }
        loginPanel = false;
        if (player != null) { player.close(); player = null; }
    }
    private static net.minecraft.network.chat.MutableComponent label(String key, Object... arguments) {
        return Component.translatable(TEXT + key, arguments);
    }
}
