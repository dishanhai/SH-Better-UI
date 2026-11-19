// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.MusicApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.FloatingLyricRenderer;
import com.dishanhai.sh_better_ui.client.music.MusicLyricFeed;
import com.dishanhai.sh_better_ui.client.music.MusicService;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicService.BackgroundTarget;
import com.dishanhai.sh_better_ui.kernel.internal.music.IslandPlaybackShortcut;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextLayout;

import java.util.Locale;
import java.util.Optional;

/** A per-view music card. Hosts supply a keyboard-accessible toggle and the two bounds. */
public final class LocalMusicIsland {
    private static final int ACCENT = 0xFF72DDD0;
    private static final String TEXT = "sh_better_ui.music.";
    private final MusicApi music;
    private final InterruptibleAnimation expansion = new InterruptibleAnimation(new Spring.Preset(.28, .64, .00035f, .02f));
    private final MusicRhythm rhythm = new MusicRhythm();
    private final com.dishanhai.sh_better_ui.client.gui.text.UiMarqueeLabel titleLabel =
            new com.dishanhai.sh_better_ui.client.gui.text.UiMarqueeLabel();
    private final FloatingLyricRenderer lyrics;
    private MusicService.Frame current;
    private boolean expanded;
    private final MusicIslandCapsuleState capsule = new MusicIslandCapsuleState();
    private final DynamicIslandState lifecycle = new DynamicIslandState();
    private final IslandNoticeQueue notices = new IslandNoticeQueue();
    private IslandNoticeQueue.Notice activeNotice;
    private final IslandAmbientState ambient = new IslandAmbientState();
    private boolean ambientShown;
    private boolean noticeSurfaceAllowed = true;
    private IslandShellStyle style = IslandShellStyle.CLASSIC;

    public LocalMusicIsland(MusicApi music) {
        this.music = music;
        current = music.getFrame();
        lyrics = ShBetterUiApi.lyrics().renderer(new MusicLyricFeed(() -> current));
    }

    public boolean expanded() { return expanded; }

    /** Pin expansion; explicit collapse also dismisses a track notification. */
    public void setExpanded(boolean expanded, long nowMs, boolean reducedMotion) {
        if (expanded) lifecycle.setPinned(true);
        else { lifecycle.dismiss(); notices.clear(); activeNotice = null; }
        animateExpansion(expanded, nowMs, reducedMotion);
    }

    public void setScene(boolean inWorld, boolean playerListVisible, boolean hudHidden, boolean playerUiOpen) {
        lifecycle.setScene(inWorld, playerListVisible, hudHidden, playerUiOpen);
        noticeSurfaceAllowed = inWorld && !playerListVisible && !hudHidden;
        if (!noticeSurfaceAllowed) notices.pause();
    }

    /** Hosts may use the same hit region for pointer and keyboard focus. */
    public void setHovered(boolean hovered, long nowMs) { lifecycle.setHovered(hovered, nowMs); }

    public void setStyle(String id) {
        style = IslandShellStyle.fromId(id);
        lifecycle.setHoverDelayMs(style.hoverDelayMs());
    }
    public String styleId() { return style.id(); }

    public void setAmbient(IslandAmbientState.Settings settings) { ambient.configure(settings); }
    public void updateAmbient(int fps, int pingMillis, int hour, int minute, long nowMs) {
        ambient.update(fps, pingMillis, hour, minute, nowMs);
    }

    private BackgroundTarget renderedMusicTarget;

    /** The expanded music frame actually drawn, null for a compact/hidden/notification surface. */
    public BackgroundTarget backgroundControlTarget() { return renderedMusicTarget; }

    public void notify(IslandNoticeQueue.Notice notice) { notices.offer(notice); }
    public boolean tryNotify(IslandNoticeQueue.Notice notice) { return notices.tryOffer(notice); }

    /** Cancel only the completed/withdrawn operation, never unrelated queued notices. */
    public void cancelNotice(String id) {
        notices.cancel(id);
        if (activeNotice != null && activeNotice.id().equals(id)) {
            activeNotice = null;
            lifecycle.setNoticeActive(false);
        }
    }

