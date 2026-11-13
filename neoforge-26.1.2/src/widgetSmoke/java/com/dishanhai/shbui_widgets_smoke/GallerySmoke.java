// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperGalleryScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiUnderlinedTabs;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Opens the packaged gallery through its control-panel entry and exercises its real widgets. */
final class GallerySmoke {
    private static final String PANEL = "sh_better_ui.panel.";
    private static final String GALLERY = PANEL + "developer.gallery.";
    private static final String TIMELINE = GALLERY + "frame_timeline.";
    private static final List<String> CONTROLS = List.of("button", "toggle", "slider", "disclosure",
            "sections", "scrollable_sections", "underlined_tabs", "stepper");
    private static final List<String> MIGRATED = List.of(
            "dynamic_status", "button_feedback", "loading_spinner", "loading_progress", "loading_skeleton",
            "attention_indicator", "gradient_card", "slide_confirm", "local_modal", "bottom_sheet",
            "elastic_collision", "pull_refresh", "interruptible_animation", "page_slide", "recipe_transition",
            "screen_crossfade", "toast", "visual_alignment", "directional_blur", "refraction_wave",
            "classic_glass_themes", "glass_panel_shadow", "glass_text_glow", "glass_tooltip", "icon_shader");
    private static final List<String> FINAL_MIGRATION = List.of(
            "radial_gradient", "spatial_surface", "spatial_shadow", "spatial_lighting", "formatted_text_effects",
            "effect_appearance", "dynamic_theme", "ascii_field", "shape_waves", "visual_reveal",
            "click_ripple", "flow_border", "breathing_glow", "capsule_reveal", "dot_matrix", "pattern_waves",
            "light_rays", "electric_logo", "antigravity", "code_rain", "laser_flow",
            "music_hud", "music_island", "local_music", "lyrics");
    private static final List<String> MUSIC = List.of("music_hud", "music_island", "local_music", "lyrics");
    private static final List<String> STANDALONE = Stream.concat(Stream.concat(MIGRATED.stream(), FINAL_MIGRATION.stream()),
            Stream.of("live_activity")).toList();
    private static final List<String> FIELD_PIXELS = List.of("electric_logo", "pattern_waves", "antigravity", "laser_flow");
    private static final List<String> FEEDBACK_STATES = List.of("baseline", "cancelled", "save_failed", "saved");
    private static ControlPanelScreen panel;
    private static DeveloperGalleryScreen gallery;
    private static DeveloperPanelScreen developer;
    private static PreviewRegistry.Registration renderRegistration;
    private static Screen renderPreview;
    private static int renderCount;
    private static int renderValue;
    private static int renderedValue = -1;
    private static int renderCloses;
    private static int renderWaitFrames;
    private static boolean renderInputSent;
    private static ControlPanelSettings applied;
    private static int stage;
    private static int frames;
    private static boolean capturePending;
    private static boolean captured;
    private static boolean complete;
    private static double blurredContrast;
    private static int[] glassInterior;
    private static int[] glassExterior;
    private static int[] clearInterior;
    private static int[] frostedInterior;
    private static int[] timelineStart;
    private static int migratedIndex;
    private static int migratedFrames;
    private static boolean materialCapturePending;
    private static boolean materialCaptured;
    private static int[] revealCover;
    private static int[] revealExterior;
    private static boolean revealReplayed;
    private static long inputFocusDeadline;
    private static long fieldCaptureAt;
    private static long revealFinishBy;
    private static int feedbackStage;
    private static boolean feedbackPending;
    private static boolean feedbackCaptured;
    private static boolean feedbackActionApplied;
    private static int[] footerBaseline;

    private GallerySmoke() {}

