// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Artist identity and this provider-search page only; never presents matches as a popularity chart. */
final class ArtistDetailPanel {
    final ArtistDetailModel model;
    private final List<MusicTextControl> rows = new ArrayList<>();
    private List<OnlineTrack> displayed = List.of();
    private int stride, rowHeight;
    private MusicTransportControl play;
    private MusicTextControl retry;
    private OnlineMusicScrollRegion scroll;
    private PreviewContext host;
    private Bounds area,heroArea,list;
    private int capacity;
    private int hero;
    private int first;
    private boolean visible;
    ArtistDetailPanel(OnlineMusicPlayer player){model=new ArtistDetailModel(player);}
    private static Component label(String key,Object...args){return MusicTypography.text(Component.translatable("sh_better_ui.online_music.artist."+key,args));}
    void build(PreviewContext host, OnlineMusicLayout layout) {
        int header = layout.content().height() < 110 ? 0 : Math.min(64, Math.max(36, layout.content().height() / 3));
        build(host, layout.content(), header, layout.sourceBar(), 30);
    }

    void build(PreviewContext host, Bounds body) {
        int header = body.height() < 96 ? 22 : 40;
        build(host, body, header, body, 24);
    }

    private void build(PreviewContext host, Bounds body, int header, Bounds compactHeader, int pitch) {
        this.host = host;
        area = body;
        rows.clear();
        hero = header;
        stride = pitch;
        rowHeight = stride - 2;
        heroArea = hero == 0 ? compactHeader : new Bounds(area.x(), area.y(), area.width(), hero - 2);
        int diameter = Math.min(44, Math.max(16, heroArea.height() - 4));
        play = host.add(new MusicTransportControl(new Bounds(heroArea.right() - diameter - 4,
                heroArea.y() + (heroArea.height() - diameter) / 2, diameter, diameter),
                label("play"), MusicTransportControl.Glyph.PLAY, host.preferences(), () -> play(0)));
        int footerHeight = 22;
        capacity = Math.max(0, (area.height() - hero - footerHeight) / stride);
        list = new Bounds(area.x(), area.y() + hero, area.width(), capacity * stride);
        scroll = host.add(new OnlineMusicScrollRegion(list.x(), list.y(), list.width(), list.height(),
                label("songs"), value -> {
                    rows.forEach(MusicTextControl::cancelInteraction);
                    first = value;
                    refresh();
                }));
        scroll.setOnBrowse(() -> host.screen().setFocused(scroll));
        scroll.onEnd(() -> visible && !model.loading() && model.more && model.page < 1000,
                () -> { model.extend(); refresh(); });
        int spill = MusicScrollRows.spill(host, capacity);
        if (spill > 0) scroll.pixelRows(stride, shift -> refresh());
        for (int i = 0; i < capacity + spill; i++) {
            int slot = i;
            var row = MusicScrollRows.add(host, new MusicTextControl(
                    new Bounds(area.x() + 6, list.y() + i * stride, Math.max(12, area.width() - 24), rowHeight),
                    Component.empty(), host.preferences(), () -> {
                        if (rows.get(slot).visible) play(first + slot);
                    }), () -> list);
            row.setVariant(MusicTextControl.Variant.ROW);
            rows.add(row);
        }
        retry = host.add(new MusicTextControl(new Bounds(area.right() - 78, area.bottom() - 22, 72, 20),
                label("retry"), host.preferences(), () -> { model.retry(); refresh(); }));
        refresh();
    }

    void open(OnlineArtist artist) {
        first = 0;
        if (scroll != null) scroll.cancelDrag();
        model.open(artist);
        refresh();
    }

    Component heading() {
        return model.artist == null ? label("songs") : MusicTypography.text(model.artist.name());
    }

    private void play(int index) {
        if (!visible) return;
        try {
            if (model.play(displayed, index) && model.status.equals("play_failed")) model.status = "ready";
        } catch (RuntimeException failed) { model.status = "play_failed"; }
        refresh();
    }

    void visible(boolean value) {
        if (visible == value) return;
        visible = value;
        if (!value && scroll != null) scroll.cancelDrag();
        refresh();
    }

    void cancel() {
        model.cancel();
        visible(false);
    }

    private void refresh() {
        if (play == null) return;
        if (displayed != model.tracks) {
            rows.forEach(MusicTextControl::cancelInteraction);
            play.cancelInteraction();
            displayed = model.tracks;
        }
        first = Math.max(0, Math.min(first, Math.max(0, displayed.size() - capacity)));
        var queue = visible ? model.player.queue() : null;
        boolean playable = queue != null && queue.supportsModeReplacement();
        play.visible = visible;
        play.setAvailable(visible && !displayed.isEmpty() && playable);
        scroll.visible = visible && capacity > 0;
        scroll.configure(first, displayed.size(), capacity);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            int index = first + i;
            int y = list.y() + i * stride - scroll.rowShift();
            row.setY(y);
            row.setVisible(visible && index < displayed.size() && MusicScrollRows.intersects(y, rowHeight, list));
            row.setAvailable(row.visible && playable);
            if (row.visible) {
                var track = displayed.get(index);
                row.setMessage(MusicTypography.text((index + 1) + "  " + track.title()));
                row.setSecondary(MusicTypography.text(track.artist() + " · " + track.album()));
                row.setPreview(label("play_selected", track.title()));
            }
        }
        retry.setVisible(visible && model.status.equals("failed"));
        retry.setAvailable(retry.visible);
    }

    void render(UiFrame ui){
        model.poll();refresh();if(!visible)return;
        scroll.tick();
        var theme=UiTheme.current();
        OnlineMusicViewPaint.panel(ui,heroArea,GuiMotion.lerpColor(theme.background()|0xff000000,theme.accent()|0xff000000,ui.preferences().highContrast()?0:.10f),false);
        OnlineMusicViewPaint.line(ui,host.font(),MusicTypography.bold(model.artist==null?"":model.artist.name()),heroArea.x()+6,heroArea.y()+(heroArea.height()<36?1:6),Math.max(1,heroArea.width()-64),theme.text());
        OnlineMusicViewPaint.line(ui,host.font(),label(heroArea.height()<36?"short_scope":"scope",model.artist==null?"":model.artist.source(),model.page),heroArea.x()+6,heroArea.y()+(heroArea.height()<36?12:24),Math.max(1,heroArea.width()-64),theme.text());
        var status = model.status;
        if (status.equals("loading") || status.equals("failed") || status.equals("play_failed")
                || status.equals("unavailable") || status.equals("empty")) {
            int y = model.tracks.isEmpty() ? list.y() + 4 : area.bottom() - 16;
            int reserved = retry.visible && !model.tracks.isEmpty() ? retry.getWidth() + 12 : 10;
            OnlineMusicViewPaint.line(ui, host.font(), label(status), area.x() + 6, y,
                    Math.max(1, area.width() - reserved), theme.text());
        }
    }
}
