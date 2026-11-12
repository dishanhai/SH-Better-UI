// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.BackdropCapture;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.panel.GalleryMaterialEffects.Effect;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/** Regional effects, classic glass treatments and texture shaders. */
final class GalleryMaterialSamples extends GallerySampleScreen {
    private static final String GALLERY = "sh_better_ui.panel.developer.gallery.";
    private static final int GAP = 6;
    private static final int CONTROL_HEIGHT = 20;
    private static final GlassOptics THEME_GLASS = new GlassOptics(0.9f, 0, 0, 0.35f, 0, 0, 4);
    private static final GlassOptics SMOKE_GLASS = new GlassOptics(0.9f, 0, 0, 0.7f, 0, 0, 8);
    private static final GlassOptics TOOLTIP_GLASS = new GlassOptics(0.96f, 0, 0, 0.45f, 0, 0, 6);
    private static final Swatch[] SWATCHES = {
            new Swatch("classic_glass_themes.classic", 0xFF8A5A00, 0xF0101010, null),
            new Swatch("classic_glass_themes.glass", 0xFF36546A, 0x590E1726, THEME_GLASS),
            new Swatch("classic_glass_themes.smoke", 0x40A8B0B4, 0xCC24282C, SMOKE_GLASS),
            new Swatch("aurora", 0x7080E3BD, 0xA60C2524, THEME_GLASS),
            new Swatch("amethyst", 0x70C6ACFF, 0xA622172F, THEME_GLASS),
            new Swatch("rose", 0x70F5B3CD, 0xA6301C29, THEME_GLASS),
            new Swatch("ember", 0x70FFCB8C, 0xA62D2118, THEME_GLASS)
    };
    private static final IconSample[] ICONS = {
            new IconSample("sweep", Effect.GRADIENT_SWEEP, 2400),
            new IconSample("cycle", Effect.COLOR_CYCLE, 3600),
            new IconSample("wave", Effect.ENERGY_WAVE, 2000),
            new IconSample("pulse", Effect.PULSE, 1800)
    };

    private int intensity = 50;
    private int angle;
    private boolean tooltipShown = true;
    private boolean paused;
    private long elapsedBeforeResume;
    private long resumedAt = Util.getMillis();

    GalleryMaterialSamples(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        Bounds controls = new Bounds(body.x(), body.bottom() - CONTROL_HEIGHT, body.width(), CONTROL_HEIGHT);
        Bounds footer = column(new Bounds(body.x(), body.bottom() + 5, body.width(), 22), 1, 2);
        switch (id) {
            case "directional_blur", "refraction_wave" -> {
                if (!preferences.useBackdropBlur()) return;
                var amount = addRenderableWidget(new UiSlider(column(controls, 0, 2), 0, 100, 5, intensity,
                        preferences, value -> gallery("slider_value", value), value -> intensity = value));
                amount.setOnCommit(value -> {}, value -> intensity = value);
                initialFocus = amount;
                var second = column(controls, 1, 2);
                if (id.equals("directional_blur")) {
                    var direction = addRenderableWidget(new UiSlider(second, 0, 180, 15, angle,
                            preferences, value -> text("angle", value), value -> angle = value));
                    direction.setOnCommit(value -> {}, value -> angle = value);
                } else {
                    addPlaybackButton(second, !preferences.reducedMotion());
                }
            }
            case "icon_shader" -> addPlaybackButton(footer, !preferences.reducedMotion() && !preferences.highContrast());
            case "glass_tooltip" -> initialFocus = addButton(footer, tooltipAction(), button -> {
                tooltipShown = !tooltipShown;
                button.setMessage(tooltipAction());
            }, false);
            default -> { }
        }
    }

    private void addPlaybackButton(Bounds bounds, boolean enabled) {
        var button = addButton(bounds, playbackAction(), source -> {
            if (paused) resumedAt = Util.getMillis();
            else elapsedBeforeResume = elapsed();
            paused = !paused;
            source.setMessage(playbackAction());
        }, false);
        button.active = enabled;
        if (enabled && initialFocus == null) initialFocus = button;
    }

    private Component playbackAction() { return gallery(paused ? "effect_resume" : "effect_pause"); }

    private Component tooltipAction() { return text(tooltipShown ? "hide" : "show"); }

    private long elapsed() { return elapsedBeforeResume + (paused ? 0 : Util.getMillis() - resumedAt); }

    private int phase(long elapsed, int period) {
        return preferences.reducedMotion() ? 500 : (int) (elapsed % period * 1000 / period);
    }

