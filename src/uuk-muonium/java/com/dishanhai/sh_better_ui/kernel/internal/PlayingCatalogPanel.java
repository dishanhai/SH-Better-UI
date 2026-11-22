// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicArtworkGrid;
import com.dishanhai.sh_better_ui.kernel.internal.music.OnlineArtwork;
import com.dishanhai.sh_better_ui.kernel.internal.music.OnlineMusicCatalog;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.ncm.music.dto.PlayList;
import com.muoniumplayer.core.rendering.font.CFontRenderer;
import com.muoniumplayer.core.rendering.rendersystem.RenderSystem;
import com.muoniumplayer.core.rendering.texture.Textures;
import com.muoniumplayer.core.rendering.ui.container.Panel;
import com.muoniumplayer.core.rendering.ui.container.ScrollPanel;
import com.muoniumplayer.core.rendering.ui.widgets.LabelWidget;
import com.muoniumplayer.core.rendering.ui.widgets.LabelWidget.WidthLimitType;
import com.muoniumplayer.core.rendering.ui.widgets.RoundedButtonWidget;
import com.muoniumplayer.core.rendering.ui.widgets.RoundedImageWidget;
import com.muoniumplayer.core.screens.ncm.NCMScreen;
import com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType;
import com.muoniumplayer.core.screens.ncm.SheetClock;
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import com.muoniumplayer.core.screens.ncm.panels.PlaylistPanel;
import com.muoniumplayer.core.utils.Location;
import com.muoniumplayer.core.utils.other.multithreading.MultiThreadingUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.kernel.internal.PlayingTrackPages.text;
import static com.dishanhai.sh_better_ui.kernel.internal.PlayingCatalogPresentation.INSET;

/** Embedded player catalogue header and tabs. PlaylistPanel owns the song rows and playback gestures. */
final class PlayingCatalogPanel extends PlaylistPanel {
    private enum Tab { FEATURED, SONGS, ALBUMS, INFO }
    private final boolean artistPage;
    private final String metadata;
    private final String description;
    private final ArrayList<OnlineAlbum> albums;
    private final List<OnlineArtist> credits;
    private final Map<String, OnlineTrack> providerTracks = new LinkedHashMap<>();
    private final OnlineArtist artist;
    private final SheetClock pageEntrance = SheetClock.cascade();
    private final SheetClock tabEntrance = new SheetClock();
    private Tab tab;
    private Panel header;
    private float bodyAlpha = 1f;
    private double bodyOffset;
    private boolean enteringPage = true, enteringTab;
    private ScrollPanel albumList, infoList;
    private MusicArtworkGrid albumGrid;
    private int descriptionWidth;
    private int page = 1;
    private boolean more, loadingMore, loadFailed;
    private int albumPage = 1;
    private boolean moreAlbums, loadingAlbums, albumsFailed;

    PlayingCatalogPanel(PlayList playlist, boolean artistPage, String metadata, String description,
                        List<OnlineAlbum> albums, List<OnlineArtist> credits, List<OnlineTrack> tracks,
                        OnlineArtist artist, boolean more, boolean moreAlbums) {
        super(playlist, true, !tracks.isEmpty());
        this.artistPage = artistPage;
        this.metadata = metadata;
        this.description = description;
        this.albums = new ArrayList<>(albums);
        this.credits = credits;
        this.artist = artist;
        this.more = more;
        this.moreAlbums = moreAlbums;
        tracks.forEach(track -> providerTracks.put(track.id(), track));
        tab = albums.isEmpty() ? Tab.SONGS : Tab.FEATURED;
    }

    OnlineTrack catalogTrack(Music track) {
        var result = providerTracks.get(track.isQQ() ? track.getSourceMid() : track.getSourceId());
        return result != null && (track.isQQ() ? result.source().equals("qq")
                : track.isNetease() && result.source().equals("netease")) ? result : null;
    }

