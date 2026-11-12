// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.*;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceGeometry;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePresentation;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity;
import com.dishanhai.sh_better_ui.kernel.api.live.network.ServerLiveActivities;
import com.dishanhai.sh_better_ui.kernel.api.live.network.ServerLiveActivityAction;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

/** Real HUD pixels, native input, resize and integrated server round trip on each loader. */
final class LiveSurfaceSmoke {
    private static final UiId MAIN = id("live/smoke_main");
    private static final UiId SECONDARY = id("live/smoke_secondary");
    private static final UiId ACTIVATE = id("live/smoke_activate");
    private static final UiId NOTICE = id("live/smoke_notice");
    private static final UiId REMOTE = id("live/smoke_remote");
    private static final UiId PREVIEW = id("live/preview");
    private final ModuleContext module;
    private final ScopeHandle session;
    private final LiveSurfaceService surfaces;
    private final Handle primary, secondary;
    private final AtomicInteger serverActions = new AtomicInteger();
    private final CompletableFuture<Boolean> remotePublished = new CompletableFuture<>();
    private final StringBuilder samples = new StringBuilder(
            "scenario,window_width,window_height,gui_scale,activities,render_cpu_ns,animation_cpu_ns\n");
    private CompletableFuture<Void> reloaded;
    private long initialResourceVersion;
    private int initialWindowWidth;
    private long nextDiagnostic;
    private long enteredAt = System.nanoTime();
    private Stage loggedStage;
    private Stage stage = Stage.SPLIT;
    private int localActions;
    private int oldNoticeActions, newNoticeActions;
    private Screen activityScreen;
    private HostInputScreen hostInput;
    private NotificationService.Handle notice;
    private int splitFrames, expandedFrames, interruptions, reloads;

    LiveSurfaceSmoke(ModuleContext module, ScopeHandle session) {
        this.module = module;
        this.session = session;
        surfaces = module.engine().require(LiveSurfaceService.KEY);
        var activities = module.engine().require(ActivityService.KEY);
        var content = new Content(Text.literal("Live smoke primary"), Text.literal("Local activity"),
                null, .4, false, List.of(new Control(ACTIVATE, Text.literal("Confirm"))), List.of());
        primary = activities.publish(module, session, new Request(MAIN, content, Priority.HIGH, Duration.ZERO, true,
                action -> { if (action.id().equals(ACTIVATE)) localActions++; }));
        secondary = activities.publish(module, session, new Request(SECONDARY,
                Content.label(Text.literal("Live smoke secondary")), Priority.LOW, Duration.ZERO, true, null));
    }

