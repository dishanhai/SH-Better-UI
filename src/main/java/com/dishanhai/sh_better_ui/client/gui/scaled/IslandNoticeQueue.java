// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** Per-view bounded queue with visible-time budgets and independent real-time expiry.
 * Inspired by MuoniumPlayer's priority/resume model; all timestamps use the host's monotonic clock.
 */
public final class IslandNoticeQueue {
    public enum Priority { LOW, NORMAL, INSTANT, HIGH }
    public record Notice(String id, String title, String detail, double progress, long holdMs,
                         Priority priority, long expiresAtMs) {
        public Notice(String id, String title, String detail, double progress, long holdMs) {
            this(id, title, detail, progress, holdMs, Priority.NORMAL, 0);
        }
        public Notice {
            Objects.requireNonNull(id); Objects.requireNonNull(title); Objects.requireNonNull(detail);
            Objects.requireNonNull(priority);
            if (id.isBlank()) throw new IllegalArgumentException("id");
            if (holdMs < 250 || holdMs > 30000) throw new IllegalArgumentException("holdMs");
            if (expiresAtMs < 0) throw new IllegalArgumentException("expiresAtMs");
            if (!Double.isFinite(progress)) progress = -1;
            progress = Math.max(-1, Math.min(1, progress));
        }
    }
    private static final int LIMIT = 8;
    private static final class Entry {
        Notice notice;
        long remaining;
        long resumeProtection;
        boolean interrupted;
        Entry(Notice notice) { this.notice = notice; remaining = notice.holdMs(); }
        void update(Notice replacement) {
            long oldExpiry = notice.expiresAtMs(), newExpiry = replacement.expiresAtMs();
            long expiry = oldExpiry == 0 ? newExpiry : newExpiry == 0 ? oldExpiry : Math.min(oldExpiry, newExpiry);
            notice = expiry == newExpiry ? replacement : new Notice(replacement.id(), replacement.title(),
                    replacement.detail(), replacement.progress(), replacement.holdMs(), replacement.priority(), expiry);
        }
        boolean expired(long now) { return notice.expiresAtMs() != 0 && now >= notice.expiresAtMs(); }
    }
    private final ArrayList<Entry> waiting = new ArrayList<>();
    private Entry active;
    private long lastTime = Long.MIN_VALUE;
    private boolean previouslyPresented;

    /** Stable IDs replace content without restarting the visible budget or queue position. */
    public void offer(Notice notice) {
        Objects.requireNonNull(notice);
        if (active != null && active.notice.id().equals(notice.id())) {
            // Existing acknowledgement belongs to this entry's budget, not its latest text.
            active.update(notice); return;
        }
        for (var entry : waiting) {
            if (entry.notice.id().equals(notice.id())) { entry.update(notice); return; }
        }
        waiting.add(new Entry(notice));
        trim();
    }

    /** Legacy standalone callers present every sample. Hidden hosts should use the overload. */
    public Optional<Notice> sample(long nowMs) { return sample(nowMs, true); }

    public Optional<Notice> sample(long nowMs, boolean presented) {
        return advance(nowMs, presented, false);
    }

    /** Inspect/advance without claiming that this frame was drawn. */
    public Optional<Notice> peek(long nowMs) { return advance(nowMs, false, true); }

    /** Post-draw acknowledgement: an outdated render must not charge a replacement notice. */
    public void presented(Notice notice, long nowMs) {
        if (active != null && active.notice == notice && lastTime == nowMs) previouslyPresented = true;
    }

    private Optional<Notice> advance(long nowMs, boolean presented, boolean renderClock) {
        if (lastTime != Long.MIN_VALUE && nowMs < lastTime) clear();
        if (active != null && previouslyPresented && (presented || renderClock) && lastTime != Long.MIN_VALUE) {
            long elapsed = nowMs - lastTime;
            if (renderClock) elapsed = Math.min(250, elapsed);
            active.remaining = Math.max(0, active.remaining - elapsed);
            active.resumeProtection = Math.max(0, active.resumeProtection - elapsed);
        }
        boolean sameFrameAck = renderClock && lastTime == nowMs && previouslyPresented;
        Entry previous = active;
        lastTime = nowMs;
        previouslyPresented = presented || sameFrameAck;
        waiting.removeIf(entry -> entry.expired(nowMs));
        if (active != null && (active.expired(nowMs) || active.remaining == 0)) active = null;
        int next = best();
        if (next >= 0 && (active == null || preempts(waiting.get(next), active))) {
            Entry incoming = waiting.remove(next);
            // Resume before equal-priority peers, preserving only its unconsumed display budget.
            if (active != null) { active.interrupted = true; waiting.add(0, active); }
            active = incoming;
            if (active.interrupted) {
                active.resumeProtection = 700;
                active.interrupted = false;
            }
            trim();
        }
        if (active != previous && renderClock) previouslyPresented = false;
        return Optional.ofNullable(active == null ? null : active.notice);
    }

    /** Pause immediately when a host stops drawing, even if it no longer calls sample. */
    public void pause() { previouslyPresented = false; }
    public void cancel(String id) {
        Objects.requireNonNull(id);
        if (active != null && active.notice.id().equals(id)) { active = null; previouslyPresented = false; }
        waiting.removeIf(entry -> entry.notice.id().equals(id));
    }
    private static boolean preempts(Entry incoming, Entry current) {
        var priority = incoming.notice.priority();
        if (priority.ordinal() < current.notice.priority().ordinal()) return false;
        if (current.resumeProtection > 0 && priority != Priority.HIGH) return false;
        if (priority.ordinal() > current.notice.priority().ordinal()) return true;
        return priority == Priority.INSTANT && !incoming.interrupted;
    }
    private int best() {
        int best = -1;
        for (int i = 0; i < waiting.size(); i++) {
            if (active != null && !preempts(waiting.get(i), active)) continue;
            if (best < 0 || waiting.get(i).notice.priority().ordinal()
                    > waiting.get(best).notice.priority().ordinal()) best = i;
        }
        return best;
    }
    private void trim() {
        while (waiting.size() > LIMIT) {
            int lowest = 0;
            for (int i = 1; i < waiting.size(); i++) {
                if (waiting.get(i).notice.priority().ordinal()
                        < waiting.get(lowest).notice.priority().ordinal()) lowest = i;
            }
            waiting.remove(lowest);
        }
    }
    public void dismiss() { active = null; previouslyPresented = false; }
    public void clear() { active = null; waiting.clear(); lastTime = Long.MIN_VALUE; previouslyPresented = false; }
}
