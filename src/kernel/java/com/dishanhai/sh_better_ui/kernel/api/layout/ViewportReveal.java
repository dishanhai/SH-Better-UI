// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import java.util.Objects;

/** One interruptible viewport entrance; reusable for rows, cards and their overlays. */
public final class ViewportReveal {
    public enum Animation { POP, SLIDE, FADE, NONE }
    public record Pose(double y, double scale, double opacity) {}
    private static final Pose REST = new Pose(0, 1, 1);
    // The supplied CSS linear() spring, sampled at uniformly spaced stops.
    private static final double[] SPRING = {0, .0603, .2033, .3821, .5633, .7258, .8584, .9574,
            1.0242, 1.0634, 1.0809, 1.0828, 1.0747, 1.0613, 1.046, 1.0313, 1.0186, 1.0085,
            1.0012, .9965, .994, .9931, .9933, .9941, .9953, .9966, .9978, .9988, 1};
    private Pose from = new Pose(12, .8, 0), to = from;
    private long changedAt, delay;
    private int state = 1;
    private Animation animation = Animation.POP;
    private boolean initialized, seen, reduced;

    public boolean seen() { return seen; }

    /** An item enters when at least half of its viewport-clamped height overlaps. */
    public void update(double top, double height, double viewport, Animation mode, boolean once,
                       long delayMs, long now, boolean reducedMotion) {
        Objects.requireNonNull(mode);
        if (!Double.isFinite(top) || !Double.isFinite(height) || !Double.isFinite(viewport)
                || height <= 0 || viewport < 0 || delayMs < 0) throw new IllegalArgumentException("Invalid reveal bounds");
        double overlap = Math.min(viewport, top + height) - Math.max(0, top);
        boolean hidden = mode != Animation.NONE && (viewport == 0 || overlap < Math.min(height, viewport) * .5)
                && !(once && seen);
        int next = hidden ? (top + height / 2 < viewport / 2 ? -1 : 1) : 0;
        Animation effective = reducedMotion && mode != Animation.NONE ? Animation.FADE : mode;
        if (initialized && state == next && animation == effective && reduced == reducedMotion) return;
        Pose previous = initialized ? sample(now) : hidden(effective, next == 0 ? 1 : next);
        animation = effective;
        reduced = reducedMotion;
        state = next;
        from = reducedMotion ? new Pose(0, 1, previous.opacity()) : previous;
        to = hidden ? hidden(effective, next) : REST;
        if (reducedMotion) to = new Pose(0, 1, to.opacity());
        changedAt = now;
        delay = hidden || reducedMotion ? 0 : delayMs;
        if (mode == Animation.NONE) from = to = REST;
        initialized = true;
        if (!hidden) seen = true;
    }

    public Pose sample(long now) {
        double elapsed = Math.max(0, now - (double) changedAt - delay);
        double t = Math.min(1, elapsed / 620);
        double position = t * (SPRING.length - 1);
        int index = Math.min(SPRING.length - 2, (int) position);
        double spring = SPRING[index] + (SPRING[index + 1] - SPRING[index]) * (position - index);
        double alpha = opacityEase(Math.min(1, elapsed / (reduced ? 120 : 300)));
        return new Pose(from.y() + (to.y() - from.y()) * spring,
                from.scale() + (to.scale() - from.scale()) * spring,
                Math.max(0, Math.min(1, from.opacity() + (to.opacity() - from.opacity()) * alpha)));
    }

    private static Pose hidden(Animation mode, int direction) {
        return switch (mode) {
            case POP -> new Pose(direction * 12, .8, 0);
            case SLIDE -> new Pose(direction * 28, .98, 0);
            case FADE -> new Pose(direction * 4, 1, 0);
            case NONE -> REST;
        };
    }

    // Invert the X coordinate of CSS cubic-bezier(.22, 1, .36, 1).
    private static double opacityEase(double progress) {
        if (progress <= 0 || progress >= 1) return progress;
        double low = 0, high = 1;
        for (int i = 0; i < 20; i++) {
            double t = (low + high) / 2, inverse = 1 - t;
            double x = 3 * inverse * inverse * t * .22 + 3 * inverse * t * t * .36 + t * t * t;
            if (x < progress) low = t;
            else high = t;
        }
        return 1 - Math.pow(1 - (low + high) / 2, 3);
    }
}
