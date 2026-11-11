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
import net.minecraftforge.event.GameShuttingDownEvent;
import com.dishanhai.sh_better_ui.client.lyrics.render.LyricView;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 山海：整個進程只註冊一條共用後處理管線；消費端僅提供畫面 predicates。 */
@Mod.EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UiClient {
    private static KeyMapping controlPanelKey;
    private static KeyMapping liveActivitiesKey;

    private UiClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientUiKernel.tick(minecraft);
        ClientUiKernel.nativeScoreboard(ForgePlatformBridge.hasSidebar(minecraft));
        while (liveActivitiesKey != null && liveActivitiesKey.consumeClick()) {
            ClientUiKernel.openLiveActivities(minecraft);
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
    public static void onLyricCommands(RegisterClientCommandsEvent event) {
        LyricClient.registerCommands(event.getDispatcher());
    }

    private static Screen createControlPanel(Screen parent) {
        return new ControlPanelScreen(parent, controlPanelKey, ShBetterUi::saveClientUiSettings);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFrameStart(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            NativeClientBridge.frameStart();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFrameEnd(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) NativeClientBridge.frameEnd();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        UiClientRendering.prepareScreenOverlays(event.getScreen());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        GaussBlur.onScreenRenderPost(event);
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
        if (ForgePlatformBridge.scroll(event)) event.setCanceled(true);
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
        ForgePlatformBridge.hud(event);
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
        ClientMusic.artwork().invalidate();
        GaussBlur.release();
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && GaussBlur.isAllowed(screen)) GaussBlur.warmUp();
    }

    @Mod.EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ReloadRegistration {
        private ReloadRegistration() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ForgePlatformBridge.start();
                ClientMusic.start(Minecraft.getInstance().gameDirectory.toPath().resolve("config"));
            });
            ModList.get().getModContainerById(ShBetterUi.MOD_ID).ifPresent(container ->
                    container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new ConfigScreenHandler.ConfigScreenFactory(UiClient::createControlPanel)));
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
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            // 等待資源準備完成，再回主執行緒重建目前畫面需要的 GL 資源。
            event.registerReloadListener(ClientUiKernel.reloadListener(UiClient::reloadCurrentScreen));
        }
    }
}
