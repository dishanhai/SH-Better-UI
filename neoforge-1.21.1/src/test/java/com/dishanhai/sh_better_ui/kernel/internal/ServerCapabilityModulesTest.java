// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.automation.ServerCapabilityModule;
import com.dishanhai.sh_better_ui.kernel.api.automation.capability.*;
import com.dishanhai.sh_better_ui.kernel.internal.automation.capability.ServerCapabilityRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ServerCapabilityModulesTest {
    private static final UiId MODULE = UiId.parse("example:module");
    private static final UiId COUNT = UiId.parse("example:count");
    private static final UiVersion VERSION = UiVersion.parse("1.0.0");
    private static final CapabilitySchema EMPTY = new CapabilitySchema(Map.of());
    private static final CapabilityDescriptor DESCRIPTOR = new CapabilityDescriptor(COUNT, 1, MODULE, VERSION,
            EMPTY, EMPTY, CapabilityDescriptor.Access.READ, COUNT, CapabilityDescriptor.Deduplication.NONE);

    @Test
    void discoversInvokesAndRevokesDeclaredCapability() {
        try (var scopes = new ServerAutomationScopes(org.junit.jupiter.api.Assertions::fail);
             var registry = registry()) {
            install(scopes, registry, "example valid\nexample valid\n");
            UUID player = UUID.randomUUID();
            var directory = registry.bind(scopes.openSession(player), new CapabilityCaller(player));
            assertEquals(List.of(DESCRIPTOR), directory.discover());
            var reference = directory.find(COUNT, 1).orElseThrow();
            var request = new CapabilityRequest(null, Map.of());
            assertEquals(CapabilityResult.Status.SUCCESS, directory.invoke(reference, request).toCompletableFuture().join().status());
            assertThrows(IllegalStateException.class, () -> Valid.saved.register(DESCRIPTOR, null));
            scopes.closeModule(MODULE);
            assertTrue(directory.find(COUNT, 1).isEmpty());
        }
    }

    @Test
    void rejectsMismatchedOwnerAndDuplicateModulesAndRollsBackFailedRegistration() {
        for (String declarations : List.of("another_mod valid", "example valid\nexample failing", "example failing")) {
            try (var scopes = new ServerAutomationScopes(org.junit.jupiter.api.Assertions::fail);
                 var registry = registry()) {
                install(scopes, registry, declarations);
                UUID player = UUID.randomUUID();
                var directory = registry.bind(scopes.openSession(player), new CapabilityCaller(player));
                assertTrue(directory.discover().isEmpty(), declarations);
            }
        }
    }

    private static ServerCapabilityRegistry registry() {
        return new ServerCapabilityRegistry(new ServerCapabilityRegistry.ServerThread() {
            @Override public boolean isOnThread() { return true; }
            @Override public void execute(Runnable action) { action.run(); }
        }, (caller, descriptor) -> true);
    }

    private static void install(ServerAutomationScopes scopes, ServerCapabilityRegistry registry, String declarations) {
        var source = new NativeModuleSources.Source("test", "test", Set.of("example", "another_mod"),
                () -> new ByteArrayInputStream(declarations.getBytes(StandardCharsets.UTF_8)),
                name -> switch (name) {
                    case "valid" -> Valid.class;
                    case "failing" -> Failing.class;
                    default -> null;
                });
        ServerCapabilityModules.install(null, scopes, registry, List.of(source));
    }

    public static class Valid implements ServerCapabilityModule {
        static Registrar saved;

        @Override public ModuleDescriptor descriptor() {
            return ModuleDescriptor.builder().id(MODULE).version(VERSION).build();
        }

        @Override public void register(MinecraftServer server, Registrar registrar) {
            saved = registrar;
            registrar.register(DESCRIPTOR, (caller, request) ->
                    CompletableFuture.completedStage(CapabilityResult.success(Map.of())));
        }
    }

    public static final class Failing extends Valid {
        @Override public void register(MinecraftServer server, Registrar registrar) {
            super.register(server, registrar);
            throw new IllegalStateException("Registration failed after one capability");
        }
    }
}
