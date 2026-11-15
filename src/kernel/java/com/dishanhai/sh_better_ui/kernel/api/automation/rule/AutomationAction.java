// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.rule;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 模板、图和脚本共用的写入入口数据；身份必须从服务端会话取得。 */
public record AutomationAction(UUID owner, String targetKey, String kind,
                               Map<String, String> arguments, long amount) {
    public AutomationAction {
        Objects.requireNonNull(owner);
        if (targetKey == null || targetKey.isBlank() || kind == null || kind.isBlank() || amount <= 0) {
            throw new IllegalArgumentException("Invalid action");
        }
        arguments = Map.copyOf(arguments);
    }

    public static AutomationAction replenish(ReplenishmentRule rule, long amount) {
        return new AutomationAction(rule.owner(), rule.targetKey(), "replenish",
                Map.of("item", rule.item()), amount);
    }
}
