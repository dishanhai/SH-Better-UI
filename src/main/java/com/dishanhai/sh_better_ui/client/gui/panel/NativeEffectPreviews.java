// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.glass.GlassTheme;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIconShader;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiAttention;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/** Small gallery scenes that call the same drawing APIs as a consuming screen. */
final class NativeEffectPreviews implements PreviewScene {
    private static final String LABEL = "sh_better_ui.panel.developer.gallery.sample.";
    private static final String BLUR_UNAVAILABLE = "sh_better_ui.panel.developer.gallery.blur_unavailable";

    private enum Effect {
        ATTENTION_INDICATOR, BACKDROP_BLUR, CLASSIC_GLASS_THEMES, DIRECTIONAL_BLUR,
        FORMATTED_TEXT_EFFECTS, GLASS_PANEL_SHADOW, GLASS_TEXT_GLOW, ICON_SHADER,
        RADIAL_GRADIENT, REFRACTION_WAVE, SCREEN_CROSSFADE, SPATIAL_LIGHTING,
        SPATIAL_SHADOW, SPATIAL_SURFACE, BUTTON_FEEDBACK, GLASS_TOOLTIP;

        String id() { return name().toLowerCase(Locale.ROOT); }
    }

    private final Effect effect;
    private final UiAttention attention = new UiAttention();
    private final ButtonFeedback feedback = new ButtonFeedback();
    private SpatialMaterialPreview spatial;
    private PreviewContext host;
    private Bounds canvas;
    private long started;

    private NativeEffectPreviews(Effect effect) {
        this.effect = effect;
    }

    static void registerInto(PreviewRegistry registry) {
        for (var effect : Effect.values()) {
            registry.register(new PreviewSpec("sh_better_ui:" + effect.id(), PreviewSpec.Category.MATERIALS,
                    310 + effect.ordinal(), LABEL + effect.id(), 106, 0, "", () -> new NativeEffectPreviews(effect)));
        }
    }

    @Override
    public void layout(PreviewContext host) {
        this.host = host;
        var viewport = host.viewport();
        canvas = new Bounds(viewport.x() + 6, viewport.y() + 6, viewport.width() - 12, viewport.height() - 12);
        started = PageTransition.nowMs();
        if (effect == Effect.SCREEN_CROSSFADE && canvas.width() >= 40 && canvas.height() >= 22) {
            var button = new UiButton(canvas.x(), canvas.y(), canvas.width(), 20,
                    Component.translatable("sh_better_ui.panel.developer.gallery.crossfade_open"),
                    source -> openCrossfade(host, source), host.preferences(), false);
            button.active = host.interactive();
            host.add(button);
        }
        if (spatial != null) {
            spatial.close();
            spatial = null;
        }
        if (canvas.width() >= 48 && canvas.height() >= 32) {
            spatial = switch (effect) {
                case SPATIAL_SURFACE -> SpatialMaterialPreview.surfaces(canvas);
                case SPATIAL_SHADOW -> SpatialMaterialPreview.shadows(canvas);
                case SPATIAL_LIGHTING -> SpatialMaterialPreview.lighting(canvas);
                default -> null;
            };
        }
    }

