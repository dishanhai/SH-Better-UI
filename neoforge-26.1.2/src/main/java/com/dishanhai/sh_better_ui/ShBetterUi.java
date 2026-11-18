// Author: dishanhai
package com.dishanhai.sh_better_ui;

import com.dishanhai.sh_better_ui.internal.fumo.FumoRegistration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Common mod entry point for Minecraft 26.1.2. */
@Mod(ShBetterUi.MOD_ID)
public final class ShBetterUi {
    public static final String MOD_ID = "sh_better_ui";

    public ShBetterUi(IEventBus modBus) {
        FumoRegistration.register(modBus);
    }
}
