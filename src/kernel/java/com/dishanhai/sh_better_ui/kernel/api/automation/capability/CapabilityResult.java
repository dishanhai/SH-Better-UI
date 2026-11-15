// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.capability;

import java.util.Map;
import java.util.Objects;

/** Machine-readable status; hosts localize codes instead of exposing provider exception messages. */
public record CapabilityResult(Status status, Code code, Map<String, CapabilityValue> values) {
    public enum Status { SUCCESS, NOT_EXECUTED, UNKNOWN }
    public enum Code {
        OK, UNAVAILABLE, PERMISSION_DENIED, INVALID_PARAMETERS, TASK_ID_REQUIRED, TASK_CONFLICT,
        DEDUP_CAPACITY, SESSION_CLOSED, SERVER_UNAVAILABLE, PROVIDER_FAILURE, INVALID_RESULT, REVOKED,
        PROVIDER_REJECTED
    }
    public CapabilityResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(code, "code");
        values = CapabilityValue.copyFields(values);
        if ((status == Status.SUCCESS) != (code == Code.OK)) throw new IllegalArgumentException("Status/code mismatch");
        if (status != Status.SUCCESS && !values.isEmpty()) throw new IllegalArgumentException("Failure cannot contain success values");
    }
    public static CapabilityResult success(Map<String, CapabilityValue> values) {
        return new CapabilityResult(Status.SUCCESS, Code.OK, values);
    }
    public static CapabilityResult notExecuted(Code code) {
        return new CapabilityResult(Status.NOT_EXECUTED, code, Map.of());
    }
    public static CapabilityResult unknown(Code code) {
        return new CapabilityResult(Status.UNKNOWN, code, Map.of());
    }
}
