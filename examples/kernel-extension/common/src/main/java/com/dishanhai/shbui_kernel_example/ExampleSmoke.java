// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityRequest;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ModuleLifecycle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Optional packaged-client check. Enable it only in the example's isolated run directory. */
final class ExampleSmoke {
    private static final String OUTPUT = System.getProperty("shbui.example.smoke");
    private static ExampleUiModule module;
    private static Screen title;
    private static DeveloperPanelScreen developer;
    private static Screen gallery;
    private static int galleryScale;
    private static LiveExamplesSmoke live;
    private static RichTextSdkSmoke richText;
    private static Stage stage = Stage.START;
    private static long startedAt;
    private static long closedGeneration;
    private static boolean queued;
    private static boolean done;
    private static boolean passed;

    private enum Stage {
        START, OPEN_GALLERY, OPEN_DIRECTORY, SEARCH_PREVIEW, TOGGLE_PREVIEW, FULLSCREEN_PREVIEW,
        RESIZE_PREVIEW, RETURN_FROM_PREVIEW,
        ACT, LIVE, CAPTURE_AND_CLOSE, REOPEN, RICH_TEXT, TEXT_READABILITY
    }

    private ExampleSmoke() {}

    static void started(ExampleUiModule instance) {
        if (OUTPUT != null) module = instance;
    }

    static void frame() {
        if (OUTPUT == null || done || queued) return;
        var client = Minecraft.getInstance();
        // The render listener may be ordered before kernel cleanup. Run edits on the next client task turn.
        queued = true;
        client.tell(() -> {
            queued = false;
            advance(client);
        });
    }

