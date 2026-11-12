// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.spatial.SpatialRenderer;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Content;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Text;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceGeometry;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePresentation;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceTokens;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Material;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Theme;
import com.dishanhai.sh_better_ui.spatial.internal.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Paints the two kernel-owned surfaces and reports controls from the same content pass. */
public final class LiveSurfacePainter {
    public static final UiId SEEK = ActivityService.SEEK;
    public static final UiId SURFACE_FOCUS = new UiId("sh_better_ui", "live/surface_focus");
    private static final Text SEEK_LABEL = new Text("sh_better_ui.live.seek", true);
    private static final int RHYTHM_WIDTH = 20;
    private static final int CONTROL_COLUMNS = 3;

    private final Scene scene;
    private final SpatialScene.Node primary;
    private final SpatialScene.Node secondary;
    private SurfaceTokens appliedPrimary;
    private SurfaceTokens appliedSecondary;

    public LiveSurfacePainter(SpatialScene scene) {
        this.scene = (Scene) scene;
        primary = node("primary", 1);
        secondary = node("secondary", 0);
    }

    public record Layer(SurfaceGeometry geometry, List<SurfaceTransition.Layer<Content>> contentMix,
                        Object targetContentKey, SurfaceTransition.Target<Content> input, UiId focusedControl,
                        UiId hoveredControl, UiId pressedControl, Double seekPreview) {
        public Layer { contentMix = List.copyOf(contentMix); }

        boolean isTargetContent(SurfaceTransition.Layer<Content> painted) {
            return targetContentKey != null && targetContentKey.equals(painted.contentKey());
        }
    }

    public record Measured(float width, float height, float minWidth, float minHeight) {}

    public record Painted(List<Control> primaryControls, List<Control> secondaryControls,
                          boolean primaryTargetPainted, boolean secondaryTargetPainted) {
        private static final Painted EMPTY = new Painted(List.of(), List.of(), false, false);
    }

    public record Control(UiId id, Text label, boolean seek, int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        public double fraction(double mouseX) { return clamp((mouseX - x) / Math.max(1, width - 1)); }
    }

    public static Measured measure(Font font, Content content, SurfacePresentation presentation, SurfaceTokens tokens) {
        if (content == null || !presentation.visible()) return new Measured(0, 0, 0, 0);
        var sizing = tokens.dimensions().forPresentation(presentation);
        if (presentation == SurfacePresentation.MINIMAL) {
            return new Measured(sizing.minWidth(), sizing.minHeight(), sizing.minWidth(), sizing.minHeight());
        }

        boolean expanded = expanded(presentation);
        int pad = sizing.padding(), gap = tokens.dimensions().gap();
        int textWidth = font.width(line(content.title()));
        if (expanded) textWidth = Math.max(textWidth, font.width(line(content.detail())));
        int headerWidth = textWidth + (content.icon() == null ? 0 : sizing.iconSize() + gap)
                + (content.rhythm().isEmpty() ? 0 : RHYTHM_WIDTH + gap);
        int actionsWidth = 0;
        if (expanded && !content.controls().isEmpty()) {
            int labelWidth = 0;
            for (var action : content.controls()) labelWidth = Math.max(labelWidth, font.width(line(action.label())));
            int columns = Math.min(CONTROL_COLUMNS, content.controls().size());
            actionsWidth = columns * Math.max(28, labelWidth + 10) + (columns - 1) * gap;
        }

        int width = Math.max(sizing.minWidth(), Math.min(sizing.maxWidth(), Math.max(headerWidth, actionsWidth) + pad * 2));
        int height = Math.max(sizing.minHeight(), bodyHeight(font, content, presentation, tokens) + pad * 2 + 1);
        return new Measured(width, height, sizing.minWidth(), height);
    }