    static boolean frame(Minecraft minecraft) {
        if (complete) return true;
        if (stage == 11) {
            require(narration((AbstractWidget) gallery.getFocused()).contains(Component.translatable(GALLERY + "mode", 2).getString()),
                    "GLFW Enter updates the mode and native widget narration");
            if (!NarratorSmoke.frame(minecraft, gallery)) return false;
            leaveSample(minecraft, "mode_indicators");
            stage = 12;
        }
        if (stage == 12) {
            if (!verifyMigratedSamples(minecraft)) return false;
            gallery.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
            require(WidgetSmokePlatform.screen(minecraft) == panel, "gallery returns to control panel");
            require(panel.getFocused() == visible(panel, PANEL + "developer.title"), "gallery entry focus restored");
            require(visible(panel, PANEL + "apply").active, "control-panel draft kept");
            require(ShBetterUiApi.settings().current().equals(applied), "samples do not change applied settings");
            click(panel, PANEL + "section.display");
            click(panel, PANEL + "motion");
            verifyHighContrast(minecraft);
            panel.onClose();
            System.out.println("SHBUI_DEVELOPER_GALLERY_PASS backend=" + WidgetSmokePlatform.backend());
            complete = true;
            return true;
        }
        if (gallery == null) {
            if (panel == null) {
                ShBetterUiApi.settings().openControlPanel();
                require(WidgetSmokePlatform.screen(minecraft) instanceof ControlPanelScreen, "opens control panel");
                panel = (ControlPanelScreen) WidgetSmokePlatform.screen(minecraft);
                applied = ShBetterUiApi.settings().current();
                click(panel, PANEL + "section.display");
                click(panel, PANEL + "motion");
                click(panel, PANEL + "section.tools");
                click(panel, PANEL + "developer.title");
                require(WidgetSmokePlatform.screen(minecraft) instanceof DeveloperPanelScreen, "opens developer panel");
                developer = (DeveloperPanelScreen) WidgetSmokePlatform.screen(minecraft);
                verifyDeveloperRoutes(minecraft, developer);
                beginRenderProbe(minecraft);
                return false;
            }
            if (!advanceRenderProbe(minecraft)) return false;
            developer.onClose();
            require(WidgetSmokePlatform.screen(minecraft) == panel, "developer panel returns to settings");
            openAllGallery(minecraft);
            verifyDirectory();
            verifyControls(minecraft);
            return false;
        }
        if (captured) {
            stage++;
            frames = 0;
            capturePending = false;
            captured = false;
            switch (stage) {
                case 1 -> openSample("backdrop_blur");
                case 2 -> click(gallery, PANEL + "enabled");
                case 3 -> {
                    leaveSample(minecraft, "backdrop_blur");
                    openSample("liquid_glass");
                }
                case 4 -> click(gallery, PANEL + "optical_glass");
                case 5 -> {
                    leaveSample(minecraft, "liquid_glass");
                    verifyLegacySamples(minecraft);
                    openSampleByKeyboard("clear_glass");
                }
                case 6, 8 -> gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
                case 7 -> {
                    gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
                    leaveSample(minecraft, "clear_glass");
                    openSampleByKeyboard("frosted_glass");
                }
                case 9 -> {
                    leaveSample(minecraft, "frosted_glass");
                    verifyTimelineControls(minecraft);
                }
                case 10 -> {
                    click(gallery, timelinePointer(timelineSlider(), 1.0));
                    require(timelineSlider().value() == 119 && narration(visible(gallery, TIMELINE + "transport"))
                            .contains(timelineState("ended")), "pointer release commits the final frame");
                }
                case 11 -> {
                    leaveSample(minecraft, "frame_timeline");
                    verifyDialog(minecraft);
                    openSampleByKeyboard("mode_indicators");
                    EntrySmoke.pressKey(minecraft, GLFW.GLFW_KEY_ENTER);
                    return false;
                }
                default -> throw new IllegalStateException("Unknown gallery smoke stage");
            }
        }
        if (++frames > 900) throw new IllegalStateException("Gallery smoke timed out at stage " + stage);
        if (capturePending || frames < 5 || WidgetSmokePlatform.hasOverlay(minecraft)
                || WidgetSmokePlatform.screen(minecraft) != gallery) return false;
        capturePending = true;
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(minecraft), image -> {
            try (image) {
                String name = switch (stage) {
                    case 0 -> "developer-gallery";
                    case 1 -> "developer-gallery-blur";
                    case 2 -> "developer-gallery-blur-off";
                    case 3 -> "developer-gallery-glass";
                    case 4 -> "developer-gallery-glass-off";
                    case 5 -> "developer-gallery-clear-glass";
                    case 6 -> "developer-gallery-clear-glass-off";
                    case 7 -> "developer-gallery-frosted-glass";
                    case 8 -> "developer-gallery-frosted-glass-off";
                    case 9 -> "developer-gallery-timeline-start";
                    case 10 -> "developer-gallery-timeline-end";
                    default -> throw new IllegalStateException("Unknown gallery capture");
                };
                verifyPreview(image, minecraft.getWindow().getGuiScale());
                image.writeToFile(minecraft.gameDirectory.toPath().resolve(name + ".png"));
                captured = true;
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } finally {
                if (!captured) minecraft.stop();
            }
        });
        return false;
    }

    private static boolean verifyMigratedSamples(Minecraft minecraft) {
        if (migratedIndex == STANDALONE.size()) {
            System.out.println("SHBUI_GALLERY_FINAL_BATCH_PASS count=" + FINAL_MIGRATION.size() + " backend=" + WidgetSmokePlatform.backend());
            return true;
        }
        if (migratedIndex == MIGRATED.size() && migratedFrames == 0) {
            System.out.println("SHBUI_GALLERY_BATCH_PASS count=" + MIGRATED.size() + " backend=" + WidgetSmokePlatform.backend());
        }
        String id = STANDALONE.get(migratedIndex);
        String titleKey = GALLERY + "sample." + id;
        boolean needsWindow = id.equals("slide_confirm") || id.equals("bottom_sheet") || FIELD_PIXELS.contains(id);
        if (migratedFrames == 0) {
            focusByTab(gallery, sampleEntry(id));
            gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
            var screen = WidgetSmokePlatform.screen(minecraft);
            require(screen != gallery && screen.getTitle().equals(Component.translatable(titleKey))
                    && !screen.getTitle().getString().equals(titleKey), "opens localized migrated sample: " + id);
            screen.resize(260, 142);
            verifyFullscreenBounds(screen, id);
            if (needsWindow && !minecraft.isWindowActive()) GLFW.glfwFocusWindow(minecraft.getWindow().handle());
            inputFocusDeadline = Util.getMillis() + 15_000;
        } else if (migratedFrames == 2) {
            if (needsWindow && !minecraft.isWindowActive()) {
                require(Util.getMillis() < inputFocusDeadline, "client window activates for " + id);
                return false;
            }
            var screen = WidgetSmokePlatform.screen(minecraft);
            require(screen.getFocused() instanceof AbstractWidget widget && widget.active && widget.visible,
                    "reflow preserves usable keyboard focus: " + id);
            if (id.equals("effect_appearance") || id.equals("dynamic_theme")) {
                if (!verifyCompactFeedback(minecraft, screen, id)) return false;
                screen.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
                require(WidgetSmokePlatform.screen(minecraft) == gallery, "compact editor returns to its catalog");
                openSample(id);
                screen = WidgetSmokePlatform.screen(minecraft);
            }
            screen.resize(gallery.width, gallery.height);
            verifyFullscreenBounds(screen, id);
            verifyMigratedInput(minecraft, screen, id);
            fieldCaptureAt = Util.getMillis() + 1400;
        } else if (migratedFrames == 4 && MUSIC.contains(id)) {
            if (!GalleryMusicSmoke.frame(minecraft, WidgetSmokePlatform.screen(minecraft), id)) return false;
        } else if (migratedFrames == 4 && FIELD_PIXELS.contains(id)) {
            if (!verifyFieldCapture(minecraft, id)) return false;
        } else if (migratedFrames == 4 && (id.equals("directional_blur") || id.equals("icon_shader")
                || id.equals("radial_gradient") || id.equals("visual_reveal"))) {
            if (id.equals("visual_reveal") && revealReplayed
                    && ((UiSlider) WidgetSmokePlatform.screen(minecraft).getFocused()).value() < 100) {
                require(Util.getMillis() < revealFinishBy, "reveal keeps playing after cancelled scrubbing");
                return false;
            }
            if (!materialCapturePending) {
                materialCapturePending = true;
                Screenshot.takeScreenshot(WidgetSmokePlatform.target(minecraft), image -> {
                    try (image) {
                        if (id.equals("visual_reveal")) {
                            String name = revealReplayed ? "developer-gallery-reveal-open.png" : "developer-gallery-reveal-cover.png";
                            image.writeToFile(minecraft.gameDirectory.toPath().resolve(name));
                        }
                        verifyMaterialPixels(id, image, minecraft.getWindow().getGuiScale());
                        materialCaptured = true;
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    } finally {
                        if (!materialCaptured) minecraft.stop();
                    }
                });
            }
            if (!materialCaptured) return false;
            if (id.equals("visual_reveal") && !revealReplayed) {
                Screen screen = WidgetSmokePlatform.screen(minecraft);
                UiSlider slider = (UiSlider) visible(screen, GALLERY + "visual_reveal.progress");
                verifyRevealCancel(screen, slider);
                click(screen, GALLERY + "effect_replay");
                verifyRevealCancel(screen, slider);
                revealFinishBy = Util.getMillis() + 10_000;
                materialCapturePending = materialCaptured = false;
                revealReplayed = true;
                return false;
            }
        } else if (migratedFrames == 5) {
            var previous = WidgetSmokePlatform.screen(minecraft);
            for (int attempt = 0; attempt < 4 && WidgetSmokePlatform.screen(minecraft) != gallery; attempt++) {
                WidgetSmokePlatform.screen(minecraft).keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
            }
            require(WidgetSmokePlatform.screen(minecraft) == gallery && gallery.getFocused() == visible(gallery, titleKey),
                    "Escape returns to the same entry: " + id);
            if (MUSIC.contains(id)) GalleryMusicSmoke.closed(previous, id);
            migratedIndex++;
            migratedFrames = 0;
            materialCapturePending = false;
            materialCaptured = false;
            return false;
        }
        migratedFrames++;
        return false;
    }

    private static void openAllGallery(Minecraft minecraft) {
        gallery = new DeveloperGalleryScreen(panel,
                () -> panel.setFocused(visible(panel, PANEL + "developer.title")));
        WidgetSmokePlatform.open(minecraft, gallery);
    }

    private static void verifyDeveloperRoutes(Minecraft minecraft, DeveloperPanelScreen developer) {
        int originalWidth = developer.width;
        int originalHeight = developer.height;
        developer.resize(260, 200);
        verifyPanelBounds(developer);
        developer.resize(originalWidth, originalHeight);
        verifyPanelBounds(developer);
        click(developer, PANEL + "developer.example.open_dialog");
        var panelCode = WidgetSmokePlatform.screen(minecraft);
        click(panelCode, GALLERY + "copy");
        require(minecraft.keyboardHandler.getClipboard().contains("new ConfirmScreen"),
                "developer panel copies the 26.x dialog example");
        panelCode.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == developer
                        && developer.getFocused() == visible(developer, PANEL + "developer.example.open_dialog"),
                "panel code example restores its entry focus");
        click(developer, GALLERY + "controls");
        var controls = (DeveloperGalleryScreen) WidgetSmokePlatform.screen(minecraft);
        var query = controls.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow();
        query.setValue("sh_better_ui:slider");
        require(findVisible(controls, GALLERY + "sample.slider") != null
                        && findVisible(controls, GALLERY + "sample.button") == null,
                "control directory searches stable IDs");
        controls.setFocused(query);
        controls.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        require(controls.getNarrationMessage().getString().contains(
                Component.translatable(GALLERY + "sample.slider").getString()), "Enter opens the first result");
        click(controls, GALLERY + "fullscreen");
        require(visible(controls, GALLERY + "exit_fullscreen").isFocused(), "fullscreen toggle keeps focus");
        controls.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        require(WidgetSmokePlatform.screen(minecraft) == controls
                        && findVisible(controls, GALLERY + "fullscreen") != null,
                "Escape exits fullscreen without leaving the preview");
        click(controls, GALLERY + "code_open");
        var code = WidgetSmokePlatform.screen(minecraft);
        require(code != controls && code.getTitle().getString().equals(Component.translatable(GALLERY + "code_title").getString()),
                "opens the source for the selected 26.x control");
        code.keyPressed(key(GLFW.GLFW_KEY_PAGE_DOWN));
        click(code, GALLERY + "copy");
        require(minecraft.keyboardHandler.getClipboard().contains("ShBetterUiApi.controls().slider"),
                "copies the complete slider example");
        code.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == controls
                        && controls.getFocused() == visible(controls, GALLERY + "code_open"),
                "code view returns to its preview and focus");
        controls.onClose();
        require(controls.getFocused() == visible(controls, GALLERY + "sample.slider"),
                "return restores the searched entry");
        query = controls.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow();
        query.setValue("sh_better_ui:bottom_sheet");
        click(controls, GALLERY + "sample.bottom_sheet");
        var standalone = WidgetSmokePlatform.screen(minecraft);
        click(standalone, GALLERY + "fullscreen");
        require(visible(standalone, GALLERY + "exit_fullscreen").isFocused(),
                "standalone fullscreen toggle keeps focus");
        standalone.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        require(WidgetSmokePlatform.screen(minecraft) == standalone
                        && findVisible(standalone, GALLERY + "fullscreen") != null,
                "Escape exits standalone fullscreen before returning");
        standalone.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == controls
                        && controls.getFocused() == visible(controls, GALLERY + "sample.bottom_sheet"),
                "standalone preview returns to its searched entry");
        controls.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == developer
                        && developer.getFocused() == visible(developer, GALLERY + "controls"),
                "control gallery returns to its developer entry");

        click(developer, GALLERY + "materials");
        var materials = (DeveloperGalleryScreen) WidgetSmokePlatform.screen(minecraft);
        query = materials.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow();
        query.setValue("managed glass #refraction");
        require(findVisible(materials, GALLERY + "sample.liquid_glass") != null,
                "material directory searches catalog aliases and tags");
        query.setValue("sh_better_ui:slider");
        require(findVisible(materials, GALLERY + "sample.slider") == null,
                "material directory excludes unrelated controls");
        query.setValue("unmatched-preview-identifier");
        require(visible(materials, GALLERY + "directory_clear").active, "empty search can be cleared");
        require(materials.getNarrationMessage().getString().contains(
                Component.translatable(GALLERY + "directory_no_matches").getString()),
                "empty search results are announced");
        click(materials, GALLERY + "directory_clear");
        require(query.getValue().isEmpty() && materials.getFocused() == query,
                "clearing a search returns focus to the input");
        materials.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == developer
                        && developer.getFocused() == visible(developer, GALLERY + "materials"),
                "material gallery returns to its developer entry");

        var settings = ShBetterUiApi.settings();
        require(settings.developerSampleIds().contains("button") && settings.developerSampleIds().size() == 76,
                "public API exposes the complete built-in directory");
        require(settings.developerSampleIds().subList(0, 5)
                        .equals(List.of("underlined_tabs", "stepper", "button", "toggle", "sections")),
                "built-in controls retain the legacy gallery order");
        var direct = settings.developerSampleFullscreen(developer, "sh_better_ui:button",
                settings.preferences(), () -> visible(developer, GALLERY + "controls"));
        WidgetSmokePlatform.open(minecraft, direct);
        require(WidgetSmokePlatform.screen(minecraft) == direct, "public ID route opens a native preview");
        direct.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == developer
                        && developer.getFocused() == visible(developer, GALLERY + "controls"),
                "direct preview returns to the supplied focus");
        direct = settings.developerSampleFullscreen(developer, "bottom_sheet", settings.preferences(),
                () -> visible(developer, GALLERY + "controls"));
        WidgetSmokePlatform.open(minecraft, direct);
        direct.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == developer
                        && developer.getFocused() == visible(developer, GALLERY + "controls"),
                "standalone preview returns to the supplied focus");
        direct = settings.developerMaterialFullscreen(developer, "managed glass", settings.preferences(),
                () -> visible(developer, GALLERY + "controls"));
        WidgetSmokePlatform.open(minecraft, direct);
        direct.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == developer
                        && developer.getFocused() == visible(developer, GALLERY + "controls"),
                "material alias opens its registered native preview");
        developer.setFocused(visible(developer, GALLERY + "materials"));
        direct = settings.developerSampleFullscreen(developer, "button");
        WidgetSmokePlatform.open(minecraft, direct);
        direct.onClose();
        require(WidgetSmokePlatform.screen(minecraft) == developer
                        && developer.getFocused() == visible(developer, GALLERY + "materials"),
                "default direct route restores focus after the parent rebuilds");
        try {
            settings.developerSampleFullscreen(developer, "missing:preview");
            throw new IllegalStateException("Unknown preview ID was accepted");
        } catch (IllegalArgumentException expected) {
            // Unknown IDs must not silently open an unrelated sample.
        }
        verifyExtensionPreview(minecraft, developer);
        System.out.println("SHBUI_DEVELOPER_ROUTES_PASS");
    }

    private static void verifyExtensionPreview(Minecraft minecraft, DeveloperPanelScreen developer) {
        var creations = new AtomicInteger();
        var layouts = new AtomicInteger();
        var clicks = new AtomicInteger();
        var closes = new AtomicInteger();
        PreviewContext[] stale = new PreviewContext[1];
        var spec = new PreviewSpec("shbui_widgets_smoke:live_preview", PreviewSpec.Category.CONTROLS, 900,
                "gui.done", () -> {
                    creations.incrementAndGet();
                    return new PreviewScene() {
                        @Override public void layout(PreviewContext host) {
                            layouts.incrementAndGet();
                            stale[0] = host;
                            var area = host.viewport();
                            host.add(ShBetterUiApi.controls().button(new Bounds(area.x(), area.y(), 100, 22),
                                    Component.translatable("gui.cancel"), ignored -> clicks.incrementAndGet(),
                                    host.preferences(), false, UiStyle.DEFAULT));
                        }
                        @Override public void close() { closes.incrementAndGet(); }
                    };
                });
        var settings = ShBetterUiApi.settings();
        try (var registration = ShBetterUiApi.client().registerDeveloperPreview(spec)) {
            require(creations.get() == 0 && settings.developerSampleIds().contains(spec.id()),
                    "registration is discoverable and lazy");
            click(developer, GALLERY + "controls");
            var controls = (DeveloperGalleryScreen) WidgetSmokePlatform.screen(minecraft);
            var query = controls.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .findFirst().orElseThrow();
            query.setValue(spec.id());
            click(controls, "gui.done");
            var preview = WidgetSmokePlatform.screen(minecraft);
            require(creations.get() == 1 && layouts.get() == 1, "directory mounts the registered scene");
            click(preview, "gui.cancel");
            require(clicks.get() == 1, "extension widget receives native input");
            preview.resize(preview.width, preview.height);
            require(creations.get() == 1 && layouts.get() == 2, "resize retains the scene instance");
            preview.onClose();
            require(WidgetSmokePlatform.screen(minecraft) == controls && closes.get() == 1,
                    "scene closes and returns to the searched directory");
            query = controls.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .findFirst().orElseThrow();
            query.setValue("shbui_widgets_smoke");
            controls.setFocused(visible(controls, "gui.done"));
            var earlier = new PreviewSpec("shbui_widgets_smoke:earlier", PreviewSpec.Category.CONTROLS, 1,
                    "gui.yes", () -> host -> {});
            try (var extra = ShBetterUiApi.client().registerDeveloperPreview(earlier)) {
                controls.tick();
                require(controls.getFocused() == visible(controls, "gui.done"),
                        "directory refresh retains the focused preview when order changes");
            }
            controls.tick();
            controls.onClose();
            var direct = settings.developerSampleFullscreen(developer, spec.id(), settings.preferences(),
                    () -> visible(developer, GALLERY + "controls"));
            WidgetSmokePlatform.open(minecraft, direct);
            registration.close();
            require(direct.keyPressed(key(GLFW.GLFW_KEY_ENTER)), "revoked preview blocks input");
            direct.tick();
            require(WidgetSmokePlatform.screen(minecraft) == developer && closes.get() == 2,
                    "revoked direct preview closes and restores its parent");
            int childCount = direct.children().size();
            var area = stale[0].viewport();
            stale[0].add(ShBetterUiApi.controls().button(new Bounds(area.x(), area.y(), 100, 22),
                    Component.translatable("gui.cancel"), ignored -> clicks.incrementAndGet(),
                    stale[0].preferences(), false, UiStyle.DEFAULT));
            require(direct.children().size() == childCount, "a stale scene context cannot attach widgets");
        }
    }

    private static void beginRenderProbe(Minecraft minecraft) {
        var spec = new PreviewSpec("shbui_widgets_smoke:render_preview", PreviewSpec.Category.MATERIALS, 900,
                "gui.done", () -> new PreviewScene() {
                    @Override public void layout(PreviewContext host) {
                        var area = host.viewport();
                        host.add(ShBetterUiApi.controls().button(new Bounds(area.x(), area.y(), 100, 22),
                                Component.translatable("gui.cancel"), ignored -> renderValue++,
                                host.preferences(), false, UiStyle.DEFAULT));
                    }
                    @Override public void render(UiFrame frame) {
                        renderCount++;
                        renderedValue = renderValue;
                        var area = frame.viewport();
                        frame.graphics().fill(area.x(), area.y() + 26, area.x() + 20, area.y() + 46,
                                renderValue == 0 ? 0xFF4477AA : 0xFF77AA44);
                    }
                    @Override public void close() { renderCloses++; }
                });
        renderRegistration = ShBetterUiApi.client().registerDeveloperPreview(spec);
        renderPreview = ShBetterUiApi.settings().developerSampleFullscreen(developer, spec.id(),
                ShBetterUiApi.settings().preferences(), () -> visible(developer, GALLERY + "controls"));
        WidgetSmokePlatform.open(minecraft, renderPreview);
    }

    private static boolean advanceRenderProbe(Minecraft minecraft) {
        require(++renderWaitFrames <= 300, "extension preview render did not advance");
        if (renderCount == 0) return false;
        if (!renderInputSent) {
            require(renderedValue == 0, "extension scene rendered its initial state");
            click(renderPreview, "gui.cancel");
            renderInputSent = true;
            return false;
        }
        if (renderedValue != 1) return false;
        renderRegistration.close();
        renderPreview.tick();
        require(WidgetSmokePlatform.screen(minecraft) == developer && renderCloses == 1,
                "live extension preview closes when its registration is revoked");
        System.out.println("SHBUI_EXTENSION_LIVE_PREVIEW_PASS");
        return true;
    }

    private static void verifyPanelBounds(Screen screen) {
        var widgets = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.visible).toList();
        for (int first = 0; first < widgets.size(); first++) {
            var a = widgets.get(first);
            for (int second = first + 1; second < widgets.size(); second++) {
                var b = widgets.get(second);
                boolean overlaps = a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                        && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight();
                require(!overlaps, "developer controls do not overlap: " + a.getMessage().getString()
                        + " / " + b.getMessage().getString());
            }
        }
    }

    private static void verifyDirectory() {
        var expected = new TreeSet<>(STANDALONE);
        expected.addAll(CONTROLS);
        expected.addAll(List.of("backdrop_blur", "liquid_glass", "capsule_toggle", "mode_indicators", "text_details",
                "search", "dialog", "liquid_button", "dark_button", "network_tab", "clear_glass", "frosted_glass",
                "reset_icon", "settings_icon", "copy_icon", "status_icon", "frame_timeline"));
        String prefix = GALLERY + "sample.";
        var actual = new TreeSet<String>();
        do {
            for (var child : gallery.children()) {
                if (child instanceof AbstractWidget entry && entry.visible && entry.active
                        && entry.getMessage().getContents() instanceof TranslatableContents title
                        && title.getKey().startsWith(prefix)) {
                    require(!entry.getMessage().getString().equals(title.getKey()), "localized catalog entry " + title.getKey());
                    require(actual.add(title.getKey().substring(prefix.length())), "unique catalog entry " + title.getKey());
                }
            }
        } while (gallery.keyPressed(key(GLFW.GLFW_KEY_PAGE_DOWN)));
        var missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        var extra = new TreeSet<>(actual);
        extra.removeAll(expected);
        require(missing.isEmpty() && extra.isEmpty(), "complete catalog missing=" + missing + " extra=" + extra);
        while (gallery.keyPressed(key(GLFW.GLFW_KEY_PAGE_UP))) { /* Return to the first page. */ }
        System.out.println("SHBUI_GALLERY_CATALOG_PASS count=" + actual.size());
    }

    private static void verifyFullscreenBounds(Screen screen, String id) {
        var fullscreen = visible(screen, GALLERY + "fullscreen");
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || widget == fullscreen || !widget.visible) continue;
            boolean overlaps = fullscreen.getX() < widget.getX() + widget.getWidth()
                    && widget.getX() < fullscreen.getX() + fullscreen.getWidth()
                    && fullscreen.getY() < widget.getY() + widget.getHeight()
                    && widget.getY() < fullscreen.getY() + fullscreen.getHeight();
            require(!overlaps, "fullscreen control does not cover " + id + ": " + widget.getMessage().getString());
        }
    }

    private static boolean verifyFieldCapture(Minecraft minecraft, String id) {
        if (Util.getMillis() < fieldCaptureAt) return false;
        if (!materialCapturePending) {
            materialCapturePending = true;
            var screen = WidgetSmokePlatform.screen(minecraft);
            var canvas = visible(screen, GALLERY + "sample." + id);
            int scale = minecraft.getWindow().getGuiScale();
            Screenshot.takeScreenshot(WidgetSmokePlatform.target(minecraft), image -> {
                try (image) {
                    image.writeToFile(minecraft.gameDirectory.toPath().resolve("developer-gallery-" + id + ".png"));
                    int pixels = fieldPixels(id, image, scale, canvas);
                    require(pixels >= 16, "field geometry reaches the framebuffer: " + id + " pixels=" + pixels);
                    System.out.println("SHBUI_GALLERY_FIELD_PIXELS_PASS id=" + id + " pixels=" + pixels);
                    materialCaptured = true;
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                } finally {
                    if (!materialCaptured) minecraft.stop();
                }
            });
        }
        return materialCaptured;
    }

    private static int fieldPixels(String id, NativeImage image, int scale, AbstractWidget canvas) {
        int left = canvas.getX() + canvas.getWidth() / 4;
        int right = left + canvas.getWidth() / 2;
        int top = canvas.getY() + 3;
        int bottom = canvas.getY() + canvas.getHeight() - 3;
        if (id.equals("laser_flow")) {
            // Below the impact line, the surface and the round flare remain; the vertical beam and dust stop above it.
            int impact = canvas.getY() + (int) (canvas.getHeight() * .77f);
            top = impact + 2;
            bottom = Math.min(bottom, impact + 10);
        }
        int background = switch (id) {
            case "electric_logo" -> 0x130C20;
            case "pattern_waves" -> 0x101521;
            case "antigravity" -> 0x100A1B;
            case "laser_flow" -> 0x141C36;
            default -> throw new IllegalArgumentException(id);
        };
        int count = 0;
        for (int y = top * scale; y < bottom * scale; y++) {
            for (int x = left * scale; x < right * scale; x++) {
                int pixel = image.getPixel(x, y);
                if (Math.abs((pixel & 255) - (background & 255)) > 16
                        || Math.abs((pixel >>> 8 & 255) - (background >>> 8 & 255)) > 16
                        || Math.abs((pixel >>> 16 & 255) - (background >>> 16 & 255)) > 16) count++;
            }
        }
        return count;
    }

    private static void verifyLiveActivity(Screen screen) {
        String prefix = "sh_better_ui.live.preview.";
        var capsule = (UiButton) visible(screen, prefix + "capsule");
        require(screen.getFocused() == capsule && findVisible(screen, prefix + "progress") == null,
                "activity island opens collapsed with capsule focus");
        screen.keyPressed(key(GLFW.GLFW_KEY_SPACE));
        var seek = (UiSlider) visible(screen, prefix + "progress");
        require(capsule.isSelected() && seek.value() == 40, "Space expands activity details at the sample progress");
        focusByTab(screen, seek);
        stepSlider(seek, GLFW.GLFW_KEY_RIGHT);
        require(seek.value() == 41 && ((TranslatableContents) capsule.getMessage().getContents()).getArgs()[1].equals(41),
                "keyboard seeks the sample activity");
        var pointer = timelinePointer(seek, .8);
        require(screen.mouseClicked(pointer, false) && seek.value() == 80, "activity seek previews pointer input");
        screen.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        screen.mouseReleased(pointer);
        require(seek.value() == 41, "Escape restores the committed activity progress");
        click(screen, pointer);
        screen.resize(260, 142);
        var resized = (UiSlider) visible(screen, prefix + "progress");
        require(resized.value() == 80 && screen.getFocused() == resized, "activity reflow retains progress and seek focus");
        click(screen, prefix + "capsule");
        require(findVisible(screen, prefix + "progress") == null && screen.getFocused() == visible(screen, prefix + "capsule"),
                "collapsing activity details restores capsule focus");
        screen.resize(gallery.width, gallery.height);
        System.out.println("SHBUI_GALLERY_LIVE_ACTIVITY_PASS");
    }

    private static void verifyRevealCancel(Screen screen, UiSlider slider) {
        focusByTab(screen, slider);
        int original = slider.value();
        var start = timelinePointer(slider, original >= 50 ? .4 : .6);
        var end = timelinePointer(slider, original >= 50 ? 0 : 1);
        require(screen.mouseClicked(start, false), "reveal slider captures pointer input");
        screen.mouseDragged(end, end.x() - start.x(), 0);
        require(slider.value() != original, "reveal drag previews a different progress");
        require(screen.keyPressed(key(GLFW.GLFW_KEY_ESCAPE)), "Escape cancels reveal scrubbing");
        screen.mouseReleased(end);
        require(screen.getFocused() == slider && !slider.isPointerEditing() && slider.value() == original,
                "cancelling reveal scrubbing restores committed progress and focus");
    }

    private static boolean verifyCompactFeedback(Minecraft minecraft, Screen screen, String id) {
        if (feedbackCaptured) {
            feedbackPending = feedbackCaptured = false;
            feedbackActionApplied = false;
            if (++feedbackStage == FEEDBACK_STATES.size()) {
                feedbackStage = 0;
                footerBaseline = null;
                System.out.println("SHBUI_GALLERY_COMPACT_FEEDBACK_PASS id=" + id);
                return true;
            }
        }
        if (feedbackPending) return false;
        String state = FEEDBACK_STATES.get(feedbackStage);
        if (feedbackStage > 0 && !feedbackActionApplied) {
            if (id.equals("effect_appearance")) {
                var slider = (UiSlider) visible(screen, GALLERY + "appearance.strength");
                focusByTab(screen, slider);
                stepSlider(slider, slider.value() == 100 ? GLFW.GLFW_KEY_LEFT : GLFW.GLFW_KEY_RIGHT);
            } else {
                click(screen, (AbstractWidget) screen.children().getFirst());
            }
            if (state.equals("cancelled")) click(screen, "gui.cancel");
            else applyCompactDraft(minecraft, screen, state.equals("save_failed"));
            require(screen.getNarrationMessage().getString().contains(
                    Component.translatable(GALLERY + "appearance." + state).getString()), "narrates compact feedback " + state);
            require(visible(screen, PANEL + "apply").active == state.equals("save_failed"),
                    "only a failed save retains the compact draft");
            feedbackActionApplied = true;
            return false;
        }
        feedbackPending = true;
        var back = visible(screen, "gui.back");
        int scale = minecraft.getWindow().getGuiScale();
        int left = back.getX() + back.getWidth() + 4;
        int right = screen.width - back.getX();
        int y = back.getY();
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(minecraft), image -> {
            try (image) {
                int[] current = new int[(right - left) * back.getHeight()];
                int index = 0;
                for (int line = y; line < y + back.getHeight(); line++) {
                    for (int x = left; x < right; x++) current[index++] = green(image, scale, x, line);
                }
                if (state.equals("baseline")) footerBaseline = current;
                else {
                    require(changed(footerBaseline, current) > 8, "compact feedback has visible pixels: " + id + " " + state);
                    image.writeToFile(minecraft.gameDirectory.toPath().resolve("developer-gallery-" + id + "-" + state + ".png"));
                }
                feedbackCaptured = true;
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } finally {
                if (!feedbackCaptured) minecraft.stop();
            }
        });
        return false;
    }

    private static void applyCompactDraft(Minecraft minecraft, Screen screen, boolean fail) {
        var path = minecraft.gameDirectory.toPath().resolve("config/sh_better_ui/gallery-previews.properties");
        try {
            byte[] previous = Files.exists(path) ? Files.readAllBytes(path) : null;
            try {
                if (fail) {
                    Files.deleteIfExists(path);
                    Files.createDirectories(path);
                }
                click(screen, PANEL + "apply");
            } finally {
                if (fail || previous == null) Files.deleteIfExists(path);
                if (previous != null) Files.write(path, previous);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void verifyHighContrast(Minecraft minecraft) {
        require(!visible(panel, PANEL + "apply").active, "control panel draft was restored before contrast check");
        boolean toggle = !ShBetterUiApi.settings().current().highContrast();
        if (toggle) {
            click(panel, PANEL + "contrast");
            click(panel, PANEL + "apply");
        }
        try {
            require(ShBetterUiApi.settings().current().highContrast(), "enabled high contrast for spatial controls");
            click(panel, PANEL + "section.tools");
            openAllGallery(minecraft);
            for (String id : List.of("spatial_surface", "spatial_shadow", "spatial_lighting")) {
                openSample(id);
                var screen = WidgetSmokePlatform.screen(minecraft);
                long inactive = screen.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).filter(widget -> widget.visible && !widget.active).count();
                require(inactive == 2 && screen.getNarrationMessage().getString().contains(
                                Component.translatable(GALLERY + "spatial_high_contrast").getString()),
                        "disabled effect controls and high contrast explanation: " + id);
                if (id.equals("spatial_lighting")) {
                    var reset = visible(screen, GALLERY + "spatial_lighting.reset_position");
                    focusByTab(screen, reset);
                    require(screen.keyPressed(key(GLFW.GLFW_KEY_RIGHT)), "light marker remains keyboard movable");
                    require(screen.keyPressed(key(GLFW.GLFW_KEY_ENTER)), "keyboard resets the light position");
                }
                screen.onClose();
            }
            gallery.onClose();
            System.out.println("SHBUI_GALLERY_HIGH_CONTRAST_PASS");
        } finally {
            if (WidgetSmokePlatform.screen(minecraft) != panel) WidgetSmokePlatform.open(minecraft, panel);
            click(panel, PANEL + "section.display");
            if (toggle) {
                click(panel, PANEL + "contrast");
                click(panel, PANEL + "apply");
            }
        }
        require(ShBetterUiApi.settings().current().equals(applied), "contrast check restores applied panel settings");
    }

    private static void verifyMaterialPixels(String id, NativeImage image, int scale) {
        int width = image.getWidth() / scale;
        int panelWidth = Math.min(500, width - 16);
        int left = (width - panelWidth) / 2 + 12;
        int middle = width / 2;
        int right = width - left;
        int top = (image.getHeight() / scale - Math.min(306, image.getHeight() / scale - 16)) / 2;
        if (id.equals("radial_gradient")) {
            int y = top + 84;
            int center = green(image, scale, middle + 8, y);
            int edge = green(image, scale, left + 5, y);
            require(center > edge + 65, "radial gradient fades to the sample backdrop: " + center + " -> " + edge);
        } else if (id.equals("visual_reveal")) {
            int y = top + 83;
            int[] interior = row(image, scale, middle - 50, middle + 50, y);
            int[] exterior = row(image, scale, left + 4, left + 37, y);
            if (revealReplayed) {
                int insideChange = changed(revealCover, interior);
                int outsideChange = changed(revealExterior, exterior);
                require(insideChange > 20 && outsideChange == 0,
                        "reveal exposes the texture only inside its preview, interior=" + insideChange + " exterior=" + outsideChange);
                System.out.println("SHBUI_GALLERY_SPATIAL_PIXELS_PASS backend=" + WidgetSmokePlatform.backend()
                        + " interior=" + insideChange + " exterior=" + outsideChange);
            } else {
                revealCover = interior;
                revealExterior = exterior;
            }
        } else if (id.equals("directional_blur")) {
            double original = rowContrast(row(image, scale, left + 18, middle - 18, top + 68));
            double filtered = rowContrast(row(image, scale, middle + 18, right - 18, top + 68));
            require(original > 8 && filtered < original * 0.80,
                    "regional blur smooths sampled stripes: " + original + " -> " + filtered);
        } else {
            int count = 0;
            int center = left + (right - left) * 3 / 8;
            for (int y = top + 78; y < top + 143; y++) {
                for (int x = center - 18; x < center + 18; x++) {
                    int pixel = image.getPixel(x * scale, y * scale);
                    int red = (pixel >>> 16) & 0xFF, green = (pixel >>> 8) & 0xFF, blue = pixel & 0xFF;
                    int highest = Math.max(red, Math.max(green, blue));
                    if (highest >= 150 && highest - Math.min(red, Math.min(green, blue)) >= 35) count++;
                }
            }
            require(count > 45, "icon pipeline draws colored texture pixels: " + count);
        }
    }

    private static double rowContrast(int[] values) {
        int variation = 0;
        for (int index = 1; index < values.length; index++) variation += Math.abs(values[index] - values[index - 1]);
        return variation / (double) (values.length - 1);
    }

    private static void verifyMigratedInput(Minecraft minecraft, Screen screen, String id) {
        if (id.equals("live_activity")) {
            verifyLiveActivity(screen);
            return;
        }
        if (id.equals("effect_appearance")) {
            verifyAppearanceEdits(minecraft, screen);
            return;
        }
        if (MUSIC.contains(id)) return;
        if (id.equals("dynamic_theme")) {
            var original = ((AbstractWidget) screen.getFocused()).getMessage();
            screen.keyPressed(key(GLFW.GLFW_KEY_ENTER));
            require(!((AbstractWidget) screen.getFocused()).getMessage().equals(original), "dynamic theme cycles the draft");
            click(screen, "gui.cancel");
            require(screen.children().stream().anyMatch(widget -> widget instanceof AbstractWidget button
                    && button.getMessage().equals(original)), "dynamic theme cancel restores the saved choice");
            return;
        }
        if (id.equals("capsule_reveal")) {
            click(screen, GALLERY + "appearance.reveal");
            click(screen, GALLERY + "appearance.hide");
            require(visible(screen, GALLERY + "appearance.reveal").active, "capsule reverses its reveal");
            return;
        }
        if (id.equals("toast")) {
            click(screen, GALLERY + "code_open");
            var code = WidgetSmokePlatform.screen(minecraft);
            click(code, GALLERY + "copy");
            require(minecraft.keyboardHandler.getClipboard().contains("SystemToast.add"),
                    "standalone toast example copies its native API");
            code.onClose();
            require(WidgetSmokePlatform.screen(minecraft) == screen
                            && screen.getFocused() == visible(screen, GALLERY + "code_open"),
                    "standalone code returns to its source control");
            click(screen, GALLERY + "toast_floating");
            click(screen, "sh_better_ui.swipe_toast.close");
            click(screen, GALLERY + "toast.undo");
            require(visible(screen, GALLERY + "toast.notice").active, "undo restores the dismissed toast");
            return;
        }
        require(screen.getFocused() instanceof AbstractWidget, "sample provides keyboard focus: " + id);
        var focused = (AbstractWidget) screen.getFocused();
        if (id.equals("slide_confirm")) {
            require(!focused.getMessage().equals(CommonComponents.GUI_BACK), "reflow retains focus on the slide handle");
            verifySlideConfirm(screen, focused);
            return;
        }
        if (id.equals("bottom_sheet")) {
            String sheet = PANEL + "developer.demo.bottom_sheet.";
            require(focused.getMessage().getContents() instanceof TranslatableContents current
                    && current.getKey().equals(sheet + "current"), "reflow retains focus on the sheet handle");
            require(screen.keyPressed(key(GLFW.GLFW_KEY_UP)) && ((UiButton) visible(screen, sheet + "high")).isSelected(),
                    "Up selects the high sheet detent");
            require(screen.keyPressed(key(GLFW.GLFW_KEY_DOWN)) && ((UiButton) visible(screen, sheet + "middle")).isSelected(),
                    "Down restores the middle sheet detent");
            return;
        }
        if (focused.getMessage().equals(CommonComponents.GUI_BACK)) return;
        if (focused instanceof UiSlider && FINAL_MIGRATION.contains(id)) {
            var slider = (UiSlider) focused;
            int before = slider.value();
            stepSlider(slider, GLFW.GLFW_KEY_RIGHT);
            if (slider.value() == before) stepSlider(slider, GLFW.GLFW_KEY_LEFT);
            require(slider.value() != before, "keyboard adjusts the sample: " + id);
            return;
        }
        screen.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        switch (id) {
            case "screen_crossfade" -> require(WidgetSmokePlatform.screen(minecraft) != screen,
                    "crossfade opens another native screen");
            case "page_slide", "recipe_transition" -> {
                require(screen.getNarrationMessage().getString().contains(Component.translatable(GALLERY + id + ".page", 2).getString()),
                        "switching pages updates narration: " + id);
                screen.keyPressed(key(GLFW.GLFW_KEY_ENTER));
                require(screen.getNarrationMessage().getString().contains(Component.translatable(GALLERY + id + ".page", 1).getString()),
                        "rapid reversal returns to the first page: " + id);
                if (id.equals("page_slide")) click(screen, GALLERY + "page_slide.flat");
                click(screen, GALLERY + "slide_up");
                require(screen.getNarrationMessage().getString().contains(Component.translatable(GALLERY + id + ".page", 2).getString()),
                        "vertical transition switches the page: " + id);
            }
            case "local_modal" -> {
                var handle = screen.getFocused();
                require(handle != focused, "opening the modal gives its handle focus");
                screen.keyPressed(key(GLFW.GLFW_KEY_TAB));
                require(screen.getFocused() != handle && screen.getFocused() != focused, "Tab stays inside the modal");
                screen.keyPressed(key(GLFW.GLFW_KEY_TAB));
                require(screen.getFocused() == handle, "Tab cycles back to the modal handle");
                screen.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
                require(screen.getFocused() == focused, "closing the modal restores its entry focus");
            }
            case "loading_spinner", "loading_progress", "directional_blur", "refraction_wave" ->
                    screen.keyPressed(key(GLFW.GLFW_KEY_RIGHT));
            default -> { }
        }
    }

    private static void verifyAppearanceEdits(Minecraft minecraft, Screen screen) {
        String key = GALLERY + "appearance.strength";
        UiSlider slider = (UiSlider) visible(screen, key);
        int saved = slider.value();
        focusByTab(screen, slider);
        int forward = saved < 100 ? GLFW.GLFW_KEY_RIGHT : GLFW.GLFW_KEY_LEFT;
        int backward = saved < 100 ? GLFW.GLFW_KEY_LEFT : GLFW.GLFW_KEY_RIGHT;
        stepSlider(slider, forward);
        require(slider.value() != saved && visible(screen, PANEL + "apply").active, "appearance change creates a draft");
        click(screen, "gui.cancel");
        require(slider.value() == saved, "appearance cancel restores saved strength");
        focusByTab(screen, slider);
        stepSlider(slider, forward);
        int appliedValue = slider.value();
        click(screen, PANEL + "apply");
        require(!visible(screen, PANEL + "apply").active, "appearance apply commits its draft");
        screen.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        require(WidgetSmokePlatform.screen(minecraft) == gallery, "appearance returns to the directory");
        openSample("effect_appearance");
        Screen reopened = WidgetSmokePlatform.screen(minecraft);
        slider = (UiSlider) visible(reopened, key);
        require(slider.value() == appliedValue, "appearance reads the saved value on reopen");
        focusByTab(reopened, slider);
        stepSlider(slider, backward);
        require(slider.value() == saved, "appearance can restore the original value");
        click(reopened, PANEL + "apply");
    }

    private static void stepSlider(UiSlider slider, int direction) {
        if (!slider.keyPressed(key(direction))) {
            slider.keyPressed(key(GLFW.GLFW_KEY_ENTER));
            slider.keyPressed(key(direction));
        }
    }

    private static void verifySlideConfirm(Screen screen, AbstractWidget handle) {
        var reset = visible(screen, GALLERY + "slide_confirm.reset");
        double y = handle.getY() + handle.getHeight() / 2.0;
        var button = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
        var start = new MouseButtonEvent(handle.getX() + 9, y, button);
        var end = new MouseButtonEvent(handle.getX() + handle.getWidth() - 3, y, button);
        require(!reset.active && screen.mouseClicked(start, false) && screen.isDragging(), "slide handle captures the pointer");
        require(screen.mouseDragged(end, end.x() - start.x(), 0), "captured slide accepts the drag");
        require(!reset.active, "drag previews confirmation before release");
        require(screen.mouseReleased(end) && reset.active, "releasing at the end confirms");
        int width = screen.width;
        int height = screen.height;
        screen.resize(260, 142);
        handle = visible(screen, "sh_better_ui.slide_confirm.completed");
        require(visible(screen, GALLERY + "slide_confirm.reset").active
                        && narration(handle).contains(Component.translatable(GALLERY + "slide_confirm_completed_hint").getString()),
                "confirmed slide keeps completion and reset guidance after reflow");
        screen.resize(width, height);
        handle = visible(screen, "sh_better_ui.slide_confirm.completed");
        reset = visible(screen, GALLERY + "slide_confirm.reset");
        click(screen, reset);
        require(!reset.active && screen.getFocused() == handle, "reset restores the slider and its focus");
        require(screen.mouseClicked(start, false) && screen.isDragging(), "reset slider captures the pointer again");
        screen.mouseDragged(end, end.x() - start.x(), 0);
        screen.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        screen.mouseReleased(end);
        require(!reset.active && screen.getFocused() == handle, "Escape cancels a captured confirmation");
    }

    private static void verifyControls(Minecraft minecraft) {
        for (String sample : CONTROLS) {
            openSample(sample);
            switch (sample) {
                case "button" -> {
                    var button = visible(gallery, GALLERY + "preview.press_count");
                    click(gallery, button);
                    gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
                    var label = (TranslatableContents) button.getMessage().getContents();
                    require(((Number) label.getArgs()[0]).intValue() == 2, "sample button responds to mouse and keyboard");
                }
                case "toggle" -> {
                    var toggle = (UiButton) gallery.getFocused();
                    boolean before = toggle.isSelected();
                    click(gallery, toggle);
                    require(toggle.isSelected() != before, "sample toggle changes state");
                    click(gallery, toggle);
                    require(toggle.isSelected() == before, "sample toggle can change back");
                }
                case "slider" -> {
                    var slider = (UiSlider) gallery.getFocused();
                    int before = slider.value();
                    stepSlider(slider, GLFW.GLFW_KEY_RIGHT);
                    require(slider.value() > before, "sample slider responds to keyboard in edit mode");
                }
                case "disclosure" -> {
                    click(gallery, GALLERY + "expand");
                    require(visible(gallery, GALLERY + "collapse").active, "sample disclosure expands");
                }
                case "sections" -> {
                    var section = (UiButton) visible(gallery, PANEL + "section.display");
                    click(gallery, section);
                    require(section.isSelected(), "fixed section selection changes");
                }
                case "scrollable_sections" -> {
                    var sections = (UiScrollableSectionBar) gallery.getFocused();
                    gallery.keyPressed(key(GLFW.GLFW_KEY_END));
                    require(sections.selectedIndex() == sections.buttons().size() - 1 && sections.scrollOffset() > 0,
                            "scrollable section selection reveals the last entry");
                }
                case "underlined_tabs" -> {
                    var tabs = (UiUnderlinedTabs) gallery.getFocused();
                    int before = tabs.selection().selectedIndex();
                    gallery.keyPressed(key(GLFW.GLFW_KEY_RIGHT));
                    require(tabs.selection().selectedIndex() != before, "underlined tabs respond to keyboard");
                }
                case "stepper" -> {
                    var next = gallery.children().stream().filter(AbstractWidget.class::isInstance)
                            .map(AbstractWidget.class::cast).filter(w -> w.getMessage().equals(CommonComponents.GUI_CONTINUE))
                            .findFirst().orElseThrow();
                    for (int step = 0; step < 3; step++) click(gallery, next);
                    require(!next.active, "stepper stops at final step");
                }
                default -> throw new IllegalStateException("Unknown control sample: " + sample);
            }
            leaveSample(minecraft, sample);
        }
    }

    private static void verifyLegacySamples(Minecraft minecraft) {
        openSampleByKeyboard("capsule_toggle");
        var capsule = (UiButton) gallery.getFocused();
        gallery.keyPressed(key(GLFW.GLFW_KEY_SPACE));
        require(capsule.isSelected() && capsule.getSelectedModeIndex() == 1, "Space enables capsule and selects second mode");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        require(!capsule.isSelected() && capsule.getSelectedModeIndex() == 0, "Enter disables capsule and selects first mode");
        leaveSample(minecraft, "capsule_toggle");

        openSampleByKeyboard("text_details");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        String details = Component.translatable(GALLERY + "long_text").getString();
        require(gallery.getNarrationMessage().getString().contains(details), "expanded text contributes its full narration");
        gallery.keyPressed(key(GLFW.GLFW_KEY_TAB));
        require(gallery.getFocused() == visible(gallery, "sh_better_ui.details.open_page"), "Tab reaches the details page button");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        var page = WidgetSmokePlatform.screen(minecraft);
        require(page instanceof AlertScreen && page.getNarrationMessage().getString().contains(details), "full details open with narration");
        page.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        require(WidgetSmokePlatform.screen(minecraft) == gallery
                && gallery.getFocused() == visible(gallery, "sh_better_ui.details.open_page"), "Escape restores the details page button");
        leaveSample(minecraft, "text_details");

        openSampleByKeyboard("search");
        var input = (EditBox) gallery.getFocused();
        for (int letter : "minecraft:".codePoints().toArray()) gallery.charTyped(new CharacterEvent(letter));
        gallery.keyPressed(key(GLFW.GLFW_KEY_DOWN));
        require(narration(input).contains("minecraft:carrot"), "arrow-selected candidate reaches native narration");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        require(input.getValue().equals("minecraft:carrot"), "Enter inserts the selected candidate");
        gallery.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL));
        require(input.getValue().equals("minecraft:"), "Ctrl+Z undoes completion");
        gallery.keyPressed(key(GLFW.GLFW_KEY_TAB));
        require(gallery.getFocused() instanceof UiButton, "Tab reaches a candidate button");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        require(gallery.getFocused() == input, "first Escape dismisses suggestions and restores input focus");
        leaveSample(minecraft, "search");

        for (String id : List.of("liquid_button", "dark_button", "network_tab")) {
            openSampleByKeyboard(id);
            var button = (UiButton) gallery.getFocused();
            gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
            require(button.isSelected() && narration(button).contains(Component.translatable("sh_better_ui.control.selected").getString()),
                    "styled button toggles and narrates selection: " + id);
            leaveSample(minecraft, id);
        }
        for (String icon : List.of("reset", "settings", "copy")) {
            String id = icon + "_icon";
            openSampleByKeyboard(id);
            require(gallery.getNarrationMessage().getString().contains("/" + icon + ".png"), "icon resource is narrated: " + icon);
            leaveSample(minecraft, id);
        }
        openSampleByKeyboard("status_icon");
        String modes = gallery.getNarrationMessage().getString();
        for (String motion : List.of("spin", "float", "bounce")) {
            require(modes.contains(Component.translatable(GALLERY + "icon_motion." + motion).getString()),
                    "animated status icon describes " + motion);
        }
        leaveSample(minecraft, "status_icon");
        System.out.println("SHBUI_LEGACY_GALLERY_KEYBOARD_PASS backend=" + WidgetSmokePlatform.backend());
    }

    private static void verifyDialog(Minecraft minecraft) {
        openSampleByKeyboard("dialog");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        var cancel = WidgetSmokePlatform.screen(minecraft);
        require(cancel instanceof ConfirmScreen, "keyboard opens a native confirmation dialog");
        cancel.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        require(WidgetSmokePlatform.screen(minecraft) == gallery
                && gallery.getFocused() == visible(gallery, PANEL + "developer.demo.dialog")
                && gallery.getNarrationMessage().getString().contains(Component.translatable("sh_better_ui.action.cancelled").getString()),
                "Escape cancels confirmation, restores focus and exposes the result");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        var confirm = WidgetSmokePlatform.screen(minecraft);
        var ok = confirm.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(button -> button.getMessage().equals(CommonComponents.GUI_OK)).findFirst().orElseThrow();
        focusByTab(confirm, ok);
        confirm.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        require(WidgetSmokePlatform.screen(minecraft) == gallery
                && gallery.getFocused() == visible(gallery, PANEL + "developer.demo.dialog")
                && gallery.getNarrationMessage().getString().contains(Component.translatable("sh_better_ui.action.succeeded").getString()),
                "keyboard confirms and returns to the same focused gallery control");
        leaveSample(minecraft, "dialog");
    }

    private static void verifyTimelineControls(Minecraft minecraft) {
        openSampleByKeyboard("frame_timeline");
        var slider = timelineSlider();
        require(gallery.getFocused() == slider && slider.value() == 0, "timeline starts at frame zero with scrubber focus");
        stepSlider(slider, GLFW.GLFW_KEY_RIGHT);
        require(slider.value() == 1, "timeline steps by one frame");
        var middle = timelinePointer(slider, 0.5);
        var end = timelinePointer(slider, 1.0);
        require(gallery.mouseClicked(middle, false), "timeline starts scrubbing");
        require(slider.value() > 1, "timeline previews the pressed frame");
        gallery.mouseDragged(end, end.x() - middle.x(), 0);
        require(slider.value() == 119, "timeline drag reaches final frame");
        gallery.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        require(slider.value() == 1 && gallery.getFocused() == slider, "Escape cancels scrubbing and restores the frame");
        click(gallery, middle);
        int frame = slider.value();
        int width = gallery.width;
        int height = gallery.height;
        gallery.resize(260, 142);
        require(timelineSlider().value() == frame && gallery.getFocused() == timelineSlider(),
                "resizing preserves the committed frame and scrubber focus");
        gallery.resize(width, height);
        var transport = visible(gallery, TIMELINE + "transport");
        focusByTab(gallery, transport);
        gallery.keyPressed(key(GLFW.GLFW_KEY_SPACE));
        if (applied.reducedMotion()) {
            require(timelineSlider().value() == 119 && narration(transport).contains(timelineState("ended")),
                    "reduced motion jumps to final frame");
        } else {
            require(narration(transport).contains(timelineState("playing")), "Play starts and narrates playback");
            gallery.keyPressed(key(GLFW.GLFW_KEY_SPACE));
            require(narration(transport).contains(timelineState("paused")), "Pause stops and narrates playback");
        }
        click(gallery, "controls.reset");
        require(timelineSlider().value() == 0 && narration(transport).contains(timelineState("ready")),
                "reset restores frame zero and Ready state");
    }

    private static UiSlider timelineSlider() { return (UiSlider) visible(gallery, TIMELINE + "frame"); }

    private static String timelineState(String state) { return Component.translatable(TIMELINE + state).getString(); }

    private static MouseButtonEvent timelinePointer(UiSlider slider, double fraction) {
        return new MouseButtonEvent(slider.getX() + 4 + (slider.getWidth() - 8) * fraction,
                slider.getY() + slider.getHeight() / 2.0, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
    }

    private static String narration(AbstractWidget widget) {
        var collector = new ScreenNarrationCollector();
        collector.update(widget::updateNarration);
        return collector.collectNarrationText(true);
    }

    private static void verifyPreview(NativeImage image, int scale) {
        require(image.getWidth() >= 320 * scale && image.getHeight() >= 200 * scale, "gallery framebuffer captured");
        switch (stage) {
            case 1 -> blurredContrast = stripeContrast(image, scale);
            case 2 -> {
                double originalContrast = stripeContrast(image, scale);
                require(originalContrast > 65 && originalContrast - blurredContrast > 50,
                        "gallery blur reduces stripe contrast from " + originalContrast + " to " + blurredContrast);
                System.out.println("SHBUI_GALLERY_BLUR_PASS contrast=" + originalContrast + " -> " + blurredContrast);
            }
            case 3 -> {
                glassInterior = row(image, scale, 125, 270);
                glassExterior = row(image, scale, 40, 90);
            }
            case 4 -> {
                int changedInterior = changed(glassInterior, row(image, scale, 125, 270));
                int changedExterior = changed(glassExterior, row(image, scale, 40, 90));
                require(changedInterior > 45 && changedExterior == 0,
                        "glass changes local pixels only, interior=" + changedInterior + ", exterior=" + changedExterior);
                System.out.println("SHBUI_GALLERY_GLASS_PASS interior=" + changedInterior + " exterior=" + changedExterior);
            }
            case 5 -> clearInterior = row(image, scale, 100, 300);
            case 6 -> require(changed(clearInterior, row(image, scale, 100, 300)) > 45,
                    "clear glass modifies its sample backdrop");
            case 7 -> frostedInterior = row(image, scale, 100, 300);
            case 8 -> {
                int frostedChange = changed(frostedInterior, row(image, scale, 100, 300));
                int presetDifference = changed(clearInterior, frostedInterior);
                require(frostedChange > 45 && presetDifference > 20, "glass presets render distinct optical samples");
                System.out.println("SHBUI_GALLERY_GLASS_PRESETS_PASS frosted=" + frostedChange + " distinct=" + presetDifference);
            }
            case 9 -> timelineStart = row(image, scale, 20, 380, 65);
            case 10 -> {
                int difference = changed(timelineStart, row(image, scale, 20, 380, 65));
                require(difference > 20, "committing the final frame changes the rendered timeline card: " + difference);
                System.out.println("SHBUI_GALLERY_TIMELINE_PASS backend=" + WidgetSmokePlatform.backend());
            }
            default -> {}
        }
    }

    private static double stripeContrast(NativeImage image, int scale) {
        int count = 0;
        int difference = 0;
        for (int x = 30; x < 95; x += 8) {
            difference += Math.abs(green(image, scale, x, 55) - green(image, scale, x + 4, 55));
            count++;
        }
        return (double) difference / count;
    }

    private static int[] row(NativeImage image, int scale, int from, int to) {
        return row(image, scale, from, to, 104);
    }

    private static int[] row(NativeImage image, int scale, int from, int to, int y) {
        var pixels = new int[to - from];
        for (int x = from; x < to; x++) pixels[x - from] = green(image, scale, x, y);
        return pixels;
    }

    private static int green(NativeImage image, int scale, int x, int y) {
        return (image.getPixel(x * scale, y * scale) >>> 8) & 0xFF;
    }

    private static int changed(int[] before, int[] after) {
        int count = 0;
        for (int i = 0; i < before.length; i++) {
            if (Math.abs(before[i] - after[i]) > 12) count++;
        }
        return count;
    }

    private static void openSample(String name) {
        click(gallery, sampleEntry(name));
    }

    private static void openSampleByKeyboard(String name) {
        focusByTab(gallery, sampleEntry(name));
        gallery.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        require(gallery.getNarrationMessage().getString().contains(Component.translatable(GALLERY + "sample." + name).getString()),
                "keyboard opens sample: " + name);
    }

    private static AbstractWidget sampleEntry(String name) {
        String id = GALLERY + "sample." + name;
        var entry = findVisible(gallery, id);
        if (entry != null) return entry;
        while (gallery.keyPressed(key(GLFW.GLFW_KEY_PAGE_UP))) { /* Return to the first page. */ }
        do {
            entry = findVisible(gallery, id);
            if (entry != null) return entry;
        } while (gallery.keyPressed(key(GLFW.GLFW_KEY_PAGE_DOWN)));
        throw new IllegalStateException("Gallery sample missing: " + id);
    }

    private static void focusByTab(Screen screen, AbstractWidget target) {
        for (int remaining = screen.children().size(); remaining > 0 && screen.getFocused() != target; remaining--) {
            screen.keyPressed(key(GLFW.GLFW_KEY_TAB));
        }
        require(screen.getFocused() == target, "Tab reaches " + target.getMessage().getString());
    }

    private static void leaveSample(Minecraft minecraft, String name) {
        gallery.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        require(WidgetSmokePlatform.screen(minecraft) == gallery
                        && gallery.getFocused() == visible(gallery, GALLERY + "sample." + name),
                "return restores sample focus: " + name);
    }

    private static KeyEvent key(int value) { return new KeyEvent(value, 0, 0); }

    private static AbstractWidget findVisible(Screen screen, String id) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(w -> w.visible && w.getMessage().getContents() instanceof TranslatableContents key
                        && key.getKey().equals(id))
                .findFirst().orElse(null);
    }

    private static AbstractWidget visible(Screen screen, String id) {
        var widget = findVisible(screen, id);
        if (widget == null) throw new IllegalStateException("Visible gallery control missing: " + id);
        return widget;
    }

    private static void click(Screen screen, String id) { click(screen, visible(screen, id)); }

    private static void click(Screen screen, AbstractWidget widget) {
        require(widget.active && widget.visible, "sample control is active");
        click(screen, new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0,
                widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)));
    }

    private static void click(Screen screen, MouseButtonEvent event) {
        require(screen.mouseClicked(event, false), "gallery accepted pointer input");
        screen.mouseReleased(event);
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Gallery smoke: " + description);
    }
}
