// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.*;
import com.dishanhai.sh_better_ui.kernel.api.dock.indicator.StatusIndicator;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import com.dishanhai.sh_better_ui.kernel.internal.dock.DockPersistence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.*;

/** Exercises the real frame, pointer, key and render path from either loader. */
final class DockSmoke {
    private static final UiId ID = UiId.parse("sh_better_ui:dock_smoke");
    private static final UiId DEMO = UiId.parse("sh_better_ui:monitor");
    private static final UiId HUD = UiId.parse("sh_better_ui:dock_hud_smoke");
    private final ModuleContext module;
    private final Screen parent;
    private final Path output;
    private final Probe screen = new Probe();
    private MiniWindowHandle window;
    private int stage, characters, drags, releases, nativeClicks;
    private int resizeStage;
    private double beforeResize;
    private boolean contentClosed;
    private long deadline;
    private long resourceVersion;
    private CompletableFuture<Void> reload;
    private ClientUiSettings savedDisplay;
    private ScopeHandle hudScope;
    private MiniWindowHandle hud;
    private DockBenchmarkSmoke benchmark;

    DockSmoke(Minecraft client, ModuleContext module, Path output) {
        this.module = module;
        this.output = output;
        parent = client.screen;
        client.setWindowActive(true);
        client.setScreen(screen);
        window.resetPlacement();
    }

    int stage() { return stage; }