    private static void advance(Minecraft client) {
        if (startedAt == 0) startedAt = System.nanoTime();
        try {
            require(System.nanoTime() - startedAt < 120_000_000_000L, "Timed out at " + stage);
            if (client.getOverlay() != null) return;
            switch (stage) {
                case START -> {
                    if (!(client.screen instanceof TitleScreen)) return;
                    verifyAndOpen(client);
                    stage = Stage.OPEN_GALLERY;
                }
                case OPEN_GALLERY -> {
                    var category = PreviewSpec.Category.CONTROLS;
                    press(developer, button(developer, Component.translatable(category.titleKey())));
                    gallery = client.screen;
                    require(gallery != developer, "The controls gallery did not open");
                    stage = Stage.OPEN_DIRECTORY;
                }
                case OPEN_DIRECTORY -> {
                    var category = PreviewSpec.Category.CONTROLS;
                    var samples = ShBetterUiApi.developerPreviews().entries().stream()
                            .filter(spec -> spec.category() == category).toList();
                    press(gallery, button(gallery, galleryLabel("sample_page",
                            Component.translatable(samples.get(0).titleKey()), 1, samples.size())));
                    require(client.screen != gallery, "The preview directory did not open");
                    stage = Stage.SEARCH_PREVIEW;
                }
                case SEARCH_PREVIEW -> {
                    Screen directory = client.screen;
                    var search = directory.children().stream().filter(SearchField.class::isInstance)
                            .map(SearchField.class::cast).findFirst().orElseThrow();
                    search.setValue(ExampleUiModule.GALLERY_TOGGLE_ID);
                    press(directory, button(directory, ExampleUiModule.label("preview.toggle.title")));
                    require(client.screen == gallery, "The searched preview did not open");
                    stage = Stage.TOGGLE_PREVIEW;
                }
                case TOGGLE_PREVIEW -> {
                    press(gallery, button(gallery, toggleLabel(false)));
                    require(button(gallery, toggleLabel(true)).active, "The preview toggle did not update");
                    press(gallery, button(gallery, galleryLabel("fullscreen")));
                    stage = Stage.FULLSCREEN_PREVIEW;
                }
                case FULLSCREEN_PREVIEW -> {
                    if (findButton(gallery, galleryLabel("exit_fullscreen")).isEmpty()) return;
                    require(button(gallery, toggleLabel(true)).active, "Fullscreen lost the preview's toggle value");
                    galleryScale = client.options.guiScale().get();
                    double oldScale = client.getWindow().getGuiScale();
                    client.options.guiScale().set(oldScale == 1 ? 2 : 1);
                    client.resizeDisplay();
                    require(client.getWindow().getGuiScale() != oldScale, "The preview did not change GUI scale");
                    stage = Stage.RESIZE_PREVIEW;
                }
                case RESIZE_PREVIEW -> {
                    Button toggle = button(gallery, toggleLabel(true));
                    double x = toggle.getX() + toggle.getWidth() / 2.0;
                    double y = toggle.getY() + toggle.getHeight() / 2.0;
                    require(gallery.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT),
                            "The preview toggle did not receive the pointer after resize");
                    gallery.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                    require(button(gallery, toggleLabel(false)).active, "The resized preview toggle did not update");
                    require(gallery.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape did not close fullscreen");
                    stage = Stage.RETURN_FROM_PREVIEW;
                }
                case RETURN_FROM_PREVIEW -> {
                    if (client.screen == gallery) {
                        if (findButton(gallery, galleryLabel("fullscreen")).isPresent()) gallery.onClose();
                        return;
                    }
                    require(client.screen == developer
                                    && developer.getFocused() == button(developer,
                                            Component.translatable(PreviewSpec.Category.CONTROLS.titleKey())),
                            "Returning from the preview did not restore the developer panel focus");
                    client.setScreen(title);
                    client.options.guiScale().set(galleryScale);
                    client.resizeDisplay();
                    press(title, ExampleClient.titleEntry(title).orElseThrow());
                    stage = Stage.ACT;
                }
                case ACT -> {
                    act(client);
                    live = new LiveExamplesSmoke(client.screen);
                    stage = Stage.LIVE;
                }
                case LIVE -> {
                    // Match the focused native UI state on an unattended smoke-test desktop.
                    if (!client.isWindowActive()) client.setWindowActive(true);
                    require(module.page() != null && module.page().screen == client.screen, "The example page closed during its demos");
                    if (live.advance(client.screen)) stage = Stage.CAPTURE_AND_CLOSE;
                }
                case CAPTURE_AND_CLOSE -> {
                    screenshotAndReopen(client);
                    stage = Stage.REOPEN;
                }
                case REOPEN -> {
                    verifyReopenAndClose(client);
                    richText = new RichTextSdkSmoke(title, Path.of(OUTPUT));
                    stage = Stage.RICH_TEXT;
                }
                case RICH_TEXT -> {
                    if (richText.advance(client)) {
                        press(title, ExampleClient.readabilityEntry(title).orElseThrow());
                        stage = Stage.TEXT_READABILITY;
                    }
                }
                case TEXT_READABILITY -> {
                    require(client.screen instanceof TextReadabilitySdkScreen,
                            "The packaged readability Screen did not open");
                    var screen = (TextReadabilitySdkScreen) client.screen;
                    if (!screen.drawn()) return;
                    try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                        image.writeToFile(Path.of(OUTPUT, "text-readability.png").toFile());
                    }
                    require(screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0) && client.screen == title
                                    && title.getFocused() == ExampleClient.readabilityEntry(title).orElseThrow()
                                    && screen.closed(),
                            "The readability Screen did not restore focus or release its scope");
                    writeSuccess();
                    passed = true;
                    done = true;
                    client.stop();
                }
            }
        } catch (IOException | RuntimeException | LinkageError | AssertionError failure) {
            System.err.println("KERNEL_EXTENSION_SMOKE_FAILED " + failure);
            failure.printStackTrace();
            done = true;
            client.stop();
        }
    }

    private static void verifyAndOpen(Minecraft client) {
        require(module != null && UnifiedUI.isAvailable(), "The loader did not discover the extension");
        var ui = UnifiedUI.get();
        int java = ui.platform().info() == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
        require(Runtime.version().feature() == java, "Unexpected client Java version");
        require(ExampleUiModule.class.getModule() != UnifiedUI.class.getModule(), "The extension and library share a module");
        String exampleJar = jarLocation(ExampleUiModule.class);
        String libraryJar = jarLocation(UnifiedUI.class);
        require(!exampleJar.equals(libraryJar), "The extension and library share a JAR");
        require(ui.lifecycle().snapshot().modules().get(ExampleUiModule.ID) == ModuleLifecycle.RUNNING,
                "The discovered module is not running");
        require(DeveloperPanelScreen.sampleIds().contains(ExampleUiModule.GALLERY_TOGGLE_ID),
                "The developer panel did not discover the extension preview");
        var capabilities = ui.capabilities().resolve(new CapabilityRequest(
                Map.of(ExampleUiModule.CONTROLS, VersionRange.ANY),
                Map.of(UIApis.SYSTEM_SCREEN_READER, VersionRange.ANY)));
        require(capabilities.requiredMissing().isEmpty() && capabilities.optionalMissing().contains(UIApis.SYSTEM_SCREEN_READER),
                "The custom capability or optional fallback is unavailable");
        require(ui.find(CounterService.KEY).isPresent() && ui.find(UIApis.THEME).isPresent(),
                "The custom or built-in service is unavailable");
        require(ui.find(ActivityService.KEY).isPresent() && ui.find(NotificationService.KEY).isPresent()
                        && ui.find(LiveSurfaceService.KEY).isPresent(), "The public live services are unavailable");

        title = client.screen;
        developer = ShBetterUiApi.settings().developerPanel(title, new UiPreferences(true, true, false));
        client.setScreen(developer);
    }

    private static void act(Minecraft client) {
        var page = module.page();
        require(page != null && client.screen == page.screen && page.tree.isOpen() && page.counter.isOpen(),
                "The extension did not mount the composed screen");
        require(!page.systemReader(), "The example did not select the Minecraft narration fallback");
        require(UnifiedUI.get().configs().get(ExampleUiModule.STEP) == 1 && page.step() == 3,
                "The component configuration was not isolated");
        press(page.screen, page.increase);
        require(page.count == 3 && page.undo.active
                && page.increase.getMessage().equals(ExampleUiModule.label("increase", 3, 3)), "Increment failed");
        press(page.screen, page.undo);
        require(page.count == 0 && !page.undo.active, "Undo failed");
        press(page.screen, page.increase);
    }

    private static void screenshotAndReopen(Minecraft client) throws IOException {
        var page = module.page();
        var legacyContext = page.screen.context();
        require(client.screen == page.screen && page.count == 3, "The counter screen was not rendered");
        var glass = UnifiedUI.get().glass().diagnostics();
        require(glass.shaderReady() && glass.opticalDraws() > 0, "The example status material was not rendered");
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(Path.of(OUTPUT, "screen.png").toFile());
        }
        require(page.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape did not close the screen");
        require(client.screen == title && module.page() == null && legacyContext.isClosed()
                && !page.tree.isOpen() && !page.counter.isOpen(), "Closing did not release the screen scope");
        require(client.screen.getFocused() == ExampleClient.titleEntry(title).orElseThrow(), "Parent focus was not restored");

        closedGeneration = page.tree.generation();
        press(title, ExampleClient.titleEntry(title).orElseThrow());
    }

    private static void verifyReopenAndClose(Minecraft client) {
        var reopened = module.page();
        require(reopened != null && client.screen == reopened.screen && reopened.counter.isOpen()
                && reopened.count == 0 && reopened.tree.generation() != closedGeneration, "The screen reused closed state");
        live.verifyReopened(reopened.screen);
        reopened.screen.onClose();
        require(!reopened.counter.isOpen() && UnifiedUI.isAvailable(), "Closing a screen changed the engine lifetime");
    }

    private static void writeSuccess() throws IOException {
        Files.writeString(Path.of(OUTPUT, "success.txt"),
                "Named extension and library JARs, discovered developer preview, custom service, optional fallback, scoped config, keyboard, undo, and screen cleanup passed.\n"
                        + "Developer gallery passed: ID search, keyboard and pointer toggle, fullscreen state, GUI scaling, "
                        + "Escape and return focus with high contrast and reduced motion enabled.\n"
                        + "The mounted status row drew Liquid Glass through the public RenderService.\n"
                        + "Live examples passed: music publication/pause/resume, local crafting cancellation/completion notification, "
                        + "machine energy/progress, priority preemption/restoration, screen scope cleanup, and connection watch toggle across reopen.\n"
                        + "Rich Text SDK passed: packaged JSON, diamond texture, item tooltip hit, updated meter pixels with unchanged layout, and scope cleanup.\n"
                        + "Text readability SDK passed: packaged third-party Screen submitted a shaped run, drew immediately in a live foreground callback, and released its scope on Escape.\n"
                        + "P9/P10 guide checks passed: project revision advanced on reload, vector pixels matched hits before and after resize, a failed renderer discarded queued drawing and old hits, and close released the project.\n"
                        + "External multiplayer connection and listening to the music remain manual checks.\n"
                        + "extension=" + jarLocation(ExampleUiModule.class) + "\nlibrary=" + jarLocation(UnifiedUI.class) + "\n");
    }

    static void shutdown() {
        if (OUTPUT == null || !passed || module == null || !module.stopped() || UnifiedUI.isAvailable()) return;
        require(!DeveloperPanelScreen.sampleIds().contains(ExampleUiModule.GALLERY_TOGGLE_ID),
                "The developer panel still listed the stopped extension preview");
        try {
            Files.writeString(Path.of(OUTPUT, "stopped.txt"), "Extension stopped, preview withdrawn, and facade withdrawn.\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Could not record extension shutdown", failure);
        }
    }

    static void press(Screen screen, Button button) {
        screen.setFocused(button);
        require(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "Enter did not activate a focused button");
    }

    private static Button button(Screen screen, Component label) {
        return findButton(screen, label).orElseThrow(() -> new IllegalStateException("Missing button: " + label));
    }

    private static Optional<Button> findButton(Screen screen, Component label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.visible && candidate.getMessage().equals(label)).findFirst();
    }

    private static Component galleryLabel(String key, Object... args) {
        return Component.translatable("sh_better_ui.panel.developer.gallery." + key, args);
    }

    private static Component toggleLabel(boolean enabled) {
        return ExampleUiModule.label("preview.toggle.state", Component.translatable(enabled ? "options.on" : "options.off"));
    }

    private static String jarLocation(Class<?> type) {
        require(type.getModule().isNamed(), "The class did not come from a named loader module");
        var source = type.getProtectionDomain().getCodeSource();
        require(source != null && source.getLocation() != null && source.getLocation().toString().contains(".jar"),
                "Missing packaged JAR code source for " + type.getName());
        return source.getLocation().toString();
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
