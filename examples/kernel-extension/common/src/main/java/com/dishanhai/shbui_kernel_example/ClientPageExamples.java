// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.PageService;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageType;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;

import java.util.Map;

final class ClientPageExamples {
    private static final UiId LOCAL = ExchangePage.id("local_exchange");
    private static final UiId HUD = ExchangePage.id("page_hud");
    private static final UiId NOTIFICATION = ExchangePage.id("page_notification");

    private final ModuleContext owner;
    private final PageService pages;
    private PageService.Instance exchange;
    private PageService.Instance hud;

    ClientPageExamples(ModuleContext owner, PageService pages) {
        this.owner = owner;
        this.pages = pages;
        pages.register(owner, ExchangePage.create(LOCAL, UiActionPolicy.LOCAL, ClientPageExamples::text));
        pages.register(owner, passive(HUD, UiPageType.HUD, "hud_panel", "hud"));
        pages.register(owner, passive(NOTIFICATION, UiPageType.NOTIFICATION, "notification", "notification"));
    }

    void open(boolean inGame) {
        exchange = pages.open(owner, LOCAL, Map.of(
                ExchangePage.BALANCE, new UiValue.Int(ExchangePage.INITIAL_CREDITS),
                ExchangePage.SESSION_ACTIVE, new UiValue.Bool(inGame)), this::onAction);
    }

    private void onAction(UiId action) {
        if (ExchangePage.REDEEM.equals(action)) {
            var snapshot = exchange.snapshot();
            var balance = (UiValue.Int) snapshot.data().get(ExchangePage.BALANCE);
            if (balance.value() > 0) pages.update(owner, snapshot.instanceId(), ExchangePage.data(balance.value() - 1));
        } else if (ExchangePage.TOGGLE_HUD.equals(action)) {
            toggleHud();
        } else if (ExchangePage.PUSH_NOTIFICATION.equals(action)) {
            pages.open(owner, NOTIFICATION, Map.of(), ignored -> {});
        }
    }

    private void toggleHud() {
        if (hud == null || hud.closed().toCompletableFuture().isDone()) {
            hud = pages.open(owner, HUD, Map.of(), ignored -> {});
        } else {
            hud.close();
            hud = null;
        }
    }

    private static UiPageDefinition passive(UiId id, UiPageType type, String rootType, String label) {
        return UiPageDefinition.builder(id).type(type).title(text(label + ".title"))
                .root("root", rootType, root -> root.child("detail", "text", node -> node.text(text(label + ".detail"))))
                .build();
    }

    private static String text(String key) {
        return ExampleUiModule.label("pages." + key).getString();
    }
}
