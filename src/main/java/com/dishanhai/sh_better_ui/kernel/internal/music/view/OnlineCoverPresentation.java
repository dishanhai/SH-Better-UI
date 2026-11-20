// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicArtworkStage;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;

/** Owns at most two native cover textures for one player view, including interrupted requests. */
final class OnlineCoverPresentation implements AutoCloseable {
    private final OnlineArtworkTexture first = new OnlineArtworkTexture();
    private final OnlineArtworkTexture second = new OnlineArtworkTexture();
    private final MusicCoverHandoff<OnlineArtworkTexture.Texture> handoff =
            new MusicCoverHandoff<>(first, second, OnlineArtworkTexture.Texture::palette);

    MusicCoverHandoff.Frame<OnlineArtworkTexture.Texture> sample(UiFrame ui, OnlineTrack track) {
        return handoff.sample(track == null ? "" : track.artworkUrl(), ui.nowMs(),
                ui.preferences().reducedMotion() || ui.preferences().highContrast());
    }

    static void paint(UiFrame ui, MusicArtworkStage stage, Bounds slot, float activity,
                      MusicCoverHandoff.Frame<OnlineArtworkTexture.Texture> cover) {
        if (slot.width() <= 0 || slot.height() <= 0) return;
        if (cover.base() == null) placeholder(ui, slot, activity, 1f);
        else layer(ui, stage, slot, cover.base(), 1f);
        if (cover.mix() <= 0f) return;
        if (cover.incoming() == null) placeholder(ui, slot, activity, cover.mix());
        else layer(ui, stage, slot, cover.incoming(), cover.mix());
    }

    private static void placeholder(UiFrame ui, Bounds slot, float activity, float opacity) {
        boolean contrast = ui.preferences().highContrast();
        int alpha = Math.round(opacity * 255) << 24;
        int surface = contrast ? UiTheme.current().background() & 0xffffff : 0x23232a;
        int ink = contrast ? UiTheme.current().text() & 0xffffff : 0x787580;
        ui.graphics().fill(slot.x(), slot.y(), slot.right(), slot.bottom(), alpha | surface);
        int size = Math.min(slot.width(), slot.height());
        if (size < 16) return;
        float cx = slot.x() + slot.width() / 2f, cy = slot.y() + slot.height() / 2f;
        float unit = Math.min(4f, size / 13f);
        float motion = ui.preferences().reducedMotion() || contrast ? 0f : Math.max(0f, Math.min(1f, activity));
        for (int i = 0; i < 3; i++) {
            float h = unit * (i == 1 ? 4f : 2.5f) + motion * (i == 0 ? unit : .5f * unit);
            RoundedCorners.draw(ui.graphics(), cx + (i - 1.5f) * unit * 1.6f, cy - h / 2f,
                    unit, h, unit / 2f, 0f, 0, alpha | ink);
        }
    }

    private static void layer(UiFrame ui, MusicArtworkStage stage, Bounds slot,
                              OnlineArtworkTexture.Texture image, float alpha) {
        if (image == null) return;
        // Blend the letterbox with the new picture, not as a dark wash over its pixels.
        float fit = Math.min(slot.width() / (float) image.width(), slot.height() / (float) image.height());
        int w = Math.min(slot.width(), Math.max(1, Math.round(image.width() * fit)));
        int h = Math.min(slot.height(), Math.max(1, Math.round(image.height() * fit)));
        int x = slot.x() + (slot.width() - w) / 2, y = slot.y() + (slot.height() - h) / 2;
        int matte = Math.round(alpha * 255) << 24 | 0x23232a;
        var g = ui.graphics();
        if (y > slot.y()) g.fill(slot.x(), slot.y(), slot.right(), y, matte);
        if (y + h < slot.bottom()) g.fill(slot.x(), y + h, slot.right(), slot.bottom(), matte);
        if (x > slot.x()) g.fill(slot.x(), y, x, y + h, matte);
        if (x + w < slot.right()) g.fill(x + w, y, slot.right(), y + h, matte);
        stage.renderCover(ui, slot, image.id(), image.width(), image.height(), alpha);
    }

    @Override public void close() { first.close(); second.close(); }
}
