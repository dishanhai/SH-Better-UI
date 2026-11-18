// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT)
public final class RenderStateSmokeEvents {
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) BackgroundRenderSmoke.advance();
    }
}
