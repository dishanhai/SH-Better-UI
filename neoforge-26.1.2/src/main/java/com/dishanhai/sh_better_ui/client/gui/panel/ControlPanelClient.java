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
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class ControlPanelClient {
    private static KeyMapping openPanel;

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
    }

    @SubscribeEvent
    public static void started(ClientStartedEvent event) {
        ControlPanelSettings.load(event.getClient().gameDirectory.toPath().resolve("config"));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (openPanel == null) return;
        Minecraft client = Minecraft.getInstance();
        while (openPanel.consumeClick()) {
            if (client.player != null && ControlPanelPlatform.screen(client) == null
                    && !ControlPanelPlatform.hasOverlay(client)) {
                ControlPanelScreen.open(client);
            }
        }
    }
}
