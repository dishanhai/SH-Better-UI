package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.FluidMorph;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation.BlendCurve;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicIsland;
import com.dishanhai.sh_better_ui.client.gui.scaled.IslandShellStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：固定头部、同底板展开与可操作的无声曲目。 */
final class MusicIslandGalleryPreview implements PreviewScene {
    static final int HEIGHT = 112;
    private static final String TEXT = "gallery.music_island.";
    private static final int COMPACT_HEIGHT = 26;
    private static final int PANEL_HEIGHT = 68;
    private static final int DURATION = 36;
    private static final List<Track> TRACKS = List.of(
            new Track("sea", 0xFF72DDD0, 667, .64f, 0),
            new Track("dusk", 0xFFB4A0FC, 486, 1f, 1.5),
            new Track("copper", 0xFFFFB57B, 552, .82f, 3.0));

    private IslandShellStyle shellStyle = IslandShellStyle.CLASSIC;
    private final MusicRhythm rhythm = ShBetterUiApi.motion().musicRhythm();
    private final InterruptibleAnimation expansion = new InterruptibleAnimation(new Spring.Preset(.28, .64, .00035f, .02f));
    private final InterruptibleAnimation tint = new InterruptibleAnimation(Spring.STAGGER, 1f);
    private PreviewContext host;
    private Bounds compact, panel;
    private Header header;
    private UiSlider seek;
    private List<UiButton> transport = List.of();
    private UiButton toggle;
    private int track;
    private int fromAccent = TRACKS.get(0).accent();
    private long elapsed, lastMs = Long.MIN_VALUE;
    private boolean playing = true, expanded;
    private Component title;
    private Component status;
    private MusicRhythm.Frame motion = MusicRhythm.Frame.STILL;
    private int accent = fromAccent;
    private float progress;
    private FluidMorph outline;

    @Override
    public void layout(PreviewContext host) {
        this.host = host;
        header = null;
        seek = null;
        toggle = null;
        transport = List.of();
        var area = host.viewport();
        if (area.width() < 100 || area.height() < COMPACT_HEIGHT + 24) return;
        int availableWidth = (area.width() - 4) & ~1;
        int width = Math.min(148, availableWidth);
        int panelWidth = Math.min(204, availableWidth);
        int y = area.y() + 24;
        var styleButton = host.add(new UiButton(area.x() + (area.width() - Math.min(150, area.width() - 4)) / 2,
                area.y() + 2, Math.min(150, area.width() - 4), 18, styleLabel(), button -> {
                    shellStyle = shellStyle.next();
                    button.setMessage(styleLabel());
                }, host.preferences(), false));
        styleButton.setTooltip(Tooltip.create(Component.translatable("sh_better_ui.music_overlay.style")));
        compact = new Bounds(area.x() + (area.width() - width) / 2, y, width, COMPACT_HEIGHT);
        panel = new Bounds(area.x() + (area.width() - panelWidth) / 2, y, panelWidth, PANEL_HEIGHT);
        boolean fits = panel.bottom() < area.bottom();
        expansion.snapTo(expanded && fits ? 1f : 0f);
        progress = expansion.value();
        outline = MusicIsland.outline(compact, panel, progress);
        header = host.add(new Header());
        header.active = fits;
        if (fits) {
            seek = host.add(new UiSlider(new Bounds(panel.x() + 12, y + 28, panel.width() - 24, 16),
                    0, DURATION, 1, (int) (elapsed / 1000), host.preferences(),
                    second -> label(TEXT + "position", clock(second), clock(DURATION)), second -> {
                        elapsed = second * 1000L;
                        lastMs = PageTransition.nowMs();
                    }));
            int first = panel.x() + panel.width() / 2 - 45;
            int row = y + 46;
            var previous = control(first, row, "previous", GraphicIcon.PREVIOUS, () -> changeTrack(-1));
            toggle = control(first + 29, row, "pause", GraphicIcon.PAUSE, this::togglePlayback);
            var next = control(first + 64, row, "next", GraphicIcon.NEXT, () -> changeTrack(1));
            transport = List.of(previous, toggle, next);
            updateBody();
        }
        updateLabels();
    }

    private Component styleLabel() {
        return Component.translatable("sh_better_ui.music_overlay.style").append(": ")
                .append(Component.translatable("sh_better_ui.music_overlay.style." + shellStyle.id()));
    }

