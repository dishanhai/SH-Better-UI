// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicArtworkStage;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import java.util.Objects;

/** New persistent mini player: the track opens Now Playing; transport actions do not navigate. */
final class MusicFrontendMiniPlayer implements AutoCloseable {
    record Placement(Bounds surface, Bounds track, Bounds previous, Bounds playPause, Bounds next, Bounds progress) {}
    private final MusicFrontendTransport transport;
    private final OnlineArtworkTexture artwork = new OnlineArtworkTexture();
    private final MusicArtworkStage artworkPaint = new MusicArtworkStage();
    private final Bounds surface, cover;
    private final MusicTextControl track;
    private final MusicTransportControl previous, playPause, next;
    private final MusicSeekControl progress;
    private OnlineTrack renderedTrack;
    private MusicFrontendTransport.Primary renderedAction;
    private boolean closed;

    MusicFrontendMiniPlayer(PreviewContext host, Placement boxes, MusicFrontendTransport transport,
                            Runnable openNowPlaying) {
        this.transport = transport;
        surface = boxes.surface();
        var meta = boxes.track();
        int artSize = Math.min(28, Math.max(0, Math.min(meta.height() - 4, meta.width() - 42)));
        cover = new Bounds(meta.x() + 4, meta.y() + (meta.height() - artSize) / 2, artSize, artSize);
        int textInset = artSize == 0 ? 0 : artSize + 8;
        track = host.add(new MusicTextControl(new Bounds(meta.x() + textInset, meta.y(),
                meta.width() - textInset, meta.height()), label("redesign.nothing_playing"), host.preferences(), openNowPlaying));
        track.setVariant(MusicTextControl.Variant.ROW);
        previous = transport(host, boxes.previous(), "previous", MusicTransportControl.Glyph.PREVIOUS, () -> transport.skip(false));
        playPause = transport(host, boxes.playPause(), "play", MusicTransportControl.Glyph.PLAY, transport::activate);
        next = transport(host, boxes.next(), "next", MusicTransportControl.Glyph.NEXT, () -> transport.skip(true));
        progress = host.add(new MusicSeekControl(boxes.progress(), label("redesign.seek"), host.preferences(),
                transport::preview, transport::commit, edit -> transport.cancelPreview()));
        progress.setTimePreviewEnabled(true);
    }

    void render(UiFrame ui) {
        if (closed) return;
        var frame = transport.frame();
        var song = frame.playback().track();
        if (!Objects.equals(song, renderedTrack)) {
            track.rebind(song == null ? label("redesign.nothing_playing") : MusicTypography.text(song.title()));
            track.setSecondary(song == null ? Component.empty() : MusicTypography.text(song.artist()));
            renderedTrack = song;
        }
        boolean engaged = Minecraft.getInstance().isWindowActive();
        track.setAvailable(song != null && engaged);
        previous.setAvailable(frame.previous() && engaged);
        next.setAvailable(frame.next() && engaged);
        var primary = frame.primary();
        if (primary != renderedAction) {
            playPause.setGlyph(primary == MusicFrontendTransport.Primary.PAUSE ? MusicTransportControl.Glyph.PAUSE : MusicTransportControl.Glyph.PLAY);
            playPause.setMessage(label(switch (primary) {
                case PAUSE -> "pause";
                case RETRY -> "redesign.retry";
                default -> "play";
            }));
            renderedAction = primary;
        }
        playPause.setAvailable(engaged && primary != MusicFrontendTransport.Primary.NONE);
        progress.sync(frame.seekKey(), frame.playback().positionMs(), frame.playback().durationMs(), frame.seekable() && engaged);
        boolean contrast = ui.preferences().highContrast();
        RoundedCorners.draw(ui.graphics(), surface.x(), surface.y(), surface.width(), surface.height(),
                contrast ? 6 : 11, contrast ? 1 : 0, 0xffffffff, contrast ? 0xff000000 : 0xf5242428);
        if (cover.width() > 0) {
            paintCover(ui,cover,song);
        }
    }

    void paintCover(UiFrame ui, Bounds bounds, OnlineTrack song) {
        if(bounds.width()<=0||bounds.height()<=0)return;
        RoundedCorners.draw(ui.graphics(),bounds.x(),bounds.y(),bounds.width(),bounds.height(),6,0,0,0xff38383d);
        artwork.texture(song).ifPresent(texture ->
                artworkPaint.renderCover(ui, bounds, texture.id(), texture.width(), texture.height()));
    }

    void cancelInteraction() {
        track.cancelInteraction(); previous.cancelInteraction(); playPause.cancelInteraction();
        next.cancelInteraction(); progress.cancelInteraction(); transport.cancelPreview();
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        cancelInteraction();
        artwork.close();
        artworkPaint.close();
    }

    private static MusicTransportControl transport(PreviewContext host, Bounds bounds, String text,
                                                   MusicTransportControl.Glyph glyph, Runnable action) {
        var control = host.add(new MusicTransportControl(bounds, label(text),glyph,host.preferences(),action));
        control.setAvailable(false);
        return control;
    }
    private static Component label(String key) {
        return MusicTypography.text(Component.translatable("sh_better_ui.online_music." + key));
    }
}