    @Override
    public void render(UiFrame frame) {
        var g = frame.graphics();
        if (canvas.width() < 40 || canvas.height() < 32) return;
        if (spatial != null) {
            spatial.draw(frame);
            return;
        }
        int x = canvas.x(), y = canvas.y();
        int middle = y + canvas.height() / 2;
        long elapsed = Math.max(0L, frame.nowMs() - started);
        var title = Component.translatable(LABEL + effect.id());
        var materials = ShBetterUiApi.materials();
        switch (effect) {
            case ATTENTION_INDICATOR -> {
                int width = (canvas.width() - 24) / 2;
                int index = frame.preferences().reducedMotion() ? 0 : (int) (elapsed / 1200 % 2);
                var left = new Bounds(x + 6, middle - 10, width, 20);
                var right = new Bounds(canvas.right() - width - 6, middle - 10, width, 20);
                var target = index == 0 ? left : right;
                attention.select(index, target.x(), target.y(), width, 20, frame.nowMs());
                var state = attention.sample(frame.nowMs(), frame.preferences());
                DarkModeTheme.background(g, left.x(), left.y(), width, 20);
                DarkModeTheme.background(g, right.x(), right.y(), width, 20);
                materials.slateSelectionIndicator(g, state, frame.preferences());
                materials.slateAttentionTarget(g, target, state, frame.preferences());
            }
            case BACKDROP_BLUR, DIRECTIONAL_BLUR, REFRACTION_WAVE -> drawBlur(frame, elapsed);
            case CLASSIC_GLASS_THEMES -> drawThemes(frame);
            case FORMATTED_TEXT_EFFECTS -> {
                var graphics = ShBetterUiApi.graphics();
                var animated = graphics.applyTextEffect(title,
                        ResourceLocation.fromNamespaceAndPath("sh_better_ui", "shanhai_dawn"), "~");
                graphics.drawFormattedText(g, host.font(), animated, x + 4, middle - 13, 0xffffffff,
                        false, frame.preferences());
                g.drawString(host.font(), graphics.gradientText(title.getVisualOrderText(), 0x65d9bf, 0xad9dfc),
                        x + 4, middle + 3, 0xffffffff, false);
            }
            case GLASS_PANEL_SHADOW -> {
                g.fill(canvas.x(), y, canvas.right(), canvas.bottom(), 0xff68788a);
                var area = inset(12);
                GlassTheme.GLASS.rootShadow(g, area.x(), area.y(), area.width(), area.height());
                g.fill(area.x(), area.y(), area.right(), area.bottom(), 0xffdce4ec);
            }
            case GLASS_TEXT_GLOW -> {
                g.fill(x, y, canvas.right(), canvas.bottom(), 0xff202834);
                g.drawString(host.font(), title, x + 6, middle - 13, 0xffd3d8db, false);
                materials.drawGlassText(g, host.font(), title.getVisualOrderText(), x + 6, middle + 3, 0xffd3d8db);
            }
            case ICON_SHADER -> {
                var shaders = List.of(GraphicIconShader.GRADIENT_SWEEP, GraphicIconShader.COLOR_CYCLE,
                        GraphicIconShader.ENERGY_WAVE, GraphicIconShader.PULSE);
                int width = canvas.width() / shaders.size();
                for (int i = 0; i < shaders.size(); i++) {
                    GraphicIcon.SHOP.withShader(shaders.get(i)).draw(g, x + i * width + (width - 16) / 2,
                            middle - 8, 1, 0xffffffff, frame.preferences());
                }
            }
            case RADIAL_GRADIENT -> ShBetterUiApi.blur().drawRadialGradient(g, canvas);
            case BUTTON_FEEDBACK -> drawFeedback(frame, title, elapsed);
            case GLASS_TOOLTIP -> {
                drawPattern(g, canvas);
                var theme = GlassTheme.GLASS;
                var area = inset(10);
                theme.tooltipPanel(g, area.x(), area.y(), area.width(), area.height());
                g.drawString(host.font(), host.font().plainSubstrByWidth(title.getString(), area.width() - 8),
                        area.x() + 4, middle - 4, 0xffffffff, false);
            }
            default -> { }
        }
    }

    private void drawBlur(UiFrame frame, long elapsed) {
        var g = frame.graphics();
        drawPattern(g, canvas);
        var area = inset(10);
        var rect = new GuiRect(area.x(), area.y(), area.width(), area.height());
        var blur = ShBetterUiApi.blur();
        boolean available = frame.preferences().useBackdropBlur() && blur.isReady();
        if (available) {
            var client = Minecraft.getInstance();
            var window = client.getWindow();
            var target = client.getMainRenderTarget();
            var pose = g.pose().last().pose();
            var transformed = blur.applyAffine(rect, pose.m00(), pose.m11(), pose.m30(), pose.m31());
            var pixels = blur.toPixels(transformed, target.width, target.height,
                    window.getGuiScaledWidth(), window.getGuiScaledHeight());
            switch (effect) {
                case BACKDROP_BLUR -> available = blur.tryBackdrop(g, rect, 1f, GlassStyle.BACKDROP, 12f, 5f);
                case DIRECTIONAL_BLUR -> available = blur.directionalPixelsNow(g, pixels, 1f, 12f, 1f, 0f);
                case REFRACTION_WAVE -> {
                    // This post-process consumes its request in the same native frame.
                    float radius = (float) window.getGuiScale() * (frame.preferences().reducedMotion()
                            ? 14f : 5f + (elapsed % 1600L) / 1600f * Math.min(area.width(), area.height()) / 2f);
                    blur.requestWavePixels(pixels, .9f, 3f, radius, 9f, 6f, .26f);
                }
                default -> throw new IllegalStateException("Unexpected blur sample " + effect);
            }
        }
        if (!available) {
            g.fill(area.x(), area.y(), area.right(), area.bottom(), 0xe020252e);
            g.drawString(host.font(), Component.translatable(BLUR_UNAVAILABLE), area.x() + 4, area.y() + 4,
                    0xffffffff, false);
        }
    }

