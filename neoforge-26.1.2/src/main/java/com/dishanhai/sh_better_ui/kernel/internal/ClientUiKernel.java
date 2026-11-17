// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.system.AsyncComputerStatus;
import com.dishanhai.sh_better_ui.config.LiveSurfaceConfig;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIPlatform;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.project.ProjectEdits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Publishes the shared kernel on the physical client. */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class ClientUiKernel {
    private static final ModuleDescriptor.Platform PLATFORM = NativePort.PLATFORM;
    private static final Set<ModuleDescriptor.Platform> PLATFORMS = Set.of(PLATFORM);
    private static final String MODULE_DESCRIPTOR = "META-INF/sh_better_ui.modules";
    private static KernelHost host;
    private static ClientMaterialCatalog catalog;
    private static ClientScreens screens;
    private static ClientReadabilitySamples samples;
    private static ClientTextReadability text;
    private static ClientHud hud;
    private static ClientDockWindows dock;
    private static ClientLiveActivities liveActivities;
    private static NativeMusicActivity musicActivity;
    private static boolean frameOpen;

    private ClientUiKernel() {}

    @SubscribeEvent
    public static void start(ClientStartedEvent event) {
        var minecraft = event.getClient();
        Path config = minecraft.gameDirectory.toPath().resolve("config");
        var liveSettings = LiveSurfaceConfig.load(config);
        if (!liveSettings.kernelEnabled()) return;

        var kernel = new KernelHost(PLATFORM, EnginePublication.CLIENT);
        kernel.storage(config.resolve("sh_better_ui/modules"));
        var clientScreens = new ClientScreens(kernel, PLATFORM);
        var readbacks = new ClientReadabilitySamples(kernel);
        var textReadability = new ClientTextReadability(kernel, clientScreens, readbacks);
        var clientHud = new ClientHud(kernel, readbacks);
        var clientDock = new ClientDockWindows(kernel, clientScreens);
        var clientActivities = new ClientLiveActivities(kernel, liveSettings);
        var nativeMusic = new NativeMusicActivity(true, () -> clientActivities.runtime().policy().notifications(),
                () -> clientActivities.runtime().diagnostics().activities() < clientActivities.runtime().policy().maxActivities(),
                () -> clientActivities.runtime().diagnostics().notifications() < clientActivities.runtime().policy().maxNotifications(),
                () -> NativePort.show(minecraft, com.dishanhai.sh_better_ui.client.api.ShBetterUiApi.settings()
                        .onlineMusicScreen(NativePort.screen(minecraft))));
        var materialCatalog = new ClientMaterialCatalog(kernel, PLATFORM, asset -> {
            var id = Identifier.tryParse(asset);
            return id != null && minecraft.getResourceManager().getResource(id).isPresent();
        });
        var candidates = new ArrayList<>(NativeModuleSources.discover(modSources()));
        candidates.add(nativeMusic.candidate());
        candidates.add(BuiltinService.candidate(PLATFORMS, UIPlatform.KEY, clientScreens));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.SCREENS, clientScreens, UIApis.NATIVE_SCREEN));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.TEXT_READABILITY, textReadability, UIApis.TEXT_READABILITY_POLICY));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.HUD, clientHud, UIApis.NATIVE_HUD));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.WINDOWS, clientDock, new CapabilityKey(UIApis.WINDOWS.id())));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.DOCK, clientDock, new CapabilityKey(UIApis.DOCK.id())));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.ACTIVITIES, clientActivities.activities(),
                com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.CAPABILITY));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.NOTIFICATIONS, clientActivities.notifications(),
                new CapabilityKey(UIApis.NOTIFICATIONS.id())));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.LIVE_SURFACES, clientActivities.surfaces(),
                com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService.CAPABILITY));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.ONLINE_MUSIC,
                new com.dishanhai.sh_better_ui.kernel.internal.music.ClientOnlineMusic()));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.COMPUTER_STATUS,
                new AsyncComputerStatus(() -> minecraft.gameDirectory.toPath(), Util.ioPool()),
                UIApis.COMPUTER_STATUS_READ));
        candidates.add(BuiltinService.candidate(PLATFORMS, PageService.KEY, new ClientPages(kernel),
                new CapabilityKey(PageService.KEY.id())));
        candidates.add(BuiltinService.candidate(PLATFORMS, MaterialCatalog.KEY, materialCatalog,
                new CapabilityKey(MaterialCatalog.KEY.id())));
        candidates.add(BuiltinService.candidate(PLATFORMS, ProjectEdits.KEY, new ProjectEditsRuntime(kernel)));
        kernel.startNative(candidates, (extension, context) -> ClientDeveloperPreviews.mount(kernel, extension, context));
        host = kernel;
        catalog = materialCatalog;
        screens = clientScreens;
        samples = readbacks;
        text = textReadability;
        hud = clientHud;
        dock = clientDock;
        liveActivities = clientActivities;
        musicActivity = nativeMusic;
        screens.sync(minecraft);
        com.dishanhai.sh_better_ui.client.music.ClientMusic.start(config);
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) { beginExtraction(); }

    @SubscribeEvent
    public static void frameEnd(RenderFrameEvent.Post event) {
        if (!frameOpen || host == null) return;
        try {
            text.endFrame();
        } finally {
            frameOpen = false;
            host.finishNativeFrame();
        }
    }

    static void beginExtraction() {
        if (host == null || frameOpen || !host.canBeginNativeFrame()) return;
        screens.sync(Minecraft.getInstance());
        if (dock != null) {
            var client = Minecraft.getInstance();
            dock.sync(NativePort.screen(client), client.isWindowActive() && !NativePort.hasOverlay(client));
        }
        host.beginNativeFrame(() -> {}, phase -> {
            if (phase == com.dishanhai.sh_better_ui.kernel.api.UIPhase.MEASURE) {
                if (musicActivity != null) musicActivity.advance();
                if (liveActivities != null) liveActivities.advance();
            }
        });
        frameOpen = true;
        samples.begin();
        text.beginFrame();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void screenBegin(ScreenEvent.Render.Pre event) {
        if (host == null || event.getScreen() != NativePort.screen(Minecraft.getInstance())) return;
        beginExtraction();
        if (frameOpen) text.beginScreen(event.getScreen(), event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void screenForeground(ScreenEvent.Render.Foreground event) {
        if (frameOpen && text.hasScreen(event.getScreen())) {
            host.renderNativeFrame(screens.activeScope(event.getScreen()));
            text.finishScreen(event.getScreen());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void screenEnd(ScreenEvent.Render.Post event) {
        if (frameOpen && event.getScreen() == NativePort.screen(Minecraft.getInstance())) {
            text.finishScreen(event.getScreen());
            text.discardScreen();
            if (dock != null && Minecraft.getInstance().isWindowActive() && !NativePort.hasOverlay(Minecraft.getInstance()))
                dock.draw(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void screenClosed(ScreenEvent.Closing event) {
        if (dock != null) dock.suspend();
        if (screens != null) screens.closing(event.getScreen());
    }

    @SubscribeEvent
    public static void screenInitialized(ScreenEvent.Init.Post event) {
        if (screens != null) screens.initialized(event.getScreen());
    }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        if (screens != null) screens.login(event.getConnection());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (dock != null) dock.closeAll();
        if (screens != null) screens.logout(event.getConnection());
    }

    @SubscribeEvent
    public static void drawHud(RenderGuiEvent.Post event) {
        if (host == null) return;
        beginExtraction();
        if (frameOpen) {
            var client = Minecraft.getInstance();
            if (NativePort.screen(client) == null) host.renderNativeFrame(null);
            hud.draw(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false), client.level);
            if (liveActivities != null) liveActivities.drawHud(event.getGuiGraphics());
        }
    }

    public static boolean musicActivityAvailable() { return musicActivity != null && musicActivity.available(); }

    public static Screen musicSettingsScreen(Screen parent) {
        if (!musicActivityAvailable()) throw new IllegalStateException("Native music activity is unavailable");
        return new com.dishanhai.sh_better_ui.client.gui.panel.NativeMusicSettingsScreen(parent, musicActivity);
    }

    public static boolean activitiesAvailable() { return host != null && liveActivities != null; }

    public static Screen activityScreen(Screen parent) {
        if (!activitiesAvailable()) throw new IllegalStateException("Native live activities are unavailable");
        return liveActivities.details(parent);
    }

    private static boolean dockInput(Screen screen, NativeInput input) {
        if (dock == null || !Minecraft.getInstance().isWindowActive() || NativePort.hasOverlay(Minecraft.getInstance())) return false;
        return dock.input(screen, input);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dockPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (dockInput(event.getScreen(), new NativeInput.Pointer(NativeInput.PointerAction.PRESS,
                event.getMouseX(), event.getMouseY(), event.getButton(), 0, 0))) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dockReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (dockInput(event.getScreen(), new NativeInput.Pointer(NativeInput.PointerAction.RELEASE,
                event.getMouseX(), event.getMouseY(), event.getButton(), 0, 0))) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dockDragged(ScreenEvent.MouseDragged.Pre event) {
        if (dockInput(event.getScreen(), new NativeInput.Pointer(NativeInput.PointerAction.DRAG,
                event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY()))) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dockScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (dockInput(event.getScreen(), new NativeInput.Pointer(NativeInput.PointerAction.SCROLL,
                event.getMouseX(), event.getMouseY(), -1, event.getScrollDeltaX(), event.getScrollDeltaY()))) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dockKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (dockInput(event.getScreen(), new NativeInput.Key(NativeInput.KeyAction.PRESS,
                event.getKeyCode(), event.getScanCode(), event.getModifiers()))) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dockKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (dockInput(event.getScreen(), new NativeInput.Key(NativeInput.KeyAction.RELEASE,
                event.getKeyCode(), event.getScanCode(), event.getModifiers()))) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dockCharacter(ScreenEvent.CharacterTyped.Pre event) {
        boolean handled = false;
        for (char unit : Character.toChars(event.getCodePoint()))
            handled |= dockInput(event.getScreen(), new NativeInput.Character(unit, 0));
        if (handled) event.setCanceled(true);
    }

    public static ScreenStamp screenStamp(ModuleContext module, ScopeHandle scope) {
        if (text == null) throw new IllegalStateException("Native Screen readability is unavailable");
        return text.screenStamp(module, scope);
    }

    public static boolean isCurrentScreenFrame(long frame, long mountGeneration) {
        return text != null && text.currentFrame(frame, mountGeneration);
    }

    public record ScreenStamp(long frameId, long mountGeneration, long nowMs, int guiScale) {}

    @SubscribeEvent
    public static void reload(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "material_catalog"),
                (ResourceManagerReloadListener) resources -> {
                    if (dock != null) dock.closeAll();
                    if (catalog != null) catalog.refreshResources();
                    if (text != null) text.invalidateSamples();
                    if (liveActivities != null) liveActivities.settings(LiveSurfaceConfig.load(
                            Minecraft.getInstance().gameDirectory.toPath().resolve("config")));
                });
    }

    @SubscribeEvent
    public static void stop(ClientStoppingEvent event) {
        var kernel = host;
        if (liveActivities != null) liveActivities.close();
        liveActivities = null;
        musicActivity = null;
        if (dock != null) dock.closeAll();
        dock = null;
        host = null;
        if (frameOpen && kernel != null) kernel.finishNativeFrame();
        frameOpen = false;
        if (samples != null) samples.invalidate();
        samples = null;
        screens = null;
        text = null;
        hud = null;
        catalog = null;
        try { if (kernel != null) kernel.close(); }
        finally { com.dishanhai.sh_better_ui.client.music.ClientMusic.close(); }
    }

    private static List<NativeModuleSources.Source> modSources() {
        var loader = FMLLoader.getCurrent();
        return ModList.get().getModFiles().stream()
                .map(info -> info.getFile())
                .filter(file -> file.getContents().containsFile(MODULE_DESCRIPTOR))
                .map(file -> new NativeModuleSources.Source(file.getId(), file.getFilePath() + "!" + MODULE_DESCRIPTOR,
                        file.getModInfos().stream().map(mod -> mod.getModId()).collect(Collectors.toUnmodifiableSet()),
                        () -> file.getContents().openFile(MODULE_DESCRIPTOR), name -> {
                            var type = Class.forName(name, false, loader.getCurrentClassLoader());
                            return loader.getModFileByClass(type) == file ? type : null;
                        }))
                .toList();
    }

}
