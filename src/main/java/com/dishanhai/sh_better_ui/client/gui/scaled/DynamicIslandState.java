// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.Objects;

/**
 * Per-view native island lifecycle. Inspired by MuoniumPlayer's separation of ambient,
 * notification and scene visibility; no MP runtime or global mutable state is required.
 * All times are monotonic milliseconds supplied by the host.
 */
public final class DynamicIslandState {
    public static final long NOTIFICATION_MS = 3500;
    public static final long HOVER_GRACE_MS = 220;
    private boolean pinned, hovered, initialized, noticeActive;
    private boolean surfaceAllowed = true, ambientAllowed = true;
    private String track;
    private long notificationUntil, hoverUntil, hoverStarted, hoverDelayMs, lastTime = Long.MIN_VALUE;

    /** Player-list and hidden HUD always win, including over a pending notification. */
    public void setScene(boolean inWorld, boolean playerListVisible, boolean hudHidden, boolean playerUiOpen) {
        surfaceAllowed = inWorld && !playerListVisible && !hudHidden;
        ambientAllowed = !playerUiOpen;
    }

    public void setPinned(boolean value) { pinned = value; }
    public boolean pinned() { return pinned; }

    public void setHoverDelayMs(long delayMs) { hoverDelayMs = Math.max(0, Math.min(1000, delayMs)); }

    public void setHovered(boolean value, long nowMs) {
        clock(nowMs);
        if (!hovered && value) hoverStarted = nowMs;
        if (hovered && !value && nowMs >= deadline(hoverStarted, hoverDelayMs))
            hoverUntil = deadline(nowMs, HOVER_GRACE_MS);
        hovered = value;
    }

    /** First observation is not a change. Missing media clears stale track notifications. */
    public void observeTrack(String identity, long nowMs) {
        clock(nowMs);
        if (initialized && identity != null && !Objects.equals(track, identity)) {
            notificationUntil = deadline(nowMs, NOTIFICATION_MS);
        }
        if (identity == null) notificationUntil = 0;
        track = identity;
        initialized = true;
    }

    public void setNoticeActive(boolean active) { noticeActive = active; }

    public boolean notificationActive(long nowMs) {
        clock(nowMs);
        return noticeActive || nowMs < notificationUntil;
    }

    public boolean visible(long nowMs) {
        return surfaceAllowed && (ambientAllowed || notificationActive(nowMs));
    }

    public boolean expanded(long nowMs) {
        clock(nowMs);
        return visible(nowMs) && (pinned || (hovered && nowMs >= deadline(hoverStarted, hoverDelayMs))
                || nowMs < hoverUntil || notificationActive(nowMs));
    }

    /** Explicit close suppresses transient reasons too, until a new hover/track event. */
    public void dismiss() {
        pinned = hovered = noticeActive = false;
        hoverUntil = notificationUntil = 0;
    }

    /** Suspend must not resurrect old requests when a screen or world is reopened. */
    public void suspend() {
        dismiss();
        initialized = false;
        track = null;
        lastTime = Long.MIN_VALUE;
    }

    private void clock(long nowMs) {
        if (lastTime != Long.MIN_VALUE && nowMs < lastTime) {
            hoverUntil = notificationUntil = 0;
            if (hovered) hoverStarted = nowMs;
        }
        lastTime = nowMs;
    }

    private static long deadline(long nowMs, long duration) {
        return nowMs > Long.MAX_VALUE - duration ? Long.MAX_VALUE : nowMs + duration;
    }
}
