package com.dishanhai.sh_better_ui.client;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
import com.dishanhai.sh_better_ui.client.lyrics.LyricClient;
import com.dishanhai.sh_better_ui.client.music.ClientMusic;
import com.dishanhai.sh_better_ui.client.spatial.SpatialRenderer;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import com.dishanhai.sh_better_ui.client.lyrics.render.LyricView;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** 山海：整個進程只註冊一條共用後處理管線；消費端僅提供畫面 predicates。 */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class UiClient {
    private static KeyMapping controlPanelKey;
    private static KeyMapping liveActivitiesKey;
    private static KeyMapping musicIslandKey;
    private static KeyMapping musicPlayerKey;
    private static final com.dishanhai.sh_better_ui.client.music.MusicOpenShortcut musicShortcut =
            new com.dishanhai.sh_better_ui.client.music.MusicOpenShortcut();

    private static boolean musicShortcutAllowed(Minecraft client) {
        return client.player != null && client.level != null && client.isWindowActive()
                && client.screen == null && client.getOverlay() == null;
    }

    private static void offerMusicShortcut(InputConstants.Key key, int action) {
        if (musicPlayerKey == null || musicPlayerKey.isUnbound()) return;
        musicShortcut.offer(action == GLFW.GLFW_PRESS, musicPlayerKey.isActiveAndMatches(key),
                musicShortcutAllowed(Minecraft.getInstance()));
    }

    @SubscribeEvent
    public static void onMusicShortcutMousePre(InputEvent.MouseButton.Pre event) {
        var key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        musicShortcut.prepareMouse(event.getAction() == GLFW.GLFW_PRESS,
                musicPlayerKey != null && !musicPlayerKey.isUnbound() && musicPlayerKey.isActiveAndMatches(key),
                musicShortcutAllowed(Minecraft.getInstance()));
    }

    @SubscribeEvent
    public static void onMusicShortcutMousePost(InputEvent.MouseButton.Post event) {
        musicShortcut.finishMouse(musicShortcutAllowed(Minecraft.getInstance()));
    }


    private UiClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientUiKernel.tick(minecraft);
        ClientUiKernel.nativeScoreboard(NeoForgePlatformBridge.hasSidebar(minecraft));
        while (liveActivitiesKey != null && liveActivitiesKey.consumeClick()) {
            ClientUiKernel.openLiveActivities(minecraft);
        }
        while (musicIslandKey != null && musicIslandKey.consumeClick()) {
            com.dishanhai.sh_better_ui.kernel.internal.NativeMusicOverlay.toggleIsland();
        }
        if (musicShortcut.take(musicShortcutAllowed(minecraft))) {
            minecraft.setScreen(com.dishanhai.sh_better_ui.client.api.ShBetterUiApi.settings().onlineMusicScreen(null));
        }
        MinecraftThemes.tick(minecraft);
        LyricClient.tick(minecraft);
        UiLocalModal.current(minecraft.screen);
        if (controlPanelKey == null) return;
        while (controlPanelKey.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null && minecraft.getOverlay() == null) {
                minecraft.setScreen(createControlPanel(null));
            }
        }
    }

    @SubscribeEvent
    public static void onLiveActivityKey(InputEvent.Key event) {
        offerMusicShortcut(InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction());
        ClientUiKernel.onLiveActivityKey(Minecraft.getInstance(), event.getKey(), event.getAction(), event.getModifiers());
    }

    @SubscribeEvent
    public static void onLyricCommands(RegisterClientCommandsEvent event) {
        LyricClient.registerCommands(event.getDispatcher());
        NativeClientBridge.registerVisualCommand(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPageInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            ClientUiKernel.observePageInteraction(Minecraft.getInstance());
        }
    }

    private static Screen createControlPanel(Screen parent) {
        return new ControlPanelScreen(parent, controlPanelKey, ShBetterUi::saveClientUiSettings);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        NativeClientBridge.frameStart();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        NativeClientBridge.frameEnd();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        NativeClientBridge.beginScreen(event.getScreen(), event.getGuiGraphics(), event.getPartialTick());
        UiClientRendering.prepareScreenOverlays(event.getScreen());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        NativeClientBridge.composeScreenPost(event.getScreen(), event.getGuiGraphics(),
                () -> GaussBlur.onScreenRenderPost(event.getScreen(), event.getGuiGraphics(), event.getPartialTick()));
        UiClientRendering.renderScreenOverlays(event.getScreen(), event.getGuiGraphics(), event.getMouseX(),
                event.getMouseY(), event.getPartialTick());
        NativeClientBridge.render(event.getScreen(), event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        var input = new NativeInput.Pointer(NativeInput.PointerAction.PRESS, event.getMouseX(), event.getMouseY(),
                event.getButton(), 0, 0);
        if (NativeClientBridge.input(event.getScreen(), input)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        var input = new NativeInput.Pointer(NativeInput.PointerAction.RELEASE, event.getMouseX(), event.getMouseY(),
                event.getButton(), 0, 0);
        if (NativeClientBridge.input(event.getScreen(), input)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        var input = new NativeInput.Pointer(NativeInput.PointerAction.DRAG, event.getMouseX(), event.getMouseY(),
                event.getMouseButton(), event.getDragX(), event.getDragY());
        if (NativeClientBridge.input(event.getScreen(), input)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (NeoForgePlatformBridge.scroll(event)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        var input = NativeClientBridge.pressedKey(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        if (NativeClientBridge.input(event.getScreen(), input)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        var input = new NativeInput.Key(NativeInput.KeyAction.RELEASE,
                event.getKeyCode(), event.getScanCode(), event.getModifiers());
        if (NativeClientBridge.input(event.getScreen(), input)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        var input = new NativeInput.Character(event.getCodePoint(), event.getModifiers());
        if (NativeClientBridge.input(event.getScreen(), input)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        var client = Minecraft.getInstance();
        UiOverlayHost overlay = UiOverlayHost.current(client.screen);
        UiLocalModal local = UiLocalModal.current(client.screen);
        if ((overlay != null && overlay.isModalOpen())
                || (local != null && client.getLastInputType().isKeyboard() && local.hidesFocusedTooltip())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLiveBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        ClientUiKernel.nativeBossBar(event.getX(), event.getY(),
                Minecraft.getInstance().font.width(event.getBossEvent().getName()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHudRenderPost(RenderGuiEvent.Post event) {
        LyricClient.renderHud(Minecraft.getInstance(), event.getGuiGraphics());
        UiClientRendering.renderHudOverlay(event.getGuiGraphics());
        NeoForgePlatformBridge.hud(event);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        UiLocalModal.interrupt(event.getScreen());
        UiPointerCapture.cancel(event.getScreen());
        ClientUiKernel.screenInitialized(event.getScreen());
        if (GaussBlur.isAllowed(event.getScreen())) GaussBlur.warmUp();
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        UiClientRendering.screenClosing(event.getScreen());
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        var connection = event.getConnection();
        Minecraft.getInstance().execute(() -> ClientUiKernel.login(connection));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        UiToast.clear();
        var connection = event.getConnection();
        Minecraft.getInstance().execute(() -> {
            ClientUiKernel.logout(connection);
            MinecraftThemes.disconnect(Minecraft.getInstance());
            UiPointerCapture.validate(null);
            GaussBlur.release();
        });
    }

    @SubscribeEvent
    public static void onShutdown(GameShuttingDownEvent event) {
        try {
            ClientUiKernel.stop();
        } finally {
            SpatialRenderer.releaseTiming();
        }
    }

    /** 資源重載仍停留在玻璃畫面時，立即以新資源恢復管線，不必重開畫面。 */
    private static void reloadCurrentScreen() {
        com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography.resourcesReloaded();
        com.dishanhai.sh_better_ui.kernel.api.music.MusicLyricsView.invalidateFontMetrics();
        com.dishanhai.sh_better_ui.client.gui.scaled.DitherBackground.invalidateAll();
        com.dishanhai.sh_better_ui.kernel.internal.appearance.ImageBackgroundRenderer.invalidateAll();
        ClientMusic.artwork().invalidate();
        GaussBlur.release();
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && GaussBlur.isAllowed(screen)) GaussBlur.warmUp();
    }

    @EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
    public static final class ReloadRegistration {
        private ReloadRegistration() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                NeoForgePlatformBridge.start();
                ClientMusic.start(Minecraft.getInstance().gameDirectory.toPath().resolve("config"));
            });
            ModList.get().getModContainerById(ShBetterUi.MOD_ID).ifPresent(container ->
                    container.registerExtensionPoint(IConfigScreenFactory.class,
                            (ignored, parent) -> createControlPanel(parent)));
        }

        @SubscribeEvent
        public static void onRegisterLyricShader(RegisterShadersEvent event) {
            LyricView.registerShader(event.getResourceProvider(), event::registerShader);
            SpatialRenderer.register(event.getResourceProvider(), event::registerShader);
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            controlPanelKey = new KeyMapping("key.sh_better_ui.control_panel", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.sh_better_ui");
            event.register(controlPanelKey);
            liveActivitiesKey = new KeyMapping("key.sh_better_ui.live_activities", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.sh_better_ui");
            event.register(liveActivitiesKey);
            musicIslandKey = new KeyMapping("key.sh_better_ui.music_island", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.sh_better_ui");
            event.register(musicIslandKey);
            musicPlayerKey = new KeyMapping("key.sh_better_ui.music_open", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.sh_better_ui");
            event.register(musicPlayerKey);
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            // 等待資源準備完成，再回主執行緒重建目前畫面需要的 GL 資源。
            event.registerReloadListener(ClientUiKernel.reloadListener(UiClient::reloadCurrentScreen));
        }
    }
}