    private UiButton control(int x, int y, String key, GraphicIcon icon, Runnable action) {
        boolean primary = key.equals("pause");
        var button = new UiButton(x, y, primary ? 32 : 26, 18, label(TEXT + key),
                pressed -> action.run(), host.preferences(), primary);
        button.setIcon(icon);
        return host.add(button);
    }

    private void togglePanel() {
        expanded = !expanded;
        float target = expanded ? 1f : 0f;
        long now = PageTransition.nowMs();
        if (still()) expansion.snapTo(target);
        else if (shellStyle == IslandShellStyle.BOUNCE) expansion.animateTo(target, now);
        else expansion.blendTo(target, now, 0, expanded ? 265 : 220, BlendCurve.SMOOTHSTEP);
        updateLabels();
        updateBody();
    }

    private void togglePlayback() {
        long now = PageTransition.nowMs();
        advanceClock(now);
        if (!playing && elapsed >= DURATION * 1000L) elapsed = 0;
        playing = !playing;
        updateLabels();
    }

    private void changeTrack(int step) {
        long now = PageTransition.nowMs();
        fromAccent = currentAccent(now);
        track = Math.floorMod(track + step, TRACKS.size());
        tint.snapTo(still() ? 1f : 0f);
        if (!still()) tint.blendTo(1f, now, 0, 460, BlendCurve.SMOOTHSTEP);
        elapsed = 0;
        lastMs = now;
        updateLabels();
    }

    @Override
    public void render(UiFrame frame) {
        if (header == null) {
            GalleryPreviewLayout.drawCentered(frame.graphics(), host.font(), label(TEXT + "space"),
                    frame.viewport().x(), frame.viewport().y(), frame.viewport().width());
            return;
        }
        long now = frame.nowMs();
        advanceClock(now);
        motion = rhythm.sample(playing ? TRACKS.get(track).audio(now, elapsed) : AudioFrame.silence(now), now, still());
        accent = currentAccent(now);
        progress = expansion.sample(now, still());
        outline = MusicIsland.outline(compact, panel, progress);
        updateBody();
        if (seek != null && seek.visible) seek.setValue((int) (elapsed / 1000));
        if (frame.viewport().bottom() >= compact.bottom() + 12 && !header.active) {
            GalleryPreviewLayout.drawCentered(frame.graphics(), host.font(), label(TEXT + "space"),
                    frame.viewport().x(), compact.bottom() - 3, frame.viewport().width());
        }
    }

    private void advanceClock(long now) {
        if (lastMs != Long.MIN_VALUE && now > lastMs && playing) {
            elapsed += now - lastMs;
            if (elapsed >= DURATION * 1000L) {
                elapsed = DURATION * 1000L;
                playing = false;
                updateLabels();
            }
        }
        lastMs = Math.max(lastMs, now);
    }

    private void updateBody() {
        if (seek == null) return;
        var rect = outline.rect();
        float height = rect.getH();
        boolean enabled = expanded && progress == 1f;
        seek.visible = height >= 50;
        seek.active = enabled;
        if (seek.visible) {
            seek.setX(Math.round(rect.getX()) + 12);
            seek.setWidth(Math.round(rect.getW()) - 24);
        }
        for (var button : transport) {
            button.visible = height >= 66;
            button.active = enabled;
        }
    }

    private void updateLabels() {
        title = label(TEXT + TRACKS.get(track).title());
        status = label(TEXT + (playing ? "playing" : "paused"));
        var action = label(TEXT + (header.active ? expanded ? "collapse" : "expand" : "space"));
        header.setMessage(label(TEXT + "header", action, title, status));
        header.setTooltip(Tooltip.create(header.getMessage()));
        if (toggle != null) {
            toggle.setMessage(label(TEXT + (playing ? "pause" : "play")));
            toggle.setIcon(playing ? GraphicIcon.PAUSE : GraphicIcon.RESUME);
        }
    }

    private boolean still() {
        return host.preferences().reducedMotion() || host.preferences().highContrast();
    }

    private int currentAccent(long now) {
        return GuiMotion.lerpColor(fromAccent, TRACKS.get(track).accent(), tint.sample(now, still()));
    }

    private static String clock(int seconds) {
        return (seconds / 60) + (seconds % 60 < 10 ? ":0" : ":") + seconds % 60;
    }

