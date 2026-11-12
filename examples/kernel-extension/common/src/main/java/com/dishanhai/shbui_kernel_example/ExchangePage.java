// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiActionPolicy;
import com.dishanhai.sh_better_ui.kernel.api.page.UiDataSchema;
import com.dishanhai.sh_better_ui.kernel.api.page.UiPageDefinition;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValue;
import com.dishanhai.sh_better_ui.kernel.api.page.UiValueSchema;

import java.util.Map;
import java.util.function.Function;

/** One exchange schema for the local preview and the server-owned page. */
final class ExchangePage {
    static final String BALANCE = "player.balance";
    static final String SESSION_ACTIVE = "session.active";
    static final int INITIAL_CREDITS = 5;
    static final UiId REDEEM = id("redeem_credit");
    static final UiId TOGGLE_HUD = id("toggle_page_hud");
    static final UiId PUSH_NOTIFICATION = id("push_page_notification");

    private ExchangePage() {}

    static UiPageDefinition create(UiId id, UiActionPolicy policy, Function<String, String> text) {
        boolean local = policy == UiActionPolicy.LOCAL;
        var page = UiPageDefinition.builder(id)
                .title(text.apply("title"))
                .field(BALANCE, UiValueSchema.integer(0, INITIAL_CREDITS))
                .action(new UiActionDefinition(REDEEM, policy, UiDataSchema.EMPTY));
        if (local) {
            page.field(SESSION_ACTIVE, UiValueSchema.bool())
                    .action(new UiActionDefinition(TOGGLE_HUD, policy, UiDataSchema.EMPTY))
                    .action(new UiActionDefinition(PUSH_NOTIFICATION, policy, UiDataSchema.EMPTY));
        }
        return page.root("root", "column", root -> {
            root.child("reward", "text", node -> node.text(text.apply("reward")))
                    .child("balance_label", "text", node -> node.text(text.apply("balance")))
                    .child("balance", "text", node -> node.bind("text", BALANCE))
                    .child("redeem", "button", node -> node.label(text.apply("redeem")).action(REDEEM));
            if (local) {
                root.child("toggle_hud", "button", node -> node.label(text.apply("hud.toggle"))
                                .action(TOGGLE_HUD).bind("visible", SESSION_ACTIVE))
                        .child("push_notice", "button", node -> node.label(text.apply("notification.push"))
                                .action(PUSH_NOTIFICATION).bind("visible", SESSION_ACTIVE));
            }
        }).build();
    }

    static Map<String, UiValue> data(int balance) {
        return Map.of(BALANCE, new UiValue.Int(balance));
    }

    static UiId id(String path) {
        return new UiId(ExampleUiModule.MOD_ID, path);
    }
}
