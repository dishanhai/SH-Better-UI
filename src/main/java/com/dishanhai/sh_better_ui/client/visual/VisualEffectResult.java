// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

/** Outcome of a visual operation. Invalid arguments throw IllegalArgumentException. */
public enum VisualEffectResult {
    DRAWN, FALLBACK, ACCEPTED, CONFLICT, EMPTY_REGION, FULLY_CLIPPED,
    UNSUPPORTED_TRANSFORM, OUTSIDE_SCREEN_PHASE, STALE_FRAME, INVALID_SCOPE,
    DISABLED, UNAVAILABLE, INVALID_TEXTURE, FAILED
}
