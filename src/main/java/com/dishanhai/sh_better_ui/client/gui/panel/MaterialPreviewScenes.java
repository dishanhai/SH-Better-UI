package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIconMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRain;
import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRainEffects;
import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRainProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LaserFlow;
import com.dishanhai.sh_better_ui.client.gui.scaled.LaserFlowProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.LaserFlowSurface;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.BUTTON_HEIGHT;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.addButton;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.centeredControl;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.drawBackdrop;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.drawCentered;

/** 山海：共享不同按钮样式、玻璃配方和资源图标的演示。 */
final class MaterialPreviewScenes {
    static final int EFFECT_HEIGHT = 96;
    private static final int EFFECT_GAP = 6;

    private MaterialPreviewScenes() {}

    static final class CodeRainPreview implements PreviewScene {
        private final CodeRainEffects.Interaction interaction =
                CodeRainEffects.interaction(true, CodeRainEffects.Click.WAVE, .18f);
        private final CodeRain rain = new CodeRain(CodeRainProfile.DEFAULT, interaction);
        private Font font;
        private Bounds canvas;
        private UiButton mode;

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
            canvas = effectCanvas(host.viewport());
            mode = addButton(host, effectButton(canvas, false), modeName(), GraphicIcon.CODE, () -> {
                rain.setProfile(rain.profile().mode() == CodeRainProfile.Mode.RAIN
                        ? CodeRainProfile.ASCII : CodeRainProfile.DEFAULT);
                mode.setMessage(modeName());
            });
            mode.setPreview(label("gallery.code_rain.mode_hint"));
            var pulse = addButton(host, effectButton(canvas, true), label("gallery.code_rain.pulse"),
                    null, () -> interaction.pulse(canvas.width() * .5f, canvas.height() * .5f));
            pulse.active = !host.preferences().highContrast() && !host.preferences().reducedMotion();
            pulse.setPreview(label("gallery.code_rain.pulse_hint"));
        }

        private Component modeName() {
            return label(rain.profile().mode() == CodeRainProfile.Mode.RAIN
                    ? "gallery.code_rain.rain" : "gallery.code_rain.ascii");
        }

        @Override
        public void render(UiFrame frame) {
            drawEffectCanvas(frame, canvas);
            if (!rain.draw(frame.graphics(), font, canvas, frame.mouse(), frame.preferences())) {
                drawEffectStatus(frame, font, canvas, label("gallery.effect_contrast"));
            }
        }