    private void drawThemes(UiFrame frame) {
        var themes = List.of(GlassTheme.CLASSIC, GlassTheme.GLASS, GlassTheme.SMOKE, GlassTheme.AURORA,
                GlassTheme.AMETHYST, GlassTheme.ROSE, GlassTheme.EMBER);
        int width = canvas.width() / themes.size();
        for (int i = 0; i < themes.size(); i++) {
            var theme = themes.get(i);
            theme.box(frame.graphics(), canvas.x() + i * width, canvas.y() + 8, width - 2,
                    canvas.height() - 16, theme.frameDark, theme.panelBg);
        }
    }

    private void drawFeedback(UiFrame frame, Component title, long elapsed) {
        var area = inset(8);
        int cycle = (int) (elapsed % 1600L);
        if (cycle < 120 && !feedback.holding()) {
            feedback.press(1L, area.x(), area.y(), area.width(), area.height(), true, frame.nowMs());
        } else if (cycle >= 120 && feedback.holding()) {
            feedback.release(frame.nowMs());
            feedback.flash(1L, elapsed / 1600L % 2 == 0 ? ButtonFeedback.Tone.FAILED : ButtonFeedback.Tone.SUCCESS,
                    frame.nowMs());
        }
        var visual = feedback.visual(1L, 1L, true, frame.nowMs(), frame.preferences());
        var g = frame.graphics();
        g.pose().pushPose();
        try {
            float cx = area.x() + area.width() / 2f, cy = area.y() + area.height() / 2f;
            g.pose().translate(cx + visual.shakeX(), cy, 0);
            g.pose().scale(visual.scale(), visual.scale(), 1);
            g.pose().translate(-cx, -cy, 0);
            g.fill(area.x(), area.y(), area.right(), area.bottom(), ButtonFeedback.tint(0xff344158, visual));
            g.drawString(host.font(), host.font().plainSubstrByWidth(title.getString(), area.width() - 8),
                    area.x() + 4, (int) cy - 4, 0xffffffff, false);
        } finally {
            g.pose().popPose();
        }
    }

    private Bounds inset(int n) {
        return new Bounds(canvas.x() + n, canvas.y() + n, canvas.width() - 2 * n, canvas.height() - 2 * n);
    }

    static void drawPattern(GuiGraphics graphics, Bounds area) {
        int size = Math.max(6, (Math.max(area.width(), area.height()) + 47) / 48);
        for (int y = area.y(); y < area.bottom(); y += size) {
            for (int x = area.x(); x < area.right(); x += size) {
                int tile = ((x - area.x()) / size + (y - area.y()) / size) & 1;
                graphics.fill(x, y, Math.min(x + size, area.right()), Math.min(y + size, area.bottom()),
                        tile == 0 ? 0xff627da4 : 0xff253243);
            }
        }
    }

    private static void openCrossfade(PreviewContext host, Button source) {
        if (!host.interactive()) return;
        var previous = host.screen();
        var screen = new CrossfadeScreen(previous, source, host.preferences());
        screen.transition.beginHandoff(previous, host.preferences());
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    public void suspend() {
        if (spatial != null) {
            spatial.close();
            spatial = null;
        }
    }

    private static final class CrossfadeScreen extends Screen {
        private final Screen previous;
        private final Button source;
        private final UiPreferences preferences;
        private final UiScreenReturn transition = new UiScreenReturn(this);
        private final long started = PageTransition.nowMs();

        CrossfadeScreen(Screen previous, Button source, UiPreferences preferences) {
            super(Component.translatable(LABEL + "screen_crossfade"));
            this.previous = previous;
            this.source = source;
            this.preferences = preferences;
        }

        @Override
        protected void init() {
            addRenderableWidget(new UiButton(width / 2 - 52, height / 2 + 12, 104, 20,
                    Component.translatable("gui.back"), ignored -> onClose(), preferences, false));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            float progress = Math.min(1f, (PageTransition.nowMs() - started) / 650f);
            transition.renderCrossfade(previous, g, partialTick, preferences, progress, 1f,
                    () -> g.fill(0, 0, width, height, 0xff30435d));
            g.drawCenteredString(font, getTitle(), width / 2, height / 2 - 14, 0xffffffff);
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(previous);
            previous.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getMessage().equals(source.getMessage())).findFirst()
                    .ifPresent(previous::setFocused);
        }

        @Override
        public void removed() {
            transition.clearHandoff();
            super.removed();
        }
    }
}
