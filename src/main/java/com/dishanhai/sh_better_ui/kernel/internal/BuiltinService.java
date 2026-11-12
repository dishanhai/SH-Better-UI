// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.Set;

/** Installs a library-owned adapter during the normal service registration window. */
record BuiltinService<T extends UIService>(ModuleDescriptor descriptor, ServiceDescriptor<T> service, T adapter)
        implements UIModule {
    static <T extends UIService> ModuleDiscovery.Candidate candidate(ServiceKey<T> key, T adapter,
                                                                    CapabilityKey... capabilities) {
        return candidate(Set.of(ModuleDescriptor.Platform.FORGE_1_20_1, ModuleDescriptor.Platform.NEOFORGE_1_21_1),
                key, adapter, capabilities);
    }

    static <T extends UIService> ModuleDiscovery.Candidate candidate(Set<ModuleDescriptor.Platform> platforms,
                                                                    ServiceKey<T> key, T adapter,
                                                                    CapabilityKey... capabilities) {
        var version = new UiVersion(1, 0, 0);
        var descriptor = ModuleDescriptor.builder().id(key.id()).version(version)
                .platforms(platforms).initializationPhase(ModuleDescriptor.InitializationPhase.SERVICE);
        for (var capability : capabilities) descriptor.providedCapability(capability, version);
        var service = new ServiceDescriptor<>(key, version, ServiceDescriptor.Visibility.PUBLIC, 0, false);
        return new ModuleDiscovery.Candidate(key.id().namespace(), adapter.getClass().getName(),
                new BuiltinService<>(descriptor.build(), service, adapter), Set.of(), false, true);
    }

    @Override
    public void register(ModuleContext context) {
        if (adapter instanceof AutoCloseable resource) context.scope().track(resource);
        context.services().register(service, adapter);
    }

    @Override public void start(ModuleContext context) {}
    @Override public void stop(ModuleContext context) {}
}
