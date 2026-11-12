// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeKernelSmokeEvents {
    private static final KernelClientSmoke.Platform GAME = new KernelClientSmoke.Platform() {
        @Override public void join(Minecraft client, Screen parent, String name) {
            client.createWorldOpenFlows().createFreshLevel(name, KernelClientSmoke.settings(),
                    KernelClientSmoke.options(), KernelClientSmoke::flat);
        }

        @Override public void rejoin(Minecraft client, Screen parent, String name) {
            client.createWorldOpenFlows().loadLevel(parent, name);
        }

        @Override public void leave(Minecraft client, Screen title) {
            client.level.disconnect();
            client.clearLevel(title);
        }

        @Override public boolean optionalPresent() { return ModList.get().isLoaded("ldlib"); }

        @Override public boolean clientCommand(String command) { return ClientCommandHandler.runCommand(command); }
    };

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) KernelClientSmoke.frame(GAME);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void shutdown(GameShuttingDownEvent event) { KernelClientSmoke.shutdown(); }
}
