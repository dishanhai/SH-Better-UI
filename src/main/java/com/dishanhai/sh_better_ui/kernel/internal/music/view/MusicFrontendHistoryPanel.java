// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl;
import com.dishanhai.sh_better_ui.kernel.internal.music.MusicOperationNavigation;
import com.dishanhai.sh_better_ui.kernel.internal.music.SessionMusicOperations;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicFrontendRoutes.Page.HISTORY;

final class MusicFrontendHistoryPanel {
    private static final int ROW_PITCH = 32;
    private static final int ROW_HEIGHT = 30;
    private final MusicFrontendHistory model;
    private final MusicFrontendRoutes routes;
    private final Consumer<MusicFrontendRoutes.Route> navigate;
    private final List<MusicTextControl> rows = new ArrayList<>();
    private final List<MusicFrontendHistory.Key> bindings = new ArrayList<>();
    private PreviewContext host;
    private Bounds body;
    private OnlineMusicScrollRegion scroll;
    private MusicTextControl previous, next;
    private MusicFrontendHistory.Key focus;
    private boolean missing, restoreFocus;
    private int first;

    MusicFrontendHistoryPanel(MusicFrontendHistory model, MusicFrontendRoutes routes,
                              Consumer<MusicFrontendRoutes.Route> navigate) {
        this.model = model; this.routes = routes; this.navigate = navigate;
    }

    boolean available() { return model.available(); }

    /** The notice highlights only its own history row; it cannot open or execute a task. */
    void notification(MusicOperationNavigation.Target target) {
        focus = model.notification(target);
        missing = focus == null;
        restoreFocus = true;
        navigate.accept(MusicFrontendRoutes.Route.of(HISTORY));
    }

    void mount(PreviewContext host, Bounds body, int ink, int muted) {
        this.host = host; this.body = body;
        rows.clear(); bindings.clear();
        int count = Math.min(12, Math.max(0, (body.height() - 38) / ROW_PITCH));
        scroll = host.add(new OnlineMusicScrollRegion(body.x()+4, body.y()+16, body.width()-8, count*ROW_PITCH,
                stock("operations.title"), this::scrollTo));
        scroll.setOnBrowse(() -> host.screen().setFocused(scroll));
        for (int i = 0; i < count; i++) {
            int slot = i;
            var row = host.add(new MusicTextControl(new Bounds(body.x()+4, body.y()+16+i*ROW_PITCH, body.width()-8, ROW_HEIGHT),
                    Component.empty(), host.preferences(), () -> open(slot)));
            row.setVariant(MusicTextControl.Variant.ROW);
            row.setInk(ink, muted);
            rows.add(row); bindings.add(null);
        }
        previous = pager(body.x()+4, "previous", MusicTransportControl.Glyph.PREVIOUS, -1);
        next = pager(body.right()-28, "next", MusicTransportControl.Glyph.NEXT, 1);
        previous.setInk(ink, muted);
        next.setInk(ink, muted);
        restoreFocus = focus != null;
        refresh();
    }

    private MusicTextControl pager(int x, String label, MusicTransportControl.Glyph glyph, int direction) {
        var button = host.add(new MusicTextControl(new Bounds(x,body.bottom()-22,24,20),
                stock(label),host.preferences(),() -> scrollTo(first + direction * rows.size())));
        button.setGlyphOnly(glyph);
        return button;
    }

    void routeChanged() {
        cancelInteraction();
        if (routes.current().page() == HISTORY && routes.current().id().isEmpty() && focus != null)
            restoreFocus = true;
        refresh();
    }

    void cancelInteraction() {
        rows.forEach(MusicTextControl::cancelInteraction);
        if (scroll != null) scroll.cancelInteraction();
    }

    private void scrollTo(int first) {
        this.first = first;
        refresh();
    }

