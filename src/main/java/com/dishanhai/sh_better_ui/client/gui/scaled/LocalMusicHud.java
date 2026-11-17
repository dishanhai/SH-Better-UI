// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.MusicApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.UiMarqueeLabel;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.FloatingLyricRenderer;
import com.dishanhai.sh_better_ui.client.music.MusicLyricFeed;
import com.dishanhai.sh_better_ui.client.music.MusicService;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;

/** Native, dependency-free information/lyric card using one coherent music frame per draw.
 * This is a SHBUI implementation of the MP HUD information/lyrics presentation concept;
 * no MP backend, fonts or raster assets are embedded. Mount through UUK HudService.
 */
public final class LocalMusicHud {
    private static final String TEXT = "sh_better_ui.music.";
    private final MusicApi music;
    private final MusicRhythm rhythm = new MusicRhythm();
    private final FloatingLyricRenderer lyrics;
    private MusicService.Frame current;
    private final UiMarqueeLabel titleLabel = new UiMarqueeLabel();
    private final UiMarqueeLabel artistLabel = new UiMarqueeLabel();

    public LocalMusicHud(MusicApi music) {
        this.music = Objects.requireNonNull(music);
        current = music.getFrame();
        lyrics = ShBetterUiApi.lyrics().renderer(new MusicLyricFeed(() -> current));
    }

    public void render(UiFrame ui, Font font, Bounds area) {
        var layout = MusicHudLayout.arrange(area, font.lineHeight);
        if (!layout.visible()) return;
        current = music.getFrame();
        var snapshot = current.snapshot();
        var session = snapshot.session();
        var track = session == null ? null : session.track();
        boolean still = ui.preferences().reducedMotion() || ui.preferences().highContrast();
        var audio = music.getAudioFeatures().orElseGet(() -> AudioFrame.silence(ui.nowMs()));
        var motion = rhythm.sample(audio, ui.nowMs(), still);
        var g = ui.graphics();
        g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        try {
            MusicHud.drawPanel(g, area, motion, ui.preferences());
            var art = layout.cover();
            var cover = music.cover(current);
            if (cover.isPresent()) {
                var texture = cover.get();
                float scale = art.width() / (float) Math.max(texture.width(), texture.height());
                int width = Math.max(1, Math.round(texture.width() * scale));
                int height = Math.max(1, Math.round(texture.height() * scale));
                g.blit(texture.id(), art.x() + (art.width() - width) / 2, art.y() + (art.height() - height) / 2,
                        width, height, 0f, 0f, texture.width(), texture.height(), texture.width(), texture.height());
            } else GraphicIcon.MUSIC.draw(g, art.x() + (art.width() - GraphicIcon.PIXELS) / 2,
                    art.y() + (art.height() - GraphicIcon.PIXELS) / 2, 1, 0xFFFFFFFF, ui.preferences());
            var text = layout.metadata();
            int secondary = ui.preferences().highContrast() ? 0xFFFFFFFF : 0xFFCBD5E1;
            titleLabel.setText(track == null ? label("unknown_title") : track.title().<Component>map(Component::literal)
                    .orElseGet(() -> label("unknown_title")));
            titleLabel.render(g, font, text.x(), text.y(), text.width(), 0xFFFFFFFF, false, ui.nowMs(), false, ui.preferences());
            artistLabel.setText(track == null ? status(snapshot) : track.artist().<Component>map(Component::literal)
                    .orElseGet(() -> label("unknown_artist")));
            artistLabel.render(g, font, text.x(), text.y() + font.lineHeight + 2, text.width(), secondary, false,
                    ui.nowMs(), false, ui.preferences());
            if (text.height() >= 3 * font.lineHeight + 4)
                draw(ui, font, status(snapshot), text.x(), text.y() + 2 * (font.lineHeight + 2), text.width(), secondary);
            var progress = layout.progress();
            long position = session == null ? -1 : session.positionAt(System.nanoTime());
            if (progress.width() > 0) {
                g.fill(progress.x(), progress.y(), progress.right(), progress.bottom(), 0xFF415064);
                if (session != null && session.durationMs().isPresent() && position >= 0) {
                    int width = (int) Math.round(progress.width() * MusicHudLayout.progress(position, session.durationMs().getAsLong()));
                    g.fill(progress.x(), progress.y(), progress.x() + width, progress.bottom(), still ? 0xFFFFFFFF : 0xFF72DDD0);
                }
                Component time = position < 0 ? label("time_unknown") : Component.translatable(TEXT + "time",
                        clock(position), session.durationMs().isPresent() ? clock(session.durationMs().getAsLong()) : label("duration_unknown"));
                draw(ui, font, time, progress.x(), progress.bottom() + 2, progress.width(), secondary);
            }
            var lyricArea = layout.lyrics();
            if (lyricArea.height() > 0 && track != null) {
                int count = lyricArea.height() >= 38 ? 2 : 1;
                if (lyrics.style().lineCount() != count) lyrics.setStyle(lyrics.style().withLineCount(count));
                if (position >= 0 && current.lyrics().state() == MusicService.LyricsState.AVAILABLE
                        && ShBetterUiApi.lyrics().draw(lyrics, ui, font, lyricArea)) return;
                draw(ui, font, label(position < 0 ? "lyrics_no_time" : current.lyrics().state() == MusicService.LyricsState.LOADING
                        ? "lyrics_loading" : current.lyrics().state() == MusicService.LyricsState.AVAILABLE
                        ? "lyrics_waiting" : "lyrics_missing"), lyricArea.x(), lyricArea.y(), lyricArea.width(), secondary);
            }
        } finally { g.disableScissor(); }
    }

    public void suspend() { rhythm.reset(); titleLabel.reset(); artistLabel.reset(); }
    private static Component label(String key) { return Component.translatable(TEXT + key); }
    private static Component status(MusicSnapshot snapshot) {
        return label("status." + (snapshot.session() == null ? snapshot.state().name() : snapshot.session().status().name()).toLowerCase(Locale.ROOT));
    }
    private static String clock(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return seconds / 60 + (seconds % 60 < 10 ? ":0" : ":") + seconds % 60;
    }
    private static void draw(UiFrame ui, Font font, Component text, int x, int y, int width, int color) {
        String value = text.getString();
        if (font.width(value) <= width) ui.graphics().drawString(font, text, x, y, color, false);
        else if (width >= font.width("…")) ui.graphics().drawString(font,
                font.plainSubstrByWidth(value, width - font.width("…")) + "…", x, y, color, false);
    }
}
