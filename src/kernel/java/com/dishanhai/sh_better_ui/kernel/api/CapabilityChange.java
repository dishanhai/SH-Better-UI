package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

public record CapabilityChange(long generation, CapabilityResolution previous, CapabilityResolution current) {
    public CapabilityChange {
        if (generation < 0) throw new IllegalArgumentException("Negative capability generation");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
    }
}
