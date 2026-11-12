// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** An open operation is successful after the client has reported DISPLAYED. */
public record UiOpenResult(UUID instanceId, Status status, Optional<UiErrorCode> error) {
    public enum Status { RECEIVED, AUTHORIZED, CONFIRMED, DISPLAYED, REJECTED, TIMED_OUT, CLOSED }

    public UiOpenResult {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(error, "error");
        boolean failure = status == Status.REJECTED || status == Status.TIMED_OUT || status == Status.CLOSED;
        if (failure != error.isPresent()) {
            throw new IllegalArgumentException("Open result status and error disagree");
        }
    }

    public static UiOpenResult rejected(UiErrorCode error) {
        return new UiOpenResult(UUID.randomUUID(), Status.REJECTED, Optional.of(error));
    }
}