    @Override
    public Component getNarrationMessage() {
        var detail = switch (id) {
            case "glass_tooltip" -> tooltipShown ? text("detail") : null;
            case "classic_glass_themes" -> CommonComponents.joinForNarration(Arrays.stream(SWATCHES)
                    .map(Swatch::label).toArray(Component[]::new));
            case "icon_shader" -> CommonComponents.joinForNarration(Arrays.stream(ICONS)
                    .map(icon -> text(icon.key())).toArray(Component[]::new));
            case "directional_blur", "refraction_wave" -> preferences != null && !preferences.useBackdropBlur()
                    ? backdropUnavailable() : null;
            default -> null;
        };
        return detail == null ? super.getNarrationMessage() : CommonComponents.joinForNarration(getTitle(), detail);
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var regional = preferences.useBackdropBlur()
                ? new Bounds(body.x(), body.y(), body.width(), body.height() - CONTROL_HEIGHT - GAP) : body;
        switch (id) {
            case "directional_blur", "refraction_wave" -> drawRegionalEffect(graphics, regional, mouseX, mouseY);
            case "classic_glass_themes" -> drawThemes(graphics, mouseX, mouseY);
            case "glass_panel_shadow" -> drawShadow(graphics, mouseX, mouseY);
            case "glass_text_glow" -> drawGlow(graphics, mouseX, mouseY);
            case "glass_tooltip" -> drawTooltip(graphics, body, mouseX, mouseY);
            case "icon_shader" -> drawIcons(graphics, body, mouseX, mouseY);
            default -> throw new IllegalStateException("Unknown material sample " + id);
        }
    }

    private void drawRegionalEffect(GuiGraphicsExtractor graphics, Bounds canvas, int mouseX, int mouseY) {
        var left = labelArea(graphics, column(canvas, 0, 2), gallery("material_compare.reference"), mouseX, mouseY);
        var right = labelArea(graphics, column(canvas, 1, 2), gallery("material_compare.effect"), mouseX, mouseY);
        pattern(graphics, left, false, preferences.highContrast());
        pattern(graphics, right, false, preferences.highContrast());
        var area = inset(right, 8);
        if (intensity > 0 || !preferences.useBackdropBlur()) {
            var snapshot = preferences.useBackdropBlur() ? BackdropCapture.request(graphics) : null;
            if (snapshot != null) {
                boolean wave = id.equals("refraction_wave");
                GalleryMaterialEffects.drawBackdrop(graphics, area, snapshot,
                        wave ? Effect.REFRACTION_WAVE : Effect.DIRECTIONAL_BLUR,
                        intensity, wave ? phase(elapsed(), 1600) : angle);
            } else {
                graphics.nextStratum();
                graphics.fill(area.x(), area.y(), area.right(), area.bottom(), 0xFF18212D);
                drawText(graphics, inset(area, 3), backdropUnavailable(), 0xFFFFFFFF, false);
            }
        }
        graphics.nextStratum();
        plate(graphics, area, 0, preferences.highContrast() ? 2 : 1, 0xFFE0F0FC, 0);
    }

    private Component backdropUnavailable() {
        return gallery(preferences.highContrast() ? "effect_contrast" : "blur_unavailable");
    }

    private void drawThemes(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int rowHeight = (body.height() - GAP) / 2;
        var firstRow = new Bounds(body.x(), body.y(), body.width(), rowHeight);
        var secondRow = new Bounds(body.x(), firstRow.bottom() + GAP, body.width(), body.bottom() - firstRow.bottom() - GAP);
        var areas = new Bounds[SWATCHES.length];
        for (int index = 0; index < SWATCHES.length; index++) {
            var swatch = SWATCHES[index];
            var cell = index < 4 ? column(firstRow, index, 4) : column(secondRow, index - 4, 3);
            areas[index] = labelArea(graphics, cell, swatch.label(), mouseX, mouseY);
            var area = areas[index];
            var half = column(area, 0, 2, 0);
            pattern(graphics, half, false, preferences.highContrast());
            pattern(graphics, column(area, 1, 2, 0), true, preferences.highContrast());
        }
        boolean optical = prepareGlass(graphics);
        graphics.nextStratum();
        for (int index = 0; index < SWATCHES.length; index++) {
            var swatch = SWATCHES[index];
            var area = inset(areas[index], 6);
            float corner = swatch.optics() == null ? 0 : 4;
            int edge = preferences.highContrast() ? swatch.edge() | 0xFF000000 : swatch.edge();
            if (optical && swatch.optics() != null && glass(graphics, area, corner, swatch.fill(), edge, swatch.optics())) {
                continue;
            }
            int fill = preferences.highContrast() ? swatch.fill() | 0xFF000000 : swatch.fill();
            plate(graphics, area, corner, preferences.highContrast() ? 2 : 1, edge, fill);
        }
    }