    public Painted render(GuiGraphics graphics, Font font, int width, int height, double guiScale,
                          SurfaceTokens tokens, Layer primaryLayer, Layer secondaryLayer, boolean reducedMotion) {
        boolean hasPrimary = update(primary, primaryLayer);
        boolean hasSecondary = update(secondary, secondaryLayer);
        if (!hasPrimary && !hasSecondary) return Painted.EMPTY;

        var viewport = new Bounds(0, 0, width, height);
        var first = new Slot(hasPrimary ? primaryLayer : null);
        var second = new Slot(hasSecondary ? secondaryLayer : null);
        var primaryTokens = shell(first.layer, tokens);
        var secondaryTokens = shell(second.layer, tokens);
        if (hasPrimary && !primaryTokens.equals(appliedPrimary)) {
            applyTokens(primary, primaryTokens);
            appliedPrimary = primaryTokens;
        }
        if (hasSecondary && !secondaryTokens.equals(appliedSecondary)) {
            applyTokens(secondary, secondaryTokens);
            appliedSecondary = secondaryTokens;
        }
        boolean still = reducedMotion || ClientUiSettings.current().reducedMotion() || tokens.highContrast();
        if (scene.settings().enabled()) {
            SpatialRenderer.draw(graphics, scene, width, height, guiScale, node -> {
                if (node == primary) paint(graphics, font, first, viewport, tokens, still);
                if (node == secondary) paint(graphics, font, second, viewport, tokens, still);
            });
        } else {
            fallback(graphics, font, second, viewport, secondaryTokens, tokens, still);
            fallback(graphics, font, first, viewport, primaryTokens, tokens, still);
        }
        return new Painted(first.controls, second.controls, first.targetPainted, second.targetPainted);
    }

    private SpatialScene.Node node(String name, int z) {
        var id = new UiId("sh_better_ui", "live/" + name);
        var node = scene.create(new SpatialScene.Spec(id, null, Shape.rectangle(1, 1), Transform.IDENTITY,
                z, 0, false, false));
        node.visible(false);
        return node;
    }

    private static boolean update(SpatialScene.Node node, Layer layer) {
        var geometry = layer == null ? SurfaceGeometry.HIDDEN : layer.geometry();
        boolean visible = geometry.visible() && geometry.width() >= .001 && geometry.height() >= .001
                && !layer.contentMix().isEmpty();
        node.visible(visible);
        if (visible) {
            node.shape(Shape.rounded(geometry.width(), geometry.height(), geometry.radius()));
            node.transform(new Transform(geometry.x(), geometry.y(), 1, 1, 0, 0, 0));
            node.opacity(geometry.opacity());
        }
        return visible;
    }

    private static void applyTokens(SpatialScene.Node node, SurfaceTokens tokens) {
        var material = new Material(tokens.surface(), 1, .86, .07, .12, .12, 0);
        node.material(material);
        var elevation = tokens.dimensions().elevation();
        node.elevation(elevation);
        node.shadows(Theme.palette(tokens.surface(), tokens.shadow()).shadows(elevation.height()));
    }

    private static SurfaceTokens shell(Layer layer, SurfaceTokens fallback) {
        if (layer == null || fallback.highContrast()) return fallback;
        SurfaceTransition.Layer<Content> strongest = null;
        for (var mix : layer.contentMix()) if (strongest == null || mix.weight() > strongest.weight()) strongest = mix;
        return strongest == null ? fallback : effective(strongest.content(), fallback);
    }

    public static SurfaceTokens effective(Content content, SurfaceTokens fallback) {
        return fallback.highContrast() || content == null || content.appearance() == null ? fallback : content.appearance();
    }

    private static void fallback(GuiGraphics graphics, Font font, Slot slot, Bounds viewport,
                                 SurfaceTokens shell, SurfaceTokens tokens, boolean still) {
        if (slot.layer == null) return;
        var geometry = slot.layer.geometry();
        rounded(graphics, geometry, 0, 0, fade(shell.surface(), geometry.opacity()));
        paint(graphics, font, slot, viewport, tokens, still);
    }

    private static void paint(GuiGraphics graphics, Font font, Slot slot, Bounds viewport,
                              SurfaceTokens tokens, boolean still) {
        Layer layer = slot.layer;
        var geometry = layer.geometry();
        var shell = shell(layer, tokens);
        boolean acceptsInput = layer.input() != null && layer.input().geometry().visible();
        if (acceptsInput && SURFACE_FOCUS.equals(layer.focusedControl())) {
            rounded(graphics, geometry, shell.highContrast() ? 2 : 1, fade(shell.focus(), geometry.opacity()), 0);
        } else if (acceptsInput && (SURFACE_FOCUS.equals(layer.hoveredControl()) || SURFACE_FOCUS.equals(layer.pressedControl()))) {
            double intensity = SURFACE_FOCUS.equals(layer.pressedControl()) ? 1 : .55;
            rounded(graphics, geometry, 1, fade(shell.accent(), geometry.opacity() * intensity), 0);
        }
        for (var mix : layer.contentMix()) {
            double opacity = geometry.opacity() * mix.weight();
            if (mix.content() == null || !mix.presentation().visible() || opacity < 1.0 / 255) continue;
            var appearance = effective(mix.content(), tokens);
            var bounds = innerBounds(geometry, appearance.dimensions().forPresentation(mix.presentation()).padding()).intersect(viewport);
            if (bounds.width() == 0 || bounds.height() == 0) continue;
            boolean interactive = acceptsInput && mix.presentation() == layer.input().presentation()
                    && layer.isTargetContent(mix);
            graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
            try {
                List<Control> controls = paintContent(graphics, font, mix.content(), mix.presentation(), bounds,
                        appearance, opacity, still || appearance.highContrast(), interactive ? layer : null);
                slot.targetPainted |= layer.isTargetContent(mix);
                if (interactive) slot.controls = controls;
            } finally {
                graphics.disableScissor();
            }
        }
    }

