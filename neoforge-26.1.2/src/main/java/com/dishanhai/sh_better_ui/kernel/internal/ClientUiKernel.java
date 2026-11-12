// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.config.LiveSurfaceConfig;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UIPlatform;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.project.ProjectEdits;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
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
    private static ClientPlatform platform;

    private ClientUiKernel() {}

    @SubscribeEvent
    public static void start(ClientStartedEvent event) {
        var minecraft = event.getClient();
        Path config = minecraft.gameDirectory.toPath().resolve("config");
        if (!LiveSurfaceConfig.load(config).kernelEnabled()) return;

        var kernel = new KernelHost(PLATFORM, EnginePublication.CLIENT);
        kernel.storage(config.resolve("sh_better_ui/modules"));
        var clientPlatform = new ClientPlatform(minecraft);
        var materialCatalog = new ClientMaterialCatalog(kernel, PLATFORM, asset -> {
            var id = Identifier.tryParse(asset);
            return id != null && minecraft.getResourceManager().getResource(id).isPresent();
        });
        var candidates = new ArrayList<>(NativeModuleSources.discover(modSources()));
        candidates.add(BuiltinService.candidate(PLATFORMS, UIPlatform.KEY, clientPlatform));
        candidates.add(BuiltinService.candidate(PLATFORMS, MaterialCatalog.KEY, materialCatalog,
                new CapabilityKey(MaterialCatalog.KEY.id())));
        candidates.add(BuiltinService.candidate(PLATFORMS, ProjectEdits.KEY, new ProjectEditsRuntime(kernel)));
        kernel.startNative(candidates);
        host = kernel;
        catalog = materialCatalog;
        platform = clientPlatform;
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        if (host == null) return;
        platform.update();
        // Only the platform-independent scheduler phases are attached in this port.
        host.beginNativeFrame(() -> {}, phase -> {});
        host.finishNativeFrame();
    }

    @SubscribeEvent
    public static void reload(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "material_catalog"),
                (ResourceManagerReloadListener) resources -> {
                    if (catalog != null) catalog.refreshResources();
                });
    }

    @SubscribeEvent
    public static void stop(ClientStoppingEvent event) {
        var kernel = host;
        host = null;
        platform = null;
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

    private static final class ClientPlatform implements UIPlatform {
        private final Minecraft minecraft;
        private volatile Viewport viewport;
        private volatile boolean clientSession;

        ClientPlatform(Minecraft minecraft) {
            this.minecraft = minecraft;
            update();
        }

        void update() {
            var window = minecraft.getWindow();
            var previous = viewport;
            int width = window.getGuiScaledWidth();
            int height = window.getGuiScaledHeight();
            int scale = window.getGuiScale();
            boolean focused = minecraft.isWindowActive();
            if (previous == null || previous.width() != width || previous.height() != height
                    || previous.guiScale() != scale || previous.focused() != focused) {
                viewport = new Viewport(width, height, scale, focused);
            }
            clientSession = minecraft.level != null && minecraft.player != null;
        }

        @Override public ModuleDescriptor.Platform info() { return PLATFORM; }
        @Override public Viewport viewport() { return viewport; }
        @Override public boolean hasClientSession() { return clientSession; }
        @Override public boolean isOnUIThread() { return minecraft.isSameThread(); }
    }
}
