package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

public record ConfigPersistenceSnapshot(boolean available, long submitted, long completed,
                                        boolean writing, String lastFailure) {
    public ConfigPersistenceSnapshot {
        Objects.requireNonNull(lastFailure, "lastFailure");
    }
}
