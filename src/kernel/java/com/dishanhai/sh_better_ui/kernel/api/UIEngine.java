// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.kernel.api.glass.GlassService;

import java.util.Optional;

/** Public view of an initialized UI engine. */
public interface UIEngine {
    int CONTRACT_VERSION = 1;

    APIRegistry registry();

    CapabilityResolver capabilities();

    UILifecycle lifecycle();

    default RuntimeMetrics metrics() {
        throw new UnsupportedOperationException("This UIEngine implementation does not expose runtime metrics");
    }

    /** Scheduler access is bound to the module context that issued this view. */
    default UIScheduler scheduler() {
        throw new UnsupportedOperationException("This UIEngine implementation does not expose a scheduler");
    }

    default UIInvalidation invalidations() {
        throw new UnsupportedOperationException("This UIEngine implementation does not expose invalidation");
    }

    default UIConfigCenter configs() {
        throw new UnsupportedOperationException("This UIEngine implementation does not expose configuration");
    }

    default UIStateStore states() {
        throw new UnsupportedOperationException("This UIEngine implementation does not expose state");
    }

    default UIPermissionManager permissions() {
        throw new UnsupportedOperationException("This UIEngine implementation does not expose permissions");
    }

    default UICommandBus commands() {
        throw new UnsupportedOperationException("This UIEngine implementation does not expose commands");
    }

    default UIEventBus events() {
        throw new UnsupportedOperationException("This UIEngine implementation does not expose events");
    }

    default UIResourceOwnership ownership() {
        throw new UnsupportedOperationException("This UIEngine implementation does not expose resource ownership");
    }

    default UIPlatform platform() {
        return require(UIPlatform.KEY);
    }

    default GlassService glass() {
        return require(GlassService.KEY);
    }

    /** Declarative validation and prediction share this engine's registry, scopes and scheduler. */
    default com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessEngine correctness() {
        return require(com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessEngine.KEY);
    }

    /** Finds a visible service whose ID, Java type and contract version match the key. */
    default <T extends UIService> Optional<T> find(ServiceKey<T> key) {
        return registry().find(key);
    }

    /** @throws java.util.NoSuchElementException if no visible service matches the key */
    default <T extends UIService> T require(ServiceKey<T> key) {
        return registry().require(key);
    }
}
