// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

/** Stable symbolic errors carried on the wire without stack traces or business data. */
public enum UiErrorCode {
    CLIENT_UNAVAILABLE,
    PROTOCOL_INCOMPATIBLE,
    SCHEMA_INVALID,
    PAGE_NOT_FOUND,
    COMPONENT_MISSING,
    CAPABILITY_MISSING,
    PERMISSION_DENIED,
    SESSION_EXPIRED,
    ACTION_INVALID,
    ACTION_BUSY,
    CONFIRMATION_REQUIRED,
    CONFIRMATION_EXPIRED,
    ACTION_CANCELLED,
    INVALID_INTERACTION,
    ACTION_TIMEOUT,
    CONTAINER_UNAVAILABLE,
    RATE_LIMITED,
    RESOURCE_LIMIT_EXCEEDED,
    INTERNAL_ERROR,
    OPEN_TIMEOUT,
    REVISION_MISMATCH,
    SESSION_CLOSED
}
