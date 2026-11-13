// Author: dishanhai
package com.dishanhai.shbui_widgets_smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Transform;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.client.visual.FrameViewSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.HudService;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import com.dishanhai.sh_better_ui.kernel.api.text.TextId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Validity;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPolicy;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.Duration;
import java.util.Set;

public final class ReadabilitySmoke implements UIModule {
    private static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;
    private static final Rect HUD_PANEL = new Rect(12, 21, 94, 17);
    private static final UiId PANEL = id("hud_text");
    private static final TextId TOP = new TextId(id("top"));
    private static final TextId BOTTOM = new TextId(id("bottom"));
    private static final TextId LOCAL = new TextId(id("local"));
    private static final TextReadabilityPolicy POLICY = new TextReadabilityPolicy(true, true, null, null, null);
    private static ReadabilitySmoke active;
    private static boolean completed;
    private ModuleContext module;
    private TextReadabilityService service;
    private ScopeHandle tree;
    private UiScreen screen;
    private Screen parent;
    private AlignmentLayout layout;
    private AlignmentLayout.Node top;
    private FrameViewSnapshot previous;
    private long started;
    private boolean brighten, middle, captured, closed;
    private AutoCloseable hudMount;
    private TextReadabilityPalette hudPalette;
    private HudService.Frame expiredHudFrame;
    private boolean hudRead, hudBright, hudMiddle, hudDone, hudScreenshot;
    private boolean previousPause;
    private long hudStarted;

    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(id("text_readability")).version(new UiVersion(1, 0, 0))
                .platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2, ModuleDescriptor.Platform.NEOFORGE_26_2))
                .requestPermission(UIPermission.REGISTER_RENDER_PASS)
                .requiredCapability(UIApis.NATIVE_SCREEN, VersionRange.ANY)
                .requiredCapability(UIApis.NATIVE_HUD, VersionRange.ANY)
                .requiredCapability(UIApis.TEXT_READABILITY_POLICY, VersionRange.ANY).build();
    }

    @Override public void register(ModuleContext context) {}

    @Override public void start(ModuleContext context) {
        module = context;
        service = context.engine().require(UIApis.TEXT_READABILITY);
        active = this;
    }

    @Override public void stop(ModuleContext context) {
        if (active == this) active = null;
        if (started != 0) System.out.println("SHBUI_TEXT_READABILITY_STOPPED backend=" + WidgetSmokePlatform.backend());
    }

    static void frame(Minecraft client) {
        if (completed) return;
        require(active != null, "published text readability module");
        active.verify(client);
    }

    private void verify(Minecraft client) {
        if (closed) {
            if (Boolean.getBoolean("shbui.widgetSmoke.readabilityHud")) verifyHud(client);
            return;
        }
        if (captured) return;
        if (screen == null) {
            if (WidgetSmokePlatform.hasOverlay(client)) return;
            started = System.nanoTime();
            require(WidgetSmokePlatform.backend().equalsIgnoreCase(System.getProperty("shbui.widgetSmoke.expectedBackend")),
                    "expected native graphics backend");
            client.options.guiScale().set(2);
            parent = WidgetSmokePlatform.screen(client);
            var screens = module.engine().require(UIApis.SCREENS);
            screen = screens.create(module, ShBetterUiApi.screen(Component.translatable("options.title")).parent(parent)
                    .onInit(owner -> {
                        if (tree != null && tree.isOpen()) return;
                        tree = screens.mount(module, owner, id("screen"));
                        service.configure(module, tree, POLICY);
                        service.setGpuDiagnostics(module, tree, true);
                        layout = new AlignmentLayout(new Rect(0, 0, owner.width, owner.height));
                        top = layout.node("top", AlignmentFrame.box(new Rect(20, 20, 80, 14))
                                .withClip(new Rect(0, 0, 58, 14)));
                    })
                    .onRender(this::render));
            screen.open();
            return;
        }
        require(System.nanoTime() - started < Duration.ofSeconds(40).toNanos(), "sample and gradient converge");
        var topResult = service.diagnostics(module, tree, TOP).orElse(null);
        var bottomResult = service.diagnostics(module, tree, BOTTOM).orElse(null);
        if (topResult == null || bottomResult == null || topResult.decision() == null || bottomResult.decision() == null) return;
        if (!brighten) {
            if (topResult.gpu().samples().isEmpty() || bottomResult.gpu().samples().isEmpty()) return;
            require(topResult.gpu().samples().stream().allMatch(point -> point.luminance() < .01), "dark top GPU ROI");
            require(bottomResult.gpu().samples().stream().allMatch(point -> point.luminance() > .99), "bright lower GPU ROI");
            require(topResult.gpu().sourceFrame() < topResult.sourceFrame(), "diagnostics label the delayed sample");
            require(topResult.effectiveClip().right() == 78, "current visual clip");
            require(service.clusterDiagnostics(module, tree, LOCAL).size() >= 2, "cluster diagnostic decisions");
            brighten = true;
            return;
        }
        int fill = topResult.decision().fillArgb();
        middle |= fill != WHITE && fill != BLACK;
        if (fill != BLACK || !middle) return;
        captured = true;
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(client), image -> {
            try (image) {
                int scale = client.getWindow().getGuiScale();
                image.writeToFile(client.gameDirectory.toPath().resolve("text-readability-26.png"));
                require(hasInk(image, scale, 20, 20, 78, 35), "final black glyph pixels");
                require(!hasInk(image, scale, 79, 20, 105, 35), "pixels outside visual clip");
                client.schedule(() -> close(client));
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });
    }

    private void close(Minecraft client) {
        screen.onClose();
        closed = !tree.isOpen() && WidgetSmokePlatform.screen(client) == parent;
        require(closed, "screen scope released on close");
        System.out.println("SHBUI_TEXT_READABILITY_26_PASS backend=" + WidgetSmokePlatform.backend()
                + " topAndBottom=true intermediate=true clipped=true clusters=true scopeClosed=true");
        if (Boolean.getBoolean("shbui.widgetSmoke.readabilityHud")) openWorld(client);
        else { completed = true; client.stop(); }
    }

    private void openWorld(Minecraft client) {
        hudStarted = System.nanoTime();
        previousPause = client.options.pauseOnLostFocus;
        client.options.pauseOnLostFocus = false;
        var hud = module.engine().require(UIApis.HUD);
        hudMount = hud.mount(module, frame -> renderHud(client, hud, frame));
        var flows = client.createWorldOpenFlows();
        String world = "shbui-readability-smoke";
        if (client.getLevelSource().levelExists(world)) {
            flows.openWorld(world, () -> WidgetSmokePlatform.open(client, parent));
            return;
        }
        var settings = new LevelSettings(world, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                false, WorldDataConfiguration.DEFAULT);
        flows.createFreshLevel(world, settings, new WorldOptions(4_413L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(), parent);
    }

    private void renderHud(Minecraft client, HudService hud, HudService.Frame frame) {
        if (client.level == null || WidgetSmokePlatform.screen(client) != null || hudDone) return;
        var graphics = frame.graphics();
        int background = hudBright ? WHITE : BLACK;
        graphics.fill(12, 21, 106, 38, background);
        var palette = hud.textPalette(module, frame, PANEL, HUD_PANEL, background, background);
        require(hudPalette == null || hudPalette == palette, "same panel ID reuses scoped HUD state");
        hudPalette = palette;
        expiredHudFrame = frame;
        int fill = palette.baseColor(WHITE);
        if (!hudBright) {
            hudRead = palette.backgroundSample().filter(sample -> sample.validity() == Validity.VALID
                    && sample.statistics().maximum() < .01).isPresent();
        } else {
            hudMiddle |= fill != WHITE && fill != BLACK;
            hudDone = hudMiddle && fill == BLACK && palette.backgroundSample().filter(sample ->
                    sample.validity() == Validity.VALID && sample.statistics().minimum() > .99).isPresent();
        }
        var line = FormattedCharSequence.forward("MMMM", net.minecraft.network.chat.Style.EMPTY);
        var inline = FormattedCharSequence.forward("MMMM", net.minecraft.network.chat.Style.EMPTY.withBold(true).withColor(0xFFFFFF));
        UiHudRichText.draw(graphics, client.font, FormattedCharSequence.composite(line, inline), 18, 25,
                fill, WHITE, frame.preferences(), palette);
    }

    private void verifyHud(Minecraft client) {
        require(System.nanoTime() - hudStarted < Duration.ofSeconds(60).toNanos(), "in-world HUD sample and gradient");
        if (hudRead && !hudBright) hudBright = true;
        if (!hudDone || hudScreenshot) return;
        var hud = module.engine().require(UIApis.HUD);
        try {
            hud.textPalette(module, expiredHudFrame, PANEL, HUD_PANEL, WHITE, WHITE);
            throw new AssertionError("Accepted a HUD callback outside its lifetime");
        } catch (IllegalStateException expected) {
            // The native HUD callback has returned.
        }
        hudScreenshot = true;
        Screenshot.takeScreenshot(WidgetSmokePlatform.target(client), image -> {
            try (image) {
                int scale = client.getWindow().getGuiScale();
                require(hasInk(image, scale, 18, 25, 40, 36), "HUD base glyph pixels");
                require(hasInk(image, scale, 43, 25, 70, 36), "HUD bold inline glyph pixels");
                image.writeToFile(client.gameDirectory.toPath().resolve("text-readability-26-hud.png"));
                client.schedule(() -> stopClient(client));
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });
    }

    private void stopClient(Minecraft client) {
        try {
            hudMount.close();
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to close HUD probe", failure);
        }
        completed = true;
        client.options.pauseOnLostFocus = previousPause;
        System.out.println("SHBUI_HUD_READABILITY_26_PASS backend=" + WidgetSmokePlatform.backend()
                + " nativeSample=true intermediate=true stablePanel=true frameExpired=true pixels=true");
        client.stop();
    }

    private void render(UiScreen owner, UiFrame frame) {
        var graphics = frame.graphics();
        graphics.fill(0, 0, owner.width, owner.height, BLACK);
        graphics.fill(0, 0, owner.width, 80, brighten ? WHITE : BLACK);
        graphics.fill(0, owner.height - 80, owner.width, owner.height, WHITE);
        var font = owner.getFont();
        var sequence = FormattedCharSequence.forward("MMMMMMMMMM", net.minecraft.network.chat.Style.EMPTY);
        var style = new TextReadabilityService.Style(WHITE, false, TextReadabilityPolicy.Usage.BODY);
        var run = new TextReadabilityService.Run(sequence, 0, 0, new Rect(0, 0, font.width(sequence), 9), 9, font);
        layout.resolve();
        var snapshot = FrameViewSnapshot.capture(module, tree, layout, new Rect(0, 0, owner.width, owner.height));
        var first = TextReadabilityService.Request.forVisual(tree, TOP, run, style, snapshot, top, 1,
                brighten ? WHITE : BLACK, 0);
        require(service.submit(module, first) == TextReadabilityService.Result.SUBMITTED, "same-frame visual request");
        if (previous != null && previous.isValid()) {
            var stale = TextReadabilityService.Request.forVisual(tree, TOP, run, style, previous, top, 1, BLACK, 0);
            require(service.submit(module, stale) == TextReadabilityService.Result.STALE_LAYOUT, "stale visual request");
        }
        previous = snapshot;
        var second = new TextReadabilityService.Request(tree, BOTTOM, run, style,
                Transform.translation(20, owner.height - 35), new Rect(0, 0, owner.width, owner.height), 1, WHITE);
        require(service.foreground(module, tree, 1, context -> require(service.drawNow(module, context, second)
                == TextReadabilityService.Result.DRAWN, "immediate native foreground")) == TextReadabilityService.Result.SUBMITTED,
                "queue native foreground");
        var clusterSequence = FormattedCharSequence.forward("MM", net.minecraft.network.chat.Style.EMPTY);
        var local = new TextReadabilityService.Run(clusterSequence, 0, 0, new Rect(0, 0, font.width(clusterSequence), 9), 9, font);
        var third = new TextReadabilityService.Request(tree, LOCAL, local, style, Transform.translation(130, 50),
                new Rect(0, 0, owner.width, owner.height), 1, BLACK).withPolicy(POLICY.withLocalColor(true));
        require(service.submit(module, third) == TextReadabilityService.Result.SUBMITTED, "queue local color");
    }

    private static boolean hasInk(com.mojang.blaze3d.platform.NativeImage image, int scale, int left, int top,
                                  int right, int bottom) {
        for (int y = top * scale; y < bottom * scale; y++) for (int x = left * scale; x < right * scale; x++) {
            if ((image.getPixel(x, y) & 0xFFFFFF) == 0) return true;
        }
        return false;
    }

    private static UiId id(String path) { return new UiId(WidgetsSmoke.ID, path); }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("26.x text readability smoke: " + reason);
    }
}
