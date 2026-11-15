// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.DeveloperPreviewProvider;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperGalleryScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.dishanhai.shbui_widgets_smoke.GallerySmoke.click;
import static com.dishanhai.shbui_widgets_smoke.GallerySmoke.focusByTab;
import static com.dishanhai.shbui_widgets_smoke.GallerySmoke.key;
import static com.dishanhai.shbui_widgets_smoke.GallerySmoke.narration;
import static com.dishanhai.shbui_widgets_smoke.GallerySmoke.visible;

/** A third-party preview contributed solely through its mod's UUK descriptor. */
public final class PreviewDiscoverySmoke implements DeveloperPreviewProvider {
    static final boolean ENABLED = Boolean.getBoolean("shbui.widgetSmoke.previewDiscovery");
    private static final UiId ID = new UiId(WidgetsSmoke.ID, "gallery_toggle");
    private static final String TITLE = "shbui_widgets_smoke.preview.toggle";
    private static final String PANEL = "sh_better_ui.panel.";
    private static final String GALLERY = PANEL + "developer.gallery.";
    private static boolean started;
    private static boolean declaredAfterStart;
    private static boolean stopped;
    private static int creations;
    private static int closes;
    private static boolean accessibleLayout;
    private static Screen original;
    private static ControlPanelScreen panel;
    private static DeveloperPanelScreen developer;
    private static DeveloperGalleryScreen directory;
    private static Screen preview;
    private static CompletableFuture<Void> reload;
    private static long deadline;
    private static int stage;

    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(ID).version(new UiVersion(1, 0, 0))
                .platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2, ModuleDescriptor.Platform.NEOFORGE_26_2))
                .build();
    }

    @Override public void register(ModuleContext context) {}

    @Override public void start(ModuleContext context) { started = true; }

    @Override public void stop(ModuleContext context) { stopped = true; }

    @Override public List<PreviewSpec> developerPreviews() {
        if (!ENABLED) return List.of();
        declaredAfterStart = started;
        return List.of(new PreviewSpec(ID.toString(), PreviewSpec.Category.CONTROLS, 900, TITLE, ToggleScene::new));
    }

    static void frame(Minecraft client) {
        if (stage == 5) return;
        if (deadline == 0) deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        require(System.nanoTime() < deadline, "timed out at stage " + stage);
        if (WidgetSmokePlatform.hasOverlay(client)) return;
        switch (stage) {
            case 0 -> {
                String backend = WidgetSmokePlatform.backend();
                String expected = System.getProperty("shbui.widgetSmoke.expectedBackend");
                require(backend.equalsIgnoreCase(expected), "expected graphics backend " + expected + ", got " + backend);
                String preference = System.getProperty("shbui.widgetSmoke.expectedPreference");
                require(preference == null || preference.equals(WidgetSmokePlatform.backendPreference(client)),
                        "saved graphics preference survives native fallback");
                require(UnifiedUI.isAvailable() && declaredAfterStart && creations == 0
                                && ShBetterUiApi.settings().developerSampleIds().contains(ID.toString()),
                        "loader discovery publishes the module preview after start without mounting it");
                client.options.guiScale().set(2);
                original = WidgetSmokePlatform.screen(client);
                ShBetterUiApi.settings().openControlPanel();
                panel = (ControlPanelScreen) WidgetSmokePlatform.screen(client);
                click(panel, PANEL + "section.display");
                for (String key : List.of("contrast", "motion")) {
                    var setting = (UiButton) visible(panel, PANEL + key);
                    if (!setting.isSelected()) click(panel, setting);
                }
                click(panel, PANEL + "section.tools");
                click(panel, PANEL + "developer.title");
                developer = (DeveloperPanelScreen) WidgetSmokePlatform.screen(client);
                click(developer, GALLERY + "controls");
                directory = (DeveloperGalleryScreen) WidgetSmokePlatform.screen(client);
                query().setValue(ID.toString());
                var result = visible(directory, TITLE);
                require(creations == 0 && !result.getMessage().getString().equals(TITLE),
                        "searching by ID finds the localized entry without mounting the preview");
                focusByTab(directory, result);
                directory.keyPressed(key(GLFW.GLFW_KEY_ENTER));
                preview = WidgetSmokePlatform.screen(client);
                require(preview != directory && creations == 1 && accessibleLayout,
                        "keyboard opens the discovered preview with high contrast and reduced motion");
                var toggle = visible(preview, TITLE + ".state");
                focusByTab(preview, toggle);
                require(preview.keyPressed(key(GLFW.GLFW_KEY_ENTER)), "keyboard toggles the preview");
                requireState(true);
                stage++;
            }
            case 1 -> {
                if (!NarratorSmoke.frame(client, preview)) return;
                click(preview, GALLERY + "fullscreen");
                visible(preview, GALLERY + "exit_fullscreen");
                requireState(true);
                require(client.getWindow().getGuiScale() == 2, "initial GUI scale");
                client.options.guiScale().set(1);
                reload = client.reloadResourcePacks();
                stage++;
            }
            case 2 -> {
                if (!reload.isDone()) return;
                reload.join();
                require(client.getWindow().getGuiScale() == 1 && WidgetSmokePlatform.screen(client) == preview
                                && creations == 1, "GUI resize and resource reload retain the same scene");
                requireState(true);
                click(preview, TITLE + ".state");
                requireState(false);
                preview.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
                require(WidgetSmokePlatform.screen(client) == preview, "Escape leaves fullscreen");
                requireState(false);
                preview.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
                require(WidgetSmokePlatform.screen(client) == directory && closes == 1
                                && query().getValue().equals(ID.toString())
                                && directory.getFocused() == visible(directory, TITLE),
                        "Escape closes the scene and restores the search and selected entry focus");
                client.options.guiScale().set(2);
                directory.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
                require(WidgetSmokePlatform.screen(client) == developer
                                && developer.getFocused() == visible(developer, GALLERY + "controls"),
                        "directory restores the developer panel entry focus");
                preview = ShBetterUiApi.settings().developerSampleFullscreen(developer, ID.toString(),
                        ShBetterUiApi.settings().preferences(),
                        () -> visible(developer, GALLERY + "controls"));
                WidgetSmokePlatform.open(client, preview);
                stage++;
            }
            case 3 -> {
                require(WidgetSmokePlatform.screen(client) == preview && creations == 2,
                        "the public fullscreen route opens the discovered preview by ID");
                requireState(false);
                preview.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
                require(WidgetSmokePlatform.screen(client) == developer && closes == 2
                                && developer.getFocused() == visible(developer, GALLERY + "controls"),
                        "the public fullscreen route closes the scene and restores focus");
                developer.onClose();
                require(WidgetSmokePlatform.screen(client) == panel
                                && panel.getFocused() == visible(panel, PANEL + "developer.title"),
                        "the control panel restores the developer entry focus");
                panel.onClose();
                if (WidgetSmokePlatform.screen(client) != original) {
                    click(WidgetSmokePlatform.screen(client), PANEL + "discard.action");
                }
                require(WidgetSmokePlatform.screen(client) == original, "close returns to the original screen");
                stage++;
            }
            case 4 -> {
                evidence(client, "preview-discovery.txt", "SHBUI_PREVIEW_DISCOVERY_PASS id=" + ID
                        + " backend=" + WidgetSmokePlatform.backend()
                        + " keyboard pointer fullscreen GUI-scale resource-reload return-focus native-narration");
                stage++;
                client.stop();
            }
            default -> throw new IllegalStateException("Unknown preview discovery stage " + stage);
        }
    }

    static void stopped(Minecraft client) {
        if (stage != 5) return;
        require(stopped && !UnifiedUI.isAvailable()
                        && ShBetterUiApi.developerPreviews().find(ID.toString()).isEmpty(),
                "client shutdown stops the module, unpublishes UUK and revokes its preview");
        evidence(client, "preview-discovery-stopped.txt", "SHBUI_PREVIEW_DISCOVERY_STOPPED id=" + ID);
    }

    private static EditBox query() {
        return directory.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow();
    }

    private static void requireState(boolean enabled) {
        var toggle = visible(preview, TITLE + ".state");
        String status = ToggleScene.label(enabled).getString();
        require(toggle.getMessage().getString().equals(status) && narration(toggle).contains(status),
                "the preview and native narration expose the current toggle state");
    }

    private static void evidence(Minecraft client, String file, String message) {
        try {
            Files.writeString(client.gameDirectory.toPath().resolve(file), message + System.lineSeparator());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        System.out.println(message);
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("Preview discovery smoke: " + reason);
    }

    private static final class ToggleScene implements PreviewScene {
        private boolean enabled;
        private UiToggleButton toggle;

        ToggleScene() { creations++; }

        @Override public void layout(PreviewContext host) {
            var area = host.viewport();
            int width = Math.min(170, area.width());
            if (width < 1 || area.height() < 22) return;
            accessibleLayout = host.preferences().highContrast() && host.preferences().reducedMotion();
            var bounds = new Bounds(area.x(), area.y(), width, 22);
            toggle = ShBetterUiApi.controls().toggle(bounds, enabled, host.preferences(), UiStyle.DEFAULT,
                    ToggleScene::label, value -> {
                        enabled = value;
                        toggle.setValue(value);
                    });
            host.add(toggle.button());
        }

        private static Component label(boolean enabled) {
            return Component.translatable(TITLE + ".state", Component.translatable(enabled ? "options.on" : "options.off"));
        }

        @Override public void close() { closes++; }
    }
}
