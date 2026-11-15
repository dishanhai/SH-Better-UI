// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.motion;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * Pixel-grid loading animation inspired by the React Bits LatticeLoader.
 * A caller owns one instance per operation and supplies monotonic milliseconds from the same clock.
 */
public final class LatticeLoader {
    public enum Status { WORKING, DONE, ERROR }

    public enum Preset {
        ARROW(grid(3, 7.2, 1, .62, (x, y) -> x + Math.abs(y - 1)), null),
        DOTS(grid(3, 3, 2.4, .62, (x, y) -> x), null),
        RIPPLE(grid(3, 4.8, 1.5, .62, (x, y) -> Math.abs(x - 1) + Math.abs(y - 1)), null),
        SPIRAL(grid(3, 9, 1.2, .35, (x, y) -> x == 1 && y == 1 ? 8 : perimeter(3, x, y)), null),
        ORBIT(grid(3, 8, 1.2, .62, (x, y) -> perimeter(3, x, y)),
                grid(4, 12, 1.2, .45, (x, y) -> perimeter(4, x, y))),
        SNAKE(grid(3, 9, 1, .35, (x, y) -> snake(3, x, y)),
                grid(4, 16, 1, .25, (x, y) -> snake(4, x, y))),
        SWEEP(null, grid(4, 7, 1, .45, Integer::sum)),
        SPIN(null, grid(4, 4, 1.6, .35, (x, y) -> y < 2 ? x / 2 : 3 - x / 2)),
        RAIN(null, grid(4, 7, 1.2, .35, (x, y) -> y + ((x * 3) % 4))),
        PULSE(null, grid(4, 2.4, 2.5, .45, (x, y) -> Math.max(1 - x, x - 2) + Math.max(1 - y, y - 2)));

        private final Pattern three;
        private final Pattern four;

        Preset(Pattern three, Pattern four) {
            this.three = three;
            this.four = four;
        }

        /** Unsupported preset/grid combinations use Orbit at 3×3 and Sweep at 4×4. */
        public Pattern pattern(int grid) {
            return switch (grid) {
                case 3 -> three == null ? ORBIT.three : three;
                case 4 -> four == null ? SWEEP.four : four;
                default -> throw new IllegalArgumentException("Grid must be 3 or 4");
            };
        }
    }

    private static Pattern grid(int size, double loop, double scale, double lit, IntBinaryOperator delay) {
        double[] cells = new double[size * size];
        for (int i = 0; i < cells.length; i++) cells[i] = delay.applyAsInt(i % size, i / size);
        return new Pattern(size, loop, scale, lit, cells);
    }

    private static int perimeter(int size, int x, int y) {
        int last = size - 1;
        if (y == 0) return x;
        if (x == last) return last + y;
        if (y == last) return 3 * last - x;
        if (x == 0) return 4 * last - y;
        return -1;
    }

    private static int snake(int size, int x, int y) {
        return y * size + (y % 2 == 0 ? x : size - 1 - x);
    }

    /** Row-major delays in step units. Use -1 for a hole. */
    public static final class Pattern {
        private final int grid;
        private final double loop;
        private final double scale;
        private final double lit;
        private final double[] delays;

        public Pattern(int grid, double loop, double scale, double lit, double... delays) {
            if (grid != 3 && grid != 4 || delays.length != grid * grid
                    || !Double.isFinite(loop) || loop <= 0 || !Double.isFinite(scale) || scale <= 0
                    || !Double.isFinite(lit) || lit <= 0 || lit > 1) {
                throw new IllegalArgumentException("Invalid lattice pattern");
            }
            for (double delay : delays) {
                if (delay != -1 && (!Double.isFinite(delay) || delay < 0)) {
                    throw new IllegalArgumentException("Invalid lattice delay");
                }
            }
            this.grid = grid;
            this.loop = loop;
            this.scale = scale;
            this.lit = lit;
            this.delays = delays.clone();
        }

        public static Pattern custom(int grid, double... delays) {
            return new Pattern(grid, Math.max(0, Arrays.stream(delays).max().orElse(0)) + 4.2,
                    1, .62, delays);
        }

        public int grid() { return grid; }
        public double delay(int cell) { return delays[cell]; }
    }

    private static final long TRANSITION_MS = 200;
    private static final int CHECK_3 = mask(2, 3, 5, 7);
    private static final int CHECK_4 = mask(7, 8, 10, 13);
    private static final int CROSS_3 = mask(0, 2, 4, 6, 8);
    private static final int CROSS_4 = mask(0, 3, 5, 6, 9, 10, 12, 15);

