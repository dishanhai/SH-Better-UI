// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.capability;

import java.util.Objects;
import java.util.UUID;

/** A registry-issued reference; a new registration never revives an old reference. */
public record CapabilityReference(CapabilityDescriptor descriptor, UUID registrationId) {
    public CapabilityReference {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(registrationId, "registrationId");
    }
}
