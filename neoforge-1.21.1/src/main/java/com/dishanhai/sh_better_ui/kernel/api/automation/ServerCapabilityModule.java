// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityHandler;
import net.minecraft.server.MinecraftServer;

/** Dedicated-server-safe provider declared in META-INF/sh_better_ui.automation. */
public interface ServerCapabilityModule {
    ModuleDescriptor descriptor();

    /** Called on the server thread once per server. The registrar is valid only during this call. */
    void register(MinecraftServer server, Registrar registrar);

    @FunctionalInterface
    interface Registrar {
        void register(CapabilityDescriptor descriptor, CapabilityHandler handler);
    }
}
