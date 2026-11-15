// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.capability;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import java.util.Objects;

public record CapabilityDescriptor(UiId id, int contractVersion, UiId provider, UiVersion providerVersion,
        CapabilitySchema parameters, CapabilitySchema result, Access access, UiId permission,
        Deduplication deduplication) {
    public enum Access { READ, WRITE }
    /** Task deduplication lasts for one server registry lifetime; it is not crash persistence. */
    public enum Deduplication { NONE, TASK_ID_REQUIRED }
    public CapabilityDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(deduplication, "deduplication");
        if (contractVersion < 1) throw new IllegalArgumentException("Contract version must be positive");
    }
}
