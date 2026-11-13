// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.audio.ClientMusicPulse;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.LiveSurfaceConfig;
import com.dishanhai.sh_better_ui.client.spatial.SpatialRenderer;
import com.dishanhai.sh_better_ui.client.system.ClientComputerStatus;
import com.dishanhai.sh_better_ui.spatial.internal.Scene;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.kernel.api.ConfigLayer;
import com.dishanhai.sh_better_ui.kernel.api.ConfigScope;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.EventResult;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.BackgroundSample;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.kernel.api.EngineState;
import com.dishanhai.sh_better_ui.kernel.api.ConflictPolicy;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import com.dishanhai.sh_better_ui.kernel.api.OwnershipLease;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ResourceRequest;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.ServiceDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UIPhase;
import com.dishanhai.sh_better_ui.kernel.api.UIDirtyFlag;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceGeometry;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePresentation;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Physical client setup and shutdown shared by the two loaders. */
public final class ClientUiKernel implements AutoCloseable {
    private static final ModuleDescriptor.ResourceClaim LEGACY_POINTER = new ModuleDescriptor.ResourceClaim(
            ModuleDescriptor.ResourceKind.POINTER_CAPTURE, UiId.parse("sh_better_ui:native/pointer"));
    private static volatile ClientUiKernel active;
    private final KernelHost host;
    private final ModuleContext theme;
    private final ClientAnimation animation;
    private final ClientCollapse collapse;
    private final ClientDockWindows dock;
    private final ClientSpatial spatial;
    private final ClientLayout layout;
    private final ClientInput input;
    private final ClientScreens screens;
    private final ClientFocus focus;
    private final ClientCollision collision;
    private final ClientRender rendering;
    private final ClientTextReadability textReadability;
    private final ClientHud hud;
    private final ClientResources resources;
    private final ClientMaterialCatalog materialCatalog;
    private final ClientCorrectness correctness;
    private final LiveSurfaceRuntime live;
    private final ClientLiveInput liveInput;
    private final ClientLiveInput.DetailsShortcut liveShortcut = new ClientLiveInput.DetailsShortcut();
    private final ClientLivePresentation livePresentation;
    private final ClientLiveNetwork liveNetwork;
    private final ClientPages pages;
    private final ClientPageNetwork pageNetwork;
    private final ClientLiveGamepad liveGamepad = new ClientLiveGamepad();
    private final ConfigScope userGlobal;
    private boolean pendingLegacySettings;
    private LiveSurfaceSettings liveSettings, publishedLiveSettings;
    private long nextPage;

    private ClientUiKernel(KernelHost host, ModuleContext theme, ClientAnimation animation, ClientLayout layout,
                           ClientInput input, ClientScreens screens, ClientFocus focus, ClientCollision collision,
                           ClientRender rendering, ClientResources resources, ClientSpatial spatial, ClientCorrectness correctness,
                           BuiltinLiveSurface surface, LiveSurfaceSettings liveSettings, ClientMaterialCatalog materialCatalog,
                           ClientCollapse collapse, ClientDockWindows dock, ClientPages pages, ClientHud hud,
                           ClientTextReadability textReadability, boolean pendingLegacySettings) {
        this.host = host;
        this.theme = theme;
        this.animation = animation;
        this.collapse = collapse;
        this.dock = dock;
        this.spatial = spatial;
        this.layout = layout;
        this.input = input;
        this.screens = screens;
        this.focus = focus;
        this.collision = collision;
        this.rendering = rendering;
        this.textReadability = textReadability;
        this.hud = hud;
        this.resources = resources;
        this.materialCatalog = materialCatalog;
        this.correctness = correctness;
        live = surface.runtime;
        liveInput = surface.input;
        livePresentation = surface.presentation;
        liveNetwork = surface.network;
        this.pages = pages;
        pageNetwork = new ClientPageNetwork(pages);
        this.liveSettings = liveSettings;
        this.pendingLegacySettings = pendingLegacySettings;
        userGlobal = new ConfigScope(host.rootScope(), ConfigLayer.USER_GLOBAL);
    }

    public static void start(ModuleDescriptor.Platform platform) {
        start(platform, List.of());
    }

    public static void start(ModuleDescriptor.Platform platform, List<ModFile> modFiles) {
        if (active == null) {
            var config = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
            var settings = LiveSurfaceConfig.load(config);
            if (!settings.kernelEnabled()) return;
            var sources = modFiles.stream().filter(file -> java.nio.file.Files.isRegularFile(file.descriptor()))
                    .map(file -> NativeModuleSources.modulePath(file.descriptor(), file.moduleName(), file.modIds())).toList();
            active = install(platform, EnginePublication.CLIENT, MinecraftThemes.manager(), MinecraftThemes::shutdown,
                    System::nanoTime, NativeModuleSources.discover(sources), settings,
                    config.resolve("sh_better_ui/modules"));
            active.dock.configure(config);
        }
    }

    /** CPU-only compatibility pump. Native render work waits for the loader's GUI callback. */
    public static void frame() {
        if (!hasNativeAuthority()) return;
        var client = active;
        if (!client.host.canBeginNativeFrame()) return;
        client.publishSettings();
        client.host.beginNativeFrame(() -> {}, client::phase);
        client.host.finishNativeFrame();
    }