    @Override
    protected DoubleSupplier catalogHeader() {
        header = new Panel().setClickable(false);
        addChild(header);
        var cover = cover(playList.getCoverUrl());
        if (cover != null) {
            header.addChild(cover);
            cover.setBeforeRenderCallback(() -> {
                float reveal = layer(0, .52);
                double size = coverSize();
                double side = size * (.86 + .14 * reveal);
                double inset = (size - side) / 2;
                cover.setBounds(INSET + inset, 12 + inset + 9 * (1 - reveal), side, side);
                cover.setRadius(artistPage ? side / 2 : 6);
                cover.setAlpha(reveal);
            });
        }
        var title = label(playList.getName(), FontManager.pf32);
        header.addChild(title);
        title.setBeforeRenderCallback(() -> {
            float reveal = layer(.08, .64);
            title.setFont(coverSize() < 68 ? FontManager.pf24bold : FontManager.pf32);
            title.setPosition(textX() + 10 * (1 - reveal), 14 + 8 * (1 - reveal));
            title.setMaxWidth(Math.max(0, getWidth() - textX() - 50));
            title.setAlpha(reveal);
        });
        if (!credits.isEmpty()) {
            String names = String.join(", ", credits.stream().map(OnlineArtist::name).toList());
            var link = textLink(names, credits.size() > 1 ? text("artist.choose") : text("artist.open", names));
            link.setOnClickCallback((x, y, button) -> {
                if (button != 0) return false;
                PlayingTrackPages.openArtists(NCMScreen.getInstance(), credits,
                        new Box(link.getX(), link.getY(), link.getWidth(), link.getHeight()));
                return true;
            });
            header.addChild(link);
            link.setBeforeRenderCallback(() -> {
                float reveal = layer(.15, .70);
                link.setPosition(textX(), (coverSize() < 68 ? 36 : 46) + 7 * (1 - reveal));
                link.setMaxWidth(Math.max(0, getWidth() - textX() - 50));
                link.setColor(getColor(link.isHovering() ? ColorType.ACCENT : ColorType.PRIMARY_TEXT));
                link.setAlpha(reveal);
                link.setClickable(reveal >= .65f);
            });
        }
        var detail = label(metadata, FontManager.pf14);
        header.addChild(detail);
        detail.setBeforeRenderCallback(() -> {
            float reveal = layer(.18, .73);
            detail.setPosition(textX(), (coverSize() < 68 ? 36 : 46) + (credits.isEmpty() ? 0 : 15) + 7 * (1 - reveal));
            detail.setMaxWidth(Math.max(0, getWidth() - textX() - 18));
            detail.setColor(getColor(ColorType.SECONDARY_TEXT));
            detail.setAlpha(reveal);
        });
        if (!playList.getMusics().isEmpty()) {
            var play = button(() -> text("detail.play"), () -> {
                CloudMusic.currentPlaylistContext = null;
                CloudMusic.play(playList.getMusics(), 0);
            });
            header.addChild(play);
            play.setBeforeRenderCallback(() -> {
                float reveal = layer(.23, .77);
                play.setBounds(textX(), Math.max(58 + (credits.isEmpty() ? 0 : 11), coverSize() - 10) + 8 * (1 - reveal), 86, 19);
                play.setAlpha(reveal);
                play.setClickable(reveal >= .65f);
                paintButton(play, true);
            });
        }

        var tabs = new ArrayList<Tab>();
        if (!albums.isEmpty()) tabs.add(Tab.FEATURED);
        tabs.add(Tab.SONGS);
        if (artistPage && !albums.isEmpty()) tabs.add(Tab.ALBUMS);
        if (!description.isBlank()) tabs.add(Tab.INFO);
        for (int index = 0; index < tabs.size(); index++) {
            var target = tabs.get(index);
            int slot = index;
            var item = new RoundedButtonWidget(() -> tabLabel(target), FontManager.pf14bold) {
                @Override protected void afterRenderChildren(double mouseX, double mouseY) {
                    if (tab == target && getAlpha() > 0f) {
                        double width = Math.min(22, getWidth() - 4);
                        roundedRect(getX() + (getWidth() - width) / 2, getY() + getHeight() - 3, width, 2, 1,
                                RenderSystem.reAlpha(NCMScreen.getColor(ColorType.ACCENT), getAlpha()));
                    }
                }
            };
            item.setOnClickCallback((x, y, mouseButton) -> {
                if (mouseButton != 0) return false;
                select(target);
                return true;
            });
            addChild(item);
            item.setBeforeRenderCallback(() -> {
                float reveal = layer(.25 + slot * .045, .73 + slot * .045);
                double width = Math.min(86, Math.max(36, (getWidth() - INSET * 2 - 4 * (tabs.size() - 1)) / tabs.size()));
                item.setBounds(INSET + slot * (width + 4), tabsY() + 8 * (1 - reveal), width, 20);
                item.setAlpha(reveal);
                item.setClickable(reveal >= .65f);
                item.setFillAlpha(item.isHovering() ? .6f : 0f);
                paintButton(item, false);
                item.setTextColor(getColor(tab == target ? ColorType.ACCENT : ColorType.PRIMARY_TEXT));
            });
        }
        for (int i = 0; i < Math.min(2, albums.size()); i++) {
            var release = albums.get(i);
            int slot = i;
            var card = releaseCard(release, false);
            addChild(card);
            card.setBeforeRenderCallback(() -> {
                float reveal = Math.min(bodyAlpha, layer(.36 + slot * .08, .86 + slot * .08));
                double width = Math.max(0, (getWidth() - INSET * 2 - 8) / 2);
                card.setBounds(INSET + slot * (width + 8), bodyY() + 14 * (1 - reveal), width, 38);
                paintButton(card, false);
                card.setClickable(featuredCards() && reveal >= .65f);
                card.setAlpha(featuredCards() ? reveal : 0f);
            });
        }
        var heading = label("", FontManager.pf16bold);
        heading.setLabel(() -> tab == Tab.FEATURED ? text("detail.featured_songs") : text("view.song"));
        addChild(heading);
        heading.setBeforeRenderCallback(() -> {
            heading.setPosition(INSET, songHeadingY() + bodyOffset + 3);
            heading.setMaxWidth(Math.max(0, getWidth() - INSET * 2 - (more ? 112 : 0)));
            heading.setAlpha(showsSongs() ? bodyAlpha : 0f);
        });
        if (artist != null) mountMoreButton();
        if (!albums.isEmpty()) {
            albumList = new ScrollPanel();
            albumList.setSpacing(MusicArtworkGrid.GAP).enableScrollbarDrag()
                    .setAlignment(ScrollPanel.Alignment.VERTICAL_WITH_HORIZONTAL_FILL).setIndexedVerticalLayout();
            addChild(albumList);
            for (var release : albums) albumList.addChild(releaseCard(release, true));
        }
        if (!description.isBlank()) {
            descriptionWidth = 0;
            infoList = mountList(this::bodyY);
        }
        return this::songsY;
    }

