// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.Objects;

/** Platform-independent swipe, settle and collapse state. Distances use GUI units and times use monotonic milliseconds. */
public final class SwipeRowState {
    public enum Direction {
        LEFT(-1), RIGHT(1);

        private final int sign;

        Direction(int sign) { this.sign = sign; }

        public int sign() { return sign; }
    }

    public record Options(Direction direction, int actionWidth, double resistance, double commitAt,
                          double snapBounce, int collapseMs, boolean fullSwipe, boolean closeOnAction) {
        public static final Options DEFAULT = new Options(Direction.LEFT, 80, .55, .6, .2, 200, true, true);

        public Options {
            Objects.requireNonNull(direction);
            if (actionWidth <= 0 || !(resistance > 0 && resistance <= 1) || !(commitAt > 0 && commitAt <= 1)
                    || !(snapBounce >= 0 && snapBounce <= 1) || collapseMs < 0) {
                throw new IllegalArgumentException("Invalid swipe row options");
            }
        }
    }

    private static final int FLICK_SPEED = 110;
    private static final int SETTLE_MS = 220;
    private static final int FILL_MS = 140;

    private final Options options;
    private final int count;
    private int width;
    private boolean open;
    private boolean dragging;
    private double dragOrigin;
    private double offset;
    private double from;
    private double overshoot;
    private long settleAt;
    private int pending = -1;
    private boolean collapsed;
    private long commitAt;

    public SwipeRowState(Options options, int actionCount) {
        this.options = Objects.requireNonNull(options);
        if (actionCount < 1) throw new IllegalArgumentException("actionCount");
        count = actionCount;
    }

    public double actionWidth() { return Math.min(options.actionWidth(), (double) width / count); }
    public double drawerWidth() { return actionWidth() * count; }
    public double offset() { return offset; }
    public double visualOffset(long now, boolean reducedMotion) {
        if (pending != 0) return offset;
        return reducedMotion ? width : from + (width - from) * ease(progress(now - commitAt, FILL_MS));
    }
    public boolean open() { return open; }
    public boolean dragging() { return dragging; }
    public boolean collapsed() { return collapsed; }
    public int pendingAction() { return pending; }
    public boolean armed() { return dragging && canCommit() && offset >= threshold(); }

    public void resize(int width) {
        width = Math.max(0, width);
        if (this.width == width) return;
        double scale = this.width == 0 ? 0 : (double) width / this.width;
        this.width = width;
        dragging = false;
        if (pending >= 0) {
            from *= scale;
            offset *= scale;
        } else from = offset = target();
        overshoot = 0;
    }

    /** Starts from the current animated position so grabbing a settling row does not jump. */
    public void begin() {
        if (pending >= 0 || collapsed || width == 0) return;
        dragging = true;
        double drawer = drawerWidth();
        if (offset < 0) dragOrigin = inverseRubber(offset);
        else if (offset <= drawer) dragOrigin = offset;
        else if (!canCommit()) dragOrigin = drawer + inverseRubber(offset - drawer);
        else if (offset <= threshold()) dragOrigin = drawer + (offset - drawer) / options.resistance();
        else dragOrigin = knee() + inverseRubber(offset - threshold());
    }

    public void drag(double dx) {
        if (!dragging || !Double.isFinite(dx)) return;
        double raw = dragOrigin + options.direction().sign() * dx;
        double drawer = drawerWidth();
        if (raw < 0) offset = rubber(raw);
        else if (raw <= drawer) offset = raw;
        else if (!canCommit()) offset = drawer + rubber(raw - drawer);
        else if (raw <= knee()) offset = drawer + options.resistance() * (raw - drawer);
        else offset = threshold() + rubber(raw - knee());
    }

    /** Only the actual release position can commit. Velocity can open or close the drawer. */
    public void release(double velocityX, long now) {
        if (!dragging) return;
        if (armed()) {
            select(0, true, now);
            return;
        }
        double velocity = Double.isFinite(velocityX)
                ? options.direction().sign() * Math.max(-1500, Math.min(1500, velocityX)) : 0;
        double projected = offset + (Math.abs(velocity) >= FLICK_SPEED ? velocity * .12 : 0);
        settle(projected >= drawerWidth() / 2, velocity, now);
    }

    public void setOpen(boolean open, long now) {
        if (pending < 0 && !collapsed) settle(open, 0, now);
    }

    public void cancel(long now) {
        if (dragging) settle(open, 0, now);
    }

    public void select(int action, boolean dismiss, long now) {
        if (action < 0 || action >= count || pending >= 0 || collapsed) return;
        if (action == 0 || dismiss) {
            pending = action;
            commitAt = now;
            from = offset;
            dragging = open = false;
        } else if (options.closeOnAction()) {
            settle(false, 0, now);
        }
    }

    public void reset() {
        open = dragging = collapsed = false;
        pending = -1;
        offset = from = overshoot = 0;
    }

    /** The host invokes the action callback once this returns a completed action index. */
    public int advance(long now, boolean reducedMotion) {
        if (pending >= 0) {
            if (heightFraction(now, reducedMotion) > 0) return -1;
            int action = pending;
            pending = -1;
            collapsed = true;
            return action;
        }
        if (!dragging && !collapsed) {
            double t = reducedMotion ? 1 : progress(now - settleAt, SETTLE_MS);
            offset = target() + (from - target()) * (1 - ease(t))
                    + overshoot * Math.sin(Math.PI * t) * (1 - t);
        }
        return -1;
    }

    public double heightFraction(long now, boolean reducedMotion) {
        if (collapsed) return 0;
        if (pending < 0) return 1;
        if (reducedMotion) return 0;
        long elapsed = now - commitAt - (pending == 0 ? FILL_MS : 0);
        return elapsed < 0 ? 1 : 1 - ease(progress(elapsed, options.collapseMs()));
    }

    private void settle(boolean open, double velocity, long now) {
        dragging = false;
        this.open = open;
        from = offset;
        settleAt = now;
        overshoot = Math.abs(velocity) >= FLICK_SPEED
                ? Math.copySign(Math.min(actionWidth(), Math.abs(velocity) * .04) * options.snapBounce(), velocity) : 0;
    }

    private double target() { return open ? drawerWidth() : 0; }
    private double threshold() { return Math.max(width * options.commitAt(), drawerWidth() + actionWidth() / 2); }
    private boolean canCommit() { return options.fullSwipe() && width > 0 && threshold() <= width; }
    private double knee() { return drawerWidth() + (threshold() - drawerWidth()) / options.resistance(); }
    private double rubber(double distance) {
        return width * (distance / (width / options.resistance() + Math.abs(distance)));
    }
    private double inverseRubber(double distance) {
        return distance * width / (options.resistance() * Math.max(1, width - Math.abs(distance)));
    }
    private static double progress(long elapsed, int duration) {
        return duration == 0 ? 1 : Math.max(0, Math.min(1, (double) elapsed / duration));
    }
    private static double ease(double t) { return 1 - Math.pow(1 - t, 3); }
}