    private static List<Control> paintContent(GuiGraphics graphics, Font font, Content content,
                                              SurfacePresentation presentation, Bounds bounds, SurfaceTokens tokens,
                                              double opacity, boolean still, Layer input) {
        if (presentation == SurfacePresentation.MINIMAL) {
            int size = Math.min(tokens.dimensions().minimal().iconSize(), Math.min(bounds.width(), bounds.height()));
            int x = bounds.x() + (bounds.width() - size) / 2;
            int y = bounds.y() + (bounds.height() - size) / 2;
            icon(graphics, content.icon(), x, y, size, tokens, opacity);
            return List.of();
        }

        boolean expanded = expanded(presentation);
        int y = bounds.y() + Math.max(0, (bounds.height() - bodyHeight(font, content, presentation, tokens)) / 2);
        int gap = tokens.dimensions().gap();
        int headerHeight = headerHeight(font, content, presentation, tokens);
        paintHeader(graphics, font, content, presentation, bounds, y, headerHeight, tokens, opacity, still);
        y += headerHeight + gap;

        var controls = new ArrayList<Control>();
        int progressHeight = progressHeight(content, expanded);
        if (progressHeight > 0 && bounds.width() >= 4 && y + progressHeight <= bounds.bottom()) {
            boolean seek = expanded && content.seekable();
            var bar = new Control(SEEK, SEEK_LABEL, true, bounds.x(), y, bounds.width(), progressHeight);
            double amount = seek && input != null && input.seekPreview() != null ? input.seekPreview() : content.progress();
            progress(graphics, bar, amount, seek, tokens, opacity, input);
            if (seek && input != null) controls.add(bar);
        }
        if (progressHeight > 0) y += progressHeight + gap;

        if (expanded && !content.controls().isEmpty()) {
            paintActions(graphics, font, content, bounds, y, tokens, opacity, input, controls);
        }
        return List.copyOf(controls);
    }

    private static void paintHeader(GuiGraphics graphics, Font font, Content content, SurfacePresentation presentation,
                                    Bounds bounds, int y, int headerHeight, SurfaceTokens tokens, double opacity,
                                    boolean still) {
        int x = bounds.x();
        int right = bounds.right();
        int gap = tokens.dimensions().gap();
        int size = tokens.dimensions().forPresentation(presentation).iconSize();
        if (content.icon() != null && bounds.width() >= size + gap + 8 && bounds.height() >= size) {
            icon(graphics, content.icon(), x, y + (headerHeight - size) / 2, size, tokens, opacity);
            x += size + gap;
        }
        if (!content.rhythm().isEmpty() && right - x >= RHYTHM_WIDTH + gap + 20) {
            right -= RHYTHM_WIDTH;
            rhythm(graphics, content.rhythm(), right, y + headerHeight / 2, fade(tokens.accent(), opacity), still);
            right -= gap;
        }
        boolean detail = hasDetail(content, presentation);
        int lineHeight = font.lineHeight;
        int textHeight = detail ? lineHeight * 2 + 2 : lineHeight;
        int textY = y + (headerHeight - textHeight) / 2;
        if (textY + lineHeight <= bounds.bottom()) {
            label(graphics, font, line(content.title()), x, textY, right - x, fade(tokens.title(), opacity), false);
        }
        if (detail && textY + textHeight <= bounds.bottom()) {
            label(graphics, font, line(content.detail()), x, textY + lineHeight + 2, right - x,
                    fade(tokens.detail(), opacity), false);
        }
    }

