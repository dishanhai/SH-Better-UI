package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

public record ResourceDataKey<T>(UiId id, Class<T> type) {
    public ResourceDataKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
