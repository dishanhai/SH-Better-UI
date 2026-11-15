// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.rule;

import java.util.Objects;
import java.util.UUID;

/** 服务端拥有的版本化补货规则；数量均为实际物品数量。 */
public record ReplenishmentRule(UUID id, long revision, UUID owner, String targetKey,
                                String item, long lower, long target, Mode mode, boolean enabled) {
    public enum Mode { PAGE_OPEN, PLAYER_ONLINE }

    public ReplenishmentRule {
        Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(mode);
        if (revision < 1 || lower < 0 || target < lower || target == 0) {
            throw new IllegalArgumentException("Invalid rule version or quantity");
        }
        if (targetKey == null || targetKey.isBlank() || item == null || item.isBlank()) {
            throw new IllegalArgumentException("Missing target or item");
        }
    }
}
