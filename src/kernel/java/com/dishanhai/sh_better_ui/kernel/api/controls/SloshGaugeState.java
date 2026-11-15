// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

/** Platform-independent level, drag gesture and liquid surface for SloshGauge. */
public final class SloshGaugeState {
    private static final double STEP = 1.0 / 120;
    private static final double GRAB_BAND = 10;

    public record Motion(double viscosity, double tilt, double splash) {
        public static final Motion DEFAULT = new Motion(0.15, 0.45, 0.42);

        public Motion {
            viscosity = clamp(viscosity, 0, 1);
            tilt = clamp(tilt, 0, 1);
            splash = clamp(splash, 0, 1);
        }
    }

    public record Surface(double level, double angleRadians) {
        public double topAt(double x, double width, double height) {
            return (1 - level / 100) * height + (width / 2 - x) * Math.tan(angleRadians);
        }
    }

    private final Motion motion;
    private int value;
    private double level;
    private double velocity;
    private long lastFrame;
    private boolean dragging;
    private int startValue;
    private double grabOffset;

    public SloshGaugeState(int value, Motion motion) {
        this.motion = java.util.Objects.requireNonNull(motion);
        setValue(value);
        snap();
    }

    public int value() { return value; }
    public boolean dragging() { return dragging; }

    public void setValue(int next) {
        value = Math.max(0, Math.min(100, next));
    }

    public void snap() {
        level = value;
        velocity = 0;
        lastFrame = 0;
    }

    public boolean press(double y, double height) {
        if (dragging || !(height > 0)) return false;
        dragging = true;
        startValue = value;
        double at = levelAt(y, height);
        double markerY = (1 - value / 100.0) * height;
        grabOffset = Math.abs(markerY - y) <= GRAB_BAND ? value - at : 0;
        drag(y, height);
        return true;
    }

    public boolean drag(double y, double height) {
        if (!dragging || !(height > 0)) return false;
        setValue((int) Math.round(clamp(levelAt(y, height) + grabOffset, 0, 100)));
        return true;
    }

    public boolean release(double y, double height) {
        if (!drag(y, height)) return false;
        dragging = false;
        return true;
    }

    public boolean cancel() {
        if (!dragging) return false;
        dragging = false;
        setValue(startValue);
        return true;
    }

    public Surface sample(long nowNanos, boolean reducedMotion) {
        if (reducedMotion || motion.viscosity() == 0 || (level == value && velocity == 0)) {
            snap();
            return new Surface(level, 0);
        }
        double dt = lastFrame == 0 ? STEP : clamp((nowNanos - lastFrame) / 1e9, 0, 0.05);
        lastFrame = nowNanos;
        double stiffness = 1224 - 1044 * motion.viscosity();
        double damping = 2 * (0.26 - 0.17 * motion.viscosity()) * Math.sqrt(stiffness);
        int steps = (int) Math.ceil(dt / STEP);
        for (int step = 0; step < steps; step++) {
            double h = dt / steps;
            velocity = velocity * Math.exp(-damping * h) + stiffness * (value - level) * h;
            level += velocity * h;
            if (level < 0 || level > 100) {
                level = clamp(level, 0, 100);
                velocity = -velocity * motion.splash();
            }
        }
        if (Math.abs(level - value) < 0.05 && Math.abs(velocity) < 2) snap();
        double angle = clamp(motion.tilt() * velocity * 0.07, -30, 30);
        return new Surface(level, Math.toRadians(angle));
    }

    private static double levelAt(double y, double height) {
        return (1 - y / height) * 100;
    }

    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }
}
