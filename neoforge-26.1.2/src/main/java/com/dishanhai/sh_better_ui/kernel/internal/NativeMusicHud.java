// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.client.music.*;
import com.dishanhai.sh_better_ui.config.MusicOverlaySettings;
import com.dishanhai.sh_better_ui.kernel.api.music.MusicLyricsView;
import com.dishanhai.sh_better_ui.kernel.internal.music.view.NativeHudLyrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Independent information HUD. It never owns or starts playback and has no global pointer capture. */
public final class NativeMusicHud {
    private final NativeMusicArtwork artwork;
    private MusicLyricsView lyrics;
    private Object lyricTimeline;
    private String lyricIdentity;
    private Bounds bounds;
    private int viewportWidth, viewportHeight;
    private MusicService.Frame frame;
    NativeMusicHud(NativeMusicArtwork artwork) { this.artwork = artwork; }
    static boolean sceneVisible(Minecraft client) {
        return client.level != null && client.player != null && !client.options.hideGui && client.isWindowActive()
                && NativePort.screen(client) == null && !NativePort.hasOverlay(client) && !client.options.keyPlayerList.isDown();
    }
    static Bounds layout(int width, int height, MusicOverlaySettings settings, int lineHeight) {
        var area = MusicOverlayLayout.panel(width, height, false, settings.hudScale(), settings.x(), settings.y());
        return MusicHudLayout.arrange(area, lineHeight).visible() ? area : null;
    }
    void prepare(NativeMusicActivity owner, MusicService.Frame frame, boolean scene, int width, int height, int lineHeight) {
        this.frame = frame; viewportWidth = width; viewportHeight = height;
        bounds = scene && owner.available() && owner.settings().hudEnabled() && NativeMusicActivity.identity(frame) != null
                ? layout(width, height, owner.settings(), lineHeight) : null;
        if (!owner.hudBounds(bounds)) bounds = null;
        if (bounds == null) { lyricTimeline = null; lyricIdentity = null; lyrics = null; }
    }
    void suspend(NativeMusicActivity owner) { owner.hudBounds(null); bounds = null; lyrics = null; lyricTimeline = null; lyricIdentity = null; }
    boolean visible() { return bounds != null; }
    MusicSnapshot.Artwork cover() { return visible() ? frame.snapshot().session().artwork() : null; }
    void draw(GuiGraphicsExtractor graphics) {
        if (bounds == null || graphics.guiWidth() != viewportWidth || graphics.guiHeight() != viewportHeight
                || !sceneVisible(Minecraft.getInstance())) return;
        var session = frame.snapshot().session();
        paint(graphics, bounds, frame, artwork.texture(NativeMusicArtwork.icon(session.artwork())));
        var area = MusicHudLayout.arrange(bounds, Minecraft.getInstance().font.lineHeight).lyrics();
        long position = session.positionAt(System.nanoTime());
        if (area.height() <= 0 || position < 0 || frame.lyrics().state() != MusicService.LyricsState.AVAILABLE) return;
        if (lyrics == null) { lyrics = new MusicLyricsView(area, Component.translatable("sh_better_ui.online_music.stage.open"), UiPreferences.configured(), ignored -> {}); lyrics.scale(1); }
        String identity = NativeMusicActivity.identity(frame);
        if (lyricTimeline != frame.lyrics().timeline() || !java.util.Objects.equals(lyricIdentity, identity)) {
            lyrics.setLines(identity, NativeHudLyrics.lines(frame.lyrics().timeline()));
            lyricTimeline = frame.lyrics().timeline(); lyricIdentity = identity;
        }
        var theme = UiTheme.current(); lyrics.preferences(UiPreferences.configured());
        lyrics.palette(theme.text(), theme.secondaryText(0xffb0b8c8, .3f), theme.surface());
        lyrics.bounds(area); lyrics.playback(position, false); lyrics.extractRenderState(graphics, -1, -1, 0);
    }
    private static void paint(GuiGraphicsExtractor graphics, Bounds panel, MusicService.Frame frame, NativeMusicArtwork.Texture texture) {
        var theme = UiTheme.current(); var font = Minecraft.getInstance().font;
        var layout = MusicHudLayout.arrange(panel, font.lineHeight);
        int text = UiPreferences.configured().highContrast() ? 0xffffffff : theme.text();
        int surface = UiPreferences.configured().highContrast() ? 0xff000000 : theme.surface() | 0xff000000;
        RoundedCorners.draw(graphics, panel.x(), panel.y(), panel.width(), panel.height(), 10, 1, theme.border(), surface);
        graphics.enableScissor(panel.x(), panel.y(), panel.right(), panel.bottom());
        try {
            var cover = layout.cover();
            if (texture != null) {
                var image = coverBounds(cover, texture.width(), texture.height());
                // This overload derives UV extent from destination/texture dimensions. Equal dimensions
                // select the full [0,1] texture while the real source aspect ratio determines the destination.
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), image.x(), image.y(), 0f, 0f,
                        image.width(), image.height(), image.width(), image.height());
            } else RoundedCorners.draw(graphics, cover.x(), cover.y(), cover.width(), cover.height(), 6, 1, theme.border(), theme.accent());
            var session = frame == null ? null : frame.snapshot().session();
            var metadata = layout.metadata();
            var title = session == null ? Component.translatableWithFallback("sh_better_ui.native.music_settings.preview_title", "Layout preview") : session.track().title().map(Component::literal).orElseGet(() -> Component.translatable("sh_better_ui.music.unknown_title"));
            var artist = session == null ? Component.translatableWithFallback("sh_better_ui.native.music_settings.preview_sample", "Static sample · not playing") : Component.literal(session.track().artist().orElse(""));
            line(graphics, title.copy().withStyle(net.minecraft.ChatFormatting.BOLD), metadata.x(), metadata.y(), metadata.width(), text);
            line(graphics, artist, metadata.x(), metadata.y()+font.lineHeight+2, metadata.width(), text);
            if (session != null && metadata.height() >= 3 * font.lineHeight + 4)
                line(graphics, Component.translatable("sh_better_ui.music.status." + session.status().name().toLowerCase(Locale.ROOT)),
                        metadata.x(), metadata.y()+2*(font.lineHeight+2), metadata.width(), text);
            long position = session == null ? 32000 : session.positionAt(System.nanoTime());
            long duration = session == null ? 180000 : session.durationMs().orElse(0);
            var bar = layout.progress();
            if (bar.width() > 0) {
                graphics.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), theme.border());
                if (position >= 0 && duration > 0) graphics.fill(bar.x(), bar.y(), bar.x()+(int)(bar.width()*MusicHudLayout.progress(position,duration)), bar.bottom(), theme.accent());
                line(graphics, Component.literal(position < 0 ? "--:--" : clock(position) + (duration > 0 ? " / " + clock(duration) : "")), bar.x(), bar.bottom()+2, bar.width(), text);
            }
            var lyric = layout.lyrics();
            if (lyric.height() > 0 && (frame == null || frame.lyrics().state() != MusicService.LyricsState.AVAILABLE))
                line(graphics, frame == null ? Component.translatableWithFallback("sh_better_ui.native.music_settings.preview_lyrics", "Preview lyrics") : Component.translatable("sh_better_ui.music.lyrics_missing"), lyric.x(), lyric.y(), lyric.width(), text);
        } finally { graphics.disableScissor(); }
    }
    static Bounds coverBounds(Bounds area, int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) throw new IllegalArgumentException("Invalid cover dimensions");
        double scale = Math.min(area.width() / (double) sourceWidth, area.height() / (double) sourceHeight);
        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));
        return new Bounds(area.x() + (area.width() - width) / 2, area.y() + (area.height() - height) / 2, width, height);
    }
    private static String clock(long millis) { return String.format(Locale.ROOT,"%d:%02d",millis/60000,millis/1000%60); }
    private static void line(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(text, Math.max(1,width))), x,y,color,false);
    }
    /** Explicit local-only layout preview, never submitted as a music frame or activity. */
    public static void drawPreview(GuiGraphicsExtractor graphics, int width, int height, MusicOverlaySettings settings) {
        var bounds = layout(width,height,settings,Minecraft.getInstance().font.lineHeight);
        if(bounds != null) paint(graphics,bounds,null,null);
    }
}