    public boolean visible(long nowMs) {
        observeMusic(nowMs);
        sampleNotice(nowMs);
        return surfaceVisible(nowMs);
    }
    public boolean notificationActive(long nowMs) {
        observeMusic(nowMs);
        sampleNotice(nowMs);
        return lifecycle.notificationActive(nowMs);
    }

    private void observeMusic(long nowMs) {
        current = music.getFrame();
        var snapshot = current.snapshot();
        var session = snapshot.session();
        var track = session == null ? null : session.track();
        String identity = track == null ? null
                : snapshot.providerId() + "\u0000" + session.id() + "\u0000" + track.key();
        lifecycle.observeTrack(identity, nowMs);
        capsule.observe(identity, session != null && session.status() == MusicSnapshot.PlaybackStatus.PLAYING, nowMs);
    }

    private boolean surfaceVisible(long nowMs) {
        return lifecycle.visible(nowMs) && (capsule.active() || (!style.musicCapsule() && !ambient.chips().isEmpty())
                || lifecycle.pinned() || lifecycle.notificationActive(nowMs));
    }

    private void sampleNotice(long nowMs) {
        if (!noticeSurfaceAllowed) notices.pause();
        activeNotice = notices.peek(nowMs).orElse(null);
        lifecycle.setNoticeActive(activeNotice != null);
    }

    private void animateExpansion(boolean expanded, long nowMs, boolean reducedMotion) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        float target = expanded ? 1 : 0;
        if (reducedMotion) expansion.snapTo(target);
        else if (style == IslandShellStyle.BOUNCE) expansion.animateTo(target, nowMs);
        else expansion.blendTo(target, nowMs, 0, expanded ? 265 : 220,
                InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
    }

    public Component narration() {
        if (activeNotice != null) return Component.literal(activeNotice.title() + "\n" + activeNotice.detail());
        if (ambientShown) return Component.literal(ambient.chips().stream()
                .map(chip -> chip.value() + " " + chip.unit()).collect(java.util.stream.Collectors.joining(", ")));
        var snapshot = current.snapshot();
        var session = snapshot.session();
        var action = Component.translatable(TEXT + (expanded ? "collapse" : "expand"));
        var status = status(snapshot);
        if (session == null || session.track() == null) return Component.translatable(TEXT + "narration_no_track", action, status);
        return Component.translatable(TEXT + "narration", action, title(session), status)
                .append("\n").append(credit(session));
    }

    public void render(UiFrame ui, Font font, Bounds compact, Bounds full, boolean hovered) {
        render(ui, font, compact, full, hovered, Optional.empty(), true);
    }

    /** The host may supply its own authorized output capture for this view. */
    public void render(UiFrame ui, Font font, Bounds compact, Bounds full, boolean hovered, Optional<AudioFrame> audio) {
        render(ui, font, compact, full, hovered, audio, false);
    }

