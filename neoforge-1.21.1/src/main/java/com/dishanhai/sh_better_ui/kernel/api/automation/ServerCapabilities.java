// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation;

import com.dishanhai.sh_better_ui.automation.AutomationRuntime;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityDirectory;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

public final class ServerCapabilities {
    private ServerCapabilities() {}

    /** Server-thread-only. Returns a directory bound to the current authenticated player session. */
    public static Optional<CapabilityDirectory> forPlayer(ServerPlayer player) {
        return AutomationRuntime.capabilitiesFor(player);
    }
}
