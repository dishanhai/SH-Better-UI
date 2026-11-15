// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.capability;

import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** A host-bound player's view. Discovery is server-thread-only; invoke safely queues to that thread. */
public interface CapabilityDirectory extends UIService {
    ServiceKey<CapabilityDirectory> KEY = new ServiceKey<>(UiId.parse("sh_better_ui:automation/capabilities"),
            CapabilityDirectory.class, 1);
    List<CapabilityDescriptor> discover();
    Optional<CapabilityReference> find(UiId id, int contractVersion);
    CompletionStage<CapabilityResult> invoke(CapabilityReference reference, CapabilityRequest request);
}
