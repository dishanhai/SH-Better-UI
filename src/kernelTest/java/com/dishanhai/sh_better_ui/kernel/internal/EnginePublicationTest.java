// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.EngineState;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class EnginePublicationTest {
    @Test
    @ResourceLock("client-ui-kernel")
    void theClientFacadeObservesOnlyTheInstalledEngine() {
        var slot = EnginePublication.CLIENT;
        assertFalse(UnifiedUI.isAvailable());
        try (var host = new KernelHost(Platform.FORGE_1_20_1, slot)) {
            host.start(List.of());
            assertTrue(UnifiedUI.isAvailable());
            assertSame(slot.current(), UnifiedUI.get());
            assertThrows(NoSuchElementException.class, UnifiedUI::pages);
            assertThrows(NoSuchElementException.class, UnifiedUI::materialCatalog);
        }
        assertFalse(UnifiedUI.isAvailable());
        assertThrows(IllegalStateException.class, UnifiedUI::get);
    }

    @Test
    void onlyOneInitializedEngineCanBePublished() {
        var publication = new EnginePublication();
        var engine = readyEngine();
        assertNull(publication.current());
        assertThrows(NullPointerException.class, () -> publication.install(null));
        var incomplete = new EngineAccess(new EngineLifecycle(), null, () -> null, null);
        assertThrows(IllegalStateException.class, () -> publication.install(incomplete));
        var installation = publication.install(engine);
        assertSame(engine, publication.current());
        assertThrows(IllegalStateException.class, () -> publication.install(readyEngine()));
        assertSame(engine, publication.current());
        installation.close();
        assertNull(publication.current());
        var next = readyEngine();
        publication.install(next);
        installation.close();
        assertSame(next, publication.current());
    }

    private static UIEngine readyEngine() {
        var lifecycle = new EngineLifecycle();
        for (var state : List.of(EngineState.DISCOVERING, EngineState.REGISTERING, EngineState.RESOLVING,
                EngineState.INITIALIZING, EngineState.READY)) {
            lifecycle.transition(state);
        }
        return new EngineAccess(lifecycle, null, () -> null, null);
    }
}
