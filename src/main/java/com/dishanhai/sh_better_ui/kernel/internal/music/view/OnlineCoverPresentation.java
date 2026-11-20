// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicArtworkStage;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import java.util.List;

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

    static void paint(UiFrame ui, MusicArtworkStage stage, Bounds slot,
                      MusicCoverHandoff.Frame<OnlineArtworkTexture.Texture> cover) {
        if (slot.width() <= 0 || slot.height() <= 0) return;
        var cue = cover.cue();
        if (cover.base() == null) {
            float skeleton = cue.mode() == MusicCoverWait.Mode.SKELETON ? cue.opacity() : 0f;
            placeholder(ui, slot, 1f, 1f - skeleton);
            if (skeleton > 0f) skeleton(ui, slot, cue);
        } else layer(ui, stage, slot, cover.base(), 1f);
        if (cover.mix() > 0f) {
            if (cover.incoming() == null) placeholder(ui, slot, cover.mix(), cover.mix());
            else layer(ui, stage, slot, cover.incoming(), cover.mix());
        }
        if (cue.mode() == MusicCoverWait.Mode.BADGE && cue.opacity() > 0f) badge(ui, slot, cue);
    }

    private static void placeholder(UiFrame ui, Bounds slot, float opacity, float glyphOpacity) {
        boolean contrast = ui.preferences().highContrast();
        int alpha = Math.round(opacity * 255) << 24;
        int surface = contrast ? UiTheme.current().background() & 0xffffff : 0x23232a;
        int ink = contrast ? UiTheme.current().text() & 0xffffff : 0x787580;
        ui.graphics().fill(slot.x(), slot.y(), slot.right(), slot.bottom(), alpha | surface);
        int size = Math.min(slot.width(), slot.height());
        if (size < 16 || glyphOpacity <= 0f) return;
        float cx = slot.x() + slot.width() / 2f, cy = slot.y() + slot.height() / 2f;
        float unit = Math.min(4f, size / 13f);
        for (int i = 0; i < 3; i++) {
            float h = unit * (i == 1 ? 4f : 2.5f);
            RoundedCorners.draw(ui.graphics(), cx + (i - 1.5f) * unit * 1.6f, cy - h / 2f,
                    unit, h, unit / 2f, 0f, 0, (Math.round(glyphOpacity * 255) << 24) | ink);
        }
    }

    private static void skeleton(UiFrame ui, Bounds slot, MusicCoverWait.Cue cue) {
        int pad = Math.min(9, Math.min(slot.width(), slot.height()) / 6);
        var block = new Bounds(slot.x() + pad, slot.y() + pad, slot.width() - pad * 2, slot.height() - pad * 2);
        UiSkeleton.draw(ui.graphics(), slot, List.of(new UiSkeleton.Block(block, Math.min(8f, pad))),
                cue.phaseMs(), ui.preferences(), cue.opacity());
    }

    private static void badge(UiFrame ui, Bounds slot, MusicCoverWait.Cue cue) {
        if (slot.width() < 12 || slot.height() < 8) return;
        boolean still = ui.preferences().reducedMotion() || ui.preferences().highContrast();
        int width = Math.min(24, slot.width() - 2);
        int x = slot.right() - width - 1, y = slot.bottom() - 8;
        int alpha = Math.round(cue.opacity() * 220) << 24;
        RoundedCorners.draw(ui.graphics(), x, y, width, 7, 3.5f, 0, 0, alpha | 0x19191d);
        double phase = (cue.phaseMs() % 900L) * (2d * Math.PI / 900d);
        for (int i = 0; i < 3; i++) {
            float cx = x + width * (i + 1) / 4f;
            double intensity = still ? 235 : 178 + 52 * Math.cos(phase - i * 2d * Math.PI / 3d);
            int ink = ((int)Math.round(cue.opacity() * intensity) << 24) | 0xffffff;
            RoundedCorners.draw(ui.graphics(), cx - 1, y + 2.5f, 2, 2, 1f, 0, 0, ink);
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