    private static void paintActions(GuiGraphics graphics, Font font, Content content, Bounds bounds, int y,
                                     SurfaceTokens tokens, double opacity, Layer input, List<Control> controls) {
        int columns = Math.min(CONTROL_COLUMNS, content.controls().size());
        int gap = tokens.dimensions().gap();
        int width = (bounds.width() - (columns - 1) * gap) / columns;
        int height = buttonHeight(font, tokens);
        if (width < 12) return;
        for (int i = 0; i < content.controls().size(); i++) {
            var action = content.controls().get(i);
            int x = bounds.x() + i % columns * (width + gap);
            int top = y + i / columns * (height + gap);
            if (top + height > bounds.bottom()) break;
            var control = new Control(action.id(), action.label(), false, x, top, width, height);
            boolean pressed = input != null && control.id().equals(input.pressedControl());
            boolean hovered = input != null && control.id().equals(input.hoveredControl());
            fill(graphics, control, fade(pressed ? tokens.buttonPressed() : hovered ? tokens.progressTrack() : tokens.button(), opacity));
            label(graphics, font, line(control.label()), x + 4, top + (height - font.lineHeight) / 2, width - 8,
                    fade(tokens.title(), opacity), true);
            focus(graphics, control, tokens, opacity, input);
            if (input != null) controls.add(control);
        }
    }

    private static void progress(GuiGraphics graphics, Control bar, double amount, boolean seek, SurfaceTokens tokens,
                                 double opacity, Layer input) {
        boolean pressed = seek && input != null && bar.id().equals(input.pressedControl());
        fill(graphics, bar, fade(pressed ? tokens.buttonPressed() : tokens.progressTrack(), opacity));
        int fill = (int) Math.round(bar.width() * clamp(amount));
        int accent = fade(pressed ? tokens.focus() : tokens.accent(), opacity);
        if (fill > 0) graphics.fill(bar.x(), bar.y(), bar.x() + fill, bar.y() + bar.height(), accent);
        if (seek) {
            int thumb = bar.x() + (int) Math.round((bar.width() - 1) * clamp(amount));
            graphics.fill(thumb, bar.y() + 1, Math.min(thumb + 2, bar.x() + bar.width()),
                    bar.y() + bar.height() - 1, fade(tokens.title(), opacity));
            focus(graphics, bar, tokens, opacity, input);
        }
    }

    private static void focus(GuiGraphics graphics, Control control, SurfaceTokens tokens, double opacity, Layer input) {
        if (input == null || !control.id().equals(input.focusedControl())) return;
        int color = fade(tokens.focus(), opacity);
        graphics.renderOutline(control.x(), control.y(), control.width(), control.height(), color);
        if (tokens.highContrast()) {
            graphics.renderOutline(control.x() + 1, control.y() + 1, control.width() - 2, control.height() - 2, color);
        }
    }

    private static void fill(GuiGraphics graphics, Control control, int color) {
        graphics.fill(control.x(), control.y(), control.x() + control.width(), control.y() + control.height(), color);
    }

    private static void rounded(GuiGraphics graphics, SurfaceGeometry geometry, int stroke, int edge, int fill) {
        RoundedCorners.draw(graphics, (float) geometry.x(), (float) geometry.y(), (float) geometry.width(),
                (float) geometry.height(), (float) geometry.radius(), stroke, edge, fill);
    }

    private static void icon(GuiGraphics graphics, UiId icon, int x, int y, int size, SurfaceTokens tokens, double opacity) {
        if (icon != null) {
            var id = ResourceLocation.fromNamespaceAndPath(icon.namespace(), icon.path());
            var minecraft = Minecraft.getInstance();
            var registered = minecraft.getTextureManager().getTexture(id, null);
            if (registered instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
                var image = dynamic.getPixels();
                double scale = (double) size / Math.max(image.getWidth(), image.getHeight());
                drawTexture(graphics, id, x, y, size, Math.max(1, (int) Math.round(image.getWidth() * scale)),
                        Math.max(1, (int) Math.round(image.getHeight() * scale)), opacity);
                return;
            }
            if (minecraft.getResourceManager().getResource(id).isPresent()) {
                drawTexture(graphics, id, x, y, size, size, size, opacity);
                return;
            }
        }
        int mark = Math.min(size, 4);
        int left = x + (size - mark) / 2;
        int top = y + (size - mark) / 2;
        graphics.fill(left, top, left + mark, top + mark, fade(tokens.accent(), opacity));
    }

