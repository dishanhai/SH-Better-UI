// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueSchema;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiActionState;
import com.dishanhai.sh_better_ui.kernel.internal.page.PageDataState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientRemoteActionTest {
    @Test
    void localTimeoutKeepsOriginalIdentityAndBlocksDuplicateIntentsUntilTheLateReceipt() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var pages = (ClientPages) fixture.module.engine().require(PageService.KEY);
            var scope = fixture.module.scope().openChild(ScopeKind.SCREEN, new UiId("example", "transaction_page"));
            var actionId = new UiId("example", "buy");
            var definition = UiPageDefinition.builder(new UiId("example", "exchange"))
                    .field("balance", UiValueSchema.integer(0, 100))
                    .action(new UiActionDefinition(actionId, UiActionPolicy.SERVER, new UiDataSchema(Map.of())))
                    .root("root", "button", button -> button.action(actionId))
                    .build();
            var duplicate = new AtomicInteger();
            var page = pages.new OpenPage(fixture.module.scope(), scope, UUID.randomUUID(), definition,
                    new PageDataState(definition.dataSchema(), Map.of("balance", new UiValue.Int(100))), true,
                    (action, values) -> duplicate.incrementAndGet(), () -> {}, List.of());
            page.presented();
            var original = page.request(actionId, Map.of(), 10);
            var originalId = original.requestId();

            assertNull(page.tick(9, 10));
            assertNull(page.request(actionId, Map.of(), 30));
            assertEquals(PageDataState.Update.APPLIED, page.patch(0, 1, Map.of("balance", new UiValue.Int(95))));
            assertEquals(original, page.tick(10, 10));
            assertTrue(page.hasRequest(originalId));
            assertNull(page.tick(20, 10));
            assertTrue(page.hasRequest(originalId));
            assertEquals(UiActionState.TIMED_OUT, page.actionState());
            assertNull(page.tick(30, 10));
            page.act(definition.root());
            assertEquals(0, duplicate.get());

            page.result(UUID.randomUUID(), UiActionState.FAILED);
            assertTrue(page.hasRequest(originalId));
            page.result(originalId, UiActionState.SUCCEEDED);
            assertFalse(page.hasRequest(originalId));
            assertEquals(UiActionState.SUCCEEDED, page.actionState());
            assertTrue(page.actionAvailable());

            var closing = page.request(actionId, Map.of(), 40);
            assertEquals(1, closing.revision());
            page.close();
            assertNull(page.tick(40, 10));
            assertNull(page.request(actionId, Map.of(), 50));
        }
    }
}