    private Status status = Status.WORKING;
    private double fromWorking = 1;
    private double fromDone;
    private double fromError;
    private long startedAt;
    private long changedAt;
    private long frozenMs;
    private long outgoingWaveMs;

    public LatticeLoader(long nowMs) {
        startedAt = nowMs;
        changedAt = nowMs;
    }

    public Status status() { return status; }

    /** Starts a new operation, including when the current operation is still working. */
    public void restart(long nowMs) {
        if (status != Status.WORKING) changeStatus(Status.WORKING, nowMs);
        startedAt = nowMs;
    }

    public void setStatus(Status next, long nowMs) {
        Objects.requireNonNull(next, "next");
        if (next != status) changeStatus(next, nowMs);
    }

    private void changeStatus(Status next, long nowMs) {
        var current = frame(nowMs, false);
        fromWorking = current.opacity(Status.WORKING);
        fromDone = current.opacity(Status.DONE);
        fromError = current.opacity(Status.ERROR);
        changedAt = nowMs;
        if (status == Status.WORKING) {
            frozenMs = elapsedMs(nowMs);
            outgoingWaveMs = frozenMs;
        }
        if (next == Status.WORKING) startedAt = nowMs;
        status = next;
    }

    public long elapsedMs(long nowMs) {
        return status == Status.WORKING ? Math.max(0, nowMs - startedAt) : frozenMs;
    }

    /** Samples the visual clock separately from the stopwatch, allowing a controlled elapsed display. */
    public Frame frame(long nowMs, boolean reducedMotion) {
        double progress = reducedMotion ? 1 : Math.max(0, Math.min(1, (nowMs - changedAt) / (double) TRANSITION_MS));
        double blend = bezier(progress, .25, .1, .25, 1);
        return new Frame(status, blend(fromWorking, Status.WORKING, blend), blend(fromDone, Status.DONE, blend),
                blend(fromError, Status.ERROR, blend), status == Status.WORKING ? elapsedMs(nowMs) : outgoingWaveMs,
                reducedMotion);
    }

    private double blend(double from, Status layer, double progress) {
        return from + ((layer == status ? 1 : 0) - from) * progress;
    }

    public record Frame(Status status, double workingOpacity, double doneOpacity, double errorOpacity,
                        long waveMs, boolean reducedMotion) {
        public double opacity(Status layer) {
            return switch (layer) {
                case WORKING -> workingOpacity;
                case DONE -> doneOpacity;
                case ERROR -> errorOpacity;
            };
        }

        public double markScale(Status layer) {
            return reducedMotion || layer != status ? 1 : .9 + .1 * opacity(layer);
        }

        /** Linear amount of ink above the idle silhouette, 0–1. */
        public double wave(Pattern pattern, int cell, double stepMs) {
            double delay = pattern.delay(cell);
            if (delay < 0) return 0;
            if (reducedMotion) return .7;
            double unitMs = stepMs * pattern.scale;
            double cycle = Math.max(1, Math.round(pattern.loop * unitMs));
            double active = waveMs - Math.round(delay * unitMs);
            if (active <= 0) return 0;
            double phase = (active % cycle) / cycle;
            double rise = Math.round(pattern.lit * 29) / 100.0;
            double hold = Math.round(pattern.lit * 68) / 100.0;
            if (phase < rise) return waveEase(phase / rise);
            if (phase <= hold) return 1;
            return phase < pattern.lit ? 1 - waveEase((phase - hold) / (pattern.lit - hold)) : 0;
        }

        public boolean marked(Status layer, int grid, int cell) {
            int cells = switch (layer) {
                case WORKING -> 0;
                case DONE -> grid == 4 ? CHECK_4 : CHECK_3;
                case ERROR -> grid == 4 ? CROSS_4 : CROSS_3;
            };
            return (cells & (1 << cell)) != 0;
        }
    }

    private static int mask(int... indices) {
        int mask = 0;
        for (int cell : indices) mask |= 1 << cell;
        return mask;
    }

    private static double waveEase(double progress) {
        return bezier(progress, .77, 0, .175, 1);
    }

    private static double bezier(double progress, double x1, double y1, double x2, double y2) {
        if (progress <= 0 || progress >= 1) return progress;
        double low = 0;
        double high = 1;
        for (int i = 0; i < 12; i++) {
            double t = (low + high) / 2;
            if (coordinate(t, x1, x2) < progress) low = t;
            else high = t;
        }
        return coordinate((low + high) / 2, y1, y2);
    }

    private static double coordinate(double t, double first, double second) {
        double remaining = 1 - t;
        return 3 * remaining * remaining * t * first + 3 * remaining * t * t * second + t * t * t;
    }
}
