// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.Objects;
import java.util.function.Consumer;

/** Client-thread state machine. All timestamps use the same monotonic millisecond clock. */
public final class FuseButtonState {
    public enum Phase { IDLE, ARMED, SETTLED }
    public enum CommitOn { PRESS, FUSE_END }
    public enum Settle { RESET, STAY }

    public record Options(long undoWindowMs, CommitOn commitOn, boolean pauseOnHover, Settle settle) {
        public static final Options DEFAULT = new Options(4_000, CommitOn.PRESS, true, Settle.RESET);

        public Options {
            if (undoWindowMs <= 0) throw new IllegalArgumentException("undoWindowMs must be positive");
            Objects.requireNonNull(commitOn, "commitOn");
            Objects.requireNonNull(settle, "settle");
        }
    }

    /** Expiry runs fuseEnd, then a deferred commit, then the phase notification. */
    public record Callbacks(Consumer<CommitOn> commit, Runnable undo, Runnable fuseEnd, Consumer<Phase> phaseChange) {
        public Callbacks {
            Objects.requireNonNull(commit, "commit");
            Objects.requireNonNull(undo, "undo");
            Objects.requireNonNull(fuseEnd, "fuseEnd");
            Objects.requireNonNull(phaseChange, "phaseChange");
        }

        public Callbacks(Consumer<CommitOn> commit, Runnable undo) {
            this(commit, undo, () -> {}, phase -> {});
        }
    }

    private final Options options;
    private final Callbacks callbacks;
    private Phase phase = Phase.IDLE;
    private long lastUpdate;
    private long remaining;
    private boolean pointerLeft;
    private boolean paused;
    private boolean notifying;

    public FuseButtonState(Options options, Callbacks callbacks) {
        this.options = Objects.requireNonNull(options, "options");
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    public Phase phase() { return phase; }

    public float fractionRemaining(long now) {
        if (phase != Phase.ARMED) return 0f;
        return (float) ((double) (remaining - elapsed(now)) / options.undoWindowMs());
    }

    private long elapsed(long now) {
        return paused ? 0 : Math.min(remaining, Math.max(0, now - lastUpdate));
    }

    public boolean arm(long now, boolean hovered, boolean windowActive) {
        if (phase != Phase.IDLE || notifying) return false;
        lastUpdate = now;
        remaining = options.undoWindowMs();
        pointerLeft = !hovered;
        paused = !windowActive;
        Runnable commit = options.commitOn() == CommitOn.PRESS ? () -> callbacks.commit().accept(CommitOn.PRESS) : null;
        transition(Phase.ARMED, commit);
        return true;
    }

    /** Update using the previous pause state, then apply current focus and hover. */
    public void advance(long now, boolean hovered, boolean windowActive) {
        if (phase != Phase.ARMED || notifying) return;
        remaining -= elapsed(now);
        lastUpdate = now;
        if (remaining == 0) {
            transition(options.settle() == Settle.STAY ? Phase.SETTLED : Phase.IDLE, () -> {
                callbacks.fuseEnd().run();
                if (options.commitOn() == CommitOn.FUSE_END) callbacks.commit().accept(CommitOn.FUSE_END);
            });
            return;
        }
        if (windowActive && !hovered) pointerLeft = true;
        paused = !windowActive || options.pauseOnHover() && pointerLeft && hovered;
    }

    /** Undo both policies. For deferred commits the callback observes cancellation. */
    public boolean undo() {
        if (phase != Phase.ARMED || notifying) return false;
        transition(Phase.IDLE, callbacks.undo());
        return true;
    }

    private void transition(Phase next, Runnable action) {
        phase = next;
        notifying = true;
        try {
            if (action != null) action.run();
        } finally {
            try {
                callbacks.phaseChange().accept(next);
            } finally {
                notifying = false;
            }
        }
    }
}
