// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassService;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Validity;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.dishanhai.sh_better_ui.kernel.internal.GlassCaptureSmoke;
import com.dishanhai.sh_better_ui.kernel.internal.TextReadabilitySmoke;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class KernelClientSmoke implements UIModule {
    private static final UiId ID = id("kernel_smoke");
    private static final UiId POINTER = id("smoke/pointer");
    private static final UiId FOCUS = id("smoke/focus");
    private static final UiId ANIMATED = id("smoke/animated");
    private static final int BACKGROUND = 0xff151515;
    private static final int MARKER = 0xff22e622;
    private static KernelClientSmoke instance;
    private static long started;
    private static boolean done;

    private ModuleContext module;
    private Stage stage = Stage.START;
    private VisualCompositionSmoke visualComposition;
    private TextReadabilitySmoke textReadability;
    private TextReadabilityServiceSmoke textReadabilityService;
    private TextReadabilityGpuSmoke textReadabilityGpu;
    private DockSmoke dock;
    private DeclarativePageSmoke pages;
    private RichTextSmoke richText;
    private RichTextEffectsSmoke richEffects;
    private RichTextEditorSmoke richEditor;
    private Screen title;
    private ProbeScreen probe;
    private CompletableFuture<Void> reload;
    private long resourceVersion;
    private long initialMisses;
    private long sessionFrames;
    private long hudGlassBefore;
    private long retryJoinKeyAt;
    private int joinKeyAttempts;
    private boolean joinKeyReceived;
    private ScopeHandle worldScope;
    private LiveSurfaceSmoke live;
    private MaterialCatalogSmoke materialCatalog;
    private AnimationService.Track animation;
    private TaskHandle screenDraw;
    private TaskHandle hudDraw;
    private List<TaskHandle> glassLayerDraws;
    private AutoCloseable directHud;
    private int failingHudFrames;
    private HudService.Frame hudTextFrame;
    private TextReadabilityPalette hudTextPalette;
    private boolean hudTextCaptured;
    private GlassService glass;
    private ClientUiSettings savedDisplay;
    private double animated;
    private boolean stopped;
    private int captures;
    private int drags;
    private int releases;
    private int cancels;
    private int moves;
    private int viewportChanges;
    private Screen viewportReplacement;
    private boolean failDrag;
    private final List<NativeInput.KeyAction> keys = new ArrayList<>();
    private final StringBuilder chars = new StringBuilder();
    private NativeInput.Pointer wheel;

    interface Platform {
        void join(Minecraft client, Screen parent, String name);
        void rejoin(Minecraft client, Screen parent, String name);
        void leave(Minecraft client, Screen title);
        boolean optionalPresent();
        boolean clientCommand(String command);
        default void flushPointer(Minecraft client) {}
    }

    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(ID).version("1.0.0")
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.POINTER_CAPTURE, POINTER))
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.KEYBOARD_FOCUS, FOCUS))
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.ANIMATION_PROPERTY, ANIMATED))
                .requestPermission(UIPermission.CAPTURE_INPUT).requestPermission(UIPermission.BLOCK_VANILLA_INPUT)
                .requestPermission(UIPermission.REGISTER_RENDER_PASS).build();
    }

    @Override public void register(ModuleContext context) {}

    @Override public void start(ModuleContext context) {
        module = context;
        instance = this;
    }

    @Override public void stop(ModuleContext context) { stopped = true; }

    static void frame(Platform platform) {
        var output = System.getProperty("shb.kernel.smoke");
        if (output == null || done) return;
        var client = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        try {
            require(System.nanoTime() - started < 120_000_000_000L, "Timed out at "
                    + (instance == null ? "discovery" : instance.stage
                    + (instance.live == null ? "" : "/" + instance.live.stage())
                    + (instance.dock == null ? "" : "/dock:" + instance.dock.stage())
                    + (instance.textReadabilityGpu == null ? "" : "/readability:" + instance.textReadabilityGpu.stage())
                    + (instance.richEditor == null ? "" : "/" + instance.richEditor.stage())
                    + (instance.pages == null ? "" : "/pages:" + instance.pages.stage())));
            if (client.getOverlay() != null) return;
            if (instance == null) {
                require(!(client.screen instanceof TitleScreen), "Loader did not discover the kernel fixture");
                return;
            }
            require(!instance.stopped, "Kernel stopped before the smoke finished at " + instance.stage);
            boolean text = Boolean.getBoolean("shb.kernel.smoke.richText");
            boolean effects = Boolean.getBoolean("shb.kernel.smoke.richEffects");
            if (Boolean.getBoolean("shb.kernel.smoke.textReadabilityGpu")) {
                instance.textReadabilityGpu(client, Path.of(output));
            } else if (Boolean.getBoolean("shb.kernel.smoke.textReadabilityService")) {
                instance.textReadabilityService(client, Path.of(output));
            } else if (Boolean.getBoolean("shb.kernel.smoke.hudText")) {
                instance.hudText(client, platform, Path.of(output));
            } else if (Boolean.getBoolean("shb.kernel.smoke.textReadability")) {
                instance.textReadability(client, Path.of(output));
            } else if (Boolean.getBoolean("shb.kernel.smoke.pages")) {
                instance.pages(client, platform, Path.of(output));
            } else if (Boolean.getBoolean("shb.kernel.smoke.dock")) {
                instance.dock(client, platform, Path.of(output));
            } else if (Boolean.getBoolean("shb.kernel.smoke.richEditor")) {
                instance.richEditor(client, platform, Path.of(output));
            } else if (effects && (!text || instance.richEffects != null)) {
                instance.richEffects(client, Path.of(output));
            } else if (text) {
                instance.richText(client, platform, Path.of(output));
            } else if (Boolean.getBoolean("shb.kernel.smoke.visualComposition")) {
                instance.visualComposition(client, Path.of(output));
            } else if (Boolean.getBoolean("shb.kernel.smoke.materialEffects")) {
                instance.materialEffects(client, platform, Path.of(output));
            } else {
                instance.advance(client, platform, Path.of(output));
            }
        } catch (Exception | AssertionError failure) {
            System.err.println("KERNEL_SMOKE_FAILED " + failure);
            failure.printStackTrace();
            done = true;
            client.stop();
        }
    }

    private void dock(Minecraft client, Platform platform, Path output) throws Exception {
        if (dock == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            glass = module.engine().glass();
            dock = new DockSmoke(client, module, output);
        } else if (dock.advance(client, platform)) {
            Files.writeString(output.resolve("success.txt"), "ADCWS native smoke passed\n");
            done = true;
            client.stop();
        }
    }

    private void pages(Minecraft client, Platform platform, Path output) throws Exception {
        if (pages == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            int java = module.engine().platform().info() == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
            require(Runtime.version().feature() == java, "Wrong declarative page client JDK");
            glass = module.engine().glass();
            pages = new DeclarativePageSmoke(module, client.screen);
        } else if (pages.advance(client, platform)) {
            String scripts = Boolean.getBoolean("shb.kernel.smoke.pages.kubejs") ? ", KubeJS client reload" : "";
            Files.writeString(output.resolve("success.txt"),
                    "Local controls, scale, Page Studio, native container reconnect, confirmed action, priority modal, "
                            + "interaction token, HUD and notification" + scripts + " passed\n");
            done = true;
            client.stop();
        }
    }

    static void shutdown() {
        var output = System.getProperty("shb.kernel.smoke");
        if (output == null || instance == null || !done || !instance.stopped || UnifiedUI.isAvailable()) return;
        try {
            var stats = instance.glass.diagnostics();
            require(stats.captureBytes() == 0 && !stats.shaderReady(), "Glass retained GPU resources after shutdown");
            Files.writeString(Path.of(output, "stopped.txt"), "module stopped, facade withdrawn, glass resources released\n");
        } catch (Exception failure) {
            throw new IllegalStateException("Could not record kernel shutdown", failure);
        }
    }

    private void richText(Minecraft client, Platform platform, Path output) throws Exception {
        if (richText == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            int java = module.engine().platform().info() == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
            require(Runtime.version().feature() == java, "Wrong rich text client JDK");
            glass = module.engine().glass();
            richText = new RichTextSmoke(client, output);
        } else if (richText.advance(client, platform)) {
            if (Boolean.getBoolean("shb.kernel.smoke.richEffects")) {
                richEffects = new RichTextEffectsSmoke(client, module, output);
                return;
            }
            Files.writeString(output.resolve("success.txt"), richText.result());
            done = true;
            client.stop();
        }
    }

    private void richEditor(Minecraft client, Platform platform, Path output) throws Exception {
        if (richEditor == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            int java = module.engine().platform().info() == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
            require(Runtime.version().feature() == java, "Wrong rich text editor client JDK");
            glass = module.engine().glass();
            richEditor = new RichTextEditorSmoke(client, platform, output);
        } else if (richEditor.advance(client, platform)) {
            Files.writeString(output.resolve("success.txt"), richEditor.result());
            done = true;
            client.stop();
        }
    }

    private void richEffects(Minecraft client, Path output) throws Exception {
        if (richEffects == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            int java = module.engine().platform().info() == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
            require(Runtime.version().feature() == java, "Wrong rich text effects client JDK");
            glass = module.engine().glass();
            richEffects = new RichTextEffectsSmoke(client, module, output);
        } else if (richEffects.advance(client)) {
            var textResult = richText == null ? "" : richText.result();
            Files.writeString(output.resolve("success.txt"), textResult + richEffects.result());
            done = true;
            client.stop();
        }
    }

    private void visualComposition(Minecraft client, Path output) throws Exception {
        if (visualComposition == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            glass = module.engine().glass();
            visualComposition = new VisualCompositionSmoke(client, module);
        } else if (visualComposition.advance(client)) {
            Files.writeString(output.resolve("success.txt"), "visual composition native smoke passed\n"
                    + visualComposition.timings());
            done = true;
            client.stop();
        }
    }

    private void textReadability(Minecraft client, Path output) throws Exception {
        if (textReadability == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            int java = module.engine().platform().info() == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
            require(Runtime.version().feature() == java, "Wrong text readability client JDK");
            glass = module.engine().glass();
            textReadability = new TextReadabilitySmoke();
            client.setScreen(textReadability);
        } else if (textReadability.verify(client, output)) {
            client.setScreen(null);
            done = true;
            client.stop();
        }
    }

    private void textReadabilityService(Minecraft client, Path output) throws Exception {
        if (textReadabilityService == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            int java = module.engine().platform().info() == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
            require(Runtime.version().feature() == java, "Wrong text readability service client JDK");
            glass = module.engine().glass();
            textReadabilityService = new TextReadabilityServiceSmoke(module);
            client.setScreen(textReadabilityService);
        } else if (textReadabilityService.verify(client, output)) {
            client.setScreen(null);
            done = true;
            client.stop();
        }
    }

    private void textReadabilityGpu(Minecraft client, Path output) throws Exception {
        if (textReadabilityGpu == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            int java = module.engine().platform().info() == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
            require(Runtime.version().feature() == java, "Wrong GPU text readability client JDK");
            glass = module.engine().glass();
            textReadabilityGpu = new TextReadabilityGpuSmoke(module);
            client.setScreen(textReadabilityGpu);
        } else if (textReadabilityGpu.verify(client, output)) {
            client.setScreen(null);
            textReadabilityGpu.verifyClosed();
            Files.writeString(output.resolve("success.txt"), textReadabilityGpu.result());
            done = true;
            client.stop();
        }
    }

    private void hudText(Minecraft client, Platform platform, Path output) throws Exception {
        if (title == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            title = client.screen;
            glass = module.engine().glass();
            platform.join(client, title, "hud-text-smoke-" + System.nanoTime());
            return;
        }
        if (client.level == null || client.player == null || client.screen != null) return;
        var hud = module.engine().require(UIApis.HUD);
        var panel = id("smoke/hud_text");
        var region = new Rect(50, 20, 16, 16);
        if (directHud == null) {
            sessionFrames = module.engine().scheduler().snapshot().frame();
            directHud = hud.mount(module, frame -> {
                frame.graphics().fill(50, 20, 66, 36, 0xffffffff);
                var palette = hud.textPalette(module, frame, panel, region, 0xff000000, 0xff000000);
                if (hudTextPalette != null) require(palette == hudTextPalette, "HUD panel lost its stable palette");
                hudTextFrame = frame;
                hudTextPalette = palette;
                palette.backgroundSample().filter(sample -> sample.validity() == Validity.VALID).ifPresent(sample -> {
                    require(sample.statistics().minimum() > 0.95, "HUD text sampled before the white plate");
                    hudTextCaptured = true;
                });
            });
            return;
        }
        if (!hudTextCaptured && module.engine().scheduler().snapshot().frame() < sessionFrames + 120) return;
        require(hudTextCaptured, "Mounted HUD callback did not receive its post-background sample");
        directHud.close();
        boolean expiredRejected = false;
        try {
            hud.textPalette(module, hudTextFrame, panel, region, 0xff000000, 0xff000000);
        } catch (IllegalStateException expected) {
            expiredRejected = true;
        }
        require(expiredRejected, "Expired HUD frame remained usable");
        Files.writeString(output.resolve("success.txt"),
                "Mounted HUD sampled its painted plate, reused the palette and rejected an expired frame.\n");
        done = true;
        client.stop();
    }

    private void materialEffects(Minecraft client, Platform platform, Path output) throws Exception {
        if (materialCatalog == null) {
            if (!(client.screen instanceof TitleScreen)) return;
            glass = module.engine().glass();
            materialCatalog = new MaterialCatalogSmoke(true);
        }
        if (!materialCatalog.advance(client, platform, output)) return;
        Files.writeString(output.resolve("success.txt"),
                "Native material routes resolved and selected effects rendered on " + module.engine().platform().info()
                        + " Java " + Runtime.version().feature() + "\n");
        done = true;
        client.stop();
    }

    private void advance(Minecraft client, Platform game, Path output) throws Exception {
        var engine = UnifiedUI.get();
        switch (stage) {
            case START -> {
                if (!(client.screen instanceof TitleScreen)) return;
                var info = engine.platform().info();
                int java = info == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
                require(Runtime.version().feature() == java, "Wrong client JDK");
                require(game.optionalPresent() == Boolean.getBoolean("shb.kernel.smoke.optional"),
                        "Optional mod load state differs from the requested variant");
                require(engine.capabilities().supports(UIApis.NATIVE_RENDER), "Render capability unavailable");
                glass = engine.glass();
                engine.require(UIApis.THEME).snapshot();
                engine.require(UIApis.ACCESSIBILITY).preferences(null);
                title = client.screen;
                client.setWindowActive(true);
                key(client, GLFW.GLFW_KEY_TAB, GLFW.GLFW_PRESS);
                key(client, GLFW.GLFW_KEY_TAB, GLFW.GLFW_RELEASE);
                require(title.getFocused() != null, "Vanilla title did not handle Tab");
                probe = new ProbeScreen();
                client.setScreen(probe);
                var themes = engine.require(UIApis.THEME);
                var scoped = themes.scope(module, probe.tree);
                var spacing = themes.snapshot().resolve(ThemeTokens.SPACING_SM);
                scoped.configure(tokens -> tokens.override(ThemeTokens.SPACING_SM, spacing + 2));
                require(scoped.resolve(ThemeTokens.SPACING_SM) == spacing + 2
                        && themes.snapshot().resolve(ThemeTokens.SPACING_SM).equals(spacing),
                        "Screen theme override escaped its scope");
                nativeInputs(client, game, info);
                animation = engine.require(UIApis.ANIMATION).bind(module, probe.tree, ANIMATED,
                        Spring.PANEL, 0, () -> false, value -> animated = value);
                animation.play(1, InterruptibleAnimation.Policy.RETARGET, 0);
                screenDraw = engine.require(UIApis.RENDER).submit(module, probe.tree, RenderService.Surface.SCREEN,
                        RenderService.Layer.OVERLAY, 0, context -> {
                            require(context.frame().surface() == RenderService.Surface.SCREEN, "Wrong draw surface");
                            context.clip(12, 60, 32, 80, () -> {
                                GlassCaptureSmoke.verify(BACKGROUND);
                                context.fill(8, 56, 36, 84, MARKER);
                            });
                            context.fill(36, 66, 40, 74, MARKER);
                            GlassOpticsSmoke.draw(context);
                        });
                stage = Stage.DRAW;
            }
            case DRAW -> {
                require(screenDraw.status() == TaskHandle.Status.SUCCEEDED, "Native screen pass did not run");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    require(pixel(image, client, 19, 70) == MARKER, "Framebuffer did not contain the render pass");
                    require(pixel(image, client, 10, 70) == BACKGROUND, "Render clip leaked outside its bounds");
                    require(pixel(image, client, 37, 70) == MARKER, "Render clip was not restored");
                    GlassOpticsSmoke.verify(image, client);
                    image.writeToFile(output.resolve("screen.png").toFile());
                }
                savedDisplay = ClientUiSettings.current();
                require(ClientUiKernel.saveClientSettings(savedDisplay.withDisplay(true, savedDisplay.reducedMotion(),
                        savedDisplay.widgetBackdropBlur())), "Could not enable high contrast in the kernel configuration");
                screenDraw = engine.require(UIApis.RENDER).submit(module, probe.tree, RenderService.Surface.SCREEN,
                        RenderService.Layer.OVERLAY, 0, GlassOpticsSmoke::drawHighContrast);
                stage = Stage.HIGH_CONTRAST;
            }
            case HIGH_CONTRAST -> {
                require(screenDraw.status() == TaskHandle.Status.SUCCEEDED, "High-contrast glass pass did not run");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    GlassOpticsSmoke.verifyHighContrast(image, client);
                    image.writeToFile(output.resolve("liquid-glass-high-contrast.png").toFile());
                } finally {
                    require(ClientUiKernel.saveClientSettings(savedDisplay), "Could not restore kernel display preferences");
                }
                var resource = engine.require(UIApis.RESOURCES);
                resourceVersion = resource.version();
                var assets = resource.alignmentAssets(module, probe.tree, client.getResourceManager());
                require(assets.texture(texture(), 1).isEmpty(), "Test texture unexpectedly present before reload");
                require(assets.texture(texture(), 1).isEmpty() && assets.stats().hits() == 1,
                        "Test cache did not reuse a missing result");
                probe.assets = assets;
                initialMisses = assets.stats().misses();
                installPack(client, output);
                reload = client.reloadResourcePacks();
                stage = Stage.RELOAD;
            }
            case RELOAD -> {
                if (!reload.isDone()) return;
                reload.join();
                require(engine.require(UIApis.RESOURCES).version() > resourceVersion, "Resource generation did not advance");
                require(engine.glass().diagnostics().captureBytes() == 0 && !engine.glass().diagnostics().shaderReady(),
                        "Liquid Glass retained GPU resources after reload");
                require(probe.assets.texture(texture(), 1).isPresent() && probe.assets.stats().misses() > initialMisses,
                        "Native resource reload kept stale texture cache");
                require(animated > 0 && animation.value() > 0, "Frame-clock animation did not advance");
                screenDraw = engine.require(UIApis.RENDER).submit(module, probe.tree, RenderService.Surface.SCREEN,
                        RenderService.Layer.OVERLAY, 0, GlassOpticsSmoke::drawReload);
                stage = Stage.BROKEN_SHADER;
            }
            case BROKEN_SHADER -> {
                require(screenDraw.status() == TaskHandle.Status.SUCCEEDED, "Broken shader did not fall back");
                var stats = glass.diagnostics();
                require(!stats.shaderReady() && stats.captureBytes() == 0 && stats.fallbackDraws() == 2,
                        "Broken shader did not produce a solid fallback " + stats);
                Files.delete(brokenGlassShader(output));
                reload = client.reloadResourcePacks();
                stage = Stage.RELOAD_SHADER;
            }
            case RELOAD_SHADER -> {
                if (!reload.isDone()) return;
                reload.join();
                screenDraw = engine.require(UIApis.RENDER).submit(module, probe.tree, RenderService.Surface.SCREEN,
                        RenderService.Layer.OVERLAY, 0, GlassOpticsSmoke::drawReload);
                stage = Stage.RECOVERED_SHADER;
            }
            case RECOVERED_SHADER -> {
                var stats = glass.diagnostics();
                require(screenDraw.status() == TaskHandle.Status.SUCCEEDED && stats.shaderReady()
                                && stats.passesWithCapture() == 3
                                && stats.opticalDraws() == GlassOpticsSmoke.INITIAL_OPTICAL_DRAWS + 1,
                        "Reload did not recover the Liquid Glass renderer " + stats);
                glassLayerDraws = GlassOpticsSmoke.submitLayerChecks(module, probe.tree);
                stage = Stage.GLASS_LAYERS;
            }
            case GLASS_LAYERS -> {
                require(glassLayerDraws.stream().allMatch(task -> task.status() == TaskHandle.Status.SUCCEEDED),
                        "Optical glass layer passes did not run");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    GlassOpticsSmoke.verifyLayers(image, client, false);
                }
                savedDisplay = ClientUiSettings.current();
                require(ClientUiKernel.saveClientSettings(savedDisplay.withDisplay(savedDisplay.highContrast(),
                        savedDisplay.reducedMotion(), false)), "Could not disable optical glass");
                glassLayerDraws = GlassOpticsSmoke.submitLayerChecks(module, probe.tree);
                stage = Stage.GLASS_LAYERS_FALLBACK;
            }
            case GLASS_LAYERS_FALLBACK -> {
                try {
                    require(glassLayerDraws.stream().allMatch(task -> task.status() == TaskHandle.Status.SUCCEEDED),
                            "Fallback glass layer passes did not run");
                    try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                        GlassOpticsSmoke.verifyLayers(image, client, true);
                    }
                } finally {
                    require(ClientUiKernel.saveClientSettings(savedDisplay), "Could not restore optical glass");
                }
                var pending = module.engine().scheduler().submit(UITask.ui(probe.tree, UIPhase.STATE_MUTATION,
                        UIPriority.NORMAL, () -> { throw new AssertionError("Closed Screen task ran"); })
                        .delayed(60_000_000_000L));
                client.setScreen(title);
                require(!probe.tree.isOpen() && !animation.isActive() && pending.status() == TaskHandle.Status.CANCELLED,
                        "Native Screen removal failed to cancel scoped work");
                for (int i = 0; i < 6; i++) {
                    client.setScreen(probe);
                    var reopened = probe.tree;
                    require(reopened.isOpen(), "Screen did not remount");
                    client.setScreen(title);
                    require(!reopened.isOpen() && !probe.pointerTarget.isActive(), "Screen resources survived remount");
                }
                client.setScreen(probe);
                var input = engine.require(UIApis.INPUT);
                input.target(module, probe.tree, 25,
                        value -> value instanceof NativeInput.Key key && key.key() == GLFW.GLFW_KEY_L);
                module.engine().events().subscribe(InputService.EVENT, probe.tree, UIEventPhase.TARGET, 0, event -> {
                    if (event.payload() instanceof NativeInput.Key key && key.action() == NativeInput.KeyAction.PRESS) {
                        event.consume();
                        if (joinKeyReceived) return;
                        joinKeyReceived = true;
                        System.out.println("KERNEL_SMOKE_JOIN native L received on attempt " + joinKeyAttempts);
                        game.join(client, title, "kernel-smoke-" + System.nanoTime());
                    }
                });
                stage = Stage.JOIN;
                sendJoinKey(client);
            }
            case JOIN -> {
                if (!joinKeyReceived) {
                    if (System.nanoTime() >= retryJoinKeyAt) {
                        require(joinKeyAttempts < 3 && client.screen == probe,
                                "Native L did not reach the join target after " + joinKeyAttempts + " attempts; screen="
                                        + client.screen + ", windowActive=" + client.isWindowActive());
                        sendJoinKey(client);
                    }
                    return;
                }
                if (client.level == null || client.player == null || client.screen != null) return;
                require(engine.platform().hasClientSession(), "Native login did not open the session");
                worldScope = engine.require(UIApis.SCREENS).session(module, id("smoke/session"));
                sessionFrames = engine.scheduler().snapshot().frame();
                hudGlassBefore = glass.diagnostics().opticalDraws();
                var hud = engine.require(UIApis.HUD);
                hud.mount(module, frame -> {
                    failingHudFrames++;
                    frame.graphics().enableScissor(0, 0, 2, 2);
                    throw new IllegalStateException("intentional HUD scissor failure");
                });
                directHud = hud.mount(module, frame -> {
                    frame.graphics().enableScissor(23, 19, 35, 31);
                    frame.graphics().fill(24, 20, 32, 28, MARKER);
                    frame.graphics().disableScissor();
                });
                hudDraw = engine.require(UIApis.RENDER).submit(module, worldScope, RenderService.Surface.HUD,
                        RenderService.Layer.OVERLAY, 0, context -> {
                            require(context.frame().surface() == RenderService.Surface.HUD, "Wrong HUD surface");
                            context.fill(12, 12, 18, 18, MARKER);
                            GlassOpticsSmoke.drawWorld(context);
                        });
                stage = Stage.HUD;
            }
            case HUD -> {
                if (!hudDraw.status().isTerminal() && engine.scheduler().snapshot().frame() < sessionFrames + 100) return;
                require(hudDraw.status() == TaskHandle.Status.SUCCEEDED, "Native HUD pass did not run");
                if (live == null) {
                    require(glass.diagnostics().opticalDraws() == hudGlassBefore + 1, "World HUD glass did not draw");
                    try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                        require(failingHudFrames == 1 && pixel(image, client, 26, 22) == MARKER,
                                "A failed direct HUD layer clipped the next layer or was invoked again");
                        image.writeToFile(output.resolve("liquid-glass-world.png").toFile());
                    }
                    directHud.close();
                    directHud.close();
                    client.options.guiScale().set(2);
                    client.resizeDisplay();
                    live = new LiveSurfaceSmoke(module, worldScope);
                }
                if (!live.advance(client, game, output)) return;
                stage = Stage.LEAVE;
                game.leave(client, title);
            }
            case LEAVE -> {
                if (!CorrectnessSmoke.started()) {
                    if (client.level != null || client.screen != title) return;
                    require(!engine.platform().hasClientSession(), "Native logout did not release the session");
                    require(!worldScope.isOpen(), "Native logout left an extension session open");
                    live.verifyLogout();
                    require(UnifiedUI.isAvailable(), "Screen/session closure stopped the engine");
                }
                if (materialCatalog == null) {
                    if (!CorrectnessSmoke.advance(module, client, game, output)) return;
                    materialCatalog = new MaterialCatalogSmoke();
                }
                if (!materialCatalog.advance(client, game, output)) return;
                var result = engine.platform().info() + " Java " + Runtime.version().feature()
                        + " optional=" + game.optionalPresent()
                        + "\nloader discovery, vanilla Tab, pointer press/drag/release/move, wheel, native focus, "
                        + "key press/repeat/release, UTF-16 input, GUI scale, window loss, framebuffer/clip, "
                        + "scoped theme, animation, resource pack/cache, remount cancellation, "
                        + "reentrant input, capture failure, world login/HUD/logout, live surface split/expand, "
                        + "live local action, 32 continuous retargets, network publish/action/result, "
                        + "window resize, GUI scale 1 and three live resource reloads\n"
                        + "Liquid Glass: shared native capture, wide frost and contrast pixels, GL state, solid fallback, "
                        + "shader compile failure and reload recovery, world HUD, shutdown release\n"
                        + "Material catalog: developer panel and fullscreen return focus, stable and alias routes, "
                        + "native optical sliders and endpoint screenshots, reduced motion, frame step/reset, "
                        + "four fixed keyframes, keyboard draft, A/B, GUI scale and reload retention, close and fresh draft\n";
                Files.writeString(output.resolve("success.txt"), result);
                System.out.println("KERNEL_SMOKE_OK " + result);
                done = true;
                client.stop();
            }
        }
    }

    private void sendJoinKey(Minecraft client) {
        joinKeyAttempts++;
        retryJoinKeyAt = System.nanoTime() + 500_000_000L;
        // The callback can render a loading Screen. Keep the synthetic key between frames.
        client.tell(() -> {
            if (client.screen != probe || joinKeyReceived || client.getOverlay() != null) return;
            client.setWindowActive(true);
            key(client, GLFW.GLFW_KEY_L, GLFW.GLFW_PRESS);
            if (client.screen == probe) key(client, GLFW.GLFW_KEY_L, GLFW.GLFW_RELEASE);
        });
    }

    private void nativeInputs(Minecraft client, Platform game, ModuleDescriptor.Platform platform) throws Exception {
        var original = probe.first;
        move(client, 22, 18);
        button(client, GLFW.GLFW_PRESS);
        button(client, GLFW.GLFW_RELEASE);
        require(probe.nativeClicks == 1, "Native widget lost its click");
        var service = UnifiedUI.get().require(UIApis.FOCUS);
        var focus = service.request(module, probe.keyboardNode, FOCUS, probe.second, 0).orElseThrow();
        require(probe.getFocused() == probe.second, "Kernel focus was not native focus");
        for (int action : new int[] {GLFW.GLFW_PRESS, GLFW.GLFW_REPEAT, GLFW.GLFW_RELEASE}) {
            key(client, GLFW.GLFW_KEY_K, action);
        }
        require(keys.equals(List.of(NativeInput.KeyAction.PRESS, NativeInput.KeyAction.REPEAT,
                NativeInput.KeyAction.RELEASE)), "Native repeat was not distinguished: " + keys);
        require(probe.vanillaKeys == 0, "Consumed keyboard input reached the native screen");
        invoke(client.keyboardHandler, "charTyped", new Class<?>[] {long.class, int.class, int.class}, window(client), (int) 'q', 0);
        invoke(client.keyboardHandler, "charTyped", new Class<?>[] {long.class, int.class, int.class}, window(client), 0x1f600, 0);
        require(chars.toString().equals("q" + Character.toString(0x1f600)), "Native character units were lost");
        focus.close();
        require(probe.getFocused() == original, "Native focus was not restored");

        move(client, 90, 30);
        button(client, GLFW.GLFW_PRESS);
        button(client, GLFW.GLFW_RELEASE);
        require(captures == 0, "The transparent glass corner acquired pointer capture");
        move(client, 105, 42);
        button(client, GLFW.GLFW_PRESS);
        move(client, 145, 65);
        game.flushPointer(client);
        button(client, GLFW.GLFW_RELEASE);
        require(captures == 1 && drags > 0 && releases == 1 && moves > 0,
                "Native pointer capture failed " + List.of(captures, drags, releases, moves));
        move(client, 105, 42);
        invoke(client.mouseHandler, "onScroll", new Class<?>[] {long.class, double.class, double.class}, window(client), 1.0, -1.0);
        require(wheel != null && wheel.deltaY() < 0
                && (platform == ModuleDescriptor.Platform.FORGE_1_20_1 ? wheel.deltaX() == 0 : wheel.deltaX() != 0),
                "Native scroll axes were incorrect");

        button(client, GLFW.GLFW_PRESS);
        require(captures == 2, "Second native capture failed");
        var focusOnLoss = service.request(module, probe.keyboardNode, FOCUS, probe.second, 0).orElseThrow();
        client.setWindowActive(false);
        require(cancels == 1 && !UnifiedUI.get().platform().viewport().focused(), "Window loss did not revoke capture");
        require(!focusOnLoss.isActive() && probe.getFocused() == original, "Window loss did not restore native focus");
        client.setWindowActive(true);
        button(client, GLFW.GLFW_RELEASE);
        require(releases == 1, "Canceled capture delivered its release");

        long generation = probe.tree.generation();
        var before = UnifiedUI.get().platform().viewport();
        client.options.guiScale().set(before.guiScale() == 1 ? 2 : 1);
        client.resizeDisplay();
        require(probe.tree.generation() == generation && original != probe.first && viewportChanges > 0,
                "GUI scale did not resize without remounting the Screen");
        require(UnifiedUI.get().platform().viewport().guiScale() != before.guiScale(), "GUI scale did not reach the bridge");

        var window = client.getWindow();
        var replacement = new ProbeScreen();
        viewportReplacement = replacement;
        int receivedMoves = moves;
        window.setGuiScale(window.getGuiScale() == 1 ? 2 : 1);
        move(client, 105, 42);
        require(client.screen == replacement && moves == receivedMoves,
                "An input from the previous Screen reached a remounted tree");
        client.resizeDisplay();
        client.setScreen(probe);

        move(client, 105, 42);
        failDrag = true;
        button(client, GLFW.GLFW_PRESS);
        move(client, 145, 65);
        game.flushPointer(client);
        button(client, GLFW.GLFW_RELEASE);
        failDrag = false;
        require(captures == 3 && cancels == 2 && releases == 1, "A failed capture did not drain its release");
    }

    private final class ProbeScreen extends Screen {
        private ScopeHandle tree;
        private ScopeHandle keyboardNode;
        private Subscription pointerTarget;
        private Button first;
        private Button second;
        private int nativeClicks;
        private int vanillaKeys;
        private com.dishanhai.sh_better_ui.client.gui.layout.AlignmentAssets assets;

        ProbeScreen() { super(Component.translatable("gui.done")); }

        @Override protected void init() {
            var engine = UnifiedUI.get();
            if (tree == null || !tree.isOpen()) {
                tree = engine.require(UIApis.SCREENS).mount(module, this, id("smoke/tree"));
                var input = engine.require(UIApis.INPUT);
                pointerTarget = input.target(module, tree, 5, event -> event instanceof NativeInput.Pointer pointer
                        && (pointer.action() == NativeInput.PointerAction.MOVE
                        || GlassOpticsSmoke.DRAG_ZONE.contains(pointer.x(), pointer.y())));
                module.engine().events().subscribe(InputService.POINTER_MOVED, tree, UIEventPhase.TARGET, 0,
                        event -> moves++);
                module.engine().events().subscribe(InputService.EVENT, tree, UIEventPhase.TARGET, 0, event -> {
                    if (!(event.payload() instanceof NativeInput.Pointer pointer)) return;
                    if (pointer.action() == NativeInput.PointerAction.SCROLL) {
                        wheel = pointer;
                        event.consume();
                    } else if (pointer.action() == NativeInput.PointerAction.PRESS) {
                        require(input.capture(module, tree, POINTER, pointer.button(), new UiPointerCapture.Handler() {
                            @Override public void drag(double x, double y) {
                                drags++;
                                if (failDrag) throw new IllegalStateException("smoke capture callback failed");
                            }
                            @Override public void release(double x, double y) { releases++; }
                            @Override public void cancel() { cancels++; }
                        }), "Kernel native pointer slot was unavailable");
                        captures++;
                        event.consume();
                    }
                });
                module.engine().events().subscribe(ScreenService.VIEWPORT_CHANGED, tree, UIEventPhase.TARGET, 0,
                        event -> {
                            viewportChanges++;
                            if (viewportReplacement != null) {
                                var next = viewportReplacement;
                                viewportReplacement = null;
                                Minecraft.getInstance().setScreen(next);
                            }
                        });
            }
            if (keyboardNode != null) keyboardNode.close();
            keyboardNode = tree.openChild(ScopeKind.COMPONENT, id("smoke/keyboard"));
            first = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> nativeClicks++)
                    .bounds(12, 12, 35, 20).build());
            second = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> {})
                    .bounds(52, 12, 35, 20).build());
            engine.require(UIApis.FOCUS).target(module, keyboardNode, second, 10);
            module.engine().events().subscribe(InputService.EVENT, keyboardNode, UIEventPhase.TARGET, 0, event -> {
                if (event.payload() instanceof NativeInput.Key key && key.key() == GLFW.GLFW_KEY_K) {
                    keys.add(key.action());
                    event.consume();
                } else if (event.payload() instanceof NativeInput.Character character) {
                    chars.append(character.codeUnit());
                    event.consume();
                }
            });
        }

        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_K) vanillaKeys++;
            return super.keyPressed(key, scan, mods);
        }

        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, BACKGROUND);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static long window(Minecraft client) { return client.getWindow().getWindow(); }

    static void key(Minecraft client, int key, int action) {
        client.keyboardHandler.keyPress(window(client), key, GLFW.glfwGetKeyScancode(key), action, 0);
    }

    static void move(Minecraft client, double x, double y) throws Exception {
        var window = client.getWindow();
        invoke(client.mouseHandler, "onMove", new Class<?>[] {long.class, double.class, double.class}, window(client),
                x * window.getScreenWidth() / window.getGuiScaledWidth(),
                y * window.getScreenHeight() / window.getGuiScaledHeight());
    }

    static void button(Minecraft client, int action) throws Exception {
        invoke(client.mouseHandler, "onPress", new Class<?>[] {long.class, int.class, int.class, int.class},
                window(client), GLFW.GLFW_MOUSE_BUTTON_LEFT, action, 0);
    }

    static void invoke(Object target, String name, Class<?>[] arguments, Object... values) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        method.invoke(target, values);
    }

    static int pixel(NativeImage image, Minecraft client, int x, int y) {
        double scale = client.getWindow().getGuiScale();
        return image.getPixelRGBA((int) (x * scale), (int) (y * scale)) | 0xff000000;
    }

    private static ResourceLocation texture() {
        return ResourceLocation.tryParse("sh_better_ui:textures/kernel_smoke.png");
    }

    private static void installPack(Minecraft client, Path output) throws Exception {
        var pack = output.resolve("resourcepacks/kernel-smoke");
        var textures = pack.resolve("assets/sh_better_ui/textures");
        Files.createDirectories(textures);
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        Files.writeString(pack.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"Kernel smoke fixture\"}}");
        try (var image = new NativeImage(2, 2, false)) {
            image.setPixelRGBA(0, 0, -1);
            image.writeToFile(textures.resolve("kernel_smoke.png").toFile());
        }
        var broken = brokenGlassShader(output);
        Files.createDirectories(broken.getParent());
        Files.writeString(broken, "#version 150\ninvalid fixture\n");
        var repository = client.getResourcePackRepository();
        repository.reload();
        require(repository.addPack("file/kernel-smoke"), "Native resource pack could not be selected");
    }

    private static Path brokenGlassShader(Path output) {
        return output.resolve("resourcepacks/kernel-smoke/assets/sh_better_ui/shaders/managed_glass.fsh");
    }

    static LevelSettings settings() {
        return new LevelSettings("Kernel smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
    }

    static WorldOptions options() { return new WorldOptions(17, false, false); }

    static WorldDimensions flat(RegistryAccess registries) {
        return registries.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
    }

    private static UiId id(String path) { return new UiId("sh_better_ui", path); }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private enum Stage {
        START, DRAW, HIGH_CONTRAST, RELOAD, BROKEN_SHADER, RELOAD_SHADER, RECOVERED_SHADER,
        GLASS_LAYERS, GLASS_LAYERS_FALLBACK, JOIN, HUD, LEAVE
    }
}
