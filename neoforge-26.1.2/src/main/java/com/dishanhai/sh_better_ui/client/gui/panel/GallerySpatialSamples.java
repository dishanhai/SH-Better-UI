// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.BackdropCapture;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.PointerDrag;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.*;
import com.dishanhai.sh_better_ui.spatial.internal.Scene;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntConsumer;

final class GallerySpatialSamples extends GallerySampleScreen {
    private static final String KEY = "sh_better_ui.panel.developer.gallery.";
    private static final int CONTROL_HEIGHT = 20;
    private static final int GAP = 5;
    private static final int INK = 0xFF0D1725;
    private static final int LIGHT_INK = 0xFFDAF1FF;
    private static final int REVEAL_DURATION = 1300;
    private static final int SLIDER_STEP = 5;
    private static final Component SPATIAL_HIGH_CONTRAST = Component.translatable(KEY + "spatial_high_contrast");
    private static final int[] PALETTE = {0x55C9FF, 0xFF865B, 0xB593FF};
    private static final String[] PALETTE_LABELS = {"cool", "warm", "violet"};
    private static final String[] WAVE_LABELS = {"mixed", "squares", "circles", "triangles"};
    private static final String[] SURFACE_LABELS = {"flat", "raised", "inset", "glass", "metal", "holographic"};
    private static final Material[] SURFACES = {Material.FLAT, Material.SOFT_RAISED, Material.SOFT_INSET,
            Material.GLASS, Material.METALLIC, Material.HOLOGRAPHIC};
    private static final ShadowType[] SHADOWS = {ShadowType.DROP, ShadowType.INNER, ShadowType.AMBIENT,
            ShadowType.CONTACT, ShadowType.DIRECTIONAL, ShadowType.GLOW};
    private static final String[] SHADOW_LABELS = {"drop", "inner", "ambient", "contact", "directional", "glow"};
    private static final LightType[] LIGHT_TYPES = {LightType.POINT, LightType.DIRECTIONAL, LightType.SPOT};
    private static final String[] LIGHT_LABELS = {"point", "directional", "spot"};
    private static final GlassOptics SURFACE_OPTICS = new GlassOptics(.95f, 0, 0, .4f, 0, 0, 5);
    private static final String[] ASCII_RAMP = {" ", ".", ":", "-", "+", "=", "*", "#", "%", "@"};
    private static final UiId LIGHT_ID = new UiId("sh_better_ui", "gallery/spatial/light");

    private final PointerDrag centerDrag = new PointerDrag();
    private final SpatialScene.Node[] tiles = new SpatialScene.Node[6];
    private final Bounds[] tileLabels = new Bounds[6];
    private Bounds canvas;
    private Scene scene;
    private UiButton option;
    private UiSlider revealSlider;
    private int amount = 65;
    private int alpha = 85;
    private int preset;
    private int revealProgress;
    private int lastAmount = -1;
    private int lastAlpha = -1;
    private double centerX = .5, centerY = .5;
    private double committedX = .5, committedY = .5;
    private boolean clipShadows = true;
    private boolean lightPositionFromKeyboard;
    private boolean paused;
    private long pausedElapsed;
    private long started = Util.getMillis();
    private long revealStarted = -1;

    private GallerySpatialSamples(DeveloperGalleryScreen parent, String id) { super(parent, id); }

    static GallerySampleScreen create(DeveloperGalleryScreen parent, String id) {
        return new GallerySpatialSamples(parent, id);
    }

