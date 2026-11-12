// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassService;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

/** Drives the normal material route and native keyboard controls in both loader clients. */
final class MaterialCatalogSmoke {
    private static final String ID = "sh_better_ui:liquid_glass";
    private static final String CONSUMER_TITLE = "shbui_catalog_consumer.title";
    private static final String GALLERY = "sh_better_ui.panel.developer.gallery.";
    private static final String KEYS = GALLERY + "material.";
    private static final UiPreferences REDUCED_MOTION = new UiPreferences(false, true, true);
    private static final int[] KEYFRAMES_MS = {800, 1600, 2400};
    private static final java.util.List<OpticalSample> OPTICAL_SAMPLES = java.util.List.of(
            new OpticalSample("clear", 0, 0, 0),
            new OpticalSample("tinted", 100, 0, 0),
            new OpticalSample("frosted", 0, 100, 0),
            new OpticalSample("warped", 0, 0, 320));

    private static final java.util.List<String> NEW_NATIVE_ROUTES = java.util.List.of(
            "attention_indicator",
            "backdrop_blur",
            "breathing_glow",
            "capsule_reveal",
            "classic_glass_themes",
            "click_ripple",
            "directional_blur",
            "elastic_collision",
            "flow_border",
            "formatted_text_effects",
            "glass_panel_shadow",
            "glass_text_glow",
            "icon_shader",
            "icon_motion",
            "pull_refresh",
            "radial_gradient",
            "refraction_wave",
            "screen_crossfade",
            "spatial_lighting",
            "spatial_shadow",
            "spatial_surface",
            "swipe_toast",
            "visual_reveal");
    private enum Stage { OPEN, PANEL, GALLERY, GALLERY_DRAWN, GALLERY_TUNING, GALLERY_OPTICS, GALLERY_BACK, PANEL_BACK,
        DOT_MATRIX_EDITOR, EFFECT_PREVIEWS, EFFECT_CROSSFADE, EFFECTS_COMPLETE, DRAWN, CALIBRATION_DRAWN, KEYFRAMES,
        RELOADED, REOPENED, CONSUMER }
    private record OpticalSample(String name, int tint, int blur, int warp) {}

    private final long started = System.nanoTime();
    private final boolean effectsOnly;
    private Stage stage = Stage.OPEN;
    private Screen parent;
    private Screen developerPanel;
    private Screen preview;
    private int previousScale;
    private int previousWidth;
    private int previousHeight;
    private int defaultSliderValue;
    private int editedSliderValue;
    private int keyframe;
    private int opticalSample;
    private int effectIndex;
    private int effectFrames;
    private long opticalBefore;
    private long resourceBefore;
    private long reloadStarted;
    private long openStarted;
    private boolean browserPresetImported;
    private CompletableFuture<Void> reload;
    private final JsonObject performance = new JsonObject();

    MaterialCatalogSmoke() { this(false); }

    MaterialCatalogSmoke(boolean effectsOnly) {
        this.effectsOnly = effectsOnly;
    }