    private static String fit(Font font, Component text, int width) {
        String value = text.getString();
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, width - font.width("…")) + "…";
    }

    @Override
    public void suspend() {
        lastMs = Long.MIN_VALUE;
        rhythm.reset();
    }

    private final class Header extends Button {
        private Header() {
            super(compact.x(), compact.y(), compact.width(), compact.height(), Component.empty(),
                    ignored -> togglePanel(), DEFAULT_NARRATION);
            PlatformUiApi.setTooltipDelay(this, host.preferences().feedback().tooltipDelayMs());
        }

        @Override
        protected boolean clicked(double x, double y) {
            return super.clicked(x, y) && withinCapsule(x, y);
        }

        @Override
        public boolean isMouseOver(double x, double y) {
            return super.isMouseOver(x, y) && withinCapsule(x, y);
        }

        private boolean withinCapsule(double x, double y) {
            double radius = shellStyle.shell(COMPACT_HEIGHT * .5f, COMPACT_HEIGHT, false, host.preferences().highContrast()).radius();
            double dx = x - UiMath.clamp(x, compact.x() + radius, compact.right() - radius);
            double dy = y - UiMath.clamp(y, compact.y() + radius, compact.bottom() - radius);
            return dx * dx + dy * dy <= radius * radius;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            var prefs = host.preferences();
            MusicIsland.drawShell(g, outline, prefs, active && withinCapsule(mouseX, mouseY), shellStyle);
            int x = compact.x(), y = compact.y();
            boolean contrast = prefs.highContrast();
            int foreground = contrast ? 0xFFFFFFFF : 0xFFF2F5FA;
            int secondary = contrast ? 0xFFFFFFFF : 0xFFB2BECD;
            if (progress > .14f) {
                var rect = outline.rect();
                int inset = 12;
                int left = Math.round(rect.getX()) + inset, right = Math.round(rect.getX() + rect.getW()) - inset;
                int rule = GuiMotion.lerpColor(0, contrast ? foreground : 0xFF344052, (progress - .14f) / .46f);
                g.fill(left, y + 25, right, y + 26, rule);
            }
            if (isFocused()) {
                RoundedCorners.draw(g, x + 1, y + 1, compact.width() - 2, COMPACT_HEIGHT - 2,
                        12, contrast ? 2 : 1, contrast ? foreground : 0xFFB8E7FF, 0);
            }
            boolean showArt = compact.width() >= 124;
            if (showArt) {
                int art = contrast ? 0xFFFFFFFF : 0xFF233141;
                g.fill(x + 11, y + 5, x + 25, y + 21, art);
                g.fill(x + 10, y + 6, x + 26, y + 20, art);
                GraphicIcon.MUSIC.draw(g, x + 10, y + 5, 1, contrast ? 0xFF050608 : foreground, prefs);
            }

            int waveX = compact.right() - 12 - 9 - MusicIsland.WAVEFORM_WIDTH;
            var font = host.font();
            int textX = x + (showArt ? 31 : 13);
            int textWidth = waveX - textX - 3;
            g.drawString(font, fit(font, title, textWidth), textX, y + 4, foreground, false);
            g.drawString(font, fit(font, status, textWidth), textX, y + 14, secondary, false);
            MusicIsland.drawWaveform(g, new Bounds(waveX, y + 4, MusicIsland.WAVEFORM_WIDTH, 18), motion, accent, prefs);
            if (active) {
                int cx = compact.right() - 12, cy = y + 13;
                int dy = expanded ? -1 : 1;
                for (int offset = -2; offset <= 2; offset++) {
                    int stepY = cy + dy * (1 - Math.abs(offset));
                    g.fill(cx + offset, stepY, cx + offset + 1, stepY + 1, secondary);
                }
            }
        }
    }

    private record Track(String title, int accent, int beatMs, float intensity, double voicing) {
        AudioFrame audio(long now, long elapsed) {
            double beat = elapsed / (double) beatMs;
            double section = .54 + .46 * Math.sin(elapsed * .00037 + voicing);
            double kick = Math.exp(-(elapsed % beatMs) / 115d) * ((long) beat % 4 == 0 ? 1 : .58);
            double notes = .57 + .29 * Math.sin(beat * 1.68 + voicing) + .14 * Math.cos(beat * .79);
            double taps = Math.exp(-((elapsed + 113) % (beatMs / 2)) / 72d);
            float low = (float) (intensity * (.026 + kick * (.08 + .16 * section)));
            float mid = (float) (intensity * (.025 + notes * (.065 + .23 * section)));
            float high = (float) (intensity * (.008 + taps * (.025 + .12 * section)));
            float rms = (float) (intensity * (.08 + .21 * section + .065 * kick));
            return new AudioFrame(now, rms, 1.35f * rms, low, mid, high, (float) kick, (long) beat);
        }
    }
}