    boolean advance(Minecraft client, KernelClientSmoke.Platform platform) throws Exception {
        client.setWindowActive(true);
        if (benchmark != null) return benchmark.advance(client);
        var view = window.snapshot().orElse(null);
        if (stage >= 14) return hud(client, platform);
        if (stage == 10) {
            require(!window.isOpen() && contentClosed, "Dock scope retained its content");
            require(module.engine().require(UIApis.WINDOWS).find(module, ID).isEmpty(), "Closed window remains registered");
            require(module.engine().require(UIApis.DOCK).layout().entries().isEmpty(), "Closed window remains presented");
            require(ClientUiKernel.saveClientSettings(savedDisplay), "Could not restore kernel display preferences");
            ClientUiKernel.openDockExample(client);
            stage++;
            return false;
        }
        if (stage >= 11) {
            var plan = module.engine().require(UIApis.DOCK).layout();
            var demo = plan.entries().get(DEMO);
            if (demo == null || !demo.visible()) return false;
            var bounds = demo.bounds();
            if (stage == 11) {
                require(bounds.width() <= 28.5, "Demo did not start as a compact strip");
                capture(client, "dock-demo-compact.png");
                move(client, bounds.x() + 12, bounds.y() + 30);
                platform.flushPointer(client);
            } else if (stage == 12) {
                if (bounds.width() < 259) return false;
                capture(client, "dock-demo-expanded.png");
                key(client, GLFW.GLFW_KEY_F10, GLFW.GLFW_PRESS);
                key(client, GLFW.GLFW_KEY_F10, GLFW.GLFW_RELEASE);
            } else {
                if (plan.entries().size() < 2) return false;
                require(plan.pushInsets().left() > 0 && plan.collisions().overlaps().isEmpty(),
                        "Same-edge demo window did not reserve space cleanly");
                client.setScreen(parent);
                client.tell(() -> platform.join(client, parent, "dock-smoke-" + System.nanoTime()));
            }
            stage++;
            return false;
        }
        if (stage == 8) {
            if (!reload.isDone()) return false;
            reload.join();
            require(module.engine().require(UIApis.RESOURCES).version() > resourceVersion, "Resources did not reload");
            require(!window.isOpen(), "Reload left the old window handle open");
            require(contentClosed, "Reload did not release the expanded content");
            require(module.engine().require(UIApis.DOCK).layout().entries().isEmpty(), "Reload left stale dock hit bounds");
            contentClosed = false;
            savedDisplay = ClientUiSettings.current();
            require(ClientUiKernel.saveClientSettings(savedDisplay.withDisplay(true, true, false)),
                    "Could not enable reduced motion and the no-blur fallback in the kernel configuration");
            screen.openWindow();
            stage++;
            return false;
        }
        if (view == null) return false;
        var bounds = view.geometry().bounds();
        var content = view.contentBounds();
        switch (stage) {
            case 0 -> {
                if (view.state() != CollapseState.COLLAPSED) return false;
                require(bounds.width() <= 28.5, "Wrong collapsed dock geometry");
                require(view.layoutVersion() == module.engine().require(UIApis.DOCK).layout().version(),
                        "Window and dock layout versions differ");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    image.writeToFile(output.resolve("dock-compact.png").toFile());
                }
                move(client, bounds.x() + 12, bounds.y() + 30);
                platform.flushPointer(client);
                stage++;
            }
            case 1 -> {
                if (view.state() != CollapseState.EXPANDED) return false;
                require(bounds.width() > 230 && !content.empty(), "Hover failed to reveal panel");
                screen.setFocused(null);
                key(client, GLFW.GLFW_KEY_TAB, GLFW.GLFW_PRESS);
                key(client, GLFW.GLFW_KEY_TAB, GLFW.GLFW_RELEASE);
                require(screen.getFocused() == screen.nativeButton, "Dock took Tab before the native button");
                key(client, GLFW.GLFW_KEY_TAB, GLFW.GLFW_PRESS);
                key(client, GLFW.GLFW_KEY_TAB, GLFW.GLFW_RELEASE);
                invoke(client.keyboardHandler, "charTyped", new Class<?>[] {long.class, int.class, int.class},
                        client.getWindow().getWindow(), (int) 'k', 0);
                require(characters == 1, "Tab could not enter Dock after a native button");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    image.writeToFile(output.resolve("dock-expanded.png").toFile());
                }
                move(client, content.x() + 26, content.y() + 12);
                platform.flushPointer(client);
                button(client, GLFW.GLFW_PRESS);
                button(client, GLFW.GLFW_RELEASE);
                invoke(client.keyboardHandler, "charTyped", new Class<?>[] {long.class, int.class, int.class},
                        client.getWindow().getWindow(), (int) 'a', 0);
                require(characters == 2, "Text did not reach focused dock content");
                move(client, client.getWindow().getGuiScaledWidth() - 2, client.getWindow().getGuiScaledHeight() - 2);
                platform.flushPointer(client);
                deadline = System.nanoTime() + 500_000_000L;
                stage++;
            }
            case 2 -> {
                if (System.nanoTime() < deadline) return false;
                require(view.focused() && view.state() == CollapseState.EXPANDED, "Text focus did not protect expansion");
                key(client, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS);
                key(client, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_RELEASE);
                stage++;
            }
            case 3 -> {
                if (view.state() != CollapseState.COLLAPSED) return false;
                window.pin(true);
                stage++;
            }
            case 4 -> {
                if (view.state() != CollapseState.EXPANDED) return false;
                move(client, content.x() + 30, content.y() + 35);
                platform.flushPointer(client);
                button(client, GLFW.GLFW_PRESS);
                move(client, bounds.right() + 10, content.y() + 45);
                platform.flushPointer(client);
                button(client, GLFW.GLFW_RELEASE);
                require(drags > 0 && releases == 1, "Drag capture did not survive leaving the window");
                window.pin(false);
                window.dock(DockEdge.RIGHT);
                stage++;
            }
            case 5 -> {
                if (view.state() != CollapseState.EXPANDED) return false;
                var placement = view.geometry().placement();
                var safe = module.engine().require(UIApis.DOCK).layout().safeArea();
                if (resizeStage < 2 && placement.edge() != DockEdge.RIGHT
                        || resizeStage >= 2 && placement.edge() != DockEdge.BOTTOM) return false;
                switch (resizeStage) {
                    case 0 -> {
                        require(Math.abs(bounds.right() - safe.right()) < .001, "Right dock lost its anchor");
                        beforeResize = placement.width();
                        resize(client, platform, bounds.x() + 3, bounds.bottom() - 3, -20, 0);
                    }
                    case 1 -> {
                        require(placement.width() == beforeResize + 20, "Right resize did not grow towards the pointer");
                        window.dock(DockEdge.BOTTOM);
                    }
                    case 2 -> {
                        require(Math.abs(bounds.bottom() - safe.bottom()) < .001, "Bottom dock lost its anchor");
                        beforeResize = placement.height();
                        resize(client, platform, bounds.right() - 3, bounds.y() + 2, 0, -20);
                    }
                    case 3 -> {
                        require(placement.height() == beforeResize + 20, "Bottom resize did not grow towards the pointer");
                        window.floatAt(60, 55);
                        stage++;
                    }
                }
                resizeStage++;
            }
            case 6 -> {
                if (view.geometry().placement().mode() != DockMode.FLOATING) return false;
                require(Math.abs(bounds.x() - 60) < .001, "Floating position was not applied");
                client.options.guiScale().set(1);
                client.resizeDisplay();
                stage++;
            }
            case 7 -> {
                if (client.getWindow().getGuiScale() != 1) return false;
                require(window.isOpen() && module.engine().require(UIApis.DOCK).layout().safeArea().contains(bounds),
                        "GUI scale resize lost the window or exceeded the safe area");
                var config = output.resolve("config/sh_better_ui-dock-layout.txt");
                if (!Files.isRegularFile(config)) return false;
                var stored = DockPersistence.decode(Files.readString(config)).get(ID);
                if (stored == null || stored.mode() != DockMode.FLOATING) return false;
                resourceVersion = module.engine().require(UIApis.RESOURCES).version();
                stage++;
                reload = client.reloadResourcePacks();
            }
            case 9 -> {
                require(view.geometry().placement().mode() == DockMode.FLOATING && bounds.x() == 60,
                        "Saved floating placement was not restored after reload");
                require(view.state() == CollapseState.EXPANDED, "Reduced-motion restore did not settle");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    require(pixel(image, client, (int) content.x() + 14, (int) content.y() + 9) == 0xffddaa22,
                            "No-blur fallback hid the window foreground");
                    image.writeToFile(output.resolve("dock-fallback-scale1.png").toFile());
                }
                move(client, 26, client.getWindow().getGuiScaledHeight() - 22);
                platform.flushPointer(client);
                button(client, GLFW.GLFW_PRESS);
                button(client, GLFW.GLFW_RELEASE);
                require(nativeClicks == 1, "A click outside the window did not reach the underlying Screen");
                client.setScreen(parent);
                stage++;
            }
            default -> throw new AssertionError("Unexpected dock smoke stage " + stage);
        }
        return false;
    }

    private static void resize(Minecraft client, KernelClientSmoke.Platform platform,
                               double x, double y, double dx, double dy) throws Exception {
        move(client, x, y);
        platform.flushPointer(client);
        button(client, GLFW.GLFW_PRESS);
        move(client, x + dx, y + dy);
        platform.flushPointer(client);
        button(client, GLFW.GLFW_RELEASE);
    }

    private void capture(Minecraft client, String name) throws java.io.IOException {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name).toFile());
        }
    }

    private boolean hud(Minecraft client, KernelClientSmoke.Platform platform) throws Exception {
        var engine = module.engine();
        switch (stage) {
            case 14 -> {
                if (client.level == null || client.player == null || client.screen != null) return false;
                require(engine.platform().hasClientSession(), "World did not mount a HUD session");
                hudScope = engine.require(UIApis.SCREENS).session(module, HUD);
                hud = engine.require(UIApis.WINDOWS).createMiniWindow(module, hudScope,
                        MiniWindowSpec.builder(HUD).title(Component.translatable("sh_better_ui.dock_example.title"))
                                .anchor(DockEdge.TOP).autoHide(false).build());
            }
            case 15 -> {
                var frame = hud.snapshot().orElse(null);
                if (frame == null || frame.state() != CollapseState.EXPANDED) return false;
                capture(client, "dock-hud.png");
                require(platform.clientCommand("shbetterdock"), "Native client did not recognize the dock command");
            }
            case 16 -> {
                if (client.screen == null || !engine.require(UIApis.DOCK).layout().entries().containsKey(DEMO)) return false;
                require(hud.isOpen() && hud.snapshot().isEmpty(), "HUD appeared over the open Screen");
                client.screen.onClose();
            }
            case 17 -> {
                if (client.screen != null || hud.snapshot().isEmpty()) return false;
                client.tell(() -> platform.leave(client, parent));
            }
            case 18 -> {
                if (client.level != null || client.screen != parent) return false;
                require(!hudScope.isOpen() && !hud.isOpen() && hud.snapshot().isEmpty(), "Logout retained the dock HUD");
                require(engine.require(UIApis.DOCK).layout().entries().isEmpty(), "Logout retained presented dock geometry");
                benchmark = new DockBenchmarkSmoke(client, module, output);
                return false;
            }
            default -> throw new AssertionError("Unexpected dock HUD smoke stage " + stage);
        }
        stage++;
        return false;
    }

    private final class Probe extends Screen {
        private ScopeHandle tree;
        private Button nativeButton;

        Probe() { super(Component.translatable("sh_better_ui.dock_example.title")); }

        @Override protected void init() {
            nativeButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> nativeClicks++)
                    .bounds(10, height - 30, 60, 18).build());
            if (stage != 8) openWindow();
        }

        void openWindow() {
            if (window != null && window.isOpen()) return;
            var engine = module.engine();
            if (tree == null || !tree.isOpen()) tree = engine.require(UIApis.SCREENS).mount(module, this, ID);
            window = engine.require(UIApis.WINDOWS).createMiniWindow(module, tree, MiniWindowSpec.builder(ID)
                    .title(getTitle()).persistent(true).glass(GlassMaterial.preset(GlassVariant.REGULAR))
                    .compactIndicator(() -> new StatusIndicator(10,
                            () -> new StatusIndicator.State(StatusIndicator.Shape.READY, 0xff86ccff)))
                    .content(() -> new WindowContent() {
                        @Override public void render(RenderService.Context draw, View view) {
                            var b = view.bounds();
                            draw.fill((int) b.x() + 12, (int) b.y() + 6, (int) b.x() + 48, (int) b.y() + 18, 0xff22aadd);
                        }
                        @Override public Response input(NativeInput input, View view) {
                            if (input instanceof NativeInput.Character) { characters++; return Response.HANDLED; }
                            if (!(input instanceof NativeInput.Pointer p)) return Response.PASS;
                            return switch (p.action()) {
                                case PRESS -> p.y() - view.bounds().y() < 25 ? Response.FOCUS : Response.CAPTURE;
                                case DRAG -> { drags++; yield Response.HANDLED; }
                                case RELEASE -> { releases++; yield Response.HANDLED; }
                                default -> Response.PASS;
                            };
                        }
                        @Override public void close() { contentClosed = true; }
                    }).build());
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff243045);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