    @Override public void onInit() {
        super.onInit();
        restartEntrance();
        updateTabVisibility();
    }

    @Override public void onRender(double mouseX, double mouseY) {
        updateEntrance();
        if (enteringPage && showsSongs()) {
            PlayingCatalogPresentation.skeleton(new Box(getX(), getY(), getWidth(), getHeight()), artistPage,
                    !credits.isEmpty(), featuredCards(), System.nanoTime() / 1_000_000L, UiPreferences.configured(),
                    getAlpha() * .8f * (1 - layer(.10, .52)));
        }
        if (tab == Tab.ALBUMS && albumList != null) layoutAlbumGrid();
        if (tab == Tab.INFO && infoList != null) {
            int width = Math.max(1, (int) (getWidth() - INSET * 2 - 12));
            if (descriptionWidth != width && FontManager.pf16 != null) {
                descriptionWidth = width;
                infoList.getChildren().clear();
                for (String line : description.split("\\R", -1)) {
                    for (String part : FontManager.pf16.fitWidth(line.isBlank() ? " " : line, width))
                        infoList.addChild(label(part, FontManager.pf16).setClickable(false));
                }
            }
        }
        super.onRender(mouseX, mouseY);
    }

    @Override public void finishTransientAnimations() {
        super.finishTransientAnimations();
        restartEntrance();
    }

    private void restartEntrance() {
        pageEntrance.restart();
        enteringPage = true;
        enteringTab = false;
    }

    private void updateEntrance() {
        boolean reduced = UiPreferences.configured().reducedMotion();
        if (reduced) {
            enteringPage = enteringTab = false;
        }
        if (enteringPage) {
            enteringPage = pageEntrance.tick() < 1.0;
        }
        float tabBody = 1f;
        if (enteringTab) {
            enteringTab = tabEntrance.tick() < 1.0;
            tabBody = (float) (.25 + .75 * tabEntrance.progress());
        }
        bodyAlpha = layer(.34, 1) * tabBody;
        bodyOffset = 15 * (1 - bodyAlpha);
        header.setBounds(0, 0, getWidth(), tabsY());
        setCatalogTracksPresentation(showsSongs() && bodyAlpha > .01f, bodyAlpha, bodyOffset);
        if (albumList != null) albumList.setAlpha(bodyAlpha);
        if (infoList != null) infoList.setAlpha(bodyAlpha);
    }

    private float layer(double start, double end) {
        return enteringPage ? (float) pageEntrance.layer(start, end) : 1f;
    }

