// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicArtworkStage;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicArtworkGrid;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class ArtistDetailPanel implements AutoCloseable {
    private enum Tab { FEATURED, SONGS, ALBUMS, INFO }

    private static final class Entry {
        final ArtistDetailModel model;
        Tab tab;
        int first;
        double offset;
        MusicTextControl returnFocus;

        Entry(ArtistDetailModel model, Tab tab) {
            this.model = model;
            this.tab = tab;
        }
    }

    private final OnlineMusicPlayer player;
    private final ArrayDeque<Entry> history = new ArrayDeque<>();
    private final List<MusicTextControl> rows = new ArrayList<>();
    private final List<MusicTextControl> links = new ArrayList<>();
    private final List<MusicTextControl> tabs = new ArrayList<>();
    private final List<MusicTextControl> releases = new ArrayList<>();
    private final List<MusicTextControl> albumCards = new ArrayList<>();
    private final List<OnlineArtworkTexture> covers = new ArrayList<>();
    private final MusicArtworkStage stage = new MusicArtworkStage();
    private OnlineArtworkTexture portrait;
    private Entry entry;
    private List<?> displayed = List.of();
    private List<FormattedCharSequence> descriptionLines = List.of();
    private String wrappedDescription;
    private MusicTextControl play, retry, credit, more;
    private OnlineMusicScrollRegion scroll;
    private PreviewContext host;
    private Bounds area, hero, cover, list, section;
    private MusicArtworkGrid albumGrid;
    private int textX, tabY, featuredY, capacity, first;
    private int scrollPitch;
    private boolean visible, compact;
    private static final int PITCH = 26;

    ArtistDetailPanel(OnlineMusicPlayer player) { this.player = player; }

    static Component label(String key, Object... args) {
        return MusicTypography.text(Component.translatable("sh_better_ui.online_music." + key, args));
    }

    void build(PreviewContext host, OnlineMusicLayout layout) {
        var compactHeader = layout.sourceBar();
        build(host, layout.content(), new Bounds(compactHeader.x(), compactHeader.y(), compactHeader.width(), 24));
    }

    void build(PreviewContext host, Bounds body) { build(host, body, body); }

    private void build(PreviewContext host, Bounds body, Bounds compactHeader) {
        var previousGrid = albumGrid;
        double offset = scroll == null ? 0 : scroll.viewportOffset();
        this.host = host;
        area = body;
        rows.clear();
        links.clear();
        tabs.clear();
        releases.clear();
        albumCards.clear();
        wrappedDescription = null;
        compact = body.height() < 156;
        boolean separateHeader = compact && compactHeader.y() < body.y();
        int heroHeight = compact ? 24 : Math.min(144, Math.max(69, Math.min(body.width() / 3, body.height() / 3)));
        hero = new Bounds(body.x() + 4, separateHeader ? compactHeader.y() : body.y() + 2,
                body.width() - 8, heroHeight);
        int size = compact ? 0 : heroHeight - 14;
        cover = new Bounds(hero.x() + 7, hero.y() + 7, size, size);
        textX = compact ? hero.x() + 4 : cover.right() + 12;
        int playWidth = compact ? 28 : Math.max(12, Math.min(120, hero.right() - textX - 8));
        play = control(new Bounds(compact ? hero.right() - playWidth : textX,
                hero.bottom() - (compact ? 23 : 26), playWidth, 22), label("detail.play"), () -> play(0), null);
        if (compact) play.setGlyphOnly(MusicTransportControl.Glyph.PLAY);
        else play.setLeadingGlyph(MusicTransportControl.Glyph.PLAY);
        play.setPreview(label("detail.play"));
        credit = control(new Bounds(textX - 4, hero.y() + 23, Math.max(12, hero.right() - textX - 4), 16),
                Component.empty(), () -> followArtist(albumCredit(), credit), null);
        credit.setVariant(MusicTextControl.Variant.LINK);
        tabY = separateHeader ? body.y() : hero.bottom() + 6;
        int tabWidth = Math.min(94, Math.max(12, (area.width() - 8) / Tab.values().length));
        for (var tab : Tab.values()) {
            var button = control(new Bounds(area.x() + 4 + tab.ordinal() * tabWidth, tabY, tabWidth, 21),
                    Component.empty(), () -> select(tab), null);
            button.setVariant(MusicTextControl.Variant.UNDERLINED_TAB);
            tabs.add(button);
        }
        featuredY = tabY + 26;
        int cardWidth = Math.max(50, (area.width() - 18) / 2);
        for (int i = 0; i < 2; i++) {
            int index = i;
            var button = control(new Bounds(area.x() + 4 + i * (cardWidth + 8), featuredY, cardWidth, 34),
                    Component.empty(), () -> openRelease(index, releases.get(index)), null);
            button.setVariant(MusicTextControl.Variant.ROW);
            button.setTextInset(28);
            releases.add(button);
        }
        list = listBounds(featuredY);
        albumGrid = MusicArtworkGrid.fit(list.width(), list.height());
        if (previousGrid != null && previousGrid.columns() != albumGrid.columns()) {
            if (entry != null && entry.tab == Tab.ALBUMS) {
                first = first * previousGrid.columns() / albumGrid.columns();
                offset = first;
            }
            for (var item : history) {
                if (item.tab != Tab.ALBUMS) continue;
                item.first = item.first * previousGrid.columns() / albumGrid.columns();
                item.offset = item.first;
            }
        }
        scroll = host.add(new OnlineMusicScrollRegion(list.x(), list.y(), list.width(), list.height(),
                label("detail.songs"), value -> { first = value; cancelRows(); refresh(); }));
        scroll.setOnBrowse(() -> host.screen().setFocused(scroll));
        scroll.onEnd(this::canExtend, this::extend);
        scrollPitch = 0;
        setScrollPitch(PITCH);
        int albumRows = albumGrid.visibleRows();
        if (MusicScrollRows.spill(host, albumRows) > 0) albumRows += 2;
        for (int i = 0; i < albumRows * albumGrid.columns(); i++) {
            int slot = i;
            albumCards.add(control(new Bounds(list.x(), list.y(), albumGrid.side(), albumGrid.cardHeight()),
                    Component.empty(), () -> openRelease(first * albumGrid.columns() + slot, albumCards.get(slot)),
                    MusicTextControl.Variant.ARTWORK_CARD));
        }
        while (covers.size() > releases.size() + 1 + albumCards.size()) covers.remove(covers.size() - 1).close();
        int maxRows = body.height() / PITCH + 1;
        for (int i = 0; i < maxRows; i++) {
            int slot = i;
            rows.add(control(new Bounds(list.x() + 28, list.y(), 40, PITCH - 2), Component.empty(),
                    () -> activateRow(slot), MusicTextControl.Variant.ROW));
            var link = control(new Bounds(list.x(), list.y(), 40, PITCH - 2), Component.empty(),
                    () -> openCredit(slot), MusicTextControl.Variant.LINK);
            links.add(link);
        }
        retry = control(new Bounds(area.right() - 72, area.bottom() - 18, 68, 18),
                label("artist.retry"), this::retry, null);
        more = control(new Bounds(area.right() - 76, featuredY, 72, 18),
                label("detail.more"), () -> select(Tab.SONGS), null);
        refresh();
        scroll.restoreViewportOffset(offset);
    }

    private Bounds listBounds(int y) {
        return new Bounds(area.x() + 4, y, area.width() - 8, Math.max(0, area.bottom() - y - (compact ? 0 : 18)));
    }

    private void setScrollPitch(int pitch) {
        if (scrollPitch == pitch) return;
        scrollPitch = pitch;
        if (host.screen() instanceof com.dishanhai.sh_better_ui.client.api.UiScreen)
            scroll.pixelRows(pitch, shift -> refresh());
    }

    private MusicTextControl control(Bounds bounds, Component title, Runnable action, MusicTextControl.Variant clipped) {
        var button = new MusicTextControl(bounds, title, host.preferences(), action);
        if (clipped == null) host.add(button);
        else {
            button.setVariant(clipped);
            MusicScrollRows.add(host, button, () -> list);
        }
        button.setVisible(false);
        return button;
    }

    void open(OnlineArtist artist) {
        cancel();
        show(artist);
    }

    void open(OnlineAlbum album) {
        cancel();
        show(album);
    }

    private void show(OnlineArtist artist) {
        var model = new ArtistDetailModel(player);
        model.open(artist);
        setEntry(new Entry(model, player.supportsArtistOverview(artist.source()) && !compact ? Tab.FEATURED : Tab.SONGS));
    }

    private void show(OnlineAlbum album) {
        var model = new ArtistDetailModel(player);
        model.open(album);
        setEntry(new Entry(model, Tab.SONGS));
    }

    private void setEntry(Entry next) {
        entry = next;
        first = next.first;
        visible = true;
        displayed = List.of();
        refresh();
        if (scroll != null) scroll.restoreViewportOffset(next.offset);
    }

    Component heading() {
        if (entry == null) return label("detail.songs");
        var model = entry.model;
        return MusicTypography.text(model.artist != null ? model.artist.name() : release().name());
    }

    private OnlineAlbum release() {
        var model = entry.model;
        return model.release == null ? model.album : model.release.album();
    }

    private void push(MusicTextControl origin) {
        scroll.cancelDrag();
        cancelRows();
        entry.first = first;
        entry.offset = scroll.viewportOffset();
        entry.returnFocus = origin;
        history.push(entry);
        if (history.size() > 16) history.removeLast().model.cancel();
    }

    boolean back() {
        if (history.isEmpty()) return false;
        entry.model.cancel();
        setEntry(history.pop());
        if (entry.returnFocus != null && entry.returnFocus.visible && entry.returnFocus.active
                && host.screen().children().contains(entry.returnFocus))
            host.screen().setFocused(entry.returnFocus);
        else focusTab();
        return true;
    }

    private void focusTab() {
        var tab = tabs.get(entry.tab.ordinal());
        host.screen().setFocused(tab.visible ? tab : play);
    }

    private void followArtist(OnlineArtist artist, MusicTextControl origin) {
        if (artist == null || !player.supportsArtistTracks(artist.source())) return;
        var current = entry.model.artist;
        if (current != null && current.source().equals(artist.source()) && current.id().equals(artist.id())) return;
        push(origin);
        show(artist);
        focusTab();
    }

    private void followAlbum(OnlineAlbum album, MusicTextControl origin) {
        if (album == null || !player.supportsAlbumDetail(album.source())) return;
        push(origin);
        show(album);
        focusTab();
    }

    private void openRelease(int index, MusicTextControl origin) {
        if (visible && index < entry.model.albums.size()) followAlbum(entry.model.albums.get(index), origin);
    }

    private OnlineArtist albumCredit() {
        if (!visible || entry == null || entry.model.release == null) return null;
        return entry.model.release.artists().stream().findFirst().orElse(null);
    }

    private void openCredit(int slot) {
        if (!visible || first + slot >= displayed.size() || !(displayed.get(first + slot) instanceof OnlineTrack track)) return;
        if (entry.model.album == null) followAlbum(track.release(), links.get(slot));
        else if (!track.artists().isEmpty()) followArtist(track.artists().get(0), links.get(slot));
    }

    private void activateRow(int slot) {
        if (!visible || first + slot >= displayed.size()) return;
        play(first + slot);
    }

    private void play(int index) {
        if (!visible) return;
        var model = entry.model;
        try {
            if (model.play(index) && model.status.equals("play_failed"))
                model.status = "ready";
        } catch (RuntimeException failed) { model.status = "play_failed"; }
        refresh();
    }

    private boolean canExtend() {
        if (!visible || entry == null) return false;
        var model = entry.model;
        return switch (entry.tab) {
            case SONGS -> model.more && !model.loading();
            case ALBUMS -> model.overview != null && model.overview.mayHaveMore()
                    && !model.overviewLoading() && !model.overviewFailed && model.albumPage < 1000;
            default -> false;
        };
    }

    private void extend() {
        if (entry.tab == Tab.ALBUMS) entry.model.loadOverview();
        else entry.model.extend();
        refresh();
    }

    private void retry() {
        var model = entry.model;
        if (model.artist != null && (entry.tab == Tab.ALBUMS || entry.tab == Tab.INFO
                || (entry.tab == Tab.FEATURED && !model.status.equals("failed"))))
            model.loadOverview();
        else model.retry();
        refresh();
    }

    private void select(Tab tab) {
        if (!visible || entry.tab == tab) return;
        scroll.cancelDrag();
        entry.tab = tab;
        first = 0;
        cancelRows();
        refresh();
        scroll.restoreViewportOffset(0);
        focusTab();
    }

    void visible(boolean value) {
        if (visible == value) return;
        visible = value;
        if (!value && scroll != null) scroll.cancelDrag();
        refresh();
    }

    void cancel() {
        if (entry != null) entry.model.cancel();
        history.forEach(item -> item.model.cancel());
        history.clear();
        entry = null;
        first = 0;
        visible(false);
        covers.forEach(OnlineArtworkTexture::clear);
        if (portrait != null) portrait.clear();
    }

    private void cancelRows() {
        rows.forEach(MusicTextControl::cancelInteraction);
        links.forEach(MusicTextControl::cancelInteraction);
        albumCards.forEach(MusicTextControl::cancelInteraction);
    }

    void cancelInteraction() {
        if (!visible || play == null) return;
        cancelRows();
        releases.forEach(MusicTextControl::cancelInteraction);
        tabs.forEach(MusicTextControl::cancelInteraction);
        for (var control : List.of(play, credit, retry, more)) control.cancelInteraction();
        scroll.cancelDrag();
    }

    private String description() {
        var model = entry.model;
        if (model.release != null) return model.release.description();
        return model.overview == null ? "" : model.overview.description();
    }

    private void updateDescription() {
        if (entry.tab != Tab.INFO) return;
        String content = description();
        if (Objects.equals(wrappedDescription, content)) return;
        wrappedDescription = content;
        descriptionLines = content.isBlank() ? List.of() : host.font().split(
                OnlineMusicViewPaint.wrapContent(MusicTypography.text(content)), Math.max(1, list.width() - 20));
    }

    private boolean hasOverview() {
        return entry.model.artist != null && player.supportsArtistOverview(entry.model.source());
    }

    private void refresh() {
        if (play == null) return;
        if (!visible || entry == null) {
            for (var control : List.of(play, credit, retry, more)) control.setVisible(false);
            for (var group : List.of(rows, links, tabs, releases, albumCards))
                group.forEach(button -> button.setVisible(false));
            scroll.visible = false;
            return;
        }
        var model = entry.model;
        boolean artist = model.artist != null;
        Tab tab = entry.tab;
        boolean featured = tab == Tab.FEATURED && featuredFits();
        boolean cards = featured && area.height() >= hero.height() + 150 && !model.albums.isEmpty();
        if (!featured && tab == Tab.FEATURED || area.height() < 84 && tab != Tab.SONGS
                || tab == Tab.ALBUMS && albumGrid.visibleRows() == 0)
            entry.tab = tab = Tab.SONGS;
        boolean showTabs = !compact || area.height() >= 84 && (!artist || hasOverview());
        int top = (showTabs ? featuredY : Math.max(area.y(), hero.bottom() + 3)) + (cards ? 39 : 0);
        section = new Bounds(area.x() + 4, top, area.width() - 8, featured ? 20 : 0);
        int headingHeight = (tab == Tab.SONGS || featured) && !compact ? 17 : 0;
        int listY = section.bottom() + headingHeight;
        list = listBounds(listY);
        boolean albumTab = tab == Tab.ALBUMS;
        capacity = albumTab ? albumGrid.visibleRows() : list.height() / PITCH;
        setScrollPitch(albumTab ? albumGrid.pitch() : PITCH);
        scroll.setBounds(list);
        updateDescription();
        List<?> next = switch (tab) {
            case ALBUMS -> model.albums;
            case INFO -> descriptionLines;
            default -> model.tracks;
        };
        if (displayed != next) {
            cancelRows();
            play.cancelInteraction();
            displayed = next;
        }
        int count = albumTab ? albumGrid.rowCount(displayed.size())
                : featured ? Math.min(6, displayed.size()) : displayed.size();
        first = Math.max(0, Math.min(first, Math.max(0, count - capacity)));
        scroll.visible = capacity > 0;
        scroll.configure(first, count, capacity);
        var queue = player.queue();
        scroll.setMessage(label(albumTab ? "detail.albums" : tab == Tab.INFO ? "detail.info" : "detail.songs"));
        play.setVisible(true);
        play.setAvailable(play.visible && !model.tracks.isEmpty() && queue != null && queue.supportsModeReplacement());
        var contributor = albumCredit();
        int creditY = hero.y() + (hero.height() >= 84 ? 30 : 23);
        credit.setY(creditY);
        bind(credit, contributor == null ? "" : contributor.name(), contributor != null && player.supportsArtistTracks(contributor.source()), !compact);
        if (credit.visible) credit.setWidth(Math.max(12, Math.min(hero.right() - textX,
                host.font().width(credit.getMessage()) + 8)));
        int tabX = area.x() + 4;
        for (var item : Tab.values()) {
            var button = tabs.get(item.ordinal());
            boolean available = showTabs && switch (item) {
                case FEATURED -> hasOverview() && featuredFits();
                case SONGS -> true;
                case ALBUMS -> hasOverview() && albumGrid.visibleRows() > 0;
                case INFO -> !artist || hasOverview();
            };
            long total = item == Tab.SONGS ? model.total : item == Tab.ALBUMS && model.overview != null ? model.overview.albumCount() : -1;
            String key = "detail." + item.name().toLowerCase(Locale.ROOT);
            var title = total < 0 ? label(key) : label("detail.tab_count", label(key), total);
            if (!title.equals(button.getMessage())) {
                button.rebind(title);
                button.setPreview(title);
            }
            button.setVisible(available);
            button.setAvailable(available);
            button.setSelected(tab == item);
            if (available) {
                button.setX(tabX);
                tabX += button.getWidth();
            }
        }
        for (int i = 0; i < releases.size(); i++) {
            var button = releases.get(i);
            var release = cards && i < model.albums.size() ? model.albums.get(i) : null;
            bind(button, release == null ? "" : release.name(), release != null, release != null);
            if (release != null) button.setSecondary(MusicTypography.text(release.releaseDate()));
        }
        more.setY(section.y());
        more.setVisible(featured && !model.tracks.isEmpty());
        more.setAvailable(more.visible);
        boolean failed = switch (tab) {
            case FEATURED -> model.overviewFailed || model.status.equals("failed");
            case ALBUMS -> model.overviewFailed;
            case INFO -> artist ? model.overviewFailed : model.status.equals("failed");
            case SONGS -> model.status.equals("failed");
        };
        retry.setVisible(failed);
        retry.setAvailable(failed);
        updateRows(albumTab ? 0 : count, tab);
        updateAlbumCards(albumTab);
    }

    private void updateRows(int count, Tab tab) {
        var current = player.snapshot().track();
        int secondary = secondaryX();
        boolean songRows = tab == Tab.FEATURED || tab == Tab.SONGS;
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var link = links.get(i);
            int y = list.y() + i * PITCH - scroll.rowShift();
            int index = first + i;
            boolean show = index < count && MusicScrollRows.intersects(y, PITCH - 2, list);
            row.setY(y);
            link.setY(y);
            if (show && songRows && displayed.get(index) instanceof OnlineTrack track) {
                row.setWidth(Math.max(12, secondary - row.getX() - 4));
                bind(row, track.title(), play.active, true);
                row.setSelected(current != null && current.source().equals(track.source()) && current.id().equals(track.id()));
                link.setX(secondary);
                link.setWidth(Math.max(12, list.right() - 51 - secondary));
                boolean album = entry.model.album != null;
                var artist = track.artists().isEmpty() ? null : track.artists().get(0);
                var release = track.release();
                bind(link, album ? (artist == null ? track.artist() : artist.name()) : track.album(),
                        album ? artist != null && player.supportsArtistTracks(artist.source())
                                : release != null && player.supportsAlbumDetail(release.source()), true);
            } else {
                row.setVisible(false);
                link.setVisible(false);
            }
        }
    }

    private void updateAlbumCards(boolean albumTab) {
        var albums = entry.model.albums;
        int columns = albumGrid.columns();
        int start = first * columns;
        for (int slot = 0; slot < albumCards.size(); slot++) {
            var card = albumCards.get(slot);
            int index = start + slot;
            int y = list.y() + MusicArtworkGrid.TOP + slot / columns * albumGrid.pitch() - scroll.rowShift();
            if (!albumTab || index >= albums.size() || !MusicScrollRows.intersects(y, albumGrid.cardHeight(), list)) {
                card.setVisible(false);
                continue;
            }
            var album = albums.get(index);
            card.setX(list.x() + slot % columns * (albumGrid.side() + MusicArtworkGrid.GAP));
            card.setY(y);
            bind(card, album.name(), player.supportsAlbumDetail(album.source()), true);
            card.setSecondary(album.releaseDate().isBlank() ? Component.empty() : MusicTypography.text(album.releaseDate()));
        }
    }

    private static void bind(MusicTextControl control, String name, boolean enabled, boolean show) {
        boolean visible = show && !name.isBlank();
        control.setVisible(visible);
        control.setAvailable(visible && enabled);
        if (!visible) return;
        var label = MusicTypography.text(name);
        if (!control.getMessage().equals(label)) {
            control.rebind(label);
            control.setPreview(label);
        }
    }

    private int secondaryX() { return list.x() + list.width() * 56 / 100; }

    private boolean featuredFits() { return area != null && !compact && area.height() >= hero.height() + 112; }

    void render(UiFrame ui) {
        if (!visible || entry == null) return;
        var model = entry.model;
        model.poll();
        refresh();
        scroll.tick();
        var theme = UiTheme.current();
        int muted = theme.secondaryText(theme.text(), .25f);
        paintHero(ui, theme, muted);
        if (entry.tab == Tab.FEATURED && !compact) {
            for (int i = 0; i < releases.size(); i++) {
                var button = releases.get(i);
                if (!button.visible) continue;
                var card = new Bounds(button.getX(), button.getY(), button.getWidth(), button.getHeight());
                OnlineMusicViewPaint.panel(ui, card, theme.text() & 0x00ffffff | 0x10000000, ui.preferences().highContrast());
                drawCover(ui, new Bounds(card.x() + 4, card.y() + 3, 28, 28), model.albums.get(i).artworkUrl(), i + 1);
            }
            line(ui, label("detail.featured_songs"), section.x() + 4, section.y() + 4, section.width() - 80, theme.text());
        }
        if (!compact && (entry.tab == Tab.SONGS || entry.tab == Tab.FEATURED)) {
            line(ui, label("view.song"), list.x() + 38, list.y() - 13, secondaryX() - list.x() - 40, muted);
            line(ui, label(model.album == null ? "detail.album" : "detail.artist"), secondaryX() + 4,
                    list.y() - 13, list.right() - secondaryX() - 44, muted);
            line(ui, label("detail.duration"), list.right() - 46, list.y() - 13, 38, muted);
        }
        OnlineMusicViewPaint.clip(ui, list);
        try {
            if (entry.tab == Tab.ALBUMS) paintAlbumCards(ui);
            else paintRows(ui, theme, muted);
        }
        finally { OnlineMusicViewPaint.unclip(ui); }
        String status = status();
        if (status != null) {
            int y = compact && displayed.isEmpty() ? list.y() + 2 : area.bottom() - 14;
            line(ui, label(status), area.x() + 8, y, area.width() - (retry.visible ? 84 : 16), muted);
        }
    }

    private String status() {
        var model = entry.model;
        if (model.status.equals("unavailable")) return "detail.unavailable";
        if (retry.visible) return "detail.failed";
        if (model.status.equals("play_failed")) return "artist.play_failed";
        if (entry.tab == Tab.ALBUMS || entry.tab == Tab.INFO && model.artist != null) {
            if (model.overviewLoading()) return "detail.loading";
            if (displayed.isEmpty()) return "detail.empty";
            return null;
        }
        if (entry.tab == Tab.INFO && model.release != null) return displayed.isEmpty() ? "detail.empty" : null;
        return switch (model.status) {
            case "loading", "empty" -> "detail." + model.status;
            default -> null;
        };
    }

    private void paintHero(UiFrame ui, UiTheme theme, int muted) {
        var model = entry.model;
        var release = release();
        String image = model.artist == null ? release.artworkUrl() : model.overview == null ? "" : model.overview.artworkUrl();
        if (!compact) {
            var texture = model.artist == null ? texture(image, 0) : portrait(image);
            var palette = texture == null ? null : texture.palette();
            int tint = palette == null ? theme.accent() : palette.topLeft();
            int background = ui.preferences().highContrast() ? theme.background() | 0xff000000
                    : GuiMotion.lerpColor(theme.background(), tint, .22f) & 0x00ffffff | 0xb8000000;
            OnlineMusicViewPaint.panel(ui, hero, background, ui.preferences().highContrast());
            paintCover(ui, cover, texture, model.artist == null ? .06f : .5f);
        }
        if (play.active) {
            int color = ui.preferences().highContrast() ? theme.text() : theme.accent();
            RoundedCorners.draw(ui.graphics(), play.getX(), play.getY(), play.getWidth(), play.getHeight(),
                    play.getHeight() / 2f, ui.preferences().highContrast() ? 1 : 0, color, color & 0xffffff | 0x36000000);
        }
        int width = hero.right() - textX - (compact ? 34 : 8);
        OnlineMusicViewPaint.scaledLine(ui, host.font(), MusicTypography.bold(heading()), textX,
                hero.y() + (compact ? 5 : 9), width, theme.text(), compact ? 1f : hero.height() >= 112 ? 2.4f : hero.height() >= 84 ? 2f : 1.45f);
        if (compact) return;
        Component subtitle = model.artist == null
                ? MusicTypography.text(release.releaseDate())
                    : model.overview != null && model.overview.followers() > 0
                        ? label("detail.followers", java.text.NumberFormat.getIntegerInstance().format(model.overview.followers()))
                        : label("source." + model.source());
        if (credit.visible && hero.height() < 84) {
            int afterCredit = credit.getX() + credit.getWidth() + 8;
            if (afterCredit < hero.right() - 8)
                line(ui, subtitle, afterCredit, credit.getY() + 4, hero.right() - afterCredit - 8, muted);
        } else {
            line(ui, subtitle, textX, hero.y() + (credit.visible ? 49 : hero.height() >= 112 ? 39 : hero.height() >= 84 ? 33 : 24), width, muted);
        }
    }

    private void paintAlbumCards(UiFrame ui) {
        int firstAlbum = first * albumGrid.columns();
        for (int slot = 0; slot < albumCards.size(); slot++) {
            var card = albumCards.get(slot);
            if (!card.visible) continue;
            int index = firstAlbum + slot;
            var album = entry.model.albums.get(index);
            var box = new Bounds(card.getX(), card.getY(), card.getWidth(), card.getWidth());
            if (MusicScrollRows.intersects(box.y(), box.height(), list))
                drawCover(ui, box, album.artworkUrl(), 1 + releases.size() + index % albumCards.size());
        }
    }

    private void paintRows(UiFrame ui, UiTheme theme, int muted) {
        int count = entry.tab == Tab.FEATURED ? Math.min(6, displayed.size()) : displayed.size();
        for (int i = 0; i < rows.size() && first + i < count; i++) {
            int index = first + i;
            int top = list.y() + i * PITCH - scroll.rowShift();
            int y = top + 7;
            if (displayed.get(index) instanceof FormattedCharSequence text) {
                OnlineMusicViewPaint.ink(ui, host.font(), text, list.x() + 6, y, theme.text());
                continue;
            }
            var row = rows.get(i);
            if (!row.visible) continue;
            var link = links.get(i);
            if (row.isHoveredOrFocused() || link.isHoveredOrFocused())
                OnlineMusicViewPaint.rule(ui, new Bounds(list.x(), top, list.width() - 9, PITCH - 2),
                        theme.text() & 0x00ffffff | 0x10000000);
            line(ui, MusicTypography.text(Integer.toString(index + 1)), list.x() + 4, y, 24, muted);
            if (displayed.get(index) instanceof OnlineTrack track) {
                long seconds = track.durationMs() / 1000;
                line(ui, MusicTypography.text(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)),
                        list.right() - 46, y, 36, muted);
            }
        }
    }

    private void line(UiFrame ui, Component content, int x, int y, int width, int color) {
        OnlineMusicViewPaint.line(ui, host.font(), content, x, y, Math.max(1, width), color);
    }

    private OnlineArtworkTexture.Texture texture(String url, int slot) {
        while (covers.size() <= slot) covers.add(new OnlineArtworkTexture(.06f));
        return covers.get(slot).poll(url).image();
    }

    private OnlineArtworkTexture.Texture portrait(String url) {
        if (portrait == null) portrait = new OnlineArtworkTexture(.5f);
        return portrait.poll(url).image();
    }

    private void drawCover(UiFrame ui, Bounds box, String url, int slot) { paintCover(ui, box, texture(url, slot), .06f); }

    private void paintCover(UiFrame ui, Bounds box, OnlineArtworkTexture.Texture texture, float cornerFraction) {
        if (texture != null) {
            stage.renderCover(ui, box, texture.id(), texture.width(), texture.height());
            return;
        }
        var theme = UiTheme.current();
        RoundedCorners.draw(ui.graphics(), box.x(), box.y(), box.width(), box.height(), box.width() * cornerFraction,
                ui.preferences().highContrast() ? 1 : 0, theme.text(), theme.surface());
        int size = Math.min(24, box.width() / 2);
        var icon = new Bounds(box.x() + (box.width() - size) / 2, box.y() + (box.height() - size) / 2, size, size);
        MusicControlRenderer.icon(ui.graphics(), icon, cornerFraction == .5f ? MusicControlRenderer.Icon.USER
                : MusicControlRenderer.Icon.LIBRARY, false, true, theme.secondaryText(theme.text(), .25f), ui.preferences());
    }

    @Override public void close() {
        cancel();
        covers.forEach(OnlineArtworkTexture::close);
        covers.clear();
        if (portrait != null) portrait.close();
        stage.close();
    }
}
