// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(ExampleUiModule.MOD_ID)
public final class NeoForgeExampleMod {
    @EventBusSubscriber(modid = ExampleUiModule.MOD_ID)
    public static final class ServerEvents {
        @SubscribeEvent
        public static void commands(RegisterCommandsEvent event) {
            if (ModList.get().isLoaded("sh_better_ui")) ExampleServerPages.registerCommand(event.getDispatcher());
        }

        @SubscribeEvent
        public static void stopping(ServerStoppingEvent event) {
            if (ModList.get().isLoaded("sh_better_ui")) ExampleServerPages.stop(event.getServer());
        }
    }

    @EventBusSubscriber(modid = ExampleUiModule.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent
        public static void screen(ScreenEvent.Init.Post event) {
            ExampleClient.addTitleEntry(event.getScreen(), event::addListener);
        }

        @SubscribeEvent public static void joined(ClientPlayerNetworkEvent.LoggingIn event) {
            ExampleClient.joined(event.getConnection());
        }

        @SubscribeEvent public static void left(ClientPlayerNetworkEvent.LoggingOut event) {
            ExampleClient.left(event.getConnection());
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void frame(RenderFrameEvent.Post event) {
            ExampleSmoke.frame();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void shutdown(GameShuttingDownEvent event) {
            ExampleSmoke.shutdown();
        }
    }
}
