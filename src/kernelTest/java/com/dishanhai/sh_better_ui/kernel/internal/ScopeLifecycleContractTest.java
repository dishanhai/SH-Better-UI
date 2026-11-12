// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.dishanhai.sh_better_ui.kernel.internal.TestModules.*;
import static org.junit.jupiter.api.Assertions.*;

class ScopeLifecycleContractTest {
    private static final UiId SCREEN = UiId.parse("example:screen");
    private static final ResourceClaim TARGET =
            new ResourceClaim(ResourceKind.RENDER_TARGET, UiId.parse("example:target"));

    @Test
    void closesChildrenAndResourcesInReverseOrderAndContinuesAfterFailure() {
        var errors = new ArrayList<Throwable>();
        var scopes = new ScopeLifecycle((scope, failure) -> errors.add(failure));
        var module = scopes.openModule(module("module").build());
        ScopeHandle session = module.openChild(ScopeKind.CLIENT_SESSION, UiId.parse("example:session"));
        ScopeHandle screen = session.openChild(ScopeKind.SCREEN, SCREEN);
        ScopeHandle tree = screen.openChild(ScopeKind.UI_TREE, UiId.parse("example:tree"));
        ScopeHandle component = tree.openChild(ScopeKind.COMPONENT, UiId.parse("example:button"));
        var log = new ArrayList<String>();
        AutoCloseable release = () -> log.add("screen");
        screen.track(release);
        screen.track(release);
        assertThrows(IllegalStateException.class, () -> tree.track(release));
        screen.track(() -> {
            log.add("broken");
            throw new IOException("cleanup failed");
        });
        component.track(() -> {
            assertFalse(screen.isOpen());
            log.add("component");
        });
        session.track(() -> log.add("session"));

        screen.close();
        screen.close();
        assertEquals(List.of("component", "broken", "screen"), log);
        assertEquals("cleanup failed", errors.get(0).getMessage());
        assertFalse(component.isOpen());
        assertTrue(session.isOpen());
        assertTrue(module.isOpen());
        assertThrows(IllegalStateException.class, () -> component.track(() -> {}));
        var remounted = session.openChild(ScopeKind.SCREEN, SCREEN);
        assertNotEquals(screen.generation(), remounted.generation());
        screen.close();
        assertTrue(remounted.isOpen());
        session.close();
        assertFalse(remounted.isOpen());
        assertEquals(List.of("component", "broken", "screen", "session"), log);
        assertThrows(IllegalStateException.class, module::close);
    }

    @Test
    void childCleanupCanOpenTheSameIdentityUnderItsLiveParent() {
        var scopes = new ScopeLifecycle((scope, failure) -> fail(failure));
        var module = scopes.openModule(module("module").build());
        var original = module.openChild(ScopeKind.SCREEN, SCREEN);
        var child = original.openChild(ScopeKind.UI_TREE, UiId.parse("example:tree"));
        var replacement = new AtomicReference<ScopeHandle>();
        child.track(() -> {
            assertFalse(child.isOpen());
            assertFalse(original.isOpen());
            replacement.set(module.openChild(ScopeKind.SCREEN, SCREEN));
        });

        original.close();
        var reopened = replacement.get();
        assertTrue(reopened.isOpen());
        assertNotEquals(original.generation(), reopened.generation());
        original.close();
        assertTrue(reopened.isOpen());
        scopes.close(module);
        assertFalse(reopened.isOpen());
    }

    @Test
    void leasesAreExclusiveAndAnOldLeaseCannotReleaseItsReplacement() {
        var log = new ArrayList<String>();
        var scopes = new ScopeLifecycle((scope, failure) -> fail(failure));
        var first = scopes.openModule(module("first").claimResource(TARGET).build());
        var second = scopes.openModule(module("second").claimResource(TARGET).build());
        var screen = first.openChild(ScopeKind.SCREEN, SCREEN);
        AutoCloseable release = () -> log.add("first");
        var lease = screen.claim(TARGET, release);
        assertThrows(IllegalStateException.class, () -> screen.track(release));
        assertThrows(IllegalStateException.class, () -> second.track(release));
        assertEquals(first.owner(), lease.owner());
        assertEquals(screen.generation(), lease.scopeGeneration());
        assertThrows(IllegalStateException.class, () -> second.claim(TARGET, () -> fail("denied lease released")));
        screen.close();
        var replacement = second.claim(TARGET, () -> log.add("second"));
        lease.close();
        assertFalse(lease.isActive());
        assertTrue(replacement.isActive());
        scopes.close(second);
        assertEquals(List.of("first", "second"), log);
    }

    @Test
    void enforcesOwnerThreadScopeShapeAndDeclaredResourceAuthority() throws Exception {
        var scopes = new ScopeLifecycle((scope, failure) -> fail(failure));
        var module = scopes.openModule(module("module").build());
        assertThrows(IllegalArgumentException.class, () -> module.openChild(ScopeKind.COMPONENT, SCREEN));
        assertThrows(SecurityException.class,
                () -> module.openChild(ScopeKind.SCREEN, UiId.parse("foreign:screen")));
        assertThrows(SecurityException.class, () -> module.claim(TARGET, () -> {}));
        var screen = module.openChild(ScopeKind.SCREEN, SCREEN);
        assertThrows(IllegalStateException.class, () -> module.openChild(ScopeKind.SCREEN, SCREEN));
        var background = new FutureTask<>(() -> {
            assertTrue(screen.isOpen());
            assertThrows(IllegalStateException.class, screen::close);
            assertThrows(IllegalStateException.class, () -> screen.track(() -> {}));
            return null;
        });
        new Thread(background, "kernel-background-caller").start();
        background.get(5, TimeUnit.SECONDS);
        scopes.close(module);
        assertFalse(screen.isOpen());
        assertFalse(module.isOpen());
    }
}
