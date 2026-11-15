// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.automation.ServerCapabilityModule;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.CapabilityHandler;
import com.dishanhai.sh_better_ui.kernel.internal.automation.capability.ServerCapabilityRegistry;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Loads server providers from the mod files selected by NeoForge. */
public final class ServerCapabilityModules {
    private static final String DESCRIPTOR = "sh_better_ui.automation";

    private ServerCapabilityModules() {}

    public static void install(MinecraftServer server, ServerAutomationScopes scopes, ServerCapabilityRegistry registry) {
        var sources = ModList.get().getModFiles().stream().<NativeModuleSources.Source>mapMulti((file, sourcesWithDescriptor) -> {
            var path = file.getFile().findResource("META-INF", DESCRIPTOR);
            if (Files.isRegularFile(path)) {
                var modIds = file.getMods().stream().map(mod -> mod.getModId()).collect(Collectors.toUnmodifiableSet());
                sourcesWithDescriptor.accept(NativeModuleSources.modulePath(path, file.moduleName(), modIds));
            }
        }).toList();
        install(server, scopes, registry, sources);
    }

    static void install(MinecraftServer server, ServerAutomationScopes scopes, ServerCapabilityRegistry registry,
            List<NativeModuleSources.Source> sources) {
        var candidates = new LinkedHashMap<UiId, Candidate>();
        var duplicates = new HashSet<UiId>();
        for (var entry : NativeModuleSources.discover(sources, ServerCapabilityModule.class)) {
            try {
                var descriptor = entry.module().descriptor();
                if (!descriptor.id().namespace().equals(entry.modId())) {
                    throw new IllegalArgumentException("Provider ID must be in the declaring mod's namespace");
                }
                if (candidates.putIfAbsent(descriptor.id(), new Candidate(descriptor, entry.module())) != null) {
                    duplicates.add(descriptor.id());
                }
            } catch (RuntimeException | LinkageError failure) {
                ShBetterUi.LOGGER.warn("Rejected UUK server module at {}", entry.location(), failure);
            }
        }
        duplicates.forEach(id -> {
            candidates.remove(id);
            ShBetterUi.LOGGER.warn("Duplicate UUK server module ID {}", id);
        });
        for (Candidate candidate : candidates.values()) {
            var id = candidate.descriptor().id();
            try {
                var scope = scopes.openModule(candidate.descriptor());
                var registrar = new Registrar(candidate.descriptor(), scope, registry);
                try {
                    candidate.module().register(server, registrar);
                    ShBetterUi.LOGGER.info("Discovered UUK server module {}", id);
                } catch (RuntimeException | LinkageError failure) {
                    scopes.closeModule(id);
                    throw failure;
                } finally {
                    registrar.open = false;
                }
            } catch (RuntimeException | LinkageError failure) {
                ShBetterUi.LOGGER.warn("Could not register UUK server module {}", id, failure);
            }
        }
    }

    private record Candidate(ModuleDescriptor descriptor, ServerCapabilityModule module) {}

    private static final class Registrar implements ServerCapabilityModule.Registrar {
        private final ModuleDescriptor module;
        private final ScopeHandle scope;
        private final ServerCapabilityRegistry registry;
        private final Thread owner = Thread.currentThread();
        private boolean open = true;

        Registrar(ModuleDescriptor module, ScopeHandle scope, ServerCapabilityRegistry registry) {
            this.module = module;
            this.scope = scope;
            this.registry = registry;
        }

        @Override
        public void register(CapabilityDescriptor descriptor, CapabilityHandler handler) {
            if (Thread.currentThread() != owner || !open) {
                throw new IllegalStateException("UUK server registration has ended or is off-thread");
            }
            if (!descriptor.id().namespace().equals(module.id().namespace())) {
                throw new IllegalArgumentException("Capability ID must be in the provider's namespace");
            }
            if (!descriptor.providerVersion().equals(module.version())) {
                throw new IllegalArgumentException("Capability provider version must match the module version");
            }
            registry.register(scope, descriptor, handler);
        }
    }
}
