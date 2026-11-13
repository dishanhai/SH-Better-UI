// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.config.LiveSurfaceConfig;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIPlatform;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.project.ProjectEdits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
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
    private static boolean frameOpen;

    private ClientUiKernel() {}

    @SubscribeEvent
    public static void start(ClientStartedEvent event) {
        var minecraft = event.getClient();
        Path config = minecraft.gameDirectory.toPath().resolve("config");
        if (!LiveSurfaceConfig.load(config).kernelEnabled()) return;

        var kernel = new KernelHost(PLATFORM, EnginePublication.CLIENT);
        kernel.storage(config.resolve("sh_better_ui/modules"));
        var clientScreens = new ClientScreens(kernel, PLATFORM);
        var readbacks = new ClientReadabilitySamples(kernel);
        var textReadability = new ClientTextReadability(kernel, clientScreens, readbacks);
        var clientHud = new ClientHud(kernel, readbacks);
        var materialCatalog = new ClientMaterialCatalog(kernel, PLATFORM, asset -> {
            var id = Identifier.tryParse(asset);
            return id != null && minecraft.getResourceManager().getResource(id).isPresent();
        });
        var candidates = new ArrayList<>(NativeModuleSources.discover(modSources()));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIPlatform.KEY, clientScreens));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.SCREENS, clientScreens, UIApis.NATIVE_SCREEN));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.TEXT_READABILITY, textReadability, UIApis.TEXT_READABILITY_POLICY));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIApis.HUD, clientHud, UIApis.NATIVE_HUD));
        candidates.add(BuiltinService.candidate(PLATFORMS, MaterialCatalog.KEY, materialCatalog,
                new CapabilityKey(MaterialCatalog.KEY.id())));
        candidates.add(BuiltinService.candidate(PLATFORMS, ProjectEdits.KEY, new ProjectEditsRuntime(kernel)));
        kernel.startNative(candidates);
        host = kernel;
        catalog = materialCatalog;
        screens = clientScreens;
        samples = readbacks;
        text = textReadability;
        hud = clientHud;
        screens.sync(minecraft);
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
        host.beginNativeFrame(() -> {}, phase -> {});
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
        }
    }

    @SubscribeEvent
    public static void screenClosed(ScreenEvent.Closing event) {
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
        }
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
                    if (catalog != null) catalog.refreshResources();
                    if (text != null) text.invalidateSamples();
                });
    }

    @SubscribeEvent
    public static void stop(ClientStoppingEvent event) {
        var kernel = host;
        host = null;
        if (frameOpen && kernel != null) kernel.finishNativeFrame();
        frameOpen = false;
        if (samples != null) samples.invalidate();
        samples = null;
        screens = null;
        text = null;
        hud = null;
        catalog = null;
        if (kernel != null) kernel.close();
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
