// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class ControlPanelClient {
    private static KeyMapping openPanel;
    private static KeyMapping openMusic;
    private static final com.dishanhai.sh_better_ui.client.music.MusicOpenShortcut musicShortcut =
            new com.dishanhai.sh_better_ui.client.music.MusicOpenShortcut();

    private static boolean musicShortcutAllowed(Minecraft client) {
        return client.player != null && client.level != null && client.isWindowActive()
                && ControlPanelPlatform.screen(client) == null && !ControlPanelPlatform.hasOverlay(client);
    }

    private static void offerMusicShortcut(InputConstants.Key key, int action) {
        if (openMusic == null || openMusic.isUnbound()) return;
        musicShortcut.offer(action == GLFW.GLFW_PRESS, openMusic.isActiveAndMatches(key),
                musicShortcutAllowed(Minecraft.getInstance()));
    }

    @SubscribeEvent
    public static void musicKey(InputEvent.Key event) {
        offerMusicShortcut(InputConstants.getKey(event.getKeyEvent()), event.getAction());
    }

    @SubscribeEvent
    public static void musicMousePre(InputEvent.MouseButton.Pre event) {
        var key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        musicShortcut.prepareMouse(event.getAction() == GLFW.GLFW_PRESS,
                openMusic != null && !openMusic.isUnbound() && openMusic.isActiveAndMatches(key),
                musicShortcutAllowed(Minecraft.getInstance()));
    }

    @SubscribeEvent
    public static void musicMousePost(InputEvent.MouseButton.Post event) {
        musicShortcut.finishMouse(musicShortcutAllowed(Minecraft.getInstance()));
    }

    private ControlPanelClient() {}

    static KeyMapping panelKey() { return openPanel; }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(ShBetterUi.MOD_ID).ifPresent(container ->
                container.registerExtensionPoint(IConfigScreenFactory.class,
                        (ignored, parent) -> new ControlPanelScreen(parent)));
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        var category = new KeyMapping.Category(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "controls"));
        event.registerCategory(category);
        openPanel = new KeyMapping("key.sh_better_ui.control_panel", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, category);
        event.register(openPanel);
        openMusic = new KeyMapping("key.sh_better_ui.music_open", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category);
        event.register(openMusic);
    }

    @SubscribeEvent
    public static void started(ClientStartedEvent event) {
        ControlPanelSettings.load(event.getClient().gameDirectory.toPath().resolve("config"));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (musicShortcut.take(musicShortcutAllowed(client))) {
            com.dishanhai.sh_better_ui.kernel.internal.NativePort.show(client,
                    com.dishanhai.sh_better_ui.client.api.ShBetterUiApi.settings().onlineMusicScreen(null));
        }
        if (openPanel == null) return;
        while (openPanel.consumeClick()) {
            if (client.player != null && ControlPanelPlatform.screen(client) == null
                    && !ControlPanelPlatform.hasOverlay(client)) {
                ControlPanelScreen.open(client);
            }
        }
    }
}
