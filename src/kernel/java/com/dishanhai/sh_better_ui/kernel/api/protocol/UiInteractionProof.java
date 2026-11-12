// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import java.util.Objects;
import java.util.UUID;

/** Identifies one locally initiated gesture within the enclosing connection. */
public record UiInteractionProof(long attempt, UUID token, UiInteractionTarget target) {
    public UiInteractionProof {
        if (attempt < 1) throw new IllegalArgumentException("Interaction attempt must be positive");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(target, "target");
    }
}