    private static void drawTexture(GuiGraphics graphics, ResourceLocation id, int x, int y, int size,
                                    int width, int height, double opacity) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + (size - width) / 2, y + (size - height) / 2, 0);
            graphics.pose().scale(width / (float) GraphicIcon.size(1), height / (float) GraphicIcon.size(1), 1);
            new GraphicIcon(id).draw(graphics, 0, 0, 1, fade(0xffffffff, opacity));
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void rhythm(GuiGraphics graphics, List<Float> values, int x, int middleY, int color, boolean still) {
        int count = Math.min(7, values.size());
        int left = x + (RHYTHM_WIDTH - (count * 3 - 1)) / 2;
        for (int i = 0; i < count; i++) {
            double level = still ? 0 : values.get(i * (values.size() - 1) / Math.max(1, count - 1));
            int half = (int) Math.round(1 + 4 * clamp(level));
            int start = left + i * 3;
            graphics.fill(start, middleY - half, start + 2, middleY + half + 1, color);
        }
    }

    private static void label(GuiGraphics graphics, Font font, String text, int x, int y, int width,
                              int color, boolean centered) {
        // Vanilla forces a nearly transparent font color to opaque.
        if (width <= 0 || color >>> 24 < 4) return;
        int textWidth = font.width(text);
        if (textWidth > width) {
            int suffix = font.width("…");
            if (suffix > width) return;
            text = font.plainSubstrByWidth(text, width - suffix) + "…";
            textWidth = font.width(text);
        }
        graphics.drawString(font, text, centered ? x + (width - textWidth) / 2 : x, y, color, false);
    }

    public static Component component(Text text) {
        if (text == null) return Component.empty();
        return text.translated() ? Component.translatable(text.value()) : Component.literal(text.value());
    }

    private static String line(Text text) {
        String value = text == null || text.translated() ? component(text).getString() : text.value();
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static int bodyHeight(Font font, Content content, SurfacePresentation presentation, SurfaceTokens tokens) {
        boolean expanded = expanded(presentation);
        int progress = progressHeight(content, expanded);
        int gap = tokens.dimensions().gap();
        int height = headerHeight(font, content, presentation, tokens) + (progress == 0 ? 0 : progress + gap);
        if (expanded) {
            int rows = (content.controls().size() + CONTROL_COLUMNS - 1) / CONTROL_COLUMNS;
            height += rows * (buttonHeight(font, tokens) + gap);
        }
        return height;
    }

    private static int headerHeight(Font font, Content content, SurfacePresentation presentation, SurfaceTokens tokens) {
        int textHeight = hasDetail(content, presentation) ? font.lineHeight * 2 + 2 : font.lineHeight;
        int iconHeight = content.icon() == null ? 0 : tokens.dimensions().forPresentation(presentation).iconSize();
        return Math.max(textHeight, Math.max(iconHeight, content.rhythm().isEmpty() ? 0 : 11));
    }

    private static boolean hasDetail(Content content, SurfacePresentation presentation) {
        return expanded(presentation) && content.detail() != null && !content.detail().value().isBlank();
    }

    private static int progressHeight(Content content, boolean expanded) {
        if (content.progress() < 0) return 0;
        return expanded && content.seekable() ? 8 : 2;
    }

    private static int buttonHeight(Font font, SurfaceTokens tokens) { return font.lineHeight + (tokens.highContrast() ? 10 : 8); }

    private static boolean expanded(SurfacePresentation presentation) {
        return presentation == SurfacePresentation.EXPANDED || presentation == SurfacePresentation.FLOATING;
    }

    private static double clamp(double amount) { return amount > 0 ? Math.min(1, amount) : 0; }

    private static int fade(int color, double opacity) {
        return color & 0xffffff | (int) Math.round((color >>> 24) * clamp(opacity)) << 24;
    }

    /** Keeps the scissor rectangle inside the curved corners, including during a spring overshoot. */
    static Bounds innerBounds(SurfaceGeometry geometry, int padding) {
        double radius = geometry.radius();
        double offset = Math.max(0, radius - padding);
        double side = Math.max(padding, radius - Math.sqrt(Math.max(0, radius * radius - offset * offset)));
        int left = (int) Math.ceil(geometry.x() + side);
        int top = (int) Math.ceil(geometry.y() + padding);
        int right = (int) Math.floor(geometry.x() + geometry.width() - side);
        int bottom = (int) Math.floor(geometry.y() + geometry.height() - padding);
        return new Bounds(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        Bounds intersect(Bounds other) {
            int left = Math.max(x, other.x), top = Math.max(y, other.y);
            int right = Math.min(right(), other.right()), bottom = Math.min(bottom(), other.bottom());
            return new Bounds(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }

    private static final class Slot {
        private final Layer layer;
        private List<Control> controls = List.of();
        private boolean targetPainted;

        private Slot(Layer layer) { this.layer = layer; }
    }
}
