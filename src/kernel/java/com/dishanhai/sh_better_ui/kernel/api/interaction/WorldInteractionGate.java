package com.dishanhai.sh_better_ui.kernel.api.interaction;

import java.util.Objects;

/** Author: dishanhai. Shared input policy for world-space UI; clocks are supplied by the host. */
public final class WorldInteractionGate {
    private final long window;
    private String action;
    private long armedAt;

    /** The window uses the same monotonic unit as the supplied timestamps. */
    public WorldInteractionGate(long window) {
        if (window <= 0) throw new IllegalArgumentException("window must be positive");
        this.window = window;
    }

    /** First tap arms; a second tap on the unchanged action confirms exactly once. */
    public boolean tap(String nextAction, long now) {
        Objects.requireNonNull(nextAction, "action");
        if (nextAction.isEmpty()) { cancel(); return false; }
        if (nextAction.equals(action) && now >= armedAt && now - armedAt <= window) {
            cancel();
            return true;
        }
        action = nextAction;
        armedAt = now;
        return false;
    }

    public boolean pending(long now) {
        if (action != null && (now < armedAt || now - armedAt > window)) cancel();
        return action != null;
    }

    public void cancel() { action = null; armedAt = 0; }

    /** Local +Z faces the viewer. Equal-depth blockers win to avoid clicking through a wall. */
    public static boolean visible(double distance, double blockerDistance, double localOriginZ,
                                  double localDirectionZ) {
        return Double.isFinite(distance) && distance > 0 && !Double.isNaN(blockerDistance)
                && Double.isFinite(localOriginZ) && Double.isFinite(localDirectionZ)
                && localOriginZ > 1e-8 && localDirectionZ < -1e-8
                && distance + 1e-5 < blockerDistance;
    }
}