    private void layoutAlbumGrid() {
        double y = bodyY() + (moreAlbums ? 22 : 0);
        int height = Math.max(0, (int) (getHeight() - y - 8));
        var grid = MusicArtworkGrid.fit((int) (getWidth() - 2 * INSET), height);
        albumList.setBounds(INSET, y + MusicArtworkGrid.TOP + bodyOffset,
                grid.contentWidth(), Math.max(0, height - MusicArtworkGrid.TOP));
        if (grid.equals(albumGrid)) return;
        albumGrid = grid;
        for (var card : albumList.getChildren()) card.setBounds(grid.side(), grid.cardHeight());
        albumList.invalidateLayout();
    }

    private ScrollPanel mountList(DoubleSupplier top) {
        var list = new ScrollPanel();
        list.setSpacing(4).enableScrollbarDrag();
        addChild(list);
        list.setBeforeRenderCallback(() -> {
            double y = top.getAsDouble();
            list.setBounds(INSET, y + bodyOffset,
                    Math.max(0, getWidth() - 2 * INSET), Math.max(0, getHeight() - y - 8));
        });
        return list;
    }

    private void select(Tab next) {
        if (next == tab) return;
        tab = next;
        tabEntrance.restart();
        enteringTab = true;
        updateTabVisibility();
    }

    private void updateTabVisibility() {
        setCatalogTracksPresentation(showsSongs(), bodyAlpha, bodyOffset);
        if (albumList != null) albumList.setHidden(tab != Tab.ALBUMS);
        if (infoList != null) infoList.setHidden(tab != Tab.INFO);
    }

    private boolean showsSongs() { return tab == Tab.FEATURED || tab == Tab.SONGS; }

    private boolean featuredCards() { return tab == Tab.FEATURED && getHeight() - bodyY() >= 78; }
    private double coverSize() { return PlayingCatalogPresentation.coverSize(getWidth(), getHeight()); }
    private double textX() { return INSET + coverSize() + 12; }
    private double tabsY() { return PlayingCatalogPresentation.tabsY(coverSize(), !credits.isEmpty()); }
    private double bodyY() { return tabsY() + 25; }
    private double songHeadingY() { return bodyY() + (featuredCards() ? 44 : 0); }
    private double songsY() { return songHeadingY() + 22; }