    boolean advance(Minecraft client, KernelClientSmoke.Platform game, Path output) throws Exception {
        var engine = UnifiedUI.get();
        var optics = engine.glass().diagnostics();
        require(System.nanoTime() - started < 45_000_000_000L,
                "Material catalog smoke timed out at " + stage + " " + optics);
        switch (stage) {
            case OPEN -> {
                parent = client.screen;
                previousScale = client.options.guiScale().get();
                previousWidth = client.getWindow().getScreenWidth();
                previousHeight = client.getWindow().getScreenHeight();
                if (effectsOnly) {
                    client.options.guiScale().set(1);
                    GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 800, 511);
                    client.resizeDisplay();
                    var appearance = engine.require(UIApis.APPEARANCE);
                    var settings = ClientUiSettings.DEFAULT.withDisplay(false, true, false);
                    preview = appearance.edit(parent, settings, "dotMatrix", new JsonObject(), new JsonObject(),
                            ignored -> { throw new AssertionError("Cancel applied the dot matrix draft"); });
                    client.setScreen(preview);
                    stage = Stage.DOT_MATRIX_EDITOR;
                    return false;
                }
                client.options.guiScale().set(2);
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 800, 480);
                client.resizeDisplay();
                developerPanel = ShBetterUiApi.settings().developerPanel(parent, REDUCED_MOTION);
                preview = developerPanel;
                client.setScreen(developerPanel);
                stage = Stage.PANEL;
            }
            case PANEL -> {
                press(client, translatedWidget(GALLERY + "materials"));
                require(client.screen != developerPanel, "Material gallery button did not open");
                preview = client.screen;
                stage = Stage.GALLERY;
            }
            case GALLERY -> {
                var selected = translatedWidget(GALLERY + "sample_page").getMessage();
                var title = (TranslatableContents) selected.getContents();
                require(title.getArgs()[0] instanceof Component name
                                && name.getContents() instanceof TranslatableContents contents
                                && contents.getKey().equals(GALLERY + "sample.liquid_glass"),
                        "Material gallery opened " + selected.getString() + " instead of Liquid Glass");
                screenshot(client, output.resolve("material-gallery-entry.png"));
                opticalBefore = optics.opticalDraws();
                press(client, translatedWidget(GALLERY + "fullscreen"));
                stage = Stage.GALLERY_DRAWN;
            }
            case GALLERY_DRAWN -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                require(client.screen == preview && optics.shaderReady(), "Gallery did not use the kernel glass shader");
                screenshot(client, output.resolve("material-gallery-liquid-glass.png"));
                widget("section.optics");
                var tint = (UiSlider) widget("tuning.tint");
                require(tint.value() == 15, "Tint slider did not invert the transmission default");
                preview.setFocused(tint);
                key(client, GLFW.GLFW_KEY_RIGHT);
                if (tint.value() == 15) {
                    key(client, GLFW.GLFW_KEY_ENTER);
                    key(client, GLFW.GLFW_KEY_RIGHT);
                }
                require(tint.value() == 16, "Tint slider did not support keyboard adjustment");
                tune(client, game, OPTICAL_SAMPLES.get(opticalSample));
                opticalBefore = optics.opticalDraws();
                stage = Stage.GALLERY_TUNING;
            }
            case GALLERY_TUNING -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                var sample = OPTICAL_SAMPLES.get(opticalSample);
                screenshot(client, output.resolve("material-gallery-tuning-" + sample.name() + ".png"));
                if (++opticalSample < OPTICAL_SAMPLES.size()) {
                    tune(client, game, OPTICAL_SAMPLES.get(opticalSample));
                    opticalBefore = optics.opticalDraws();
                    return false;
                }
                for (String section : java.util.List.of("optics", "playback", "parameters", "compare")) {
                    press(client, widget("section." + section));
                }
                press(client, widget("defaults"));
                press(client, widget("section.presets"));
                require(((UiSlider) widget("tuning.tint")).value() == 15, "Preset reset did not update the quick sliders");
                press(client, widget("view.snell"));
                widget("view.optics_compare");
                opticalBefore = optics.opticalDraws();
                stage = Stage.GALLERY_OPTICS;
            }
            case GALLERY_OPTICS -> {
                if (optics.opticalDraws() < opticalBefore + 2) return false;
                require(optics.opticalDraws() == opticalBefore + 2, "Optics comparison did not draw two surfaces");
                screenshot(client, output.resolve("material-gallery-snell-comparison.png"));
                key(client, GLFW.GLFW_KEY_ESCAPE);
                stage = Stage.GALLERY_BACK;
            }
            case GALLERY_BACK -> {
                require(client.screen == preview, "Exiting fullscreen did not retain the material gallery");
                translatedWidget(GALLERY + "sample_page");
                key(client, GLFW.GLFW_KEY_ESCAPE);
                stage = Stage.PANEL_BACK;
            }
            case PANEL_BACK -> {
                if (client.screen == preview) return false;
                require(client.screen == developerPanel
                                && developerPanel.getFocused() == translatedWidget(developerPanel, GALLERY + "materials"),
                        "Material gallery did not restore focus to its developer panel entry");
                key(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == parent, "Developer panel did not return to its parent");
                openEffects(client);
            }
            case DOT_MATRIX_EDITOR -> {
                if (++effectFrames < 3) return false;
                require(client.screen == preview, "Appearance service did not open the dot matrix editor");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    require(dotsVisible(image, client, 8) && dotsVisible(image, client, preview.width - 40),
                            "Dot matrix editor did not paint both margins outside the form");
                    image.writeToFile(output.resolve("dot-matrix-editor.png").toFile());
                }
                key(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == parent, "Cancel did not return from the dot matrix editor");
                openEffects(client);
            }
            case EFFECT_PREVIEWS -> {
                if (++effectFrames < 3) return false;
                var id = NEW_NATIVE_ROUTES.get(effectIndex);
                require(client.screen == preview && !visibleControl(preview, GALLERY + "retry_preview"),
                        "Native material preview failed to render: " + id);
                if ("refraction_wave".equals(id) || "directional_blur".equals(id) || "backdrop_blur".equals(id)
                        || "visual_reveal".equals(id)) {
                    require(ShBetterUiApi.blur().isReady() && ShBetterUiApi.blur().isAllowed(preview),
                            "Native blur was unavailable: " + id);
                    screenshot(client, output.resolve("material-effect-" + id + ".png"));
                }
                if (id.startsWith("spatial_")) {
                    screenshot(client, output.resolve("material-effect-" + id + ".png"));
                }
                if ("elastic_collision".equals(id) || "pull_refresh".equals(id)) {
                    if (effectFrames == 3) {
                        press(client, translatedWidget("elastic_collision".equals(id)
                                ? GALLERY + "collision_toggle" : "sh_better_ui.pull_refresh.label"));
                        return false;
                    }
                    screenshot(client, output.resolve("material-effect-" + id + ".png"));
                }
                if ("icon_motion".equals(id)) {
                    screenshot(client, output.resolve("material-effect-" + id + ".png"));
                }
                if ("swipe_toast".equals(id)) {
                    translatedWidget(GALLERY + "toast_floating");
                    translatedWidget(GALLERY + "toast_inline");
                }
                if ("screen_crossfade".equals(id)) {
                    press(client, translatedWidget(GALLERY + "crossfade_open"));
                    require(client.screen != preview, "Crossfade did not open the next screen");
                    stage = Stage.EFFECT_CROSSFADE;
                    return false;
                }
                closeEffect(client);
            }
            case EFFECT_CROSSFADE -> {
                require(client.screen != preview, "Crossfade screen left before rendering");
                key(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == preview, "Crossfade did not return to its preview");
                require(preview.getFocused() == translatedWidget(GALLERY + "crossfade_open"),
                        "Crossfade did not restore focus to the open control");
                stage = Stage.EFFECT_PREVIEWS;
                closeEffect(client);
            }
            case EFFECTS_COMPLETE -> {
                Files.write(output.resolve("native-effect-previews.txt"), NEW_NATIVE_ROUTES);
                client.options.guiScale().set(previousScale);
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), previousWidth, previousHeight);
                client.resizeDisplay();
                return true;
            }
            case DRAWN -> {
                if (effectIndex == NEW_NATIVE_ROUTES.size()) {
                    effectIndex++;
                    Files.write(output.resolve("native-effect-previews.txt"), NEW_NATIVE_ROUTES);
                }
                if (optics.opticalDraws() <= opticalBefore) return false;
                performance.addProperty("openToFirstOpticalWallMillis", (System.nanoTime() - openStarted) / 1_000_000d);
                press(client, widget("view.snell"));
                press(client, widget("view.optics_compare"));
                widget("view.calibration");
                opticalBefore = optics.opticalDraws();
                stage = Stage.CALIBRATION_DRAWN;
            }
            case CALIBRATION_DRAWN -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                require(!widget("play").active, "Reduced motion started continuous playback");
                var frame = (UiSlider) widget("frame");
                require(frame.value() == 0, "Reduced motion did not start at frame zero");
                press(client, widget("step"));
                require(frame.value() == 1, "Native single-step did not advance one frame");
                press(client, widget("time_reset"));
                require(frame.value() == 0, "Native time reset did not rewind the scene");
                captureKeyframe(client, output, 0);
                performance.add("baselineOptics", optics(optics));
                seekKeyframe(client, game, optics.opticalDraws());
                stage = Stage.KEYFRAMES;
            }
            case KEYFRAMES -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                captureKeyframe(client, output, KEYFRAMES_MS[keyframe]);
                keyframe++;
                if (keyframe < KEYFRAMES_MS.length) {
                    seekKeyframe(client, game, optics.opticalDraws());
                    return false;
                }
                press(client, widget("time_reset"));
                press(client, widget("section.playback"));
                var slider = (UiSlider) parameter();
                defaultSliderValue = slider.value();
                key(client, GLFW.GLFW_KEY_TAB);
                preview.setFocused(slider);
                key(client, GLFW.GLFW_KEY_RIGHT);
                if (slider.value() == defaultSliderValue) {
                    key(client, GLFW.GLFW_KEY_ENTER);
                    key(client, GLFW.GLFW_KEY_RIGHT);
                }
                editedSliderValue = slider.value();
                require(editedSliderValue > defaultSliderValue, "Native keyboard did not edit the parameter draft");
                press(client, widget("compare_off"));
                client.options.guiScale().set(2);
                client.resizeDisplay();
                require(client.screen == preview && ((UiSlider) parameter()).value() == editedSliderValue,
                        "GUI scale lost the active material draft");
                resourceBefore = engine.require(UIApis.RESOURCES).version();
                opticalBefore = engine.glass().diagnostics().opticalDraws();
                reloadStarted = System.nanoTime();
                reload = client.reloadResourcePacks();
                stage = Stage.RELOADED;
            }
            case RELOADED -> {
                if (!reload.isDone()) return false;
                reload.join();
                if (!performance.has("reloadWallMillis")) {
                    performance.addProperty("reloadWallMillis", (System.nanoTime() - reloadStarted) / 1_000_000d);
                }
                require(engine.require(UIApis.RESOURCES).version() > resourceBefore, "Resource reload did not publish a generation");
                if (optics.opticalDraws() < opticalBefore + 2) return false;
                require(client.screen == preview && ((UiSlider) parameter()).value() == editedSliderValue,
                        "Resource reload lost the active material draft");
                screenshot(client, output.resolve("material-catalog-liquid-glass-reloaded.png"));
                performance.add("reloadedOptics", optics(optics));
                var runtime = Runtime.getRuntime();
                performance.addProperty("processJavaHeapUsedBytes", runtime.totalMemory() - runtime.freeMemory());
                key(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == parent, "Fullscreen material preview did not return to its parent");
                opticalBefore = optics.opticalDraws();
                open(client, "liquid_glass");
                stage = Stage.REOPENED;
            }
            case REOPENED -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                press(client, widget("section.optics"));
                press(client, widget("section.playback"));
                require(((UiSlider) parameter()).value() == defaultSliderValue,
                        "A fresh preview reused the discarded draft");
                key(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == parent, "Reopened material preview did not close");
                client.options.guiScale().set(previousScale);
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), previousWidth, previousHeight);
                client.resizeDisplay();
                performance.addProperty("smokePhaseWallMillis", (System.nanoTime() - started) / 1_000_000d);
                Files.writeString(output.resolve("material-catalog-performance.json"), performance.toString());
                if (Boolean.getBoolean("shb.kernel.smoke.materialConsumer")) {
                    var catalog = engine.require(UIApis.MATERIAL_CATALOG);
                    browserPresetImported = importBrowserPreset(catalog, output.resolve("consumer-preset.json"));
                    opticalBefore = engine.glass().diagnostics().opticalDraws();
                    openConsumer(client);
                    stage = Stage.CONSUMER;
                    return false;
                }
                return true;
            }
            case CONSUMER -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                require(client.screen == preview, "Consumer preview left the active screen before rendering");
                screenshot(client, output.resolve("material-catalog-consumer.png"));
                key(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == parent, "Consumer preview did not return to the title screen");
                Files.writeString(output.resolve("material-catalog-consumer.txt"),
                        "Consumer title button opened its UiScreen, rendered optical glass and returned to title\n"
                        + "Optical draws: " + opticalBefore + " -> " + optics.opticalDraws() + "\n"
                        + "Browser preset imported into native catalog: " + browserPresetImported + "\n");
                return true;
            }
        }
        return false;
    }

    private void open(Minecraft client, String id) {
        preview = ShBetterUiApi.settings().developerMaterialFullscreen(parent, id, REDUCED_MOTION, () -> null);
        require(preview.getTitle().getContents() instanceof TranslatableContents title
                        && title.getKey().equals("sh_better_ui.panel.developer.gallery.sample.liquid_glass"),
                "Material route did not reach the Liquid Glass PreviewSpec");
        client.setScreen(preview);
    }

    private void openConsumer(Minecraft client) {
        require(parent instanceof TitleScreen && client.screen == parent, "Consumer smoke requires the actual title screen");
        var button = translatedWidget(parent, CONSUMER_TITLE);
        require(button.active, "Material consumer title button was disabled");
        parent.setFocused(button);
        key(client, GLFW.GLFW_KEY_ENTER);
        preview = client.screen;
        require(preview instanceof UiScreen && preview.getTitle().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(CONSUMER_TITLE),
                "Material consumer button did not open its own UiScreen");
    }

    private static boolean importBrowserPreset(MaterialCatalog catalog, Path file) throws Exception {
        if (!Files.isRegularFile(file)) return false;
        var imported = catalog.importPreset(Files.readString(file));
        require(ID.equals(imported.materialId()) && imported.parameters().get("refraction") instanceof Number refraction
                        && refraction.doubleValue() == 8d,
                "Browser preset did not import into the expected material and refraction value");
        return true;
    }

    private void seekKeyframe(Minecraft client, KernelClientSmoke.Platform game, long opticalDraws) throws Exception {
        var slider = (UiSlider) widget("frame");
        int timeMs = KEYFRAMES_MS[keyframe];
        int frame = timeMs * 60 / 1000;
        int x = slider.getX() + 4 + Math.round((slider.getWidth() - 8) * (timeMs / 2400f));
        KernelClientSmoke.move(client, x, slider.getY() + slider.getHeight() / 2);
        game.flushPointer(client);
        KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
        KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
        require(slider.value() == frame, "Fixture seek selected frame " + slider.value() + " instead of " + frame);
        KernelClientSmoke.move(client, 1, 1);
        game.flushPointer(client);
        opticalBefore = opticalDraws;
    }

    private AbstractWidget parameter() {
        return preview.children().stream().filter(UiSlider.class::isInstance).map(UiSlider.class::cast)
                .filter(widget -> widget.visible).findFirst().orElseThrow();
    }

    private void tune(Minecraft client, KernelClientSmoke.Platform game, OpticalSample sample) throws Exception {
        sliderEndpoint(client, game, "tuning.tint", sample.tint(), 100);
        sliderEndpoint(client, game, "tuning.blur", sample.blur(), 100);
        sliderEndpoint(client, game, "tuning.warp", sample.warp(), 320);
        sliderEndpoint(client, game, "tuning.radius", 320, 320);
        KernelClientSmoke.move(client, 1, 1);
        game.flushPointer(client);
    }

    private void sliderEndpoint(Minecraft client, KernelClientSmoke.Platform game, String id, int target, int max) throws Exception {
        var slider = (UiSlider) widget(id);
        int y = slider.getY() + slider.getHeight() / 2;
        int x = slider.getX() + (target == 0 ? 2 : slider.getWidth() - 2);
        KernelClientSmoke.move(client, slider.getX() + slider.getWidth() / 2, y);
        game.flushPointer(client);
        KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
        KernelClientSmoke.move(client, x, y);
        game.flushPointer(client);
        KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
        require((target == 0 || target == max) && slider.value() == target, "Pointer did not drag " + id + " to " + target);
    }

    private AbstractWidget widget(String key) {
        return translatedWidget(KEYS + key);
    }

    private AbstractWidget translatedWidget(String key) {
        return translatedWidget(preview, key);
    }

    private void openEffects(Minecraft client) {
        client.options.guiScale().set(1);
        GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 800, 511);
        client.resizeDisplay();
        var catalog = UnifiedUI.get().require(UIApis.MATERIAL_CATALOG);
        var previews = ShBetterUiApi.developerPreviews();
        for (var entry : catalog.entries()) {
            if (!entry.id().startsWith("sh_better_ui:")) continue;
            String route = entry.nativePreviewId();
            require(route != null && previews.find(route).filter(spec -> spec.id().equals(route)).isPresent(),
                    "Missing native preview registration for " + entry.id());
        }
        require(catalog.find(ID).orElseThrow() == catalog.find("liquid_glass").orElseThrow(),
                "Material alias did not resolve the stable catalog entry");
        measureLookup(catalog);
        openEffect(client);
        stage = Stage.EFFECT_PREVIEWS;
    }

    private void openEffect(Minecraft client) {
        effectFrames = 0;
        preview = ShBetterUiApi.settings().developerMaterialFullscreen(parent,
                "sh_better_ui:" + NEW_NATIVE_ROUTES.get(effectIndex),
                UiPreferences.DEFAULT, parent::getFocused);
        client.setScreen(preview);
    }

    private void closeEffect(Minecraft client) {
        key(client, GLFW.GLFW_KEY_ESCAPE);
        require(client.screen == parent, "Effect preview did not return to its parent");
        if (++effectIndex < NEW_NATIVE_ROUTES.size()) {
            openEffect(client);
        } else if (effectsOnly) {
            stage = Stage.EFFECTS_COMPLETE;
        } else {
            opticalBefore = UnifiedUI.get().glass().diagnostics().opticalDraws();
            openStarted = System.nanoTime();
            open(client, ID);
            stage = Stage.DRAWN;
        }
    }

    private static boolean visibleControl(Screen screen, String key) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .anyMatch(widget -> widget.visible && widget.getMessage().getContents() instanceof TranslatableContents text
                        && text.getKey().equals(key));
    }

    private static AbstractWidget translatedWidget(Screen screen, String key) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.visible && widget.getMessage().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(key)).findFirst().orElseThrow(() -> new AssertionError("Missing control " + key));
    }

    private void press(Minecraft client, AbstractWidget widget) {
        require(widget.active, "Expected active material control " + widget.getMessage().getString());
        preview.setFocused(widget);
        key(client, GLFW.GLFW_KEY_ENTER);
    }

    private static void key(Minecraft client, int key) {
        KernelClientSmoke.key(client, key, GLFW.GLFW_PRESS);
        KernelClientSmoke.key(client, key, GLFW.GLFW_RELEASE);
    }

    private static void screenshot(Minecraft client, Path path) throws Exception {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(path.toFile());
        }
    }

    private static boolean dotsVisible(NativeImage image, Minecraft client, int left) {
        int background = KernelClientSmoke.pixel(image, client, left, 80);
        for (int y = 80; y < 96; y++) {
            for (int x = left; x < left + 32; x++) {
                if (KernelClientSmoke.pixel(image, client, x, y) != background) return true;
            }
        }
        return false;
    }

    private void measureLookup(MaterialCatalog catalog) {
        int batchSize = 10_000;
        var expected = catalog.find(ID).orElseThrow();
        var samples = new long[5];
        int hits = 0;
        for (int i = -2; i < samples.length; i++) {
            long start = System.nanoTime();
            for (int j = 0; j < batchSize; j++) {
                if (catalog.find((j & 1) == 0 ? ID : "liquid_glass").orElse(null) == expected) hits++;
            }
            if (i >= 0) samples[i] = System.nanoTime() - start;
        }
        require(hits == batchSize * (samples.length + 2), "Material lookup benchmark returned an unexpected entry");
        Arrays.sort(samples);
        long median = samples[samples.length / 2];
        performance.addProperty("queryBatchSize", batchSize);
        performance.addProperty("queryWarmupBatches", 2);
        performance.addProperty("queryMeasuredBatches", samples.length);
        performance.addProperty("queryMedianBatchMillis", median / 1_000_000d);
        performance.addProperty("queryMedianNanosPerLookup", median / (double) batchSize);
    }

    private static JsonObject optics(GlassService.Diagnostics diagnostics) {
        var result = new JsonObject();
        result.addProperty("passesWithCapture", diagnostics.passesWithCapture());
        result.addProperty("opticalDraws", diagnostics.opticalDraws());
        result.addProperty("fallbackDraws", diagnostics.fallbackDraws());
        result.addProperty("captureBytes", diagnostics.captureBytes());
        result.addProperty("shaderReady", diagnostics.shaderReady());
        return result;
    }

    private void captureKeyframe(Minecraft client, Path output, int timeMs) throws Exception {
        var first = widget("section.playback");
        var last = widget("view.calibration");
        var back = translatedWidget("gui.back");
        int canvasX = first.getX();
        int canvasY = back.getY() + back.getHeight() + 9;
        int canvasWidth = last.getX() + last.getWidth() - canvasX;
        int canvasHeight = first.getY() - canvasY - client.font.lineHeight - 6;
        float scale = Math.min(canvasWidth / 640f, canvasHeight / 384f);
        int width = Math.round(640 * scale);
        int height = Math.round(384 * scale);
        int x = canvasX + (canvasWidth - width) / 2;
        int y = canvasY + (canvasHeight - height) / 2;
        double guiScale = client.getWindow().getGuiScale();
        require(guiScale == 1 && width == 640 && height == 384,
                "Unexpected native material fixture geometry " + width + "x" + height + " @" + guiScale);
        String filename = "material-catalog-liquid-glass" + (timeMs == 0 ? "" : "-" + timeMs);
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget());
             var crop = new NativeImage(width, height, false)) {
            if (timeMs == 0) image.writeToFile(output.resolve(filename + ".png").toFile());
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    crop.setPixelRGBA(column, row, image.getPixelRGBA(x + column, y + row));
                }
            }
            crop.writeToFile(output.resolve(filename + "-crop.png").toFile());
        }

        var catalog = UnifiedUI.get().require(UIApis.MATERIAL_CATALOG);
        var entry = catalog.find(ID).orElseThrow();
        var evidence = new JsonObject();
        evidence.addProperty("sceneId", "liquid-glass");
        evidence.addProperty("timeMs", timeMs);
        evidence.addProperty("guiScale", guiScale);
        var bounds = new JsonObject();
        bounds.addProperty("x", x);
        bounds.addProperty("y", y);
        bounds.addProperty("width", width);
        bounds.addProperty("height", height);
        evidence.add("crop", bounds);
        evidence.add("preset", JsonParser.parseString(catalog.exportPreset(ID, entry.defaultValues())));
        Files.writeString(output.resolve(filename + "-scene.json"), evidence.toString());
    }
}