    @Override
    protected void initSample() {
        var controls = new Bounds(body.x(), body.bottom() - CONTROL_HEIGHT, body.width(), CONTROL_HEIGHT);
        var footerRight = column(new Bounds(body.x(), body.bottom() + GAP, body.width(), 22), 1, 2);
        canvas = new Bounds(body.x(), body.y(), body.width(), Math.max(1, controls.y() - body.y() - GAP));
        option = null;
        revealSlider = null;
        switch (id) {
            case "radial_gradient" -> {
                slider(column(controls, 0, 2), 15, 100, amount, "radius", value -> amount = value);
                slider(column(controls, 1, 2), 0, 100, alpha, "opacity", value -> alpha = value);
                option = preferences.highContrast() ? resetPosition(footerRight, Component.translatable("controls.reset"))
                        : cycle(footerRight, PALETTE_LABELS);
                option.setTooltip(Tooltip.create(text("position_hint")));
            }
            case "spatial_surface" -> {
                disableForContrast(slider(column(controls, 0, 2), 0, 100, alpha, "opacity", value -> alpha = value));
                disableForContrast(slider(column(controls, 1, 2), 0, 100, amount, "elevation", value -> amount = value));
                initScene();
            }
            case "spatial_shadow" -> {
                disableForContrast(slider(column(controls, 0, 2), 0, 100, amount, "softness", value -> amount = value));
                disableForContrast(addButton(column(controls, 1, 2), clipLabel(), button -> {
                    clipShadows = !clipShadows;
                    lastAmount = -1;
                    button.setMessage(clipLabel());
                }, false));
                initScene();
            }
            case "spatial_lighting" -> {
                disableForContrast(slider(column(controls, 0, 2), 0, 100, amount, "intensity", value -> amount = value));
                var lightType = cycle(column(controls, 1, 2), LIGHT_LABELS);
                disableForContrast(lightType);
                option = preferences.highContrast() ? resetPosition(footerRight, text("reset_position")) : lightType;
                option.setTooltip(Tooltip.create(text("position_hint")));
                initScene();
            }
            case "formatted_text_effects", "ascii_field", "shape_waves" -> {
                playback(controls);
                if (id.equals("shape_waves")) option = cycle(footerRight, WAVE_LABELS);
            }
            case "visual_reveal" -> {
                revealSlider = slider(column(controls, 0, 2), 0, 100, revealSliderValue(), "progress", ignored -> {});
                revealSlider.setOnCommit(value -> {
                    revealStarted = -1;
                    revealProgress = value * 10;
                }, ignored -> syncReveal());
                addButton(column(controls, 1, 2), Component.translatable(KEY + "effect_replay"),
                        button -> startReveal(.5, .5), true);
            }
            default -> throw new IllegalStateException("Unknown spatial sample " + id);
        }
    }

    private UiSlider slider(Bounds area, int min, int max, int value, String key, IntConsumer edit) {
        var slider = addRenderableWidget(new UiSlider(area, min, max, SLIDER_STEP, value, preferences,
                current -> text(key, current), edit));
        slider.setOnCommit(current -> {}, edit);
        if (initialFocus == null) initialFocus = slider;
        return slider;
    }

    private void disableForContrast(AbstractWidget control) {
        if (!preferences.highContrast()) return;
        control.active = false;
        control.setTooltip(Tooltip.create(SPATIAL_HIGH_CONTRAST));
        if (initialFocus == control) initialFocus = null;
    }

    private UiButton resetPosition(Bounds area, Component label) {
        var reset = addButton(area, label, button -> {
            centerX = committedX = .5;
            centerY = committedY = .5;
            lightPositionFromKeyboard = true;
        }, false);
        if (initialFocus == null) initialFocus = reset;
        return reset;
    }

    private UiButton cycle(Bounds area, String[] labels) {
        return addButton(area, text(labels[preset]), button -> {
            preset = (preset + 1) % labels.length;
            button.setMessage(text(labels[preset]));
        }, false);
    }

    private Component clipLabel() { return text(clipShadows ? "clip_on" : "clip_off"); }

    private void playback(Bounds controls) {
        boolean animated = !preferences.reducedMotion() && !preferences.highContrast();
        var pause = addButton(column(controls, 0, 2), playLabel(), button -> {
            if (paused) started = Util.getMillis() - pausedElapsed;
            else pausedElapsed = elapsed();
            paused = !paused;
            button.setMessage(playLabel());
        }, false);
        pause.active = animated;
        var replay = addButton(column(controls, 1, 2), Component.translatable(KEY + "effect_replay"), button -> {
            started = Util.getMillis();
            pausedElapsed = 0;
            paused = false;
            pause.setMessage(playLabel());
        }, false);
        replay.active = animated;
        if (animated) initialFocus = pause;
    }

    private Component playLabel() { return Component.translatable(KEY + (paused ? "effect_resume" : "effect_pause")); }

    private long elapsed() { return paused ? pausedElapsed : Util.getMillis() - started; }

