// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT)
public final class NeoKernelSmokeEvents {
    private static final KernelClientSmoke.Platform GAME = new KernelClientSmoke.Platform() {
        @Override public void join(Minecraft client, Screen parent, String name) {
            client.createWorldOpenFlows().createFreshLevel(name, KernelClientSmoke.settings(),
                    KernelClientSmoke.options(), KernelClientSmoke::flat, parent);
        }

        @Override public void rejoin(Minecraft client, Screen parent, String name) {
            client.createWorldOpenFlows().openWorld(name, () -> client.setScreen(parent));
        }

        @Override public void leave(Minecraft client, Screen title) {
            client.level.disconnect();
            client.disconnect(title);
        }

        @Override public boolean optionalPresent() { return ModList.get().isLoaded("ldlib2"); }

        @Override public boolean clientCommand(String command) { return ClientCommandHandler.runCommand(command); }

        @Override public void flushPointer(Minecraft client) { client.mouseHandler.handleAccumulatedMovement(); }
    };

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frame(RenderFrameEvent.Post event) { KernelClientSmoke.frame(GAME); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void shutdown(GameShuttingDownEvent event) { KernelClientSmoke.shutdown(); }
}
