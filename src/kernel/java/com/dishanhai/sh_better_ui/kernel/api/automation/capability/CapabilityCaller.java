// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.capability;

import java.util.Objects;
import java.util.UUID;

/** The host obtains this identity from its authenticated server connection, never from request data. */
public record CapabilityCaller(UUID playerId) {
    public CapabilityCaller { Objects.requireNonNull(playerId, "playerId"); }
}
