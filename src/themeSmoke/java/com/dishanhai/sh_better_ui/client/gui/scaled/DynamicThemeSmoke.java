// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.theme.DynamicThemePreview;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityStatus;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.context.ContextKeys;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Opt-in client smoke test. Compiled only by gradle/theme-smoke.init.gradle. */
public final class DynamicThemeSmoke {
    private static final String[] SCENES = {"dark", "light", "nether", "end", "high-contrast",
            "transition-midpoint", "hover", "pressed", "focused", "disabled", "resource-pack"};
    private static Fixture fixture;
    private static TitleScreen title;
    private static int scene;
    private static long sceneStarted;
    private static long reloadVersion;
    private static long reloadStarted;
    private static CompletableFuture<Void> reload;
    private static boolean finished;
    private static boolean handlingFrame;
    private static long firstFrame = Long.MIN_VALUE;
    private static UiScreen preview;
    private static DynamicThemeWorldSmoke world;
    private static ThemeScope beforeWindowResize;
    private static ThemeScope pageTheme;
    private static int originalWidth;
    private static int originalHeight;
    private static long windowResizeDeadline;

    private DynamicThemeSmoke() {}

    static void frame(DynamicThemeWorldSmoke.Hooks platform) {
        String output = System.getProperty("shb.theme.smoke");
        if (output == null || finished || handlingFrame) return;
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null && reload == null) return;
        handlingFrame = true;
        try {
            var now = System.nanoTime() / 1_000_000L;
            String settings = System.getProperty("shb.theme.smoke.settings", "");
            if (settings.equals("hud")) {
                if (DynamicThemeSettingsSmoke.hudFrame(client, platform, Path.of(output), now)) {
                    finish(client, output, "settings=hud");
                }
                return;
            }
            if (!settings.isEmpty()) {
                if (DynamicThemeSettingsSmoke.frame(client, Path.of(output), settings, now)) {
                    finish(client, output, "settings=" + settings);
                }
                return;
            }
            if (world != null) {
                if (world.frame(client, now)) finish(client, output, "frames=" + SCENES.length);
                return;
            }
            if (firstFrame == Long.MIN_VALUE) firstFrame = now;
            if (fixture == null) {
                if (!(client.screen instanceof TitleScreen menu)) {
                    require(now - firstFrame < 90_000, "Title screen did not load");
                    return;
                }
                title = menu;
                checkKernel();
                require(!MinecraftThemes.environment().get(ContextKeys.WORLD_PRESENT), "Main menu has world state");
                var pack = MinecraftThemes.manager().registry().find("sh_better_ui:aurora").orElseThrow();
                fixture = new Fixture();
                fixture.themes.register(pack);
                client.setScreen(fixture);
                fixture.scene(0);
                sceneStarted = now;
                return;
            }
            if (reload != null) {
                if (!reload.isDone()) {
                    require(now - reloadStarted < 60_000, "Reload timed out");
                    return;
                }
                reload.join();
                require(MinecraftThemes.manager().resourceVersion() > reloadVersion, "Native reload did not publish theme resources");
                require(MinecraftThemes.manager().registry().find("sh_better_ui:aurora").isPresent(), "Pack theme disappeared");
                checkKernel();
                if (Boolean.getBoolean("shb.theme.smoke.worlds")) {
                    world = new DynamicThemeWorldSmoke(title, platform, now);
                    world.start(client);
                } else {
                    finish(client, output, "frames=" + SCENES.length);
                }
                return;
            }
            if (preview != null) {
                if (client.screen != preview || now - sceneStarted < 300) return;
                if (beforeWindowResize == null) {
                    Path path = Path.of(output, "preview.png");
                    try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                        image.writeToFile(path);
                    }
                    checkMaterialBindings();
                    pageTheme = preview.context().themeScope();
                    beforeWindowResize = preview.widgetThemeScope();
                    originalWidth = client.getWindow().getScreenWidth();
                    originalHeight = client.getWindow().getScreenHeight();
                    windowResizeDeadline = now + 10_000;
                    client.getWindow().setWindowed(originalWidth + 64, originalHeight + 32);
                    return;
                }
                var context = MinecraftThemes.environment();
                if (context.get(ContextKeys.WINDOW_WIDTH) != originalWidth + 64
                        || context.get(ContextKeys.WINDOW_HEIGHT) != originalHeight + 32) {
                    require(now < windowResizeDeadline, "Native window resize was not captured");
                    return;
                }
                require(beforeWindowResize.isClosed(), "Window resize did not release previous widget scope");
                require(preview.context().themeScope() == pageTheme, "Window resize replaced the kernel-owned page scope");
                System.out.println("DYNAMIC_THEME_RESIZE_OK");
                var widgetScope = preview.widgetThemeScope();
                client.setScreen(title);
                require(widgetScope.isClosed(), "Preview screen did not close its widget scope");
                require(pageTheme.isClosed(), "Preview screen did not close its kernel-owned theme scope");
                client.getWindow().setWindowed(originalWidth, originalHeight);
                reloadVersion = MinecraftThemes.manager().resourceVersion();
                reloadStarted = now;
                reload = client.reloadResourcePacks();
                return;
            }
            if (client.screen != fixture || fixture.firstDrawnAt == 0 || now - fixture.firstDrawnAt < 300) return;
            save(client, Path.of(output), SCENES[scene]);
            if (++scene < SCENES.length) {
                fixture.scene(scene);
                sceneStarted = now;
            } else {
                int initialScale = client.options.guiScale().get();
                var previous = fixture.button;
                client.options.guiScale().set(initialScale == 1 ? 2 : 1);
                client.resizeDisplay();
                require(fixture.button != previous && !previous.hasThemeBinding(), "Resize kept old button subscriptions");
                client.options.guiScale().set(initialScale);
                client.resizeDisplay();
                preview = DynamicThemePreview.screen(title, () -> null);
                client.setScreen(preview);
                require(fixture.themes.isClosed(), "Screen did not release its theme tree");
                sceneStarted = now;
            }
        } catch (Exception | AssertionError failure) {
            failure.printStackTrace();
            System.err.println("DYNAMIC_THEME_SMOKE_FAILED: " + failure);
            finished = true;
            client.stop();
        } finally {
            handlingFrame = false;
        }
    }

    private static void checkMaterialBindings() {
        var samples = preview.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(button -> button.getMessage().getContents() instanceof TranslatableContents text
                        && text.getKey().startsWith("sh_better_ui.theme.dynamic.material.")).toList();
        require(samples.size() == 3 && samples.stream().allMatch(UiButton::hasThemeBinding),
                "Preview did not bind all three materials");
        var idle = new DarkModeTheme.State(0, 0, 0, false, true, false);
        var before = samples.stream().map(button -> button.themePaint(idle, false).fill()).toList();
        samples.get(1).onPress();
        for (int index = 0; index < samples.size(); index++) {
            require(samples.get(index).themePaint(idle, false).fill() != before.get(index),
                    "Local surface change missed material " + index);
        }
        System.out.println("DYNAMIC_THEME_MATERIALS_OK");
    }

    static void checkKernel() {
        var engine = UnifiedUI.get();
        var theme = engine.require(UIApis.THEME);
        require(engine.capabilities().status(UIApis.DYNAMIC_THEME) == CapabilityStatus.AVAILABLE,
                "Dynamic theme capability unavailable");
        require(engine.scheduler().snapshot().frame() > 0, "Native frames did not pump the kernel");
        require(theme.snapshot() == MinecraftThemes.manager().snapshot(), "Kernel and native theme snapshots differ");
        require(theme.availableThemes().contains("sh_better_ui:aurora"), "Kernel cannot read pack themes");
        System.out.println("DYNAMIC_THEME_KERNEL_OK");
    }

    private static void finish(Minecraft client, String output, String result) throws java.io.IOException {
        Files.createDirectories(Path.of(output));
        Files.writeString(Path.of(output, "success.txt"), result);
        System.out.println("DYNAMIC_THEME_SMOKE_OK " + result + " reload=" + MinecraftThemes.manager().resourceVersion());
        finished = true;
        client.stop();
    }

    private static void save(Minecraft client, Path output, String name) throws java.io.IOException {
        Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int abgr = image.getPixelRGBA(image.getWidth() / 8, image.getHeight() / 8);
            int actual = (abgr & 0xff00ff00) | ((abgr >>> 16) & 255) | ((abgr & 255) << 16);
            int expected = fixture.themes.global().resolve(ThemeTokens.BACKGROUND).argb();
            require(near(actual, expected), name + " background expected %08X but rendered %08X".formatted(expected, actual));
            image.writeToFile(output.resolve(name + ".png"));
            if (!Boolean.getBoolean("shb.theme.smoke.captureOnly")) {
                Path baseline = Path.of(System.getProperty("shb.theme.smoke.baseline"), name + ".png");
                try (var reference = NativeImage.read(Files.newInputStream(baseline))) {
                    compare(name, image, reference);
                }
            }
            System.out.println("DYNAMIC_THEME_FRAME " + name + " background=" + "%08X".formatted(actual));
        }
    }

    private static void compare(String name, NativeImage image, NativeImage reference) {
        double xScale = image.getWidth() / (double) fixture.width;
        double yScale = image.getHeight() / (double) fixture.height;
        int mismatch = 0;
        // Sample the fixed widget neighborhood in GUI units. The reference is an 800 × 480, GUI-scale-2 capture.
        for (int y = -24; y < 24; y++) {
            for (int x = -64; x < 64; x++) {
                int actual = image.getPixelRGBA(image.getWidth() / 2 + (int) Math.round(x * xScale),
                        image.getHeight() / 2 + (int) Math.round(y * yScale));
                int expected = reference.getPixelRGBA(reference.getWidth() / 2 + 2 * x, reference.getHeight() / 2 + 2 * y);
                if (!near(actual, expected)) mismatch++;
            }
        }
        require(mismatch <= 123, name + " differs from baseline at " + mismatch + " / 6144 sampled pixels");
    }

    private static boolean near(int actual, int expected) {
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((actual >>> shift) & 255) - ((expected >>> shift) & 255)) > 5) return false;
        }
        return true;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture extends Screen {
        private final AtomicLong time = new AtomicLong();
        private final ThemeManager themes = new ThemeManager(time::get, error -> { throw new AssertionError(error); });
        private final com.dishanhai.sh_better_ui.theme.ThemeScope page = themes.global().child("smoke");
        private UiButton button;
        private boolean hover;
        private int clicks;
        private long firstDrawnAt;

        Fixture() { super(Component.translatable("sh_better_ui.theme.dynamic.title")); }

        @Override
        protected void init() {
            if (button != null) button.unbindTheme();
            button = addRenderableWidget(new UiButton(width / 2 - 50, height / 2 - 11, 100, 22,
                    Component.translatable("gui.done"), ignored -> clicks++));
            button.setTooltip(null);
            button.bindTheme(page);
        }

        void scene(int index) {
            firstDrawnAt = 0;
            hover = index == 6 || index == 7;
            button.active = index != 9;
            button.setHeld(index == 7);
            setFocused(index == 8 ? button : null);
            themes.preferences(ThemePreferences.DEFAULT);
            switch (index) {
                case 0, 6, 7, 8, 9 -> themes.setTheme(BuiltinThemes.DARK);
                case 1 -> themes.setTheme(BuiltinThemes.LIGHT_ID);
                case 2 -> themes.setTheme(BuiltinThemes.NETHER);
                case 3 -> themes.setTheme(BuiltinThemes.END);
                case 4 -> {
                    themes.setTheme(BuiltinThemes.DARK);
                    themes.preferences(themes.preferences().withHighContrast(true));
                }
                case 5 -> {
                    themes.setTheme(BuiltinThemes.DARK);
                    themes.transitionTo(BuiltinThemes.LIGHT_ID, TransitionSpec.of(120, Easing.LINEAR));
                    time.addAndGet(60);
                    themes.frame();
                    require(themes.inspect(page).transitionProgress() == .5, "Expected midpoint sample");
                }
                case 10 -> themes.setTheme("sh_better_ui:aurora");
                default -> throw new IllegalArgumentException("Unknown scene");
            }
            if (index == 7) {
                int before = clicks;
                require(button.mouseClicked(width / 2.0, height / 2.0, 0), "Press was not accepted");
                require(clicks == before + 1, "Press callback count changed from " + before + " to " + clicks);
            }
            if (index == 9) {
                require(!button.mouseClicked(width / 2.0, height / 2.0, 0), "Disabled button accepted press");
            }
        }

        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (firstDrawnAt == 0) firstDrawnAt = System.nanoTime() / 1_000_000L;
            UiTheme.preview(UiTheme.fromScope(page), () -> {
                DarkModeTheme.background(graphics, 0, 0, width, height);
                super.render(graphics, hover ? width / 2 : -1, hover ? height / 2 : -1, partialTick);
            });
        }

        @Override
        public void removed() {
            themes.close();
            super.removed();
        }
    }
}
