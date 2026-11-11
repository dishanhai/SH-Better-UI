// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.List;

/** Queries capability offers visible in an engine or scope lifetime. */
public interface CapabilityResolver {
    default boolean supports(CapabilityKey key) {
        return status(key) != CapabilityStatus.UNAVAILABLE;
    }

    CapabilityStatus status(CapabilityKey key);

    List<CapabilityDescriptor> available();

    CapabilityResolution resolve(CapabilityRequest request);

    default CapabilityResolver at(ScopeHandle scope) {
        throw new UnsupportedOperationException("Scoped capabilities are not supported by this provider");
    }

    default Subscription provide(ScopeHandle scope, CapabilityKey key, ModuleDescriptor.CapabilityOffer offer) {
        throw new UnsupportedOperationException("Dynamic capabilities are not supported by this provider");
    }

    default Subscription observe(ScopeHandle scope, CapabilityRequest request,
                                 java.util.function.Consumer<CapabilityChange> listener) {
        throw new UnsupportedOperationException("Capability observation is not supported by this provider");
    }
}
