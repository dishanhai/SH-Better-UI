// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-host facade over the same lifecycle used by the kernel, created and used on the server thread. */
public final class ServerAutomationScopes implements AutoCloseable {
    private final Thread ownerThread = Thread.currentThread();
    private final ScopeLifecycle lifecycle;
    private final Map<UiId, ScopeLifecycle.Scope> modules = new HashMap<>();
    private final Map<UUID, ScopeHandle> sessions = new HashMap<>();

    public ServerAutomationScopes(Consumer<Throwable> cleanupFailures) {
        Objects.requireNonNull(cleanupFailures, "cleanupFailures");
        lifecycle = new ScopeLifecycle((scope, failure) -> cleanupFailures.accept(failure));
    }

    public ScopeHandle openModule(ModuleDescriptor descriptor) {
        assertThread();
        Objects.requireNonNull(descriptor, "descriptor");
        var scope = lifecycle.openModule(descriptor);
        modules.put(descriptor.id(), scope);
        return scope;
    }

    public void closeModule(UiId module) {
        assertThread();
        var scope = modules.remove(Objects.requireNonNull(module, "module"));
        if (scope != null) lifecycle.close(scope);
    }

    /** A repeated login must close the old session explicitly before creating its replacement. */
    public ScopeHandle openSession(UUID player) {
        assertThread();
        Objects.requireNonNull(player, "player");
        ScopeHandle previous = sessions.get(player);
        if (previous != null && previous.isOpen()) throw new IllegalStateException("Player session is already open");
        var scope = lifecycle.root().openChild(ScopeKind.SERVER_SESSION,
                new UiId("sh_better_ui", "automation/session/" + player));
        sessions.put(player, scope);
        return scope;
    }

    public void closeSession(UUID player) {
        assertThread();
        ScopeHandle scope = sessions.remove(Objects.requireNonNull(player, "player"));
        if (scope != null) scope.close();
    }

    @Override
    public void close() {
        assertThread();
        lifecycle.close(lifecycle.root());
        modules.clear();
        sessions.clear();
    }

    private void assertThread() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Server scopes require server thread");
    }
}
