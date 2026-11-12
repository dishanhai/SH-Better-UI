// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod(ExampleUiModule.MOD_ID)
public final class ForgeExampleMod {
    @Mod.EventBusSubscriber(modid = ExampleUiModule.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

    @Mod.EventBusSubscriber(modid = ExampleUiModule.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
        public static void frame(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.END) ExampleSmoke.frame();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void shutdown(GameShuttingDownEvent event) {
            ExampleSmoke.shutdown();
        }
    }
}
