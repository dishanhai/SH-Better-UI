// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Backend-independent world overlay geometry. Hosts supply camera-relative clip coordinates. */
public final class WorldOverlayGeometry {
    private WorldOverlayGeometry() {}

    public record Vertex(double x, double y, double z) {}
    public record Edge(Vertex a, Vertex b) {}
    public record Clip(double x, double y, double z, double w) {
        boolean finite() { return Double.isFinite(x + y + z + w); }
        Clip lerp(Clip b, double t) {
            return new Clip(x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t, w + (b.w - w) * t);
        }
        double plane(int plane) {
            return switch (plane) {
                case 0 -> w - 0.00001;
                case 1 -> x + w;
                case 2 -> w - x;
                case 3 -> y + w;
                case 4 -> w - y;
                case 5 -> z + w;
                default -> w - z;
            };
        }
    }
    public record Point(int x, int y) {}
    public record Line(Point a, Point b) {}

    public static Point project(Clip p, int width, int height) {
        if (!p.finite() || width < 1 || height < 1) return null;
        for (int i = 0; i < 7; i++) if (p.plane(i) < -1e-9) return null;
        return new Point(Math.max(0, Math.min(width - 1, (int) Math.round((p.x / p.w + 1) * width / 2))),
                Math.max(0, Math.min(height - 1, (int) Math.round((1 - p.y / p.w) * height / 2))));
    }

    /** Clips before division, including near-plane crossings and offscreen endpoints. */
    public static Line line(Clip a, Clip b, int width, int height) {
        if (!a.finite() || !b.finite()) return null;
        double start = 0, end = 1;
        for (int i = 0; i < 7; i++) {
            double da = a.plane(i), db = b.plane(i);
            if (da < 0 && db < 0) return null;
            if (da < 0) start = Math.max(start, da / (da - db));
            else if (db < 0) end = Math.min(end, da / (da - db));
        }
        if (start > end) return null;
        var pa = project(a.lerp(b, start), width, height);
        var pb = project(a.lerp(b, end), width, height);
        return pa == null || pb == null ? null : new Line(pa, pb);
    }

    public static List<Edge> box(double x, double y, double z, double width, double height, double depth) {
        var vertices = new Vertex[8];
        for (int i = 0; i < 8; i++) vertices[i] = new Vertex(x + ((i & 1) == 0 ? 0 : width),
                y + ((i & 2) == 0 ? 0 : height), z + ((i & 4) == 0 ? 0 : depth));
        var edges = new ArrayList<Edge>(12);
        for (int i = 0; i < 8; i++) for (int bit = 1; bit <= 4; bit *= 2)
            if ((i & bit) == 0) edges.add(new Edge(vertices[i], vertices[i | bit]));
        return List.copyOf(edges);
    }

    /** A transparent clock face that rotates about its vertical axis, with two moving hands. */
    public static List<Edge> clock(double x, double y, double z, double radius, double phase) {
        var edges = new ArrayList<Edge>(38);
        for (int i = 0; i < 32; i++) edges.add(new Edge(clockPoint(x, y, z, radius, i * Math.PI / 16, phase),
                clockPoint(x, y, z, radius, (i + 1) * Math.PI / 16, phase)));
        var center = new Vertex(x, y, z);
        edges.add(new Edge(center, clockPoint(x, y, z, radius * .85, phase * 3, phase)));
        edges.add(new Edge(center, clockPoint(x, y, z, radius * .55, phase / 12, phase)));
        return List.copyOf(edges);
    }

    private static Vertex clockPoint(double x, double y, double z, double radius, double angle, double rotation) {
        double horizontal = Math.sin(angle) * radius;
        return new Vertex(x + horizontal * Math.cos(rotation), y + Math.cos(angle) * radius,
                z + horizontal * Math.sin(rotation));
    }

    /** Bounded integer raster for a previously clipped GUI line. */
    public static void raster(Line line, BiConsumer<Integer, Integer> pixel) {
        int x = line.a.x, y = line.a.y, dx = Math.abs(line.b.x - x), dy = -Math.abs(line.b.y - y);
        if ((long) dx - dy > 16384) return;
        int sx = x < line.b.x ? 1 : -1, sy = y < line.b.y ? 1 : -1, error = dx + dy;
        while (true) {
            pixel.accept(x, y);
            if (x == line.b.x && y == line.b.y) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x += sx; }
            if (twice <= dx) { error += dx; y += sy; }
        }
    }
}