    public static OwnershipLease ownNativeGpu(ModuleDescriptor.ResourceKind kind, UiId id, Runnable release) {
        RenderSystem.assertOnRenderThread();
        if (kind != ModuleDescriptor.ResourceKind.RENDER_TARGET && kind != ModuleDescriptor.ResourceKind.TEXTURE_RESOURCE) {
            throw new IllegalArgumentException("Not a GPU resource kind: " + kind);
        }
        if (!id.namespace().equals("sh_better_ui")) throw new IllegalArgumentException("Native UI resource namespace");
        Objects.requireNonNull(release);
        var client = active;
        if (client == null || client.host.lifecycle().state() != EngineState.RUNNING) return null;
        return client.host.rootScope().claim(new ModuleDescriptor.ResourceClaim(kind, id), release::run);
    }

    public static boolean hasNativeAuthority() {
        var client = active;
        return client != null && client.host.lifecycle().state() == EngineState.RUNNING;
    }

    /** Saves through the User Global transaction and waits for the existing save result contract. */
    public static boolean saveClientSettings(ClientUiSettings settings) {
        var client = active;
        return client != null && client.host.lifecycle().state() == EngineState.RUNNING && client.saveSettings(settings);
    }

    boolean saveSettings(ClientUiSettings settings) {
        var configs = theme.engine().configs();
        var previous = configs.get(UIApis.CLIENT_SETTINGS);
        try {
            configs.beginTransaction().set(userGlobal, UIApis.CLIENT_SETTINGS, settings).commit();
            configs.flush().toCompletableFuture().join();
            return true;
        } catch (CompletionException failure) {
            configs.beginTransaction().set(userGlobal, UIApis.CLIENT_SETTINGS, previous).commit();
            host.report("Saving global client UI settings", failure.getCause());
            return false;
        } catch (IllegalArgumentException failure) {
            host.report("Validating global client UI settings", failure);
            return false;
        }
    }

    public static boolean beginNativeFrame(Minecraft minecraft, LongConsumer frameClock) {
        if (!hasNativeAuthority()) return false;
        var client = active;
        if (!client.host.canBeginNativeFrame()) return false;
        client.sync(minecraft);
        client.liveGamepad.poll(minecraft);
        client.publishSettings();
        client.livePresentation.beginFrame();
        client.host.beginNativeFrame(() -> {
            client.textReadability.advance();
            frameClock.accept(client.host.nowNanos());
        }, phase -> {
            if (phase == UIPhase.STYLE_RESOLUTION) MinecraftThemes.frame();
            client.phase(phase, minecraft);
        });
        return true;
    }

    public static void renderNative(Screen owner, GuiGraphics graphics, float partialTick) {
        if (!hasNativeAuthority()) return;
        RenderSystem.assertOnRenderThread();
        var minecraft = Minecraft.getInstance();
        if (owner != minecraft.screen || minecraft.getOverlay() != null || owner == null && minecraft.options.hideGui) return;
        var client = active;
        var previous = client.screens.activeScope(owner);
        client.sync(minecraft);
        if (!client.sameNativeSurface(minecraft, owner, previous)) return;
        var screen = client.screens.activeScope(owner);
        if (owner != null && screen == null) return;
        var viewport = client.screens.viewport();
        var surface = owner == null ? RenderService.Surface.HUD : RenderService.Surface.SCREEN;
        var frame = new RenderService.Frame(surface, viewport.width(), viewport.height(), viewport.guiScale(), partialTick);
        client.rendering.render(frame, new GuiCanvas(graphics), () -> client.host.renderNativeFrame(screen, () -> {
                client.rendering.renderMaterialPreview(owner, screen);
                if (owner == null) client.hud.render(graphics, partialTick);
                client.dock.render(client.rendering, owner, frame);
                client.livePresentation.render(minecraft, graphics,
                        new ClientLivePresentation.RenderServiceFrame(frame.width(), frame.height(), frame.guiScale()),
                        client.liveSettings);
                client.correctness.render(screen, graphics);
                if (owner != null) client.textReadability.renderOverlay(graphics);
        }));
    }

