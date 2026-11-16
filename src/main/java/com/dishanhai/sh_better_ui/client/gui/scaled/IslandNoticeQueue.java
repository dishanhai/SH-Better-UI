// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;

/** Bounded, per-view notice queue; stable IDs coalesce frequent operation progress updates. */
public final class IslandNoticeQueue {
    public record Notice(String id, String title, String detail, double progress, long holdMs) {
        public Notice {
            Objects.requireNonNull(id); Objects.requireNonNull(title); Objects.requireNonNull(detail);
            if (id.isBlank()) throw new IllegalArgumentException("id");
            if (holdMs < 250 || holdMs > 30000) throw new IllegalArgumentException("holdMs");
            if (!Double.isFinite(progress)) progress = -1;
            progress = Math.max(-1, Math.min(1, progress));
        }
    }
    private static final int LIMIT = 8;
    private final ArrayDeque<Notice> waiting = new ArrayDeque<>();
    private Notice active;
    private long started, lastTime = Long.MIN_VALUE;

    /** Latest waiting value wins without disturbing its FIFO position. */
    public void offer(Notice notice) {
        Objects.requireNonNull(notice);
        if (active != null && active.id().equals(notice.id())) { active = notice; return; }
        var replaced = new ArrayDeque<Notice>();
        boolean found = false;
        for (var entry : waiting) {
            boolean match = entry.id().equals(notice.id());
            replaced.add(match ? notice : entry);
            found |= match;
        }
        if (!found) {
            if (replaced.size() >= LIMIT) replaced.removeFirst();
            replaced.addLast(notice);
        }
        waiting.clear(); waiting.addAll(replaced);
    }

    public Optional<Notice> sample(long nowMs) {
        if (lastTime != Long.MIN_VALUE && nowMs < lastTime) clear();
        lastTime = nowMs;
        if (active != null && nowMs - started >= active.holdMs()) active = null;
        if (active == null && !waiting.isEmpty()) { active = waiting.removeFirst(); started = nowMs; }
        return Optional.ofNullable(active);
    }
    public void dismiss() { active = null; }
    public void clear() { active = null; waiting.clear(); lastTime = Long.MIN_VALUE; }
}