    private void open(int slot) {
        if (routes.current().page() != HISTORY || !routes.current().id().isEmpty()) return;
        var key = bindings.get(slot);
        if (model.lookup(key) == null) {
            missing = true;
            refresh();
            return;
        }
        focus = key;
        missing = false;
        navigate.accept(MusicFrontendHistory.detail(key));
    }

    void refresh() {
        if (scroll == null) return;
        boolean list = routes.current().page() == HISTORY && routes.current().id().isEmpty();
        var entries = list ? model.entries() : List.<MusicFrontendHistory.Entry>of();
        int maximum = Math.max(0, entries.size() - rows.size());
        if (list && restoreFocus && focus != null) {
            int at = 0;
            while (at < entries.size() && !entries.get(at).key().equals(focus)) at++;
            if (at == entries.size() || model.lookup(focus) == null) {
                missing = true; focus = null;
            } else if (at < first || at >= first + rows.size()) first = at;
        }
        if (list) first = Math.min(Math.max(0, first), maximum);
        scroll.visible = list;
        scroll.configure(first, entries.size(), rows.size());
        previous.setVisible(list && maximum > 0); previous.active = first > 0;
        next.setVisible(list && maximum > 0); next.active = first < maximum;
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var entry = i + first < entries.size() ? entries.get(i + first) : null;
            var key = entry == null ? null : entry.key();
            if (!Objects.equals(key, bindings.set(i, key))) {
                row.cancelInteraction();
                if (host.screen().getFocused() == row) host.screen().setFocused(null);
            }
            row.setWidth(body.width()-8-(scroll.active ? 8 : 0));
            row.active = entry != null && switch (entry.snapshot().state()) {
                case RUNNING, SUCCEEDED, FAILED -> true;
                default -> false;
            };
            row.setVisible(entry != null);
            if (entry != null) {
                row.setMessage(title(entry.snapshot()));
                row.setSecondary(state(entry.snapshot()));
                if (restoreFocus && row.active && key.equals(focus)) host.screen().setFocused(row);
            }
        }
        if (list && !rows.isEmpty()) restoreFocus = false;
    }

    void render(UiFrame ui, int color) {
        if (routes.current().page() != HISTORY || body == null) return;
        if (routes.current().id().isEmpty()) {
            line(ui, missing ? stock("operations.expired") : stock(bindings.stream().anyMatch(Objects::nonNull)
                    ? "operations.hint" : "operations.empty"), 2, color);
        } else {
            var item = model.details(MusicFrontendHistory.parse(routes.current()));
            line(ui, item == null ? stock("operations.expired") : title(item.entry().snapshot()), 3, color);
            if (item != null) {
                line(ui, state(item.entry().snapshot()), 19, color);
                if (!item.caption().isBlank()) line(ui, MusicTypography.text(item.caption()), 35, color);
            }
        }
    }

    private void line(UiFrame ui, Component text, int inset, int color) {
        OnlineMusicViewPaint.line(ui, host.font(), text, body.x()+4, body.y()+inset, body.width()-8, color);
    }

    private static Component title(SessionMusicOperations.Snapshot entry) {
        var provider = switch (entry.source()) {
            case "netease", "qq", "listen1" -> stock("source." + entry.source());
            case "apple" -> MusicTypography.text("Apple Music");
            default -> MusicTypography.text(entry.source());
        };
        return stock("operations.kind." + entry.kind().name().toLowerCase(Locale.ROOT)).copy().append(" · ").append(provider);
    }

    private static Component state(SessionMusicOperations.Snapshot entry) {
        var state = switch (entry.error()) {
            case EXPIRED, LOGIN_REQUIRED, UNKNOWN, REJECTED -> entry.error().name().toLowerCase(Locale.ROOT);
            default -> entry.state().name().toLowerCase(Locale.ROOT);
        };
        return stock("operations.state." + state);
    }

    private static Component stock(String key) { return Component.translatable("sh_better_ui.online_music." + key); }
}