    private void drawShadow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int index = 0; index < 2; index++) {
            var canvas = labelArea(graphics, column(body, index, 2),
                    gallery(index == 0 ? "material_compare.reference" : "material_compare.effect"), mouseX, mouseY);
            graphics.fillGradient(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), 0xFFCBDCE8, 0xFF596F86);
            var area = inset(canvas, 16);
            graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
            try {
                if (index == 1 && !preferences.highContrast()) {
                    for (int ring = 8; ring >= 1; ring--) {
                        var shadow = new Bounds(area.x() - ring, area.y() + 3 - ring,
                                area.width() + ring * 2, area.height() + ring * 2);
                        plate(graphics, shadow, 6 + ring, 0, 0, (32 - ring * 3) << 24);
                    }
                }
                plate(graphics, area, 6, preferences.highContrast() ? 2 : 1, 0xFF1D2A3D, 0xFFDFEBF4);
            } finally {
                graphics.disableScissor();
            }
        }
    }

    private void drawGlow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int index = 0; index < 2; index++) {
            var canvas = labelArea(graphics, column(body, index, 2),
                    gallery(index == 0 ? "material_compare.reference" : "material_compare.effect"), mouseX, mouseY);
            plate(graphics, canvas, 5, 1, 0xFF425569, preferences.highContrast() ? 0xFF101820 : 0xFF202834);
            drawText(graphics, inset(canvas, 6), getTitle(), 0xFFF0F6FF, index == 1 && !preferences.highContrast());
        }
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, Bounds canvas, int mouseX, int mouseY) {
        var dark = labelArea(graphics, column(canvas, 0, 2), gallery("material_compare.dark"), mouseX, mouseY);
        var light = labelArea(graphics, column(canvas, 1, 2), gallery("material_compare.light"), mouseX, mouseY);
        pattern(graphics, dark, false, preferences.highContrast());
        pattern(graphics, light, true, preferences.highContrast());
        if (!tooltipShown) return;
        var detail = text("detail");
        var darkTooltip = tooltipArea(dark);
        var textArea = inset(darkTooltip, 4);
        boolean shared = font.split(detail, textArea.width()).size() * font.lineHeight > textArea.height();
        var tooltips = shared
                ? List.of(tooltipArea(new Bounds(dark.x(), dark.y(), light.right() - dark.x(), dark.height())))
                : List.of(darkTooltip, tooltipArea(light));
        boolean optical = prepareGlass(graphics);
        graphics.nextStratum();
        for (var area : tooltips) drawTooltipPlate(graphics, area, optical);
        graphics.nextStratum();
        for (var area : tooltips) drawText(graphics, inset(area, 4), detail, 0xFFFFFFFF, false);
    }

    private Bounds tooltipArea(Bounds canvas) {
        int height = Math.min(42, canvas.height());
        int width = Math.min(176, canvas.width());
        return new Bounds(canvas.x() + (canvas.width() - width) / 2, canvas.y() + (canvas.height() - height) / 2,
                width, height);
    }

    private void drawTooltipPlate(GuiGraphicsExtractor graphics, Bounds area, boolean optical) {
        int edge = preferences.highContrast() ? 0xFFEAF5FF : 0xFF638DA6;
        if (!optical || !glass(graphics, area, 4, 0xE60A1322, edge, TOOLTIP_GLASS)) {
            plate(graphics, area, 4, preferences.highContrast() ? 2 : 1, edge, 0xFF0A1322);
        }
    }

    private boolean prepareGlass(GuiGraphicsExtractor graphics) {
        return preferences.useBackdropBlur() && ControlPanelSettings.current().glassEnabled()
                && BackdropCapture.request(graphics) != null;
    }

    private boolean glass(GuiGraphicsExtractor graphics, Bounds area, float radius, int tint, int edge, GlassOptics optics) {
        return LiquidGlassSurface.drawPrepared(graphics, area.x(), area.y(), area.width(), area.height(),
                radius, tint, edge, optics, preferences, 1f);
    }

    private void drawIcons(GuiGraphicsExtractor graphics, Bounds canvas, int mouseX, int mouseY) {
        long elapsed = elapsed();
        for (int index = 0; index < ICONS.length; index++) {
            var icon = ICONS[index];
            var cell = column(canvas, index, ICONS.length);
            var area = labelArea(graphics, cell, text(icon.key()), mouseX, mouseY);
            int scale = Math.clamp(Math.min(area.width(), area.height()) / GraphicIcon.PIXELS, 1, 3);
            int size = GraphicIcon.size(scale);
            int x = area.x() + (area.width() - size) / 2;
            int y = area.y() + (area.height() - size) / 2;
            graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
            try {
                plate(graphics, area, 5, 1, 0xFF425569, 0xFF111925);
                if (preferences.highContrast()) {
                    GraphicIcon.SHOP.draw(graphics, x, y, scale, 0xFFFFFFFF, preferences);
                } else {
                    GalleryMaterialEffects.drawIcon(graphics, new Bounds(x, y, size, size), GraphicIcon.SHOP,
                            icon.effect(), phase(elapsed, icon.period()));
                }
            } finally {
                graphics.disableScissor();
            }
        }
    }

    private Bounds labelArea(GuiGraphicsExtractor graphics, Bounds cell, Component name, int mouseX, int mouseY) {
        PanelText.drawLine(graphics, font, name, cell.x(), cell.y(), cell.width(), theme.text(), mouseX, mouseY);
        int labelHeight = font.lineHeight + 2;
        return new Bounds(cell.x(), cell.y() + labelHeight, cell.width(), cell.height() - labelHeight);
    }

    private void drawText(GuiGraphicsExtractor graphics, Bounds area, Component text, int color, boolean glow) {
        var lines = font.split(text, area.width());
        int count = Math.min(lines.size(), area.height() / font.lineHeight);
        int y = area.y() + (area.height() - count * font.lineHeight) / 2;
        for (int index = 0; index < count; index++) {
            var line = lines.get(index);
            int x = area.x() + (area.width() - font.width(line)) / 2;
            if (glow) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx != 0 || dy != 0) graphics.text(font, line, x + dx, y + dy, 0x0CFFFFFF, false);
                    }
                }
            }
            graphics.text(font, line, x, y, color, false);
            y += font.lineHeight;
        }
    }

    private static void pattern(GuiGraphicsExtractor graphics, Bounds area, boolean light, boolean contrast) {
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), light ? 0xFFDDE8F1 : 0xFF20344D);
        if (contrast) return;
        int spacing = Math.max(9, (area.width() + 29) / 30);
        for (int x = area.x() + spacing / 2; x < area.right(); x += spacing) {
            graphics.fill(x, area.y(), Math.min(x + 2, area.right()), area.bottom(), light ? 0xFF6D91AE : 0xFF8DB3D9);
        }
        int band = area.y() + area.height() / 3;
        graphics.fill(area.x(), band, area.right(), Math.min(band + 3, area.bottom()), light ? 0xFF235680 : 0xFFE6C581);
        band = area.y() + 2 * area.height() / 3;
        graphics.fill(area.x(), band, area.right(), Math.min(band + 3, area.bottom()), light ? 0xFF70536A : 0xFFCBA6D0);
    }

    private static Bounds column(Bounds row, int index, int count) { return column(row, index, count, GAP); }

    private static Bounds column(Bounds row, int index, int count, int gap) {
        int usable = row.width() - gap * (count - 1);
        int start = usable * index / count;
        int end = usable * (index + 1) / count;
        return new Bounds(row.x() + start + index * gap, row.y(), end - start, row.height());
    }

    private static Bounds inset(Bounds area, int amount) {
        int pad = Math.min(amount, Math.min(area.width(), area.height()) / 5);
        return new Bounds(area.x() + pad, area.y() + pad, area.width() - 2 * pad, area.height() - 2 * pad);
    }

    private static void plate(GuiGraphicsExtractor graphics, Bounds area, float radius, float stroke, int edge, int fill) {
        RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), radius, stroke, edge, fill);
    }

    private static Component gallery(String key, Object... args) { return Component.translatable(GALLERY + key, args); }

    private record Swatch(String key, int edge, int fill, @Nullable GlassOptics optics) {
        Component label() {
            String prefix = key.startsWith("classic_glass_themes.") ? GALLERY : "sh_better_ui.panel.theme.group.";
            return Component.translatable(prefix + key);
        }
    }

    private record IconSample(String key, Effect effect, int period) {}
}