    private String tabLabel(Tab target) {
        if (target == Tab.INFO && !artistPage) return text("detail.album_info");
        if (target == Tab.SONGS && !artistPage)
            return text("detail.tab_count", text("detail.songs"), playList.getMusics().size());
        return text("detail." + target.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void mountMoreButton() {
        var next = button(() -> tab == Tab.ALBUMS
                        ? text(loadingAlbums ? "detail.loading" : albumsFailed ? "artist.retry" : "detail.load_more_albums")
                        : text(loadingMore ? "detail.loading" : loadFailed ? "artist.retry" : "detail.load_more"),
                () -> { if (tab == Tab.ALBUMS) loadMoreAlbums(); else loadMore(); });
        addChild(next);
        next.setBeforeRenderCallback(() -> {
            boolean show = tab == Tab.ALBUMS ? moreAlbums : more && showsSongs();
            next.setBounds(Math.max(INSET, getWidth() - INSET - 104),
                    (tab == Tab.ALBUMS ? bodyY() : songHeadingY()) + bodyOffset, 104, 18);
            next.setAlpha(show ? bodyAlpha : 0f);
            next.setClickable(show && !(tab == Tab.ALBUMS ? loadingAlbums : loadingMore));
            paintButton(next, false);
        });
    }

    private void loadMoreAlbums() {
        if (!moreAlbums || loadingAlbums) return;
        loadingAlbums = true;
        albumsFailed = false;
        MultiThreadingUtil.runAsync(() -> {
            try {
                var batch = OnlineMusicCatalog.artistOverview(artist, albumPage + 1);
                MultiThreadingUtil.runOnMainThread(() -> {
                    var existing = new java.util.HashSet<String>();
                    albums.forEach(album -> existing.add(album.id()));
                    for (var release : batch.albums()) {
                        if (existing.add(release.id())) {
                            albums.add(release);
                            albumList.addChild(releaseCard(release, true));
                        }
                    }
                    albumPage++;
                    moreAlbums = batch.mayHaveMore();
                    loadingAlbums = false;
                    albumList.invalidateLayout();
                });
            } catch (Exception failure) {
                MultiThreadingUtil.runOnMainThread(() -> { loadingAlbums = false; albumsFailed = true; });
            }
        });
    }

    private void loadMore() {
        if (!more || loadingMore) return;
        loadingMore = true;
        loadFailed = false;
        MultiThreadingUtil.runAsync(() -> {
            try {
                var batch = OnlineMusicCatalog.artistTracks(artist, page + 1);
                MultiThreadingUtil.runOnMainThread(() -> {
                    var newTracks = batch.tracks().stream().filter(track -> providerTracks.putIfAbsent(track.id(), track) == null).toList();
                    appendCatalogTracks(PlayingTrackPages.music(newTracks));
                    page++;
                    more = batch.mayHaveMore();
                    loadingMore = false;
                });
            } catch (Exception failure) {
                MultiThreadingUtil.runOnMainThread(() -> { loadingMore = false; loadFailed = true; });
            }
        });
    }

    private RoundedButtonWidget releaseCard(OnlineAlbum album, boolean tile) {
        var card = button(() -> "", () -> PlayingTrackPages.openAlbum(NCMScreen.getInstance(), album));
        if (tile && albumGrid != null) card.setBounds(albumGrid.side(), albumGrid.cardHeight());
        else card.setBounds(0, 0, 0, 34);
        card.setTooltip(album.name());
        var picture = cover(album.artworkUrl());
        if (picture != null) {
            card.addChild(picture);
            picture.setBeforeRenderCallback(() -> {
                double side = tile ? card.getWidth() : 26;
                picture.setBounds(tile ? 0 : 4, tile ? 0 : 4, side, side);
                picture.setRadius(tile ? Math.min(8, side * .06) : 4);
            });
        }
        var name = label(album.name(), FontManager.pf16bold);
        var date = label(album.releaseDate(), FontManager.pf12);
        card.addChild(name);
        card.addChild(date);
        name.setBeforeRenderCallback(() -> {
            name.setPosition(tile ? 2 : 36, tile ? card.getWidth() + 4 : album.releaseDate().isBlank() ? 12 : 5);
            name.setMaxWidth(Math.max(0, card.getWidth() - (tile ? 4 : 42)));
            name.setColor(getColor(card.isHovering() ? ColorType.ACCENT : ColorType.PRIMARY_TEXT));
        });
        date.setBeforeRenderCallback(() -> {
            date.setPosition(tile ? 2 : 36, tile ? card.getWidth() + 20 : 21);
            date.setMaxWidth(Math.max(0, card.getWidth() - (tile ? 4 : 42)));
            date.setColor(getColor(ColorType.SECONDARY_TEXT));
        });
        card.setBeforeRenderCallback(() -> {
            if (tile) card.setFillAlpha(card.isHovering() ? .75f : .28f);
            paintButton(card, false);
        });
        return card;
    }

    private LabelWidget label(String value, CFontRenderer font) {
        return new LabelWidget(value, font).setWidthLimitType(WidthLimitType.TRIM_TO_WIDTH)
                .setClickable(false).setColor(getColor(ColorType.PRIMARY_TEXT));
    }

    private LabelWidget textLink(String value, String tooltip) {
        var link = label(value, FontManager.pf16bold);
        link.setClickable(true).setShouldOverrideMouseCursor(true).setTooltip(tooltip);
        return link;
    }

    private RoundedButtonWidget button(Supplier<String> label, Runnable action) {
        var button = new RoundedButtonWidget(label, FontManager.pf14bold);
        button.setRadius(5).setShouldOverrideMouseCursor(true);
        button.setOnClickCallback((x, y, mouseButton) -> {
            if (mouseButton != 0) return false;
            action.run();
            return true;
        });
        return button;
    }

    private void paintButton(RoundedButtonWidget button, boolean selected) {
        button.setColor(getColor(selected ? ColorType.ACCENT
                : button.isHovering() ? ColorType.ELEMENT_HOVER : ColorType.ELEMENT_BACKGROUND));
        button.setTextColor(getColor(ColorType.PRIMARY_TEXT));
    }

    private static RoundedImageWidget cover(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            String safeUrl = OnlineArtwork.validated(url).toString();
            var location = Location.of("muonium/textures/catalog/" + Integer.toUnsignedString(safeUrl.hashCode()) + ".png");
            if (com.muoniumplayer.core.rendering.TextureManager.getInstance().getTexture(location) == null)
                Textures.downloadTextureAndLoadAsync(safeUrl, location);
            var cover = new RoundedImageWidget(location, 0, 0, 0, 0).setClickable(false);
            return UiPreferences.configured().reducedMotion() ? cover : cover.fadeIn();
        } catch (IOException invalid) { return null; }
    }
}
