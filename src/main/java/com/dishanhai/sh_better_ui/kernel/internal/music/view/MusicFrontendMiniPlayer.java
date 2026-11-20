// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkPalette;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.ArtworkStageMotion;
import com.dishanhai.sh_better_ui.kernel.api.music.presentation.MusicArtworkStage;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import java.util.Objects;

/** New persistent mini player: the track opens Now Playing; transport actions do not navigate. */
final class MusicFrontendMiniPlayer implements AutoCloseable {
    record Placement(Bounds surface, Bounds track, Bounds previous, Bounds playPause, Bounds next, Bounds progress) {}
    private final MusicFrontendTransport transport;
    private final OnlineCoverPresentation artwork = new OnlineCoverPresentation();
    private final ArtworkStageMotion backdrop = new ArtworkStageMotion();
    private final MusicArtworkStage artworkPaint = new MusicArtworkStage();
    private final Placement placement;
    private final Bounds cover;
    private MusicFrontendNowPlayingLayout immersive;
    private final MusicTextControl track;
    private final MusicTransportControl previous, playPause, next;
    private final MusicSeekControl progress;
    private OnlineTrack renderedTrack;
    private MusicFrontendTransport.Primary renderedAction;
    private boolean closed;

    MusicFrontendMiniPlayer(PreviewContext host, Placement boxes, MusicFrontendTransport transport,
                            Runnable openNowPlaying) {
        this.transport = transport;
        placement = boxes;
        var meta = boxes.track();
        int artSize = Math.min(28, Math.max(0, Math.min(meta.height() - 4, meta.width() - 42)));
        cover = new Bounds(meta.x() + 4, meta.y() + (meta.height() - artSize) / 2, artSize, artSize);
        int textInset = artSize == 0 ? 0 : artSize + 8;
        track = host.add(new MusicTextControl(new Bounds(meta.x() + textInset, meta.y(),
                meta.width() - textInset, meta.height()), label("redesign.nothing_playing"), host.preferences(), openNowPlaying));
        track.setVariant(MusicTextControl.Variant.ROW);
        track.setInk(0xfff5f5f7, 0xffb4b4ba);
        previous = transport(host, boxes.previous(), "previous", MusicTransportControl.Glyph.PREVIOUS, () -> transport.skip(false));
        playPause = transport(host, boxes.playPause(), "play", MusicTransportControl.Glyph.PLAY, transport::activate);
        next = transport(host, boxes.next(), "next", MusicTransportControl.Glyph.NEXT, () -> transport.skip(true));
        progress = host.add(new MusicSeekControl(boxes.progress(), label("redesign.seek"), host.preferences(),
                edit -> {
                    if (seekInputActive(host)) transport.preview(edit);
                    else transport.cancelPreview();
                }, edit -> {
                    if (seekInputActive(host)) transport.commit(edit);
                    else transport.cancelPreview();
                }, edit -> transport.cancelPreview()));
        progress.setTimePreviewEnabled(true);
    }

    private boolean seekInputActive(PreviewContext host) {
        return !closed && host.interactive() && Minecraft.getInstance().isWindowActive();
    }

    void immersive(MusicFrontendNowPlayingLayout nextLayout) {
        if (Objects.equals(immersive, nextLayout)) return;
        cancelInteraction();
        immersive = nextLayout;
        track.visible = immersive == null;
        if (!track.visible) track.setAvailable(false);
        progress.setTimePreviewEnabled(immersive == null);
        var boxes = immersive == null
                ? new MusicFrontendLayout.Transport(placement.track(), placement.previous(), placement.playPause(),
                        placement.next(), placement.progress())
                : immersive.transport();
        place(previous, boxes.previous());
        place(playPause, boxes.playPause());
        place(next, boxes.next());
        place(progress, boxes.progress());
    }

    private static void place(AbstractWidget widget, Bounds bounds) {
        widget.setX(bounds.x()); widget.setY(bounds.y());
        widget.setWidth(bounds.width()); widget.setHeight(bounds.height());
    }

    void renderBackdrop(UiFrame ui, Bounds frame, OnlineTrack song) {
        if (ui.preferences().highContrast()) {
            backdrop.reset();
            ui.graphics().fill(frame.x(), frame.y(), frame.right(), frame.bottom(), 0xff000000);
            return;
        }
        var palette = artwork.sample(ui, song).background();
        if (palette == null) palette = ArtworkPalette.fallback(0xff3b333e);
        // Resolve the color transition separately; keep the inexpensive four-corner mesh.
        var shown = backdrop.sample(palette, ui.nowMs(), ui.preferences().reducedMotion(), false);
        artworkPaint.renderAudioBackground(ui, frame, shown.palette(), ui.nowMs(), true, 0f);
        // A uniform scrim keeps even very light artwork behind white foreground text.
        ui.graphics().fill(frame.x(), frame.y(), frame.right(), frame.bottom(), 0x33000000);
    }

    void render(UiFrame ui, boolean glass) {
        render(ui, glass, transport.frame(), Minecraft.getInstance().isWindowActive());
    }

    void render(UiFrame ui, boolean glass, MusicFrontendTransport.Frame frame, boolean engaged) {
        if (closed) return;
        var song = frame.playback().track();
        if (!Objects.equals(song, renderedTrack)) {
            track.rebind(song == null ? label("redesign.nothing_playing") : MusicTypography.text(song.title()));
            track.setSecondary(song == null ? Component.empty() : MusicTypography.text(song.artist()));
            renderedTrack = song;
        }
        track.setAvailable(track.visible && song != null && engaged);
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
        progress.sync(frame.seekKey(), frame.displayedPositionMs(), frame.playback().durationMs(), frame.seekable() && engaged);
        if (immersive != null) {
            drawTimes(ui, progress.isEditing() ? progress.previewPositionMs() : frame.displayedPositionMs(),
                    frame.playback().durationMs());
            return;
        }
        var surface = placement.surface();
        boolean contrast = ui.preferences().highContrast();
        if (!glass) RoundedCorners.draw(ui.graphics(), surface.x(), surface.y(), surface.width(), surface.height(),
                contrast ? 6 : 11, contrast ? 1 : 0, 0xffffffff, contrast ? 0xff000000 : 0xf5242428);
        if (cover.width() > 0) {
            paintCover(ui,cover,song);
        }
    }

    private void drawTimes(UiFrame ui, long position, long duration) {
        if (duration <= 0) return;
        var font = Minecraft.getInstance().font;
        position = Math.min(duration, Math.max(0, position));
        var elapsed = MusicTypography.text(time(position));
        var remaining = MusicTypography.text("−" + time(duration - position));
        int ink = ui.preferences().highContrast() ? 0xffffffff : 0xffd7d4da;
        var left = immersive.elapsed();
        var right = immersive.remaining();
        OnlineMusicViewPaint.line(ui, font, elapsed, left.x(), left.y() + 1, left.width(), ink);
        int rightX = Math.max(right.x(), right.right() - font.width(remaining.getVisualOrderText()));
        OnlineMusicViewPaint.line(ui, font, remaining, rightX, right.y() + 1, right.right() - rightX, ink);
    }

    private static String time(long millis) {
        long s = millis / 1000;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    boolean trackFocused() { return track.isFocused(); }

    Bounds coverBounds() { return cover; }

    void paintCover(UiFrame ui, Bounds bounds, OnlineTrack song) {
        if(bounds.width()<=0||bounds.height()<=0)return;
        OnlineCoverPresentation.paint(ui, artworkPaint, bounds, 0f, artwork.sample(ui, song));
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