    private void render(UiFrame ui, Font font, Bounds compact, Bounds full, boolean hovered, Optional<AudioFrame> audio, boolean systemAudio) {
        renderedMusicTarget = null;
        if (compact.width() < 94 || compact.height() < 28) { notices.pause(); return; }
        observeMusic(ui.nowMs());
        if (systemAudio) audio = music.getAudioFeatures(current);
        var snapshot = current.snapshot();
        var session = snapshot.session();
        var preferences = ui.preferences();
        boolean still = preferences.reducedMotion() || preferences.highContrast();
        var observedTrack = session == null ? null : session.track();
        sampleNotice(ui.nowMs());
        animateExpansion(lifecycle.expanded(ui.nowMs()), ui.nowMs(), still);
        if (!surfaceVisible(ui.nowMs())) { rhythm.reset(); return; }
        var outline = MusicIsland.outline(compact, full, expansion.sample(ui.nowMs(), still));
        var graphics = ui.graphics();
        var rect = outline.rect();
        MusicIsland.drawShell(graphics, outline, preferences, hovered, style);
        graphics.enableScissor(Math.round(rect.getX()), Math.round(rect.getY()),
                Math.round(rect.getX() + rect.getW()), Math.round(rect.getY() + rect.getH()));
        try {
        ambientShown = false;
        if (activeNotice != null) {
            int left = Math.round(outline.rect().getX()) + 10;
            int right = Math.round(outline.rect().getX() + outline.rect().getW()) - 10;
            int top = Math.round(outline.rect().getY()) + 7;
            drawText(ui, font, Component.literal(activeNotice.title()), left, top, right, 0xFFFFFFFF);
            drawText(ui, font, Component.literal(activeNotice.detail()), left, top + MusicTypography.LINE_HEIGHT + 2,
                    right, preferences.highContrast() ? 0xFFFFFFFF : 0xFFCBD5E1);
            int barY = top + 2 * MusicTypography.LINE_HEIGHT + 5;
            if (activeNotice.progress() >= 0 && barY + 2 < outline.rect().getY() + outline.rect().getH() - 5) {
                graphics.fill(left, barY, right, barY + 2, 0xFF415064);
                graphics.fill(left, barY, left + (int) Math.round((right-left)*activeNotice.progress()),
                        barY + 2, preferences.highContrast() ? 0xFFFFFFFF : ACCENT);
            }
            if (rect.getH() >= 2 * MusicTypography.LINE_HEIGHT + 18) notices.presented(activeNotice, ui.nowMs());
            return;
        }

        if (!style.musicCapsule() && !expanded && !lifecycle.notificationActive(ui.nowMs())
                && ambient.page(ui.nowMs(), observedTrack != null, preferences.reducedMotion()) == IslandAmbientState.Page.STATUS) {
            ambientShown = true;
            drawAmbient(ui, font, compact);
            return;
        }

        // Use the explicit lifecycle state, never the scaled surface height, to choose content.
        // While expanding, keep compact content until the shell has room for readable details.
        boolean detail = expanded && rect.getH() >= 76;
        int surfaceLeft = Math.round(rect.getX());
        int surfaceRight = Math.round(rect.getX() + rect.getW());
        int surfaceTop = Math.round(rect.getY());
        int x = detail ? surfaceLeft + 12 : compact.x() + 8;
        int artY = detail ? surfaceTop + 12 : compact.y() + (compact.height() - 18) / 2;
        var cover = music.cover(current);
        if (cover.isPresent()) {
            var texture = cover.get();
            float fit = 18f / Math.max(texture.width(), texture.height());
            int width = Math.max(1, Math.round(texture.width() * fit));
            int height = Math.max(1, Math.round(texture.height() * fit));
            graphics.blit(texture.id(), x + (18 - width) / 2, artY + (18 - height) / 2, width, height,
                    0f, 0f, texture.width(), texture.height(), texture.width(), texture.height());
        } else {
            // Native rounded note; no bitmap/pixel-art placeholder or borrowed branded artwork.
            RoundedCorners.draw(graphics, x + 9, artY + 2, 2, 12, 1, 0, 0xFFFFFFFF, 0xFFFFFFFF, false);
            RoundedCorners.draw(graphics, x + 9, artY + 2, 7, 3, 1, 0, 0xFFFFFFFF, 0xFFFFFFFF, false);
            RoundedCorners.draw(graphics, x + 4, artY + 11, 7, 5, 2.5f, 0, 0xFFFFFFFF, 0xFFFFFFFF, false);
        }

        int textRight = detail ? surfaceRight - 12 : compact.right() - 9;
        int indicatorX = textRight - MusicIsland.WAVEFORM_WIDTH;
        if (audio.isPresent() && session != null && session.status() == MusicSnapshot.PlaybackStatus.PLAYING) {
            var movement = rhythm.sample(audio.get(), ui.nowMs(), still);
            MusicIsland.drawWaveform(graphics, new Bounds(indicatorX, artY, MusicIsland.WAVEFORM_WIDTH, 18),
                    movement, ACCENT, preferences);
        } else {
            rhythm.reset();
            drawPlaybackIndicator(ui, session == null ? MusicSnapshot.PlaybackStatus.UNKNOWN : session.status(),
                    indicatorX + 7, artY + 3);
        }
        // Compact leading/trailing presentation deliberately leaves the centre quiet.
        // Track title, credit, timeline and lyrics belong to the expanded presentation.
        if (!detail) return;
        renderedMusicTarget = IslandPlaybackShortcut.target(snapshot);
        textRight = indicatorX - 6;

        var state = status(snapshot);
        var track = session == null ? null : session.track();
        int secondary = preferences.highContrast() ? 0xFFFFFFFF : 0xFFCBD5E1;
        int step = MusicTypography.LINE_HEIGHT + 1;
        int y = surfaceTop + 10;
        int textX = x + 25;
        if (session != null) {
            titleLabel.setText(MusicTypography.text(title(session)));
            titleLabel.render(graphics, font, textX, y, Math.max(0, textRight - textX), 0xFFFFFFFF,
                    false, ui.nowMs(), hovered, preferences, MusicTypography.LINE_HEIGHT);
            y += step;
            if (track != null) drawText(ui, font, credit(session), textX, y, textRight, secondary);
        } else drawText(ui, font, Component.translatable(TEXT + "unknown_title"), textX, y, textRight, secondary);
        // Status remains textual in the accessible expanded surface, including buffering/unknown.
        drawText(ui, font, state, surfaceLeft + 12, surfaceTop + 34, surfaceRight - 12, secondary);

        int height = Math.round(outline.rect().getH());
        int lineY = surfaceTop + 47;
        int bottom = surfaceTop + height - 7;
        if (bottom < lineY + step + 4 || track == null) return;
        int left = Math.round(outline.rect().getX()) + 10;
        int right = Math.round(outline.rect().getX() + outline.rect().getW()) - 10;
        long position = session.positionAt(System.nanoTime());
        if (position >= 0 && session.durationMs().isPresent()) {
            long duration = session.durationMs().getAsLong();
            graphics.fill(left, lineY, right, lineY + 2, 0xFF415064);
            int fill = (int) Math.round((right - left) * MusicIslandCapsuleState.progress(position, duration));
            if (fill > 0) graphics.fill(left, lineY, left + fill, lineY + 2, still ? 0xFFFFFFFF : ACCENT);
        }
        var time = position < 0 ? Component.translatable(TEXT + "time_unknown")
                : Component.translatable(TEXT + "time", clock(position),
                        session.durationMs().isPresent() ? clock(session.durationMs().getAsLong())
                                : Component.translatable(TEXT + "duration_unknown"));
        drawText(ui, font, time, left, lineY + 4, right, secondary);
        int lyricY = lineY + step + 7;
        if (bottom - lyricY < step) return;
        var lyricState = current.lyrics().state();
        if (lyricState == MusicService.LyricsState.AVAILABLE && position >= 0) {
            int lyricHeight = bottom - lyricY;
            int lineCount = lyricHeight >= 28 ? 2 : 1;
            if (lyrics.style().lineCount() != lineCount) lyrics.setStyle(lyrics.style().withLineCount(lineCount));
            if (ShBetterUiApi.lyrics().drawScoped(lyrics, ui, font, new Bounds(left, lyricY, right - left, lyricHeight), MusicTypography.font(), MusicTypography.LINE_HEIGHT)) {
                return;
            }
        }
        String hint = lyricState == MusicService.LyricsState.LOADING ? "lyrics_loading"
                : lyricState != MusicService.LyricsState.AVAILABLE ? "lyrics_missing"
                : position < 0 ? "lyrics_no_time" : "lyrics_waiting";
        drawText(ui, font, Component.translatable(TEXT + hint), left, lyricY, right, secondary);
        } finally { graphics.disableScissor(); }
    }