    private boolean animate() { return !preferences.reducedMotion() && !preferences.highContrast(); }

    private static Bounds column(Bounds row, int index, int count) {
        int available = row.width() - (count - 1) * GAP;
        int left = available * index / count;
        int right = available * (index + 1) / count;
        return new Bounds(row.x() + left + index * GAP, row.y(), right - left, row.height());
    }

    private void initScene() {
        closeScene();
        scene = new Scene(() -> {});
        boolean shadows = !preferences.highContrast() && !id.equals("spatial_lighting");
        boolean blur = preferences.useBackdropBlur() && ControlPanelSettings.current().glassEnabled();
        scene.settings(new Settings(true, shadows, !preferences.highContrast(), blur, Quality.HIGH));
        boolean lighting = id.equals("spatial_lighting");
        int rows = lighting ? 1 : 2;
        int count = lighting ? 3 : 6;
        int rowHeight = canvas.height() / rows;
        for (int index = 0; index < count; index++) {
            var row = new Bounds(canvas.x(), canvas.y() + rowHeight * (index / 3), canvas.width(), rowHeight);
            var cell = column(row, index % 3, 3);
            int labelHeight = cell.height() >= 31 ? font.lineHeight + 3 : 0;
            var frame = node("clip/" + index, null, Shape.rectangle(cell.width(), cell.height()),
                    at(cell.x(), cell.y()), index * 3, true);
            frame.material(new Material(0, 1, 1, 0, 0, 0, 0));
            frame.shadows(List.of());
            int inset = Math.min(10, Math.max(2, (cell.height() - labelHeight) / 6));
            int w = cell.width() - 2 * inset;
            int h = cell.height() - 2 * inset - labelHeight;
            Shape shape = index == 2 || lighting && index == 1 ? Shape.ellipse(w, h) : Shape.rounded(w, h, 5);
            boolean glass = id.equals("spatial_surface") && index == 3;
            // The glass snapshot is axis-aligned and clipped by the GUI canvas.
            tiles[index] = node("tile/" + index, glass ? null : frame.id(), shape,
                    glass ? at(cell.x() + inset, cell.y() + inset) : at(inset, inset), index * 3 + 1, false);
            tileLabels[index] = new Bounds(cell.x() + 2, cell.bottom() - labelHeight, cell.width() - 4, labelHeight);
            if (id.equals("spatial_surface") && (index == 3 || index == 5)) {
                // The translucent face covers a second node so its depth order is visible.
                var behind = node("underlay/" + index, frame.id(), Shape.rounded(w / 2, h, 4),
                        at(inset / 2.0, inset / 2.0), index * 3, false);
                behind.material(new Material(0xFFA45F76, 1, .7, .2, 0, .2, 0));
                behind.shadows(List.of());
            }
        }
        lastAmount = lastAlpha = -1;
    }

    private SpatialScene.Node node(String path, UiId parentId, Shape shape, Transform transform, int z, boolean clip) {
        return scene.create(new SpatialScene.Spec(new UiId("sh_better_ui", "gallery/spatial/" + path), parentId,
                shape, transform, z, 0, false, clip));
    }

    private static Transform at(double x, double y) { return new Transform(x, y, 1, 1, 0, 0, 0); }

    private void updateScenePaint() {
        if (lastAmount == amount && lastAlpha == alpha) return;
        lastAmount = amount;
        lastAlpha = alpha;
        boolean lighting = id.equals("spatial_lighting");
        boolean shadow = id.equals("spatial_shadow");
        for (int index = 0; index < (lighting ? 3 : tiles.length); index++) {
            var tile = tiles[index];
            Material material = shadow ? Material.SOFT_RAISED : lighting ? SURFACES[index * 2 + 1] : SURFACES[index];
            if (preferences.highContrast()) {
                material = new Material(index % 2 == 0 ? 0xFFBEDFFF : 0xFF789BBE, 1, 1, 0, 0, 0, 0);
            }
            if (!preferences.highContrast() && !shadow && !lighting && (index == 3 || index == 5)) {
                int tint = material.color() & 0xFFFFFF | ((material.color() >>> 24) * alpha / 100) << 24;
                material = new Material(tint, 1, material.roughness(), material.highlight(),
                        material.border(), material.depth(), material.blur());
            }
            tile.material(material);
            tile.elevation(lighting ? index * 5 : !shadow ? amount * .12 : 4);
            if (shadow) {
                var kind = SHADOWS[index];
                int color = kind == ShadowType.GLOW ? 0xFF8ECFFF : 0xFF03070D;
                double blur = (1 + amount * .07) * (kind == ShadowType.CONTACT ? .48 : 1);
                double dx = kind == ShadowType.DIRECTIONAL ? -5 : 0;
                double dy = kind == ShadowType.CONTACT ? 1 : kind == ShadowType.GLOW ? 0 : 4;
                tile.shadows(List.of(new Shadow(kind, dx, dy, blur, kind == ShadowType.GLOW ? 3 : 1.5,
                        .92, color, true)));
                tile.shadowClip(clipShadows ? ShadowClip.PARENT : ShadowClip.WINDOW);
            }
        }
    }

