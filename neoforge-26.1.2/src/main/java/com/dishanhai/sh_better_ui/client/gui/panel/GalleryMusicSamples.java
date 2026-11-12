// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricWord;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GalleryMusicSamples extends GallerySampleScreen {
    private static final String ISLAND = "sh_better_ui.panel.developer.gallery.music_island.";
    private static final String[] TRACKS = {"sea", "dusk", "copper"};
    private static final String[] LYRICS = {"first", "second", "third"};
    private static final int[] ACCENTS = {0xFF83DCFA, 0xFFEBB0E8, 0xFFFFC096};
    private static final int SEEK_STEPS = 1000;
    private static final int GAP = 4;
    private static final int MAX_IMPORTED = 16;

    private final GalleryMusicPlayback playback = new GalleryMusicPlayback();
    private final MusicRhythm rhythm = new MusicRhythm();
    private final InterruptibleAnimation capsule = new InterruptibleAnimation(Spring.FLUID_PANEL, 1f);
    private final Kind kind;
    private final List<Path> imported = new ArrayList<>();
    private int track;
    private Selection selection;
    private Component notice;
    private boolean loaded;
    private boolean open = true;
    private boolean hudOverlay = true;
    private LyricTimeline demoLyrics = LyricTimeline.empty();
    private Bounds canvas;
    private UiButton mode;
    private UiButton previous;
    private UiButton transport;
    private UiButton next;
    private UiSlider seek;
    private long shownDuration = -1;
    private Component transportLabel;
    private Component modeLabel;

    private enum Kind { HUD, ISLAND, LOCAL, LYRICS }

    private record Selection(int index, boolean importing) {}

    private GalleryMusicSamples(DeveloperGalleryScreen parent, String id, Kind kind) {
        super(parent, id);
        this.kind = kind;
    }

    static GallerySampleScreen create(DeveloperGalleryScreen parent, String id) {
        return new GalleryMusicSamples(parent, id, switch (id) {
            case "music_hud" -> Kind.HUD;
            case "music_island" -> Kind.ISLAND;
            case "local_music" -> Kind.LOCAL;
            case "lyrics" -> Kind.LYRICS;
            default -> throw new IllegalArgumentException(id);
        });
    }

    @Override
    protected void initSample() {
        if (!loaded) {
            loaded = true;
            selectTrack(0, false);
        }
        int row = body.height() < 84 ? 16 : 20;
        int buttonsY = body.bottom() - row;
        boolean narrowHeight = body.height() < 76;
        int seekY = narrowHeight ? body.bottom() + 8 : buttonsY - row - GAP;
        int seekX = narrowHeight ? body.x() + body.width() / 2 + GAP : body.x();
        int canvasBottom = (narrowHeight ? buttonsY : seekY) - GAP;
        canvas = new Bounds(body.x(), body.y(), body.width(), Math.max(0, canvasBottom - body.y()));
        int modeWidth = kind == Kind.HUD || kind == Kind.ISLAND ? Math.min(120, body.width() / 3) : 0;
        int controlWidth = body.width() - (modeWidth == 0 ? 0 : modeWidth + GAP);
        int edge = Math.min(104, (controlWidth - 2 * GAP) / 3);
        int center = controlWidth - 2 * (edge + GAP);
        previous = addButton(new Bounds(body.x(), buttonsY, edge, row), island("previous"), ignored -> switchTrack(-1), false);
        transport = addButton(new Bounds(body.x() + edge + GAP, buttonsY, center, row), island("play"),
                ignored -> {
                    if (playback.state() == GalleryMusicPlayback.State.ERROR) selectTrack(track, true);
                    else {
                        notice = null;
                        playback.toggle();
                    }
                    refreshControls();
                }, true);
        next = addButton(new Bounds(body.x() + edge + center + 2 * GAP, buttonsY, edge, row),
                island("next"), ignored -> switchTrack(1), false);
        previous.setLeadingIcon(GraphicIcon.PREVIOUS);
        next.setLeadingIcon(GraphicIcon.NEXT);
        seek = addRenderableWidget(ShBetterUiApi.controls().slider(new Bounds(seekX, seekY, body.right() - seekX, row),
                0, SEEK_STEPS, 5, 0, preferences, value -> island("position", clock(seekTime(value)),
                        clock(playback.duration())), ignored -> {}));
        seek.setOnCommit(value -> {
            playback.seek(seekTime(value));
            rhythm.reset();
            refreshControls();
        }, ignored -> refreshControls());
        mode = modeWidth == 0 ? null : addButton(new Bounds(body.right() - modeWidth, buttonsY, modeWidth, row),
                Component.empty(), ignored -> togglePresentation(), false);
        shownDuration = -1;
        transportLabel = modeLabel = null;
        initialFocus = open ? transport : mode;
        refreshControls();
    }

    private void togglePresentation() {
        if (kind == Kind.HUD) hudOverlay = !hudOverlay;
        if (kind == Kind.ISLAND) {
            open = !open;
            capsule.setOpen(open, Util.getMillis());
            seek.cancelEdit();
        }
        refreshControls();
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (compact || !acceptsDrop() || files.isEmpty()) return;
        selection = new Selection(track, true);
        notice = null;
        playback.loadFiles(files);
    }

    private void switchTrack(int delta) {
        int index = selection != null && !selection.importing() ? selection.index() : track;
        selectTrack(Math.floorMod(index + delta, GalleryMusicPlayback.DEMO_COUNT + imported.size()),
                playback.wantsPlayback());
    }

    private void selectTrack(int index, boolean play) {
        selection = new Selection(index, false);
        notice = null;
        Path file = index < GalleryMusicPlayback.DEMO_COUNT ? null : imported.get(index - GalleryMusicPlayback.DEMO_COUNT);
        updateVolume();
        playback.load(index, file, play);
        refreshControls();
    }

    private void completeSelection() {
        var result = playback.completeLoad();
        if (result == null) return;
        var pending = selection;
        selection = null;
        if (result.failure() != null) {
            notice = shared(switch (result.failure()) {
                case UNSUPPORTED -> "unsupported";
                case INVALID_AUDIO -> "invalid_audio";
                case TOO_LARGE -> "too_large";
                case DEVICE -> "failed";
            });
            minecraft.getNarrator().saySystemQueued(notice);
            return;
        }
        if (pending.importing()) {
            Path file = result.file();
            int slot = imported.indexOf(file);
            if (slot < 0) {
                if (imported.size() == MAX_IMPORTED) imported.removeFirst();
                slot = imported.size();
                imported.add(file);
            }
            track = GalleryMusicPlayback.DEMO_COUNT + slot;
            notice = shared("imported");
        } else {
            track = pending.index();
        }
        if (seek != null) seek.cancelEdit();
        rhythm.reset();
        demoLyrics = track == 0 ? demoLyrics() : LyricTimeline.empty();
        Component summary = CommonComponents.joinForNarration(trackName(), status());
        minecraft.getNarrator().saySystemQueued(notice == null ? summary : CommonComponents.joinForNarration(notice, summary));
    }

    @Override
    public void tick() {
        super.tick();
        if (compact || !minecraft.isWindowActive()) {
            playback.pause();
            rhythm.reset();
        }
        updateVolume();
        completeSelection();
        refreshControls();
    }

    @Override
    public void removed() {
        super.removed();
        playback.close();
        capsule.close();
    }

    private void updateVolume() {
        playback.setVolume(minecraft.options.getSoundSourceVolume(SoundSource.MASTER)
                * minecraft.options.getSoundSourceVolume(SoundSource.MUSIC));
    }

    private void refreshControls() {
        if (seek == null || compact) return;
        var state = playback.state();
        long duration = playback.duration();
        boolean expanded = kind != Kind.ISLAND || open;
        seek.visible = seek.active = expanded && duration > 0;
        if (duration != shownDuration) {
            shownDuration = duration;
            seek.refreshLabel();
        }
        if (!seek.isPointerEditing() && duration > 0) {
            seek.setValue((int) Math.round(playback.position() * (double) SEEK_STEPS / duration));
        }
        previous.visible = previous.active = next.visible = next.active = transport.visible = transport.active = expanded;
        boolean playing = playback.wantsPlayback();
        Component label = state == GalleryMusicPlayback.State.ERROR ? shared("retry") : island(playing ? "pause" : "play");
        if (!label.equals(transportLabel)) {
            transportLabel = label;
            transport.setMessage(label);
            transport.setLeadingIcon(playing ? GraphicIcon.PAUSE : GraphicIcon.RESUME);
        }
        if (mode != null) {
            label = kind == Kind.ISLAND ? island(open ? "collapse" : "expand")
                    : shared(hudOverlay ? "panel" : "hud");
            if (!label.equals(modeLabel)) {
                modeLabel = label;
                mode.setMessage(label);
            }
        }
    }

    private long seekTime(int value) {
        return Math.round(value * (double) playback.duration() / SEEK_STEPS);
    }

    private long shownPosition() {
        return seek != null && seek.isPointerEditing() ? seekTime(seek.value()) : playback.position();
    }

    private Component trackName() {
        return track < TRACKS.length ? island(TRACKS[track])
                : Component.literal(imported.get(track - TRACKS.length).getFileName().toString());
    }

    private Component status() {
        return switch (playback.state()) {
            case LOADING -> shared("loading");
            case PLAYING -> music("status.playing");
            case PAUSED -> music("status.paused");
            case ENDED -> music("status.stopped");
            case ERROR -> shared("failed");
        };
    }

    private Component displayStatus() {
        if (selection != null) return shared("loading");
        return notice == null ? status() : notice;
    }

    private boolean acceptsDrop() {
        return kind == Kind.LOCAL || kind == Kind.LYRICS;
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (canvas.height() <= 2) return;
        refreshControls();
        long position = shownPosition();
        long now = Util.getMillis();
        boolean still = preferences.reducedMotion() || preferences.highContrast();
        var motion = rhythm.sample(playback.audio(playback.position(), now), now, still);
        int accent = preferences.highContrast() ? 0xFFFFFFFF
                : theme.light() ? theme.accent() : ACCENTS[Math.floorMod(track, ACCENTS.length)];
        int backColor = preferences.highContrast() ? 0xFF000000 : theme.background() | 0xFF000000;
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), backColor);
        switch (kind) {
            case HUD -> drawHud(graphics, motion, position, accent, mouseX, mouseY);
            case ISLAND -> drawIsland(graphics, motion, position, accent, now, still, mouseX, mouseY);
            case LOCAL -> drawLocalTrack(graphics, motion, position, accent, mouseX, mouseY);
            case LYRICS -> drawLyrics(graphics, position, accent, mouseX, mouseY);
        }
        graphics.disableScissor();
    }

    private void drawHud(GuiGraphicsExtractor graphics, MusicRhythm.Frame motion, long position, int accent,
                         int mouseX, int mouseY) {
        int panelWidth = hudOverlay ? Math.min(210, canvas.width() - 8) : canvas.width() - 8;
        int panelHeight = Math.min(hudOverlay ? 56 : 96, canvas.height() - 4);
        if (panelHeight < 12) return;
        if (hudOverlay && !preferences.highContrast()) {
            for (int y = canvas.y() + 7; y < canvas.bottom(); y += 8) {
                graphics.fill(canvas.x() + 4, y, canvas.right() - 4, y + 1, 0xFF293B49);
            }
        }
        int x = hudOverlay ? canvas.right() - panelWidth - 4 : canvas.x() + 4;
        int y = hudOverlay ? canvas.y() + 2 : canvas.y() + (canvas.height() - panelHeight) / 2;
        RoundedCorners.draw(graphics, x, y, panelWidth, panelHeight, 5, 1, accent, surfaceColor());
        drawTrackInfo(graphics, new Bounds(x + 5, y + 4, panelWidth - 10, panelHeight - 5), position, mouseX, mouseY);
        if (panelHeight >= 44) {
            drawBars(graphics, new Bounds(x + 6, y + 29, panelWidth - 12, panelHeight - 34), motion, accent);
        }
    }

    private void drawIsland(GuiGraphicsExtractor graphics, MusicRhythm.Frame motion, long position, int accent,
                            long now, boolean still, int mouseX, int mouseY) {
        float expansion = GuiMotion.clamp01(capsule.sample(now, still));
        int w = Math.min(Math.round(GuiMotion.lerp(178, 300, expansion)), canvas.width() - 8);
        int h = Math.min(Math.round(GuiMotion.lerp(30, 63, expansion)), canvas.height() - 4);
        if (h < 12) return;
        int x = canvas.x() + (canvas.width() - w) / 2;
        int y = canvas.y() + 2;
        int radius = Math.round(GuiMotion.lerp(15, 10, expansion));
        RoundedCorners.draw(graphics, x, y, w, h, Math.min(h / 2, radius), 1, accent,
                surfaceColor());
        int meter = Math.min(47, w / 3);
        drawTrackInfo(graphics, new Bounds(x + 9, y + 4, w - meter - 16, h - 5), position, mouseX, mouseY);
        if (h >= 24) {
            drawBars(graphics, new Bounds(x + w - meter - 8, y + 5, meter, Math.min(h - 10, 19)), motion, accent);
        }
        if (h >= 44) {
            int total = w - 20;
            long duration = playback.duration();
            int filled = duration == 0 ? 0 : (int) (total * position / duration);
            graphics.fill(x + 10, y + h - 10, x + 10 + total, y + h - 8, theme.border() | 0xFF000000);
            graphics.fill(x + 10, y + h - 10, x + 10 + filled, y + h - 8, accent);
        }
    }

    private void drawTrackInfo(GuiGraphicsExtractor graphics, Bounds area, long position, int mouseX, int mouseY) {
        PanelText.drawLine(graphics, font, trackName(), area.x(), area.y(), area.width(), textColor(), mouseX, mouseY);
        if (area.height() >= 2 * font.lineHeight + 1) {
            PanelText.drawLine(graphics, font, music("narration_no_track", displayStatus(),
                            music("time", clock(position), clock(playback.duration()))),
                    area.x(), area.y() + font.lineHeight + 1, area.width(), textColor(), mouseX, mouseY);
        }
    }

    private void drawBars(GuiGraphicsExtractor graphics, Bounds area, MusicRhythm.Frame motion, int accent) {
        int spacing = Math.max(2, area.width() / 17);
        int width = Math.max(1, spacing - 1);
        for (int index = 0; index < 16; index++) {
            int x = area.x() + index * spacing;
            if (x + width > area.right()) break;
            float band = index < 5 ? motion.bass() : index < 12 ? motion.mid() : motion.treble();
            float shaped = Math.min(1, band * (0.65f + 0.35f * (index % 5) / 4f)
                    + Math.abs(motion.wave(index * .65f)) * .22f + motion.sparkle(index * .9f) * .16f);
            int barHeight = Math.max(1, Math.round(shaped * area.height()));
            graphics.fill(x, area.bottom() - barHeight, x + width, area.bottom(), accent);
        }
    }

    private void drawLocalTrack(GuiGraphicsExtractor graphics, MusicRhythm.Frame motion, long position,
                                int accent, int mouseX, int mouseY) {
        int x = canvas.x() + 6;
        int y = canvas.y() + 5;
        int available = canvas.width() - 12;
        int hintY = drawDropHint(graphics, x, available, mouseX, mouseY);
        int panelHeight = Math.min(62, hintY - y - 4);
        if (panelHeight < 35) {
            y = canvas.y() + 1;
            drawTrackInfo(graphics, new Bounds(x, y, available, hintY - y), position, mouseX, mouseY);
            return;
        }
        RoundedCorners.draw(graphics, x, y, available, panelHeight, 7, 1, accent, surfaceColor());
        GraphicIcon.MUSIC.draw(graphics, x + 6, y + 5, 1, accent, preferences);
        drawTrackInfo(graphics, new Bounds(x + 29, y + 4, available - 36, panelHeight - 5), position, mouseX, mouseY);
        drawBars(graphics, new Bounds(x + 7, y + 29, available - 14, panelHeight - 35), motion, accent);
        int bottom = y + panelHeight + 5;
        if (bottom + font.lineHeight <= hintY - 2) {
            var line = currentLine(position);
            Component caption = line == null ? lyricStatus() : Component.literal(line.text());
            PanelText.drawLine(graphics, font, caption, x, bottom, available, textColor(), mouseX, mouseY);
        }
    }

    private void drawLyrics(GuiGraphicsExtractor graphics, long position, int accent, int mouseX, int mouseY) {
        int x = canvas.x() + 6;
        int available = canvas.width() - 12;
        int hintY = drawDropHint(graphics, x, available, mouseX, mouseY);
        int titleY = canvas.y() + (canvas.height() < 45 ? 1 : 4);
        drawTrackInfo(graphics, new Bounds(x, titleY, available, hintY - titleY), position, mouseX, mouseY);
        int top = titleY + 2 * font.lineHeight + 5;
        int step = font.lineHeight + 2;
        int lower = hintY - step;
        if (lower < top) return;
        var line = currentLine(position);
        if (line == null) {
            PanelText.drawLine(graphics, font, lyricStatus(), x, top, available, textColor(), mouseX, mouseY);
        } else {
            int color = textColor();
            var text = Component.empty();
            if (preferences.highContrast()) text.append("▶ ");
            for (var word : line.words()) {
                float progress = preferences.reducedMotion() || preferences.highContrast()
                        ? position >= word.startMs() ? 1 : 0 : word.progressAt(position);
                int wordColor = GuiMotion.lerpColor(color, accent, progress);
                text.append(Component.literal(word.text()).withColor(wordColor & 0xFFFFFF));
            }
            var wrapped = font.split(text, available);
            if (top + wrapped.size() * step > lower + step) {
                PanelText.drawLine(graphics, font, text, x, top, available, color, mouseX, mouseY);
            } else {
                for (var part : wrapped) {
                    graphics.text(font, part, x + (available - font.width(part)) / 2, top, color, false);
                    top += step;
                }
                if (!line.translation().isEmpty() && top <= lower) {
                    PanelText.drawLine(graphics, font, Component.literal(line.translation()), x, top, available,
                            color, mouseX, mouseY);
                }
            }
        }
    }

    private int drawDropHint(GuiGraphicsExtractor graphics, int x, int available, int mouseX, int mouseY) {
        int y = canvas.bottom() - font.lineHeight - 2;
        Component hint = shared("import");
        PanelText.drawLine(graphics, font, font.width(hint) <= available ? hint : shared("import_short"),
                x, y, available, textColor(), mouseX, mouseY);
        return y;
    }

    private LyricTimeline lyrics() {
        return track == 0 ? demoLyrics : playback.lyrics();
    }

    private LyricLine currentLine(long position) {
        var line = lyrics().lineAt(position);
        return line != null && position < line.endMs() ? line : null;
    }

    private Component lyricStatus() {
        if (playback.state() == GalleryMusicPlayback.State.LOADING) return music("lyrics_loading");
        if (lyrics().isEmpty()) return shared("no_lyrics");
        return music("lyrics_waiting");
    }

    @Override
    public Component getNarrationMessage() {
        var summary = CommonComponents.joinForNarration(getTitle(), trackName(), status());
        if (selection != null || notice != null) summary = CommonComponents.joinForNarration(summary, displayStatus());
        if (!acceptsDrop()) return summary;
        var line = currentLine(shownPosition());
        Component words = line == null ? lyricStatus() : Component.literal(line.text());
        return CommonComponents.joinForNarration(summary, words, shared("import"));
    }

    private static LyricTimeline demoLyrics() {
        List<LyricLine> lines = new ArrayList<>(LYRICS.length);
        int beat = GalleryMusicPlayback.beatMs(0);
        for (int index = 0; index < LYRICS.length; index++) {
            long start = (index * 8L + 1) * beat;
            List<LyricWord> words = new ArrayList<>(3);
            var line = new StringBuilder();
            String key = "sh_better_ui.lyrics.demo." + LYRICS[index] + ".";
            for (int word = 0; word < 3; word++) {
                String value = Component.translatable(key + word).getString();
                line.append(value);
                words.add(new LyricWord(value, start + word * 2L * beat, start + (word + 1) * 2L * beat));
            }
            String translation = Component.translatable(key + "translation").getString();
            lines.add(new LyricLine(line.toString(), translation, start, start + 7L * beat, words, ""));
        }
        return new LyricTimeline(lines);
    }

    private int textColor() {
        return preferences.highContrast() ? 0xFFFFFFFF : theme.text();
    }

    private int surfaceColor() {
        return preferences.highContrast() ? 0xFF000000 : theme.surface() | 0xFF000000;
    }

    private static String clock(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static Component island(String suffix, Object... args) {
        return Component.translatable(ISLAND + suffix, args);
    }

    private static Component music(String suffix, Object... args) {
        return Component.translatable("sh_better_ui.music." + suffix, args);
    }

    private static Component shared(String suffix, Object... args) {
        return Component.translatable("sh_better_ui.panel.developer.gallery.music_preview." + suffix, args);
    }
}