    boolean advance(Minecraft client, KernelClientSmoke.Platform platform, Path output) throws Exception {
        // The fixture runs on an unattended desktop. Simulate the focused state used by the input checks.
        if (!client.isWindowActive()) client.setWindowActive(true);
        var view = surfaces.view();
        long now = System.nanoTime();
        if (stage != loggedStage) {
            enteredAt = now;
            nextDiagnostic = now;
            loggedStage = stage;
        }
        if (now >= nextDiagnostic) {
            nextDiagnostic = now + Duration.ofSeconds(4).toNanos();
            System.out.println("LIVE_SMOKE " + stage + " screen=" + client.screen
                    + " target=" + view.target() + " settled=" + view.settled()
                    + " interactive=" + view.interactive() + " geometry=" + view.primary()
                    + " active=" + surfaces.diagnostics().activities() + " localActions=" + localActions
                    + " serverActions=" + serverActions.get() + " notices=" + surfaces.diagnostics().notifications());
        }
        require(now - enteredAt < Duration.ofSeconds(stage == Stage.RELOAD ? 35 : 20).toNanos(), "Live stage stalled " + stage);
        switch (stage) {
            case SPLIT -> {
                if (view.settled() != SurfacePresentation.SPLIT || view.secondary().opacity() < .9) return false;
                require(view.plan().primary().id().equals(MAIN) && view.plan().secondary().id().equals(SECONDARY),
                        "Wrong live split arbitration");
                require(!view.interactive(), "Live HUD claimed gameplay input without a Screen");
                sample(client, "hud-split");
                if (++splitFrames < 48) return false;
                screenshot(client, output.resolve("live-hud.png"), view.primary());
                primary.requestPresentation(SurfacePresentation.MINIMAL);
                stage = Stage.MINIMAL_HINTS;
            }
            case MINIMAL_HINTS -> {
                if (view.settled() != SurfacePresentation.MINIMAL || view.transitioning()) return false;
                screenshot(client, output.resolve("live-minimal-shortcuts.png"), view.primary());
                stage = Stage.HINTS_RETRACTED;
            }
            case HINTS_RETRACTED -> {
                if (now - enteredAt < Duration.ofMillis(2_300).toNanos()) return false;
                require(view.settled() == SurfacePresentation.MINIMAL && view.primary().visible(),
                        "Shortcut hint timeout hid the live activity");
                screenshot(client, output.resolve("live-minimal-shortcuts-retracted.png"), view.primary());
                primary.requestPresentation(SurfacePresentation.COMPACT);
                stage = Stage.POINTER_SHORTCUT;
            }
            case POINTER_SHORTCUT -> {
                if (view.settled() != SurfacePresentation.SPLIT || view.transitioning()) return false;
                altKey(client, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_PRESS);
                require(client.screen == null, "Alt opened before the chord was known");
                altKey(client, GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_PRESS);
                altKey(client, GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_RELEASE);
                KernelClientSmoke.key(client, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_RELEASE);
                require(client.screen != null && !client.mouseHandler.isMouseGrabbed(),
                        "Alt+grave did not release the pointer");
                stage = Stage.OPEN;
            }
            case OPEN -> {
                if (!view.interactive()) return false;
                clickBody(client, platform, view.primary());
                stage = Stage.POINTER_EXPANDED;
            }
            case POINTER_EXPANDED -> {
                if (view.settled() != SurfacePresentation.EXPANDED || view.transitioning()) return false;
                pressKey(client, GLFW.GLFW_KEY_ESCAPE);
                if (client.screen != null) pressKey(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == null && client.mouseHandler.isMouseGrabbed(),
                        "Escape did not restore gameplay mouse capture");
                stage = Stage.ALT_DETAILS;
            }
            case ALT_DETAILS -> {
                if (view.interactive() || !view.primary().visible()) return false;
                altKey(client, GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_PRESS);
                KernelClientSmoke.key(client, GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_RELEASE);
                require(client.screen != null, "Solo Alt did not open the activity dashboard");
                stage = Stage.EXPANDED;
            }
            case EXPANDED -> {
                if (view.settled() != SurfacePresentation.EXPANDED || view.transitioning() || !view.interactive()) return false;
                sample(client, "screen-expanded");
                if (++expandedFrames < 48) return false;
                screenshot(client, output.resolve("live-expanded.png"), view.primary());
                pressKey(client, GLFW.GLFW_KEY_TAB);
                pressKey(client, GLFW.GLFW_KEY_ENTER);
                stage = Stage.LOCAL_ACTION;
            }
            case LOCAL_ACTION -> {
                if (localActions == 0) return false;
                require(localActions == 1, "Local action dispatched more than once");
                stage = Stage.INTERRUPT;
            }
            case INTERRUPT -> {
                if (interruptions < 32) {
                    var geometry = view.primary();
                    if (geometry.visible()) {
                        require(geometry.x() >= 0 && geometry.y() >= 0
                                        && geometry.x() + geometry.width() <= client.getWindow().getGuiScaledWidth()
                                        && geometry.y() + geometry.height() <= client.getWindow().getGuiScaledHeight(),
                                "Retargeted geometry escaped the viewport");
                        sample(client, "screen-retarget");
                    }
                    primary.requestPresentation(interruptions++ % 2 == 0 ? SurfacePresentation.COMPACT : SurfacePresentation.EXPANDED);
                    return false;
                }
                if (view.transitioning() || !view.interactive()) return false;
                var geometry = view.primary();
                moveToBody(client, platform, geometry);
                KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
                client.setWindowActive(false);
                KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
                client.setWindowActive(true);
                primary.cancel();
                secondary.cancel();
                activityScreen = client.screen;
                require(DeveloperPanelScreen.sampleIds().contains("live_activity"),
                        "Activity Island is missing from the developer gallery");
                stage = Stage.PREVIEW_ENTRY;
            }
            case PREVIEW_ENTRY -> {
                var label = Component.translatable("sh_better_ui.live.preview.open").getString();
                var entry = client.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.visible && button.active && button.getMessage().getString().equals(label))
                        .findFirst();
                if (entry.isEmpty()) return false;
                entry.orElseThrow().onPress();
                require(client.screen != activityScreen, "Activity Island preview did not open");
                stage = Stage.PREVIEW_COMPACT;
            }
            case PREVIEW_COMPACT -> {
                if (!view.interactive() || view.plan().primary() == null || !PREVIEW.equals(view.plan().primary().id())
                        || view.settled() != SurfacePresentation.COMPACT || view.transitioning()) return false;
                clickBody(client, platform, view.primary());
                stage = Stage.PREVIEW_EXPANDED;
            }
            case PREVIEW_EXPANDED -> {
                if (!view.interactive() || view.settled() != SurfacePresentation.EXPANDED || view.transitioning()) return false;
                screenshot(client, output.resolve("live-activity-preview.png"), view.primary());
                client.screen.onClose();
                require(client.screen == activityScreen, "Activity Island preview did not return to its parent");
                require(surfaces.diagnostics().activities() == 0, "Activity Island preview survived its Screen");
                hostInput = new HostInputScreen();
                client.setScreen(hostInput);
                notice = publishNotice(false);
                stage = Stage.NOTICE_OPEN;
            }
            case NOTICE_OPEN -> {
                if (!view.interactive() || view.plan().primary() == null || !NOTICE.equals(view.plan().primary().id())) return false;
                clickBody(client, platform, view.primary());
                stage = Stage.NOTICE_PRESS;
            }
            case NOTICE_PRESS -> {
                if (view.settled() != SurfacePresentation.EXPANDED || view.transitioning() || !view.interactive()) return false;
                moveToAction(client, platform, view.primary());
                KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
                require(hostInput.nativePresses == 0, "Live notification did not capture the native press");
                require(publishNotice(true) == notice, "Notice replacement did not reuse its publication");
                stage = Stage.NOTICE_RELEASE;
            }
            case NOTICE_RELEASE -> {
                if (view.plan().primary() == null || view.plan().primary().revision() != notice.snapshot().revision()
                        || view.transitioning() || !view.interactive()) return false;
                KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
                stage = Stage.NOTICE_FRESH_ACTION;
            }
            case NOTICE_FRESH_ACTION -> {
                require(oldNoticeActions == 0 && newNoticeActions == 0, "Held old notice executed after replacement");
                moveToAction(client, platform, view.primary());
                KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
                KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
                stage = Stage.NOTICE_FOCUS;
            }
            case NOTICE_FOCUS -> {
                require(oldNoticeActions == 0 && newNoticeActions == 1, "Fresh notice action did not execute exactly once");
                KernelClientSmoke.move(client, hostInput.field.getX() + 10, hostInput.field.getY() + 9);
                platform.flushPointer(client);
                KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
                KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
                require(hostInput.getFocused() == hostInput.field, "The host input field did not take focus");
                int end = hostInput.field.getValue().length();
                hostInput.field.setCursorPosition(end);
                hostInput.field.setHighlightPos(end);
                pressKey(client, GLFW.GLFW_KEY_BACKSPACE);
                require("x".equals(hostInput.field.getValue()) && notice.snapshot().lifecycle() == Lifecycle.RUNNING,
                        "Live notification kept keyboard focus after the host input field was clicked");
                notice.dismiss();
                client.setScreen(activityScreen);
                var server = client.getSingleplayerServer();
                var user = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(user);
                    var data = new NetworkLiveActivity(REMOTE, "Live smoke server", "Authoritative integrated server",
                            NetworkLiveActivity.Status.RUNNING, .65f, new NetworkLiveActivity.ActionOffer(ACTIVATE, "Confirm"));
                    remotePublished.complete(ServerLiveActivities.publish(player, data,
                            new ServerLiveActivityAction<>(sender -> sender == player, sender -> serverActions.incrementAndGet())));
                });
                stage = Stage.REMOTE;
            }
            case REMOTE -> {
                if (!remotePublished.isDone()) return false;
                require(remotePublished.join(), "Server did not negotiate the live surface channel");
                if (view.plan().primary() == null || view.plan().primary().kind() != Kind.REMOTE || !view.interactive()) return false;
                clickBody(client, platform, view.primary());
                stage = Stage.REMOTE_OPEN;
            }
            case REMOTE_OPEN -> {
                if (view.settled() != SurfacePresentation.EXPANDED || view.transitioning() || !view.interactive()) return false;
                screenshot(client, output.resolve("live-remote.png"), view.primary());
                var geometry = view.primary();
                KernelClientSmoke.move(client, geometry.x() + geometry.width() / 2, geometry.y() + geometry.height() - 16);
                platform.flushPointer(client);
                KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
                KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
                stage = Stage.REMOTE_ACTION;
            }
            case REMOTE_ACTION -> {
                if (serverActions.get() == 0 || surfaces.diagnostics().notifications() == 0) return false;
                require(serverActions.get() == 1, "Server action was applied more than once");
                initialWindowWidth = client.getWindow().getScreenWidth();
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 864, 512);
                client.options.guiScale().set(1);
                client.resizeDisplay();
                reload(client);
                stage = Stage.RELOAD;
            }
            case RELOAD -> {
                if (!reloaded.isDone()) return false;
                reloaded.join();
                require(com.dishanhai.sh_better_ui.kernel.api.UnifiedUI.get()
                                .require(com.dishanhai.sh_better_ui.kernel.api.UIApis.RESOURCES).version() > initialResourceVersion,
                        "Resource reload did not reach the kernel");
                require(client.getWindow().getGuiScale() == 1, "GUI scale was not applied");
                if (view.primary().opacity() < .95 || view.transitioning()
                        || client.getWindow().getScreenWidth() == initialWindowWidth) return false;
                require(surfaces.diagnostics().activities() == 1, "Reload lost the remote session");
                if (++reloads < 3) {
                    reload(client);
                    return false;
                }
                screenshot(client, output.resolve("live-reload-scale1.png"), view.primary());
                Files.writeString(output.resolve("live-frame-samples.csv"), samples);
                Files.writeString(output.resolve("live-environment.txt"), "OpenGL renderer: " + GL11.glGetString(GL11.GL_RENDERER)
                        + "\nOpenGL version: " + GL11.glGetString(GL11.GL_VERSION)
                        + "\nJava: " + Runtime.version() + "\n" + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n");
                stage = Stage.DONE;
                return true;
            }
            case DONE -> { return true; }
        }
        return false;
    }

    void verifyLogout() {
        require(localActions == 1 && serverActions.get() == 1, "A captured action was repeated or lost");
        require(surfaces.diagnostics().activities() == 0 && surfaces.diagnostics().notifications() == 0,
                "Live activities or action results survived native logout");
    }

    String stage() { return stage.name(); }

    private NotificationService.Handle publishNotice(boolean replacement) {
        var content = new Content(Text.literal(replacement ? "Live notice B" : "Live notice A"),
                Text.literal("Local activity"), null, .4, false,
                List.of(new Control(ACTIVATE, Text.literal("Confirm"))), List.of());
        return module.engine().require(NotificationService.KEY).publish(module, session,
                new NotificationService.Request(NOTICE, content, Priority.HIGH, Duration.ofSeconds(30), NOTICE,
                        NotificationService.Merge.REPLACE, ignored -> {
                            if (replacement) newNoticeActions++;
                            else oldNoticeActions++;
                        }));
    }

    private void reload(Minecraft client) {
        initialResourceVersion = com.dishanhai.sh_better_ui.kernel.api.UnifiedUI.get()
                .require(com.dishanhai.sh_better_ui.kernel.api.UIApis.RESOURCES).version();
        reloaded = client.reloadResourcePacks();
    }

    private void sample(Minecraft client, String scenario) {
        var timing = surfaces.diagnostics();
        var window = client.getWindow();
        samples.append(scenario).append(',').append(window.getScreenWidth()).append(',').append(window.getScreenHeight())
                .append(',').append(window.getGuiScale()).append(',').append(timing.activities()).append(',')
                .append(timing.renderNanos()).append(',').append(timing.animationNanos()).append('\n');
    }

    private static void clickBody(Minecraft client, KernelClientSmoke.Platform platform, SurfaceGeometry geometry) throws Exception {
        moveToBody(client, platform, geometry);
        KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
        KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
    }

    private static void moveToBody(Minecraft client, KernelClientSmoke.Platform platform, SurfaceGeometry geometry) throws Exception {
        KernelClientSmoke.move(client, geometry.x() + geometry.width() / 2, geometry.y() + Math.min(7, geometry.height() / 2));
        platform.flushPointer(client);
    }

    private static void moveToAction(Minecraft client, KernelClientSmoke.Platform platform, SurfaceGeometry geometry) throws Exception {
        KernelClientSmoke.move(client, geometry.x() + geometry.width() / 2, geometry.y() + geometry.height() - 16);
        platform.flushPointer(client);
    }

    private static void pressKey(Minecraft client, int key) {
        KernelClientSmoke.key(client, key, GLFW.GLFW_PRESS);
        KernelClientSmoke.key(client, key, GLFW.GLFW_RELEASE);
    }

    private static void altKey(Minecraft client, int key, int action) {
        client.keyboardHandler.keyPress(client.getWindow().getWindow(), key, GLFW.glfwGetKeyScancode(key),
                action, GLFW.GLFW_MOD_ALT);
    }

    private static void screenshot(Minecraft client, Path path, SurfaceGeometry geometry) throws Exception {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int pixel = KernelClientSmoke.pixel(image, client, (int) (geometry.x() + geometry.width() / 2), (int) (geometry.y() + 3));
            require((pixel & 255) < 96 && (pixel >> 8 & 255) < 96 && (pixel >> 16 & 255) < 96,
                    "Live surface shell not found in " + path + " RGB " + Integer.toHexString(pixel));
            image.writeToFile(path.toFile());
        }
    }

    private static UiId id(String path) { return new UiId("sh_better_ui", path); }

    private static final class HostInputScreen extends Screen {
        private EditBox field;
        private int nativePresses;

        HostInputScreen() { super(Component.empty()); }

        @Override protected void init() {
            field = addRenderableWidget(new EditBox(font, 12, height - 46, 80, 18, Component.empty()));
            field.setValue("xy");
        }

        @Override public boolean mouseClicked(double x, double y, int button) {
            nativePresses++;
            return super.mouseClicked(x, y, button);
        }

        @Override public boolean isPauseScreen() { return false; }
    }

    private enum Stage {
        SPLIT, MINIMAL_HINTS, HINTS_RETRACTED, POINTER_SHORTCUT, OPEN, POINTER_EXPANDED, ALT_DETAILS, EXPANDED, LOCAL_ACTION,
        INTERRUPT, PREVIEW_ENTRY, PREVIEW_COMPACT, PREVIEW_EXPANDED,
        NOTICE_OPEN, NOTICE_PRESS, NOTICE_RELEASE, NOTICE_FRESH_ACTION, NOTICE_FOCUS,
        REMOTE, REMOTE_OPEN, REMOTE_ACTION, RELOAD, DONE
    }
}