    public static void toggleTextReadabilityOverlay(Minecraft minecraft) {
        if (!hasNativeAuthority()) return;
        boolean enabled = active.textReadability.toggleOverlay();
        if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.translatable(enabled
                ? "sh_better_ui.text_readability.debug.enabled" : "sh_better_ui.text_readability.debug.disabled"));
    }

    public static void finishNativeFrame() {
        if (!hasNativeAuthority()) return;
        var client = active;
        try {
            client.host.finishNativeFrame();
        } finally {
            client.textReadability.endFrame();
        }
    }

    public static boolean paintHudGlass(GuiGraphics graphics, GlassDraw surface) {
        var client = active;
        if (client == null || client.host.lifecycle().state() != EngineState.RUNNING) return false;
        client.rendering.paintHudGlass(graphics, surface);
        return true;
    }

    /** Keeps one HUD text palette across layout rebuilds for a stable panel ID. */
    public static TextReadabilityPalette hudTextPalette(UiId id) {
        var client = active;
        return id == null || client == null || client.host.lifecycle().state() != EngineState.RUNNING
                ? null : client.rendering.hudTextPalette(id);
    }

    public static BackgroundSample sampleHudText(GuiGraphics graphics, Rect local) {
        return sampleHudText(graphics, null, local);
    }

    public static BackgroundSample sampleHudText(GuiGraphics graphics, UiId id, Rect local) {
        var client = active;
        return client == null || client.host.lifecycle().state() != EngineState.RUNNING
                ? null : client.rendering.sampleHudText(graphics, id, local);
    }

    public static void tick(Minecraft minecraft) {
        if (hasNativeAuthority()) {
            active.sync(minecraft);
            active.pages.tickPresentation();
            active.pageNetwork.tick();
            com.dishanhai.sh_better_ui.client.visual.ClientRichDocuments.tick(minecraft);
        }
    }

    public static void nativeScoreboard(boolean visible) {
        if (hasNativeAuthority()) active.livePresentation.scoreboard(visible);
    }

    public static void nativeBossBar(int x, int y, int titleWidth) {
        if (hasNativeAuthority()) active.livePresentation.boss(x, y, titleWidth);
    }

    public static void openLiveActivities(Minecraft minecraft) {
        if (!hasNativeAuthority() || !active.liveSettings.layout().enabled() || minecraft.player == null
                || minecraft.screen != null || minecraft.getOverlay() != null) return;
        showLiveActivities(minecraft, 0);
    }

    public static void onLiveActivityKey(Minecraft minecraft, int key, int action, int modifiers) {
        if (!hasNativeAuthority()) return;
        Screen screen = minecraft.screen;
        if (!active.liveSettings.layout().enabled() || minecraft.player == null
                || !minecraft.isWindowActive() || minecraft.getOverlay() != null
                || (screen == null ? minecraft.options.hideGui : !(screen instanceof LiveActivityScreen))) {
            active.liveShortcut.reset();
            return;
        }
        var shortcut = active.liveShortcut.key(key, action, modifiers);
        if (shortcut == ClientLiveInput.DetailsShortcut.Result.NONE) return;
        boolean expand = shortcut == ClientLiveInput.DetailsShortcut.Result.EXPAND;
        if (expand && !active.liveSettings.layout().expanded()) return;

        var visible = active.live.view();
        var primary = visible.plan().primary();
        if (primary == null || !visible.primary().visible() || !active.live.isInputCurrent(primary)) return;
        if (screen == null) {
            showLiveActivities(minecraft, expand ? primary.instance() : 0);
        } else if (expand && active.liveInput.available()) {
            var overlay = UiOverlayHost.current(screen);
            if ((overlay == null || !overlay.isModalOpen()) && UiLocalModal.current(screen) == null) {
                active.live.expand(primary);
            }
        }
    }

    private static void showLiveActivities(Minecraft minecraft, long expandOnOpen) {
        minecraft.setScreen(new LiveActivityScreen(() -> liveActivityPreviewAvailable()
                && active.live.plan().primary() == null, () -> openLiveActivityPreview(minecraft), expandOnOpen));
    }

    public static boolean liveActivityPreviewAvailable() {
        return hasNativeAuthority() && active.liveSettings.layout().enabled();
    }

    public static void openLiveActivityPreview(Minecraft minecraft) {
        if (liveActivityPreviewAvailable() && minecraft.getOverlay() == null) {
            minecraft.setScreen(new LiveActivityScreen(active.theme, minecraft.screen));
        }
    }

    public static void openVisualCompositionExample(Minecraft minecraft) {
        if (hasNativeAuthority()) minecraft.setScreen(
                new com.dishanhai.sh_better_ui.client.gui.panel.VisualCompositionScreen(active.theme, minecraft.screen));
    }

    public static void openDockExample(Minecraft minecraft) {
        if (hasNativeAuthority()) minecraft.setScreen(new DockExampleScreen(active.dock.context(), minecraft.screen));
    }

    public static void openRichTextExample(Minecraft minecraft) {
        if (hasNativeAuthority()) minecraft.setScreen(
                new com.dishanhai.sh_better_ui.client.gui.panel.RichTextExampleScreen(active.theme, minecraft.screen));
    }

    public static void openRichTextDocument(Minecraft minecraft,
            com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument document) {
        if (hasNativeAuthority()) minecraft.setScreen(
                new com.dishanhai.sh_better_ui.client.gui.panel.RichTextExampleScreen(active.theme, minecraft.screen,
                        java.util.Objects.requireNonNull(document, "document")));
    }

    public static void openRichTextPipeline(Minecraft minecraft) {
        if (hasNativeAuthority()) minecraft.setScreen(
                new com.dishanhai.sh_better_ui.client.gui.panel.RichTextPipelineScreen(active.theme, minecraft.screen));
    }

    public static boolean richTextEditorEnabled() {
        return Boolean.parseBoolean(System.getProperty("shb.richText.editor", "true"));
    }

    public static void openRichTextEditor(Minecraft minecraft) {
        if (richTextEditorEnabled() && hasNativeAuthority() && minecraft.getOverlay() == null) minecraft.setScreen(
                new com.dishanhai.sh_better_ui.client.gui.panel.RichTextEditorScreen(active.theme, minecraft.screen));
    }

    /** Loader channels call this after scheduling onto the native game thread. */
    public static void receiveLiveActivity(Object connection, LiveActivityPackets.ServerMessage message,
                                           Consumer<LiveActivityPackets.Action> reply) {
        if (hasNativeAuthority()) active.liveNetwork.receive(connection, message, reply);
    }

    public static void screenInitialized(Screen owner) {
        if (hasNativeAuthority()) {
            active.screens.initialized(owner);
            active.focus.reconcile();
        }
    }

    public static void screenClosing(Screen owner) {
        if (hasNativeAuthority()) active.screens.closing(owner);
    }

    public static void login(Object connection) {
        if (hasNativeAuthority()) {
            active.screens.login(connection);
            active.pageNetwork.login(connection);
        }
    }

    public static void logout(Object connection) {
        if (hasNativeAuthority()) {
            com.dishanhai.sh_better_ui.client.visual.ClientRichDocuments.disconnected(connection);
            active.pageNetwork.logout(connection);
            active.screens.logout(connection);
            active.pages.clearStudioPage();
        }
    }

    public static void receivePageFrame(net.minecraft.network.Connection connection, byte[] frame) {
        if (hasNativeAuthority() && active.screens.acceptsConnection(connection)) {
            active.pageNetwork.receive(connection, frame);
        }
    }

    public static void openPagePermissions(Minecraft minecraft, com.dishanhai.sh_better_ui.kernel.api.page.UiPageType type) {
        if (hasNativeAuthority()) active.pageNetwork.permissions(minecraft, type);
    }

    public static void openPriorityPagePermissions(Minecraft minecraft) {
        if (hasNativeAuthority()) active.pageNetwork.priorityPermissions(minecraft);
    }

    public static void openInteractionPagePermissions(Minecraft minecraft) {
        if (hasNativeAuthority()) active.pageNetwork.interactionPermissions(minecraft);
    }

    public static void observePageInteraction(Minecraft minecraft) {
        if (hasNativeAuthority()) active.pageNetwork.observeInteraction(minecraft);
    }

    public static void requestServerPage(Minecraft minecraft, UiId page) {
        if (!hasNativeAuthority() || minecraft.player == null) return;
        boolean sent = active.pageNetwork.requestPage(page);
        minecraft.player.sendSystemMessage(Component.translatable(sent
                ? "sh_better_ui.pages.request.sent" : "sh_better_ui.pages.request.unavailable", page.toString()));
    }

    public static void showPageTrace(Minecraft minecraft) {
        if (hasNativeAuthority()) active.pageNetwork.showTrace(minecraft);
    }

    public static void openPageStudio(Minecraft minecraft) {
        if (!hasNativeAuthority()) return;
        var client = active;
        minecraft.setScreen(new CorrectnessInspectorScreen(minecraft.screen, () -> {
            var server = minecraft.getCurrentServer();
            var identity = server != null ? Component.literal(server.name)
                    : Component.translatable(minecraft.getSingleplayerServer() == null
                    ? "sh_better_ui.pages.studio.disconnected" : "menu.singleplayer");
            return new PageStudioReport(client.pages.studioPages(), client.pageNetwork.diagnostics(), identity);
        }));
    }

    public static ScopeHandle openScriptPageScope() {
        if (!hasNativeAuthority()) throw new IllegalStateException("Client UI kernel is unavailable");
        return active.pages.openScriptScope();
    }

    public static void registerScriptPage(ScopeHandle scope,
            com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition page) {
        registerScriptPage(scope, page, NativeTextReadability.PageOptions.DEFAULT);
    }

    public static void registerScriptPage(ScopeHandle scope,
            com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition page, NativeTextReadability.PageOptions text) {
        if (!hasNativeAuthority()) throw new IllegalStateException("Client UI kernel is unavailable");
        active.pages.registerScript(scope, page, text);
    }

    public static NativeTextReadability.Mount mountScriptText(com.dishanhai.sh_better_ui.client.api.UiScreen screen) {
        if (!hasNativeAuthority()) throw new IllegalStateException("Client UI kernel is unavailable");
        return active.pages.mountText(screen);
    }

    public static com.dishanhai.sh_better_ui.kernel.api.page.PageService.Instance openScriptPage(ScopeHandle scope,
            UiId id, Map<String, com.dishanhai.sh_better_ui.kernel.api.page.UiValue> data,
            java.util.function.Consumer<UiId> action) {
        if (!hasNativeAuthority()) throw new IllegalStateException("Client UI kernel is unavailable");
        return active.pages.openScript(scope, id, data, action);
    }

    public static boolean updateScriptPage(ScopeHandle scope, java.util.UUID instance,
            Map<String, com.dishanhai.sh_better_ui.kernel.api.page.UiValue> updates) {
        if (!hasNativeAuthority()) throw new IllegalStateException("Client UI kernel is unavailable");
        return active.pages.updateScript(scope, instance, updates);
    }

    public static void windowFocus(Minecraft minecraft) {
        if (hasNativeAuthority() && active.host.isOnUIThread()) active.sync(minecraft);
    }

    public static boolean nativeInput(Screen owner, NativeInput event) {
        if (!hasNativeAuthority()) return false;
        var minecraft = Minecraft.getInstance();
        if (owner == null || owner != minecraft.screen || minecraft.getOverlay() != null
                || !minecraft.isWindowActive()) return false;
        var client = active;
        if (!client.host.canRouteNativeInput()) return false;
        var previous = client.screens.activeScope(owner);
        client.sync(minecraft);
        return client.sameNativeSurface(minecraft, owner, previous) && minecraft.isWindowActive()
                && client.dispatch(client.screens.activeScope(owner), event).blockVanilla();
    }

    public static void pointerMoved(long windowHandle, double x, double y) {
        if (!hasNativeAuthority()) return;
        var client = active;
        var minecraft = Minecraft.getInstance();
        if (!client.input.hasTargets(client.screens.activeScope()) || minecraft.screen == null) return;
        var window = minecraft.getWindow();
        if (window.getWindow() != windowHandle) return;
        nativeInput(minecraft.screen, new NativeInput.Pointer(NativeInput.PointerAction.MOVE,
                x * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth()),
                y * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight()), -1, 0, 0));
    }

    public static OwnershipLease ownNativeModal(Screen owner, Runnable release) {
        Objects.requireNonNull(release);
        if (!hasNativeAuthority() || Minecraft.getInstance().screen != owner) return null;
        var client = active;
        client.host.assertOwner();
        var scope = client.screens.activeScope(owner);
        if (scope == null || !scope.isOpen()) return null;
        return scope.claim(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.MODAL_LAYER,
                UiId.parse("sh_better_ui:native/modal")), release::run);
    }

    public static OwnershipLease claimNativePointer(Screen owner, ModuleContext module, ScopeHandle node,
                                                    UiId claimId, Runnable revoke) {
        if (!hasNativeAuthority()) return null;
        var client = active;
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen != owner) return null;
        var previous = client.screens.activeScope(owner);
        client.sync(minecraft);
        if (!client.sameNativeSurface(minecraft, owner, previous) || !minecraft.isWindowActive()) return null;
        var screen = client.screens.activeScope(owner);
        if (screen == null) return null;
        if (module == null) {
            return client.host.pointerAvailable(screen, LEGACY_POINTER) ? screen.claim(LEGACY_POINTER, revoke::run) : null;
        }
        var target = client.host.ownedNode(module, node);
        if (client.screens.ownerOf(target) != owner) return null;
        var access = client.host.moduleAccess(module);
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.POINTER_CAPTURE, claimId);
        access.requireClaim(claim);
        access.require(UIPermission.CAPTURE_INPUT, target);
        access.require(UIPermission.BLOCK_VANILLA_INPUT, target);
        return client.host.pointerAvailable(target, claim) ? client.host.acquireNativePointer(access,
                new ResourceRequest(claim, target, 0, 0, ConflictPolicy.REJECT), revoke::run) : null;
    }

    public static void nativeCaptureFailed(ModuleContext module, String action, Throwable failure) {
        var client = active;
        if (client != null) client.host.report("UI pointer " + action + " for " + module.descriptor().id(), failure);
    }

    public static PreparableReloadListener reloadListener(Runnable apply) {
        return (barrier, manager, preparationProfiler, reloadProfiler, prepareExecutor, reloadExecutor) -> {
            var client = active;
            var themes = MinecraftThemes.prepareReload(manager, prepareExecutor);
            var data = client == null
                    ? java.util.concurrent.CompletableFuture.<ClientResources.Prepared>completedFuture(null)
                    : client.resources.prepare(manager, prepareExecutor);
            record Batch(MinecraftThemes.PreparedReload themes, ClientResources.Prepared data) {}
            return themes.thenCombine(data, Batch::new).thenCompose(barrier::wait).thenAcceptAsync(ready -> {
                var running = active;
                if (client != null && running != client) return;
                Runnable install = () -> {
                    if (!MinecraftThemes.commitReload(ready.themes())) {
                        throw new IllegalStateException("UI theme reload was superseded or stopped");
                    }
                    if (running == null) apply.run();
                    else running.reload(apply);
                };
                if (running == null) install.run();
                else if (client == null) running.resources.commit(running.resources.begin(), install);
                else running.resources.commit(ready.data(), install);
            }, Minecraft.getInstance());
        };
    }

    public static void stop() {
        try {
            var minecraft = Minecraft.getInstance();
            if (minecraft != null) removeActivePage(minecraft.screen);
        } finally {
            ClientMusicPulse.global().close();
            var client = active;
            if (client != null) {
                try {
                    client.close();
                } finally {
                    active = null;
                }
            } else {
                MinecraftThemes.shutdown();
            }
        }
    }

    /** The native client can render one final frame after its shutdown event. */
    static void removeActivePage(Screen screen) {
        if (screen instanceof UiScreen page) page.removed();
    }

    /** The opt-in UI context closes this handle on removal. Shutdown also closes any remaining pages. */
    public static AutoCloseable ownPageTheme(ThemeScope scope) {
        if (active == null) return scope;
        return active.attachPageTheme(scope);
    }

    ScopeHandle attachPageTheme(ThemeScope scope) {
        var page = theme.scope().openChild(ScopeKind.SCREEN, new UiId("sh_better_ui", "theme_page/" + ++nextPage));
        page.track(scope);
        return page;
    }

    void runFrame() {
        publishSettings();
        host.runFrame(this::phase);
    }

    EventResult dispatch(ScopeHandle screen, NativeInput event) {
        dock.observe(event);
        liveInput.observe(event);
        return input.dispatch(screen, event);
    }

    ClientScreens screens() { return screens; }

    ClientRender rendering() { return rendering; }

    ClientResources resources() { return resources; }

    void reconcileFocus() { focus.reconcile(); }

    private void sync(Minecraft minecraft) {
        boolean wasFocused = screens.viewport().focused();
        var target = minecraft.getMainRenderTarget();
        if (target != null) {
            textReadability.targetChanged(target, target.getColorTextureId(), target.width, target.height,
                    minecraft.getWindow().getGuiScale());
        }
        screens.sync(minecraft);
        if (wasFocused && !screens.viewport().focused()) {
            UiPointerCapture.windowLost(minecraft.screen);
            focus.windowLost();
        }
        focus.reconcile();
        boolean visible = minecraft.isWindowActive() && minecraft.getOverlay() == null
                && (minecraft.screen != null || !minecraft.options.hideGui);
        var overlay = minecraft.screen == null ? null : UiOverlayHost.current(minecraft.screen);
        visible &= overlay == null || !overlay.isModalOpen();
        if (!visible) liveShortcut.reset();
        dock.sync(minecraft, visible);
        if (!visible) correctness.suspendPresentation();
        liveInput.bind(minecraft.screen, visible && liveSettings.layout().enabled()
                && (live.hasResources() || live.view().primary().visible()),
                liveSettings.input());
        if (!visible && live.view().primary().visible()) {
            live.present(new LiveSurfaceService.View(live.plan(), SurfacePresentation.SUSPENDED, live.view().settled(),
                    SurfaceGeometry.HIDDEN, SurfaceGeometry.HIDDEN, false));
        }
    }

    private boolean sameNativeSurface(Minecraft minecraft, Screen owner, ScopeHandle previous) {
        return minecraft.screen == owner && minecraft.getOverlay() == null
                && (previous == null || screens.activeScope(owner) == previous);
    }

    private void phase(UIPhase phase) {
        phase(phase, null);
    }

    private void phase(UIPhase phase, Minecraft minecraft) {
        switch (phase) {
            case STYLE_RESOLUTION -> spatial.resolveStyles();
            case MEASURE -> {
                dock.advance(minecraft);
                liveInput.advance();
                live.advance(liveInput.protectedInteraction(), minecraft != null && liveInput.available());
                layout.process(UIDirtyFlag.MEASURE);
            }
            case LAYOUT -> {
                layout.process(UIDirtyFlag.LAYOUT);
                if (minecraft != null) livePresentation.measure(minecraft, liveSettings, resources.version());
            }
            case ANIMATION -> {
                animation.update();
                collapse.update();
                spatial.update();
                if (minecraft != null) livePresentation.animate(liveSettings);
            }
            case GEOMETRY_UPDATE -> {
                collision.update();
                dock.prepare(minecraft);
            }
            case RENDER_PREPARATION -> {
                var viewport = screens.viewport();
                var areas = minecraft == null ? List.<com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot.ReservedArea>of()
                        : livePresentation.correctnessAreas(minecraft, viewport.width(), viewport.height(), correctness.frameId() + 1);
                correctness.prepare(viewport, areas, resources.version(),
                        theme.engine().configs().get(com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessEngine.SETTINGS));
            }
            default -> {}
        }
    }

    private void publishSettings() {
        var configs = theme.engine().configs();
        if (pendingLegacySettings) pendingLegacySettings = !importLegacySettings(host, theme);
        if (!liveSettings.equals(publishedLiveSettings)) {
            configs.beginTransaction().set(userGlobal, UIApis.LIVE_SURFACE_SETTINGS, liveSettings).commit();
            publishedLiveSettings = liveSettings;
        }
    }

    private static boolean importLegacySettings(KernelHost host, ModuleContext context) {
        if (host.hasUserConfig(UIApis.CLIENT_SETTINGS)) return true;
        var legacy = ClientUiSettings.legacySnapshot();
        if (legacy == null) return false;
        var configs = context.engine().configs();
        configs.beginTransaction().set(configs.userGlobal(), UIApis.CLIENT_SETTINGS, legacy).commit();
        return true;
    }

    private void reload(Runnable apply) {
        com.dishanhai.sh_better_ui.client.visual.VisualFrameCoordinator.end();
        textReadability.invalidateTarget();
        TextReadabilityPainter.releaseGpuResources();
        var next = LiveSurfaceConfig.load(Minecraft.getInstance().gameDirectory.toPath().resolve("config"));
        liveSettings = next;
        live.policy(policy(next));
        liveInput.resetPresentation();
        rendering.reload();
        dock.reload();
        correctness.reset();
        apply.run();
        materialCatalog.refreshResources();
    }

    @Override
    public void close() {
        screens.clear();
        try {
            host.close();
        } finally {
            textReadability.invalidateTarget();
            textReadability.endFrame();
            live.clearPresentation();
        }
    }

    static ClientUiKernel install(ModuleDescriptor.Platform platform, EnginePublication publication,
                                  ThemeManager manager, AutoCloseable release) {
        return install(platform, publication, manager, release, System::nanoTime, List.of());
    }

    static ClientUiKernel install(ModuleDescriptor.Platform platform, EnginePublication publication,
                                  ThemeManager manager, AutoCloseable release, LongSupplier clock,
                                  List<ModuleDiscovery.Candidate> extensions) {
        return install(platform, publication, manager, release, clock, extensions, LiveSurfaceSettings.DEFAULT, null);
    }

    static ClientUiKernel install(ModuleDescriptor.Platform platform, EnginePublication publication,
                                          ThemeManager manager, AutoCloseable release, LongSupplier clock,
                                          List<ModuleDiscovery.Candidate> extensions, LiveSurfaceSettings liveSettings,
                                          Path storage) {
        var kernel = new KernelHost(platform, publication, clock, null, SchedulerBudget.DEFAULT);
        if (storage != null) kernel.storage(storage);
        var module = new BuiltinTheme(kernel, manager, release, storage != null);
        var animation = new ClientAnimation(kernel);
        var screens = new ClientScreens(kernel, platform);
        var layout = new ClientLayout(kernel, screens);
        var input = new ClientInput(kernel, screens);
        var collapse = new ClientCollapse(kernel, animation);
        var dock = new ClientDockWindows(kernel, screens, input, layout, collapse);
        var focus = new ClientFocus(kernel, screens, input);
        var collision = new ClientCollision(kernel, input);
        var spatial = new ClientSpatial(kernel, manager);
        var glass = new ClientGlassRender(kernel);
        var correctness = new ClientCorrectness(kernel, manager, glass);
        var rendering = new ClientRender(kernel, screens, spatial, glass);
        var textReadability = new ClientTextReadability(kernel, screens);
        var hud = new ClientHud(kernel);
        var resources = new ClientResources(kernel);
        var materialCatalog = new ClientMaterialCatalog(kernel, platform, asset -> {
            var minecraft = Minecraft.getInstance();
            return minecraft != null && minecraft.getResourceManager()
                    .getResource(net.minecraft.resources.ResourceLocation.tryParse(asset)).isPresent();
        });
        var live = new BuiltinLiveSurface(kernel, screens, input, spatial, liveSettings);
        var pages = new ClientPages(kernel, screens, live.runtime);
        var candidates = new ArrayList<ModuleDiscovery.Candidate>();
        candidates.add(new ModuleDiscovery.Candidate(UIApis.THEME.id().namespace(),
                BuiltinTheme.class.getName(), module, Set.of(UIPermission.MODIFY_GLOBAL_CONFIG), true));
        candidates.add(BuiltinService.candidate(UIApis.ANIMATION, animation, UIApis.SCOPED_ANIMATION));
        candidates.add(BuiltinService.candidate(UIApis.LAYOUT, layout, UIApis.INCREMENTAL_LAYOUT));
        candidates.add(BuiltinService.candidate(UIApis.INPUT, input, UIApis.INPUT_ROUTING));
        candidates.add(BuiltinService.candidate(UIApis.FOCUS, focus, UIApis.SCREEN_FOCUS));
        candidates.add(BuiltinService.candidate(UIApis.COLLISION, collision, UIApis.PRESENTED_HIT_TEST));
        candidates.add(BuiltinService.candidate(UIApis.RENDER, rendering, UIApis.NATIVE_RENDER));
        candidates.add(BuiltinService.candidate(UIApis.TEXT_READABILITY, textReadability, UIApis.TEXT_READABILITY_POLICY));
        candidates.add(BuiltinService.candidate(UIApis.HUD, hud, UIApis.NATIVE_HUD));
        candidates.add(BuiltinService.candidate(UIApis.GLASS, glass));
        candidates.add(BuiltinService.candidate(UIApis.MATERIAL_CATALOG, materialCatalog, UIApis.MATERIAL_DISCOVERY));
        candidates.add(BuiltinService.candidate(UIApis.CORRECTNESS, correctness, UIApis.UI_CORRECTNESS));
        candidates.add(BuiltinService.candidate(UIApis.SPATIAL, spatial, UIApis.SPATIAL_SCENE));
        candidates.add(BuiltinService.candidate(UIApis.RESOURCES, resources, UIApis.RELOAD_GENERATIONS));
        candidates.add(BuiltinService.candidate(com.dishanhai.sh_better_ui.kernel.api.project.ProjectEdits.KEY,
                new ProjectEditsRuntime(kernel)));
        candidates.add(BuiltinService.candidate(UIApis.ACCESSIBILITY, new ClientAccessibility(kernel),
                UIApis.MINECRAFT_NARRATION));
        candidates.add(BuiltinService.candidate(UIApis.PLATFORM, screens));
        candidates.add(BuiltinService.candidate(UIApis.COMPUTER_STATUS, ClientComputerStatus.INSTANCE,
                UIApis.COMPUTER_STATUS_READ));
        candidates.add(BuiltinService.candidate(UIApis.SCREENS, screens, UIApis.NATIVE_SCREEN));
        candidates.add(new ModuleDiscovery.Candidate("sh_better_ui", ClientPages.class.getName(),
                pages, Set.of(), false, true));
        candidates.add(BuiltinService.candidate(UIApis.APPEARANCE,
                com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffectEditor::new));
        candidates.add(new ModuleDiscovery.Candidate(UIApis.LIVE_SURFACES.id().namespace(),
                BuiltinLiveSurface.class.getName(), live, BuiltinLiveSurface.GRANTS, false, true));
        candidates.addAll(extensions);
        candidates.add(dock.candidate());
        kernel.startNative(candidates);
        var client = new ClientUiKernel(kernel, module.context, animation, layout, input, screens, focus, collision,
                rendering, resources, spatial, correctness, live, liveSettings, materialCatalog, collapse, dock, pages, hud,
                textReadability, module.pendingLegacySettings);
        try {
            client.publishSettings();
            return client;
        } catch (RuntimeException | LinkageError failure) {
            kernel.close();
            throw failure;
        }
    }

    private static LiveSurfaceRuntime.Policy policy(LiveSurfaceSettings settings) {
        var layout = settings.layout();
        var notifications = settings.notifications();
        return new LiveSurfaceRuntime.Policy(layout.enabled(), layout.compact(), layout.expanded(), layout.split(),
                notifications.enabled(), notifications.deduplicate(), notifications.quiet(), layout.maxActivities(),
                notifications.maxNotifications(), TimeUnit.MILLISECONDS.toNanos(notifications.defaultDurationMs()),
                notifications.priorities());
    }

    private static final class BuiltinLiveSurface implements UIModule {
        private static final UiVersion VERSION = new UiVersion(1, 0, 0);
        private static final Set<UIPermission> GRANTS = Set.of(UIPermission.CAPTURE_INPUT, UIPermission.BLOCK_VANILLA_INPUT,
                UIPermission.PUBLISH_CRITICAL_ACTIVITY);
        private final LiveSurfaceRuntime runtime;
        private final ClientLiveInput input;
        private final ClientLivePresentation presentation;
        private final ClientLiveNetwork network;

        BuiltinLiveSurface(KernelHost host, ClientScreens screens, ClientInput router, ClientSpatial spatial,
                           LiveSurfaceSettings settings) {
            runtime = new LiveSurfaceRuntime(host);
            runtime.policy(policy(settings));
            input = new ClientLiveInput(runtime, host, screens, router);
            presentation = new ClientLivePresentation(host, spatial, runtime, input);
            network = new ClientLiveNetwork(host, screens, runtime);
        }

        @Override public ModuleDescriptor descriptor() {
            var builder = ModuleDescriptor.builder().id(UIApis.LIVE_SURFACES.id()).version(VERSION)
                    .initializationPhase(ModuleDescriptor.InitializationPhase.SERVICE)
                    .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.POINTER_CAPTURE, ClientLiveInput.POINTER));
            for (var permission : GRANTS) builder.requestPermission(permission);
            for (var key : List.of(UIApis.LIVE_SURFACES, UIApis.ACTIVITIES, UIApis.NOTIFICATIONS)) {
                builder.providedCapability(new CapabilityKey(key.id()), VERSION);
            }
            return builder.build();
        }

        @Override public void register(ModuleContext context) {
            register(context, UIApis.LIVE_SURFACES, runtime);
            register(context, UIApis.ACTIVITIES, runtime);
            register(context, UIApis.NOTIFICATIONS, runtime);
        }

        private static <T extends UIService> void register(ModuleContext context, ServiceKey<T> key, T service) {
            context.services().register(new ServiceDescriptor<>(key, VERSION, ServiceDescriptor.Visibility.PUBLIC, 0, false), service);
        }

        @Override public void start(ModuleContext context) {
            input.initialize(context);
            presentation.initialize(context);
            network.initialize(context);
        }

        @Override public void stop(ModuleContext context) {}
    }

    /** Descriptor path and identities supplied by the physical loader. */
    public record ModFile(Path descriptor, String moduleName, Set<String> modIds) {
        public ModFile {
            Objects.requireNonNull(descriptor);
            Objects.requireNonNull(moduleName);
            modIds = Set.copyOf(modIds);
        }
    }

    private record GuiCanvas(GuiGraphics graphics) implements ClientRender.Backend {
        @Override public void push() { graphics.pose().pushPose(); }
        @Override public void pop() { graphics.pose().popPose(); }
        @Override public void translate(double x, double y, double z) { graphics.pose().translate(x, y, z); }
        @Override public void scale(float factor) { graphics.pose().scale(factor, factor, 1); }
        @Override public void clip(int x, int y, int right, int bottom) { graphics.enableScissor(x, y, right, bottom); }
        @Override public void unclip() { graphics.disableScissor(); }
        @Override public void fill(int x, int y, int right, int bottom, int color) { graphics.fill(x, y, right, bottom, color); }
        @Override public void glass(ClientGlassRender glass, List<GlassDraw> surfaces) {
            glass.paint(graphics, surfaces);
        }
        @Override public void text(Component label, int x, int y, int color) {
            graphics.drawString(Minecraft.getInstance().font, label, x, y, color, false);
        }
        @Override public void spatial(Scene scene, RenderService.Frame frame, Consumer<SpatialScene.Node> content) {
            SpatialRenderer.draw(graphics, scene, frame.width(), frame.height(), frame.guiScale(), content);
        }
    }

    private static final class BuiltinTheme implements UIModule {
        private static final UiVersion VERSION = new UiVersion(1, 0, 0);
        private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder().id(UIApis.THEME.id()).version(VERSION)
                .initializationPhase(ModuleDescriptor.InitializationPhase.CORE)
                .providedCapability(UIApis.DYNAMIC_THEME, VERSION)
                .requestPermission(UIPermission.MODIFY_GLOBAL_CONFIG)
                .build();
        private final ThemeManager manager;
        private final KernelHost host;
        private final AutoCloseable release;
        private final boolean persistent;
        private ModuleContext context;
        private boolean pendingLegacySettings;

        private BuiltinTheme(KernelHost host, ThemeManager manager, AutoCloseable release, boolean persistent) {
            this.host = host;
            this.manager = manager;
            this.release = release;
            this.persistent = persistent;
        }

        @Override
        public ModuleDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public void register(ModuleContext context) {
            context.scope().track(release);
            context.services().register(new ServiceDescriptor<>(UIApis.THEME, VERSION,
                    ServiceDescriptor.Visibility.PUBLIC, 0, false), new ClientTheme(host, manager, context.scope()));
        }

        @Override
        public void start(ModuleContext context) {
            var configs = context.engine().configs();
            configs.define(UIApis.CLIENT_SETTINGS);
            configs.define(UIApis.LIVE_SURFACE_SETTINGS);
            configs.define(com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessEngine.SETTINGS);
            if (persistent) context.scope().track(configs.persist(UIApis.CLIENT_SETTINGS, ClientSettingsCodec.CODEC));
            pendingLegacySettings = !importLegacySettings(host, context);
            this.context = context;
        }

        @Override
        public void stop(ModuleContext context) {}
    }

}
