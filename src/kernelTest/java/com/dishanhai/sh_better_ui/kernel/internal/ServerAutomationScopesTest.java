// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ServerAutomationScopesTest {
    @Test
    void moduleAndPlayerLifetimesUseKernelGenerationsAndCleanUpExactlyOnce() {
        var scopes = new ServerAutomationScopes(failure -> fail(failure));
        var provider = UiId.parse("example:provider");
        var descriptor = ModuleDescriptor.builder().id(provider).version(new UiVersion(1, 0, 0)).build();
        var module = scopes.openModule(descriptor);
        var player = UUID.randomUUID();
        var session = scopes.openSession(player);
        assertEquals(ScopeKind.SERVER_SESSION, session.kind());
        var cleanups = new AtomicInteger();
        module.track(cleanups::incrementAndGet);
        session.track(cleanups::incrementAndGet);
        assertThrows(IllegalStateException.class, () -> scopes.openSession(player));
        scopes.closeSession(player);
        assertFalse(session.isOpen());
        var next = scopes.openSession(player);
        assertNotEquals(session.generation(), next.generation());
        scopes.closeModule(provider);
        assertFalse(module.isOpen());
        assertTrue(next.isOpen());
        scopes.close();
        scopes.close();
        assertFalse(next.isOpen());
        assertEquals(2, cleanups.get());
        assertThrows(IllegalStateException.class, () -> scopes.openSession(player));
    }
}
