// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.Objects;

public record UiActionDefinition(UiId id, UiActionPolicy policy, UiDataSchema payloadSchema) {
    public UiActionDefinition {
        PageNames.id(id);
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(payloadSchema, "payloadSchema");
    }
}
