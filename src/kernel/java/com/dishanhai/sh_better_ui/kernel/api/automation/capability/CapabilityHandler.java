// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.capability;

import java.util.concurrent.CompletionStage;

/** Called on the server thread. Async continuations touching game state must return to the server thread. */
@FunctionalInterface
public interface CapabilityHandler {
    CompletionStage<CapabilityResult> invoke(CapabilityCaller caller, CapabilityRequest request);
}
