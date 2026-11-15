// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.motion;

import java.util.ArrayList;
import java.util.Comparator;

/** Samples a translucent trefoil ribbon in a normalized, square viewport. */
public final class PhotonKnot {
    private static final int SEGMENTS = 96;
    private static final double TURN = Math.PI * 2;
    private static final double[] RAILS = {-1.28, -1, -.81, 0, .81, 1, 1.28};
    private static final double[] OPACITY = {0, .73, .28, .16, .28, .73, 0};
    private static final Comparator<Segment> FAR_FIRST = Comparator.comparingDouble(Segment::depth);

    private long lastFrame = Long.MIN_VALUE;
    private double phase;
    private boolean stopped;

    public void setPaused(boolean paused) {
        stopped = paused;
        suspend();
    }

    public boolean isPaused() {
        return stopped;
    }

    /** Drop the last timestamp when the owning surface becomes hidden. */
    public void suspend() {
        lastFrame = Long.MIN_VALUE;
    }

    /**
     * Emits depth-ordered quads as groups of four vertices. Positions are relative to the
     * viewport center and its shorter side. Colors use straight (non-premultiplied) ARGB.
     */
    public void sample(long timeMs, boolean reducedMotion, int startColor, int endColor, VertexSink sink) {
        if (!stopped && !reducedMotion && lastFrame != Long.MIN_VALUE && timeMs > lastFrame) {
            phase = (phase + Math.min(timeMs - lastFrame, 100) * TURN / 24_000) % TURN;
        }
        lastFrame = stopped || reducedMotion ? Long.MIN_VALUE : timeMs;

        var segments = new ArrayList<Segment>(SEGMENTS);
        Section first = section(0, startColor, endColor);
        Section previous = first;
        for (int index = 1; index <= SEGMENTS; index++) {
            Section next = index == SEGMENTS ? first : section(index * TURN / SEGMENTS, startColor, endColor);
            segments.add(new Segment(previous, next));
            previous = next;
        }
        segments.sort(FAR_FIRST);
        for (Segment segment : segments) {
            emit(segment.a, segment.b, sink);
        }
    }

    private Section section(double t, int start, int end) {
        double azimuth = 2 * t;
        double loop = 3 * t;
        double radial = .24 + .097 * Math.cos(loop);
        double x = radial * Math.cos(azimuth), y = radial * Math.sin(azimuth), z = .097 * Math.sin(loop);

        // The torus normal and its cross product with the curve tangent span the ribbon width.
        double nx = Math.cos(loop) * Math.cos(azimuth), ny = Math.cos(loop) * Math.sin(azimuth), nz = Math.sin(loop);
        double dr = -.291 * Math.sin(loop);
        double tx = dr * Math.cos(azimuth) - 2 * y, ty = dr * Math.sin(azimuth) + 2 * x, tz = .291 * Math.cos(loop);
        double bx = ty * nz - tz * ny, by = tz * nx - tx * nz, bz = tx * ny - ty * nx;
        double twist = 2 * t - phase * 2;
        double across = Math.sin(twist) / Math.sqrt(bx * bx + by * by + bz * bz);
        double normal = Math.cos(twist);
        double wx = .045 * (nx * normal + bx * across);
        double wy = .045 * (ny * normal + by * across);
        double wz = .045 * (nz * normal + bz * across);

        // Keep the crossing readable while the knot turns around a tilted axis.
        double yaw = .58 * Math.sin(phase) + .34;
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        double depth = (-sin * x + cos * z) * .866 - y * .5;
        double px = cos * x + sin * z;
        double py = y * .866 + (-sin * x + cos * z) * .5;
        double pwx = cos * wx + sin * wz;
        double pwy = wy * .866 + (-sin * wx + cos * wz) * .5;
        double turnCos = Math.cos(phase), turnSin = Math.sin(phase);
        double shade = Math.max(.38, Math.min(1, .72 + depth * 1.3));
        double color = .5 + .5 * Math.cos(t - phase);
        return new Section(turnCos * px - turnSin * py, turnSin * px + turnCos * py,
                turnCos * pwx - turnSin * pwy, turnSin * pwx + turnCos * pwy,
                depth, mix(start, end, color), end, shade);
    }

    private static void emit(Section a, Section b, VertexSink sink) {
        double dx = b.x - a.x, dy = b.y - a.y;
        boolean front = (a.wx + b.wx) * dy - (a.wy + b.wy) * dx <= 0;
        for (int rail = 1; rail < RAILS.length; rail++) {
            if (front) {
                a.vertex(rail - 1, sink);
                a.vertex(rail, sink);
                b.vertex(rail, sink);
                b.vertex(rail - 1, sink);
            } else {
                b.vertex(rail - 1, sink);
                b.vertex(rail, sink);
                a.vertex(rail, sink);
                a.vertex(rail - 1, sink);
            }
        }
    }

    private static int mix(int start, int end, double amount) {
        int color = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int from = (start >>> shift) & 255;
            int to = (end >>> shift) & 255;
            color |= (int) Math.round(from + (to - from) * amount) << shift;
        }
        return color;
    }

    private record Section(double x, double y, double wx, double wy, double depth, int rgb, int highlight, double shade) {
        void vertex(int rail, VertexSink sink) {
            int alpha = (int) Math.round(OPACITY[rail] * shade * 255);
            int color = rail == 1 || rail == 5 ? mix(rgb, highlight, .42) : rgb;
            sink.vertex((float) (x + wx * RAILS[rail]), (float) (y + wy * RAILS[rail]), alpha << 24 | color);
        }
    }

    private record Segment(Section a, Section b) {
        double depth() {
            return (a.depth + b.depth) * .5;
        }
    }

    @FunctionalInterface
    public interface VertexSink {
        void vertex(float x, float y, int argb);
    }
}