    public void suspend() {
        rhythm.reset();
        titleLabel.reset();
        lifecycle.suspend();
        capsule.reset();
        notices.clear();
        activeNotice = null;
        ambient.reset();
        ambientShown = false;
        expanded = false;
        expansion.snapTo(0);
    }

    private void drawAmbient(UiFrame ui, Font font, Bounds bounds) {
        var chips = ambient.chips();
        if (chips.isEmpty()) return;
        int available = Math.max(0, bounds.width() - 16);
        // Width follows templates, not live values: FPS jitter never changes the island geometry.
        int total = 0;
        for (var chip : chips) total += font.width(MusicTypography.text(chip.widthTemplate()))
                + (chip.unit().isEmpty() ? 0 : font.width(MusicTypography.text(chip.unit())) + 3) + 8;
        if (total - 8 > available) {
            // Compact/narrow hosts show one complete chip, rather than cropping the last reading.
            int index = ui.preferences().reducedMotion() ? 0 : (int) Math.floorMod(ui.nowMs() / 5000, chips.size());
            chips = java.util.List.of(chips.get(index));
            var only = chips.get(0);
            total = font.width(MusicTypography.text(only.widthTemplate())) + (only.unit().isEmpty() ? 0 : font.width(MusicTypography.text(only.unit()))+3)+8;
        }
        int x = bounds.x() + 8 + Math.max(0, (available - total + 8) / 2);
        int y = bounds.y() + (bounds.height() - MusicTypography.LINE_HEIGHT) / 2;
        ui.graphics().enableScissor(bounds.x()+8, bounds.y(), bounds.right()-8, bounds.bottom());
        try {
            for (var chip : chips) {
                int width = font.width(MusicTypography.text(chip.widthTemplate()));
                int color = ui.preferences().highContrast() ? 0xFFFFFFFF : switch (chip.health()) {
                    case GOOD -> 0xFF9DE3C1;
                    case WARN -> 0xFFFFDB91;
                    case BAD -> 0xFFFFB1B1;
                    case NEUTRAL -> 0xFFFFFFFF;
                };
                ui.graphics().drawString(font, MusicTypography.text(chip.value()), x + Math.max(0,(width-font.width(MusicTypography.text(chip.value())))/2), y, color, false);
                x += width;
                if (!chip.unit().isEmpty()) {
                    ui.graphics().drawString(font, MusicTypography.text(chip.unit()), x+3, y, 0xFFFFFFFF, false);
                    x += font.width(MusicTypography.text(chip.unit()))+3;
                }
                x += 8;
            }
        } finally { ui.graphics().disableScissor(); }
    }

