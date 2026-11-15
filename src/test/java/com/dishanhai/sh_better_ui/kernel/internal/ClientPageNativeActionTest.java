// 作者：山海／dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class ClientPageNativeActionTest {
    @Test void nativeBridgeCannotSubmitWithoutAnActiveRemoteSession() {
        try (var fixture = new ClientServiceFixture(java.util.Set.of(), java.util.Set.of())) {
            var pages = (ClientPages) fixture.module.engine().require(PageService.KEY);
            assertTrue(pages.activeRemoteSnapshot().isEmpty());
            assertFalse(pages.submitRemoteAction(UUID.randomUUID(), UiId.parse("test:action"), Map.of()));
        }
    }

    @Test void rejectsBackgroundLocalClosedUnfocusedAndUnsynchronizedPages() {
        var ready = snapshot(PageService.State.ACTIVE, true);
        assertTrue(ClientPages.nativeActionEligible(ready, true, true, true, true, true));
        assertFalse(ClientPages.nativeActionEligible(ready, false, true, true, true, true));
        assertFalse(ClientPages.nativeActionEligible(ready, true, false, true, true, true));
        assertFalse(ClientPages.nativeActionEligible(ready, true, true, false, true, true));
        assertFalse(ClientPages.nativeActionEligible(ready, true, true, true, false, true));
        assertFalse(ClientPages.nativeActionEligible(ready, true, true, true, true, false));
        assertFalse(ClientPages.nativeActionEligible(snapshot(PageService.State.ACTIVE, false), true, true, true, true, true));
        for (var state : PageService.State.values()) if (state != PageService.State.ACTIVE) {
            assertFalse(ClientPages.nativeActionEligible(snapshot(state, true), true, true, true, true, true));
        }
    }

    private static PageService.Snapshot snapshot(PageService.State state, boolean synced) {
        return new PageService.Snapshot(UUID.randomUUID(), UiId.parse("test:page"), state, 1, Map.of(), synced);
    }
}
