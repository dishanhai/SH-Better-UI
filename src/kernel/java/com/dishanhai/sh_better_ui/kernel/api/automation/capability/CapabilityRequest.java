// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.capability;

import java.util.Map;
import java.util.UUID;

/** taskId may be null only for capabilities that do not require deduplication. No player identity is accepted. */
public record CapabilityRequest(UUID taskId, Map<String, CapabilityValue> parameters) {
    public CapabilityRequest { parameters = CapabilityValue.copyFields(parameters); }
}
