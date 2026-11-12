// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientPagesAvailabilityTest {
    @Test
    void liveSettingsGateLocalOpensRemoteValidationAndProtocolOffer() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var module = fixture.module;
            var pages = (ClientPages) module.engine().require(PageService.KEY);
            var live = (LiveSurfaceRuntime) module.engine().require(UIApis.LIVE_SURFACES);
            var network = new ClientPageNetwork(pages);
            var hud = definition(UiPageType.HUD, "hud_panel");
            var notice = definition(UiPageType.NOTIFICATION, "notification");
            var screenTypes = Set.of(UiPageType.SCREEN, UiPageType.MODAL);

            live.policy(policy(false, true));
            assertEquals(screenTypes, pages.pageTypes());
            assertEquals(screenTypes, network.capabilities().pageTypes());
            try (var registration = pages.register(module, hud)) {
                for (int i = 0; i < 9; i++) {
                    var unavailable = assertThrows(ClientPages.Rejected.class,
                            () -> pages.open(module, hud.id(), Map.of(), ignored -> {}));
                    assertEquals(UiErrorCode.CAPABILITY_MISSING, unavailable.code);
                }
            }
            assertEquals(UiErrorCode.CAPABILITY_MISSING, assertThrows(ClientPages.Rejected.class,
                    () -> pages.validateRemote(hud, Map.of())).code);

            live.policy(policy(true, false));
            var withoutNotices = Set.of(UiPageType.SCREEN, UiPageType.MODAL, UiPageType.HUD);
            assertEquals(withoutNotices, pages.pageTypes());
            assertEquals(withoutNotices, network.capabilities().pageTypes());
            assertEquals(UiErrorCode.CAPABILITY_MISSING, assertThrows(ClientPages.Rejected.class,
                    () -> pages.validateRemote(notice, Map.of())).code);

            live.policy(policy(true, true));
            assertEquals(Set.of(UiPageType.SCREEN, UiPageType.MODAL, UiPageType.HUD, UiPageType.NOTIFICATION),
                    network.capabilities().pageTypes());
            pages.validateRemote(notice, Map.of());
        }
    }

    @Test
    void disablingIslandSettlesUnpresentedPageAndAllowsReopenWhenEnabled() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var module = fixture.module;
            var pages = (ClientPages) module.engine().require(PageService.KEY);
            var live = (LiveSurfaceRuntime) module.engine().require(UIApis.LIVE_SURFACES);
            fixture.client.screens().login(new Object());
            var hud = definition(UiPageType.HUD, "hud_panel");
            try (var registration = pages.register(module, hud)) {
                var opening = pages.open(module, hud.id(), Map.of(), ignored -> {});
                assertEquals(PageService.State.OPENING, opening.snapshot().state());
                live.policy(policy(false, true));
                pages.tickPresentation();
                var displayed = opening.displayed().toCompletableFuture();
                var closed = opening.closed().toCompletableFuture();
                assertTrue(displayed.isDone());
                assertFalse(displayed.join());
                assertTrue(closed.isDone());
                assertEquals(PageService.State.FAILED, closed.join().state());

                live.policy(policy(true, true));
                try (var reopened = pages.open(module, hud.id(), Map.of(), ignored -> {})) {
                    assertEquals(PageService.State.OPENING, reopened.snapshot().state());
                }
            }
        }
    }

    private static UiPageDefinition definition(UiPageType type, String root) {
        return UiPageDefinition.builder(new UiId("example", root)).title(root).type(type)
                .root("root", root, ignored -> {}).build();
    }

    private static LiveSurfaceRuntime.Policy policy(boolean enabled, boolean notifications) {
        var defaults = LiveSurfaceRuntime.Policy.DEFAULT;
        return new LiveSurfaceRuntime.Policy(enabled, defaults.compact(), defaults.expanded(), defaults.split(),
                notifications, defaults.deduplicate(), defaults.quiet(), defaults.maxActivities(), defaults.maxNotifications(),
                defaults.defaultDurationNanos(), defaults.priorities());
    }
}