    @Override
    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (preferences.highContrast() && scene != null) {
            PanelText.drawLine(graphics, font, SPATIAL_HIGH_CONTRAST, body.x(), canvas.y() - font.lineHeight - 2,
                    body.width(), theme.text(), mouseX, mouseY);
        }
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        try {
            switch (id) {
                case "radial_gradient" -> drawRadial(graphics, mouseX, mouseY);
                case "spatial_surface", "spatial_shadow", "spatial_lighting" -> drawScene(graphics, mouseX, mouseY);
                case "formatted_text_effects" -> GallerySpatialText.draw(graphics, font, canvas, text("demo"),
                        text("gradient"), elapsed(), animate(), preferences.highContrast());
                case "ascii_field" -> drawAscii(graphics, mouseX, mouseY);
                case "shape_waves" -> {
                    fillCanvas(graphics, INK);
                    boolean hovered = canvas.contains(mouseX, mouseY) && animate();
                    int phase = animate() ? (int) (elapsed() % 3600 * 4096 / 3600) : 700;
                    GallerySpatialEffects.waves(graphics, canvas, phase, preset, hovered ? mouseX - canvas.x() : -1,
                            hovered ? mouseY - canvas.y() : -1, preferences.highContrast() ? 0xFFFFFFFF : 0xFF93E9E0);
                }
                case "visual_reveal" -> drawReveal(graphics);
                default -> throw new IllegalStateException("Unknown spatial sample " + id);
            }
        } finally {
            graphics.disableScissor();
        }
        graphics.nextStratum();
    }

    private void drawRadial(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        pattern(graphics);
        int x = localX(), y = localY();
        int radius = Math.max(2, Math.min(canvas.width(), canvas.height()) * amount / 100);
        int rgb = preferences.highContrast() ? 0xFFFFFF : PALETTE[preset];
        GallerySpatialEffects.radial(graphics, canvas, x, y, radius, (alpha * 255 / 100) << 24 | rgb);
        if (canvas.contains(mouseX, mouseY) || centerDrag.active() || getFocused() == option || preferences.highContrast()) {
            marker(graphics, canvas.x() + x, canvas.y() + y);
        }
    }

    private void drawScene(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        pattern(graphics);
        updateScenePaint();
        boolean lighting = id.equals("spatial_lighting");
        boolean pointerLight = lighting && !lightPositionFromKeyboard && canvas.contains(mouseX, mouseY);
        int lightX = pointerLight ? mouseX : canvas.x() + localX();
        int lightY = pointerLight ? mouseY : canvas.y() + localY();
        if (lighting) {
            var type = LIGHT_TYPES[preset];
            var direction = Math.atan2(canvas.y() + canvas.height() * .5 - lightY + .01,
                    canvas.x() + canvas.width() * .5 - lightX + .01);
            scene.light(LIGHT_ID, new Light(type, lightX, lightY, 28, amount * .032,
                    Math.max(36, canvas.width() * .43), 0xFF9DDBFF, direction, .52));
        }
        boolean glass = id.equals("spatial_surface") && scene.settings().blur() && alpha > 0
                && BackdropCapture.request(graphics) != null;
        double scale = minecraft.getWindow().getGuiScale();
        scene.draw(width, height, scale, true, (paint, mesh, backdrop) -> {
            if (glass && backdrop != null) {
                LiquidGlassSurface.drawPrepared(graphics, (int) backdrop.x(), (int) backdrop.y(), (int) backdrop.width(),
                        (int) backdrop.height(), backdrop.corner(), 0x206F92AD, 0x506385A7,
                        SURFACE_OPTICS, preferences, alpha * .0045f);
            }
            GallerySpatialEffects.spatial(graphics, paint, mesh);
        });
        if (lighting) {
            marker(graphics, lightX, lightY);
        } else {
            String[] labels = id.equals("spatial_surface") ? SURFACE_LABELS : SHADOW_LABELS;
            for (int i = 0; i < labels.length; i++) {
                var label = tileLabels[i];
                if (label.height() > 0) PanelText.drawLine(graphics, font, text(labels[i]), label.x(), label.y(),
                        label.width(), LIGHT_INK, mouseX, mouseY);
            }
        }
    }

    private void drawAscii(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        fillCanvas(graphics, INK);
        double time = animate() ? elapsed() / 590.0 : 1.2;
        int focusX = canvas.contains(mouseX, mouseY) && animate() ? mouseX : canvas.x() + canvas.width() / 2;
        int focusY = canvas.contains(mouseX, mouseY) && animate() ? mouseY : canvas.y() + canvas.height() / 2;
        int cellWidth = Math.max(8, font.width("@") + 2), cellHeight = Math.max(10, font.lineHeight + 2);
        for (int y = canvas.y() + 2; y + font.lineHeight <= canvas.bottom(); y += cellHeight) {
            for (int x = canvas.x() + 2; x + cellWidth <= canvas.right(); x += cellWidth) {
                double dx = (x - focusX) * .049, dy = (y - focusY) * .07;
                double wave = Math.sin(2.4 * Math.hypot(dx, dy) - time);
                double density = .48 + .25 * wave + .24 * Math.cos(dx + dy * 1.6 + time * .6);
                int index = Math.clamp((int) (density * ASCII_RAMP.length), 0, ASCII_RAMP.length - 1);
                if (index > 0) {
                    int color = preferences.highContrast() ? 0xFFFFFFFF : 0xFF60B4BF + (index * 9 << 16) + (index * 5 << 8);
                    graphics.text(font, ASCII_RAMP[index], x, y, color, false);
                }
            }
        }
    }

    private void startReveal(double x, double y) {
        centerX = x;
        centerY = y;
        revealStarted = animate() ? Util.getMillis() : -1;
        revealProgress = animate() ? 0 : 1000;
        revealSlider.setValue(revealSliderValue());
    }

    private int revealSliderValue() {
        return revealProgress / (SLIDER_STEP * 10) * SLIDER_STEP;
    }

    private void syncReveal() {
        if (revealStarted >= 0) {
            revealProgress = Math.min(1000, (int) ((Util.getMillis() - revealStarted) * 1000 / REVEAL_DURATION));
            if (revealProgress == 1000) revealStarted = -1;
        }
        if (!revealSlider.isPointerEditing()) revealSlider.setValue(revealSliderValue());
    }

    private void drawReveal(GuiGraphicsExtractor graphics) {
        syncReveal();
        int progress = revealSlider.isPointerEditing() ? revealSlider.value() * 10 : revealProgress;
        pattern(graphics);
        var area = revealBounds();
        var snapshot = preferences.useBackdropBlur() && progress < 1000 ? BackdropCapture.request(graphics) : null;
        GallerySpatialEffects.reveal(graphics, area, snapshot, progress,
                (int) (centerX * area.width()), (int) (centerY * area.height()), preferences.highContrast());
        graphics.outline(area.x() - 1, area.y() - 1, area.width() + 2, area.height() + 2, LIGHT_INK);
    }

    private Bounds revealBounds() {
        int border = Math.min(12, Math.max(3, canvas.height() / 9));
        int w = Math.min(260, canvas.width() - border * 2);
        int h = Math.min(canvas.height() - border * 2, w * 3 / 5);
        w = Math.min(w, h * 5 / 3);
        return new Bounds(canvas.x() + (canvas.width() - w) / 2, canvas.y() + (canvas.height() - h) / 2, w, h);
    }

    private void pattern(GuiGraphicsExtractor graphics) {
        fillCanvas(graphics, INK);
        int color = preferences.highContrast() ? 0xFF384E62 : 0xFF344D67;
        for (int y = canvas.y(); y < canvas.bottom(); y += 16) {
            for (int x = canvas.x() + ((y - canvas.y()) / 16 & 1) * 8; x < canvas.right(); x += 16) {
                graphics.fill(x, y, x + 8, y + 8, color);
            }
        }
    }

    private void fillCanvas(GuiGraphicsExtractor graphics, int color) {
        graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), color);
    }

    private void marker(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 5, y - 2, x + 6, y + 3, INK);
        graphics.fill(x - 2, y - 5, x + 3, y + 6, INK);
        graphics.fill(x - 4, y, x + 5, y + 1, LIGHT_INK);
        graphics.fill(x, y - 4, x + 1, y + 5, LIGHT_INK);
    }

    private int localX() { return (int) (centerX * (canvas.width() - 1)); }

    private int localY() { return (int) (centerY * (canvas.height() - 1)); }

    private double relativeX(double x) { return Math.clamp((x - canvas.x()) / (canvas.width() - 1), 0, 1); }

    private double relativeY(double y) { return Math.clamp((y - canvas.y()) / Math.max(1, canvas.height() - 1), 0, 1); }

    private void moveCenter(double x, double y) {
        centerX = relativeX(x);
        centerY = relativeY(y);
    }

    @Override
    public void mouseMoved(double x, double y) {
        lightPositionFromKeyboard = false;
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (centerDrag.active()) return true;
        if (!compact && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && canvas.contains(event.x(), event.y())) {
            if (id.equals("radial_gradient") && centerDrag.begin(event.button(), event.x(), event.y())) {
                moveCenter(event.x(), event.y());
                return true;
            }
            if (id.equals("visual_reveal")) {
                var area = revealBounds();
                if (area.contains(event.x(), event.y())) {
                    startReveal((event.x() - area.x()) / area.width(), (event.y() - area.y()) / area.height());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (centerDrag.move(event.button(), event.x(), event.y()) == null) return super.mouseDragged(event, dx, dy);
        moveCenter(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (centerDrag.release(event.button(), event.x(), event.y()) == null) return super.mouseReleased(event);
        moveCenter(event.x(), event.y());
        committedX = centerX;
        committedY = centerY;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && centerDrag.active()) {
            cancelInteraction();
            return true;
        }
        if (!compact && option != null && getFocused() == option
                && (id.equals("radial_gradient") || id.equals("spatial_lighting"))) {
            int dx = event.key() == GLFW.GLFW_KEY_LEFT ? -5 : event.key() == GLFW.GLFW_KEY_RIGHT ? 5 : 0;
            int dy = event.key() == GLFW.GLFW_KEY_UP ? -5 : event.key() == GLFW.GLFW_KEY_DOWN ? 5 : 0;
            if (dx != 0 || dy != 0) {
                moveCenter(canvas.x() + localX() + dx, canvas.y() + localY() + dy);
                committedX = centerX;
                committedY = centerY;
                lightPositionFromKeyboard = true;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected void cancelInteraction() {
        if (centerDrag.active()) {
            centerDrag.cancel();
            centerX = committedX;
            centerY = committedY;
        }
    }

    @Override
    public void removed() {
        super.removed();
        closeScene();
    }

    private void closeScene() {
        if (scene != null) {
            scene.close();
            scene = null;
        }
    }

    @Override
    public Component getNarrationMessage() {
        Component detail = switch (id) {
            case "radial_gradient", "spatial_lighting" -> text("position_hint");
            case "formatted_text_effects" -> CommonComponents.joinForNarration(text("demo"), text("gradient"));
            default -> null;
        };
        if (preferences != null && preferences.highContrast() && scene != null) {
            return detail == null ? CommonComponents.joinForNarration(getTitle(), SPATIAL_HIGH_CONTRAST)
                    : CommonComponents.joinForNarration(getTitle(), SPATIAL_HIGH_CONTRAST, detail);
        }
        return detail == null ? super.getNarrationMessage() : CommonComponents.joinForNarration(getTitle(), detail);
    }
}