        @Override public void suspend() { rain.suspend(); }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            CodeRainProfile profile = rain.profile();
            return () -> {
                var restored = new CodeRainPreview();
                restored.rain.setProfile(profile);
                return restored;
            };
        }
    }

    static final class LaserFlowPreview implements PreviewScene {
        private final LaserFlow laser = new LaserFlow(LaserFlowProfile.builder().mouseInteraction(true).build());
        private Font font;
        private Bounds canvas;
        private LaserFlowSurface surface;
        private UiButton playback;

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
            canvas = effectCanvas(host.viewport());
            int surfaceWidth = Math.min(180, canvas.width() * 2 / 3);
            surface = new LaserFlowSurface(new Bounds(canvas.x() + (canvas.width() - surfaceWidth) / 2,
                    canvas.y() + canvas.height() / 3, surfaceWidth, canvas.height() / 3), 6);
            playback = addButton(host, effectButton(canvas, false), playbackName(), null, () -> {
                laser.setProfile(laser.profile().toBuilder().paused(!laser.profile().paused()).build());
                playback.setMessage(playbackName());
            });
            var replay = addButton(host, effectButton(canvas, true), label("gallery.effect_replay"),
                    GraphicIcon.RESET, () -> {
                        laser.setProfile(laser.profile().toBuilder().paused(false).build());
                        playback.setMessage(playbackName());
                        laser.restart();
                    });
            playback.active = replay.active = !host.preferences().highContrast() && !host.preferences().reducedMotion();
        }

        private Component playbackName() {
            return label(laser.profile().paused() ? "gallery.effect_resume" : "gallery.effect_pause");
        }

        @Override
        public void render(UiFrame frame) {
            drawEffectCanvas(frame, canvas);
            var card = surface.bounds();
            RoundedCorners.draw(frame.graphics(), card.x(), card.y(), card.width(), card.height(),
                    surface.radius(), 1, DarkModeTheme.borderColor(), DarkModeTheme.surfaceColor());
            if (!laser.draw(frame.graphics(), canvas, surface, null, frame.mouse(), frame.preferences())) {
                drawEffectStatus(frame, font, canvas, label(frame.preferences().highContrast()
                        ? "gallery.effect_contrast" : "gallery.laser_flow.unavailable"));
            }
        }

        @Override public void suspend() { laser.suspend(); }

        @Override
        public Supplier<? extends PreviewScene> saveForRemount() {
            LaserFlowProfile profile = laser.profile();
            return () -> {
                var restored = new LaserFlowPreview();
                restored.laser.setProfile(profile);
                return restored;
            };
        }
    }

    private static Bounds effectCanvas(Bounds viewport) {
        return new Bounds(viewport.x() + EFFECT_GAP, viewport.y() + EFFECT_GAP, viewport.width() - 2 * EFFECT_GAP,
                viewport.height() - BUTTON_HEIGHT - 3 * EFFECT_GAP);
    }

    private static Bounds effectButton(Bounds canvas, boolean second) {
        int width = (canvas.width() - EFFECT_GAP) / 2;
        return new Bounds(second ? canvas.right() - width : canvas.x(), canvas.bottom() + EFFECT_GAP,
                width, BUTTON_HEIGHT);
    }

    private static void drawEffectCanvas(UiFrame frame, Bounds canvas) {
        frame.graphics().fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), DarkModeTheme.backgroundColor());
    }

    private static void drawEffectStatus(UiFrame frame, Font font, Bounds canvas, Component message) {
        drawCentered(frame.graphics(), font, message, canvas.x() + EFFECT_GAP,
                canvas.y() + (canvas.height() - BUTTON_HEIGHT) / 2, canvas.width() - 2 * EFFECT_GAP);
    }

    static final class StyledButton implements PreviewScene {
        private final UiStyle style;
        private boolean selected;

        StyledButton(UiStyle style) {
            this.style = style;
        }

        @Override
        public void layout(PreviewContext host) {
            var area = centeredControl(host.viewport());
            var button = new UiButton(area.x(), area.y(), area.width(), area.height(), Component.translatable("gui.done"),
                    pressed -> {
                        selected = !selected;
                        ((UiButton) pressed).setSelected(selected);
                    }, host.preferences(), false, style);
            button.setLeadingIcon(GraphicIcon.RESET);
            button.setSelected(selected);
            host.add(button);
        }
    }

    static final class Glass implements PreviewScene {
        private final GlassStyle style;
        private Font font;

        Glass(GlassStyle style) {
            this.style = style;
        }

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
        }

        @Override
        public void render(UiFrame frame) {
            var preview = frame.viewport();
            var graphics = frame.graphics();
            int x = preview.x() + 8;
            int y = preview.y() + 5;
            int width = preview.width() - 16;
            int height = preview.height() - 10;
            drawBackdrop(graphics, x, y, width, height);
            boolean sampled = frame.preferences().useBackdropBlur() && GlassBlur.tryBackdrop(graphics,
                    new GuiRect(x, y, width, height), 1f, style, 12f, 4f);
            if (!sampled) {
                graphics.fill(x, y, x + width, y + height, 0x9020252E);
                drawCentered(graphics, font, label("gallery.blur_unavailable"), x + 3, y + 7, width - 6);
            }
        }
    }

    static final class IconMotion implements PreviewScene {
        private static final GraphicIconMotion[] MODES = GraphicIconMotion.values();
        private static final List<GraphicIcon> ICONS = Arrays.stream(MODES).map(GraphicIcon.STATUS_PENDING::withMotion).toList();
        private Font font;

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
        }

        @Override
        public void render(UiFrame frame) {
            var area = frame.viewport();
            int width = area.width() / MODES.length;
            int scale = width >= 40 && area.height() >= 58 ? 2 : 1;
            int size = GraphicIcon.size(scale);
            if (width < size || area.height() < size + 24) return;
            int y = area.y() + (area.height() - size - 24) / 2;
            for (int i = 0; i < MODES.length; i++) {
                var motion = MODES[i];
                int x = area.x() + i * width;
                ICONS.get(i).draw(frame.graphics(), x + (width - size) / 2, y,
                        scale, DarkModeTheme.textColor(), frame.preferences());
                drawCentered(frame.graphics(), font, label("gallery.icon_motion." + motion.name().toLowerCase(Locale.ROOT)),
                        x, y + size + 4, width);
            }
        }
    }

    static final class Icon implements PreviewScene {
        private final GraphicIcon icon;
        private Font font;

        Icon(GraphicIcon icon) {
            this.icon = icon;
        }

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
        }

        @Override
        public void render(UiFrame frame) {
            var preview = frame.viewport();
            var graphics = frame.graphics();
            int x = preview.x() + 10;
            int size = GraphicIcon.size(2);
            icon.draw(graphics, x, preview.y() + (preview.height() - size) / 2, 2, 0xFFFFFFFF, frame.preferences());
            int pathX = x + size + 8;
            var lines = font.split(Component.literal(icon.texture().toString()), preview.right() - pathX - 8);
            int y = preview.y() + (preview.height() - lines.size() * font.lineHeight) / 2;
            for (var line : lines) {
                graphics.drawString(font, line, pathX, y, LiquidGlassTheme.textColor(), false);
                y += font.lineHeight;
            }
        }
    }

    static final class GradientCard implements PreviewScene {
        private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.card();
        private Font font;

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
        }

        @Override
        public void render(UiFrame frame) {
            var preview = frame.viewport();
            var graphics = frame.graphics();
            int width = Math.min(164, preview.width() - 32);
            int x = preview.x() + (preview.width() - width) / 2;
            int y = preview.y() + 6;
            var state = motion.sample(false, false, false, true, frame.preferences(), frame.nowMs());
            DarkModeTheme.card(graphics, x, y, width, BUTTON_HEIGHT, state,
                    () -> drawCentered(graphics, font, Component.translatable("gui.stats"), x + 6, y, width - 12));
        }
    }
}