    /** Static, truthful transport glyphs; no synthetic waveform when audio features are unavailable. */
    private static void drawPlaybackIndicator(UiFrame ui, MusicSnapshot.PlaybackStatus status, int x, int y) {
        int color = ui.preferences().highContrast() ? 0xFFFFFFFF : ACCENT;
        var graphics = ui.graphics();
        switch (status) {
            case PAUSED -> {
                RoundedCorners.draw(graphics, x + 1, y, 3, 12, 1.5f, 0, color, color, false);
                RoundedCorners.draw(graphics, x + 8, y, 3, 12, 1.5f, 0, color, color, false);
            }
            case STOPPED -> RoundedCorners.draw(graphics, x + 1, y + 1, 10, 10, 2, 0, color, color, false);
            case PLAYING -> { // Static equal-length bars indicate playing, not measured audio amplitude.
                for (int i = 0; i < 3; i++)
                    RoundedCorners.draw(graphics, x + i * 5, y + 2, 2, 8, 1, 0, color, color, false);
            }
            case BUFFERING -> {
                for (int i = 0; i < 3; i++)
                    RoundedCorners.draw(graphics, x + i * 5, y + 5, 2, 2, 1, 0, color, color, false);
            }
            case UNKNOWN -> RoundedCorners.draw(graphics, x + 1, y + 5, 10, 2, 1, 0,
                    0xFFCBD5E1, 0xFFCBD5E1, false);
        }
    }

    private static void drawText(UiFrame ui, Font font, Component text, int x, int y, int right, int color) {
        ui.graphics().drawString(font, MusicTextLayout.ellipsize(font,text,Math.max(0,right-x),MusicTypography.font()), x,y,color,false);
    }

    private static String clock(long millis) {
        long seconds = millis / 1000;
        return seconds / 60 + (seconds % 60 < 10 ? ":0" : ":") + seconds % 60;
    }

    private static Component credit(MusicSnapshot.Session session) {
        var track = session.track();
        var artist = track.artist().map(Component::literal).orElseGet(() -> Component.translatable(TEXT + "unknown_artist"));
        return track.album().<Component>map(album -> Component.translatable(TEXT + "credit", artist, album)).orElse(artist);
    }

    private static Component title(MusicSnapshot.Session session) {
        return session.track() == null ? Component.translatable(TEXT + "unknown_title")
                : session.track().title().map(Component::literal).orElseGet(() ->
                        Component.translatable(TEXT + "unknown_title"));
    }

    private static Component status(MusicSnapshot snapshot) {
        String key = snapshot.session() == null
                ? snapshot.state().name() : snapshot.session().status().name();
        return Component.translatable(TEXT + "status." + key.toLowerCase(Locale.ROOT));
    }
}
