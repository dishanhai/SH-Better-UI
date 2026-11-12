// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Status;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.spatial.GuiGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry.Triangle;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Native extraction for the loading and control-feedback samples. */
final class GalleryControlPaint {
    static final int CARD_RADIUS = 6;
    private static final double TAU = 2.0 * Math.PI;
    private static final int RING_STEPS = 48;
    private static final float RING_SWEEP = 0.28f;
    private static final FilledGeometry<Local> RING_TRACK = arc(1f);
    private static final FilledGeometry<Local> RING_SPINNER = arc(RING_SWEEP);

    private GalleryControlPaint() {}

    static GraphicIcon icon(Status status) {
        return switch (status) {
            case READY -> GraphicIcon.STATUS_READY;
            case PENDING -> GraphicIcon.STATUS_PENDING;
            case SUCCEEDED -> GraphicIcon.STATUS_SUCCEEDED;
            case PARTIAL -> GraphicIcon.STATUS_PARTIAL;
            case FAILED -> GraphicIcon.STATUS_FAILED;
            case CANCELLED -> GraphicIcon.STATUS_CANCELLED;
            case TIMED_OUT -> GraphicIcon.STATUS_TIMED_OUT;
        };
    }

    static int statusColor(Status status, int text, int accent, int background) {
        int color = switch (status) {
            case READY, CANCELLED -> text;
            case PENDING -> accent;
            case SUCCEEDED -> 0xFF4ADE80;
            case PARTIAL, TIMED_OUT -> 0xFFFBBF24;
            case FAILED -> 0xFFF87171;
        };
        return ContrastPolicy.readable(color, background, background, 3);
    }

    static void bar(GuiGraphicsExtractor graphics, Bounds bounds, float offset, float fraction, int track, int accent) {
        float radius = bounds.height() * 0.5f;
        RoundedCorners.draw(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, 0, 0, track);
        RoundedCorners.draw(graphics, bounds.x() + bounds.width() * offset, bounds.y(),
                bounds.width() * fraction, bounds.height(), radius, 0, 0, accent);
    }

    static void spinner(GuiGraphicsExtractor graphics, float x, float y, float diameter, long time,
                        boolean reducedMotion, int track, int accent) {
        float turn = reducedMotion ? 0f : cycle(time, 900L);
        ring(graphics, x, y, diameter, turn, RING_SPINNER, track, accent);
    }

    static void ring(GuiGraphicsExtractor graphics, float x, float y, float diameter, float turn,
                     FilledGeometry<Local> progress, int track, int accent) {
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).scale(diameter * 0.5f);
            GuiGeometry.fill(graphics, RING_TRACK, track);
            pose.rotate((float) (turn * TAU));
            GuiGeometry.fill(graphics, progress, accent);
        } finally {
            pose.popMatrix();
        }
    }

    static FilledGeometry<Local> arc(float fraction) {
        int steps = (int) Math.ceil(RING_STEPS * fraction);
        var triangles = new ArrayList<Triangle<Local>>(2 * steps);
        for (int i = 0; i < steps; i++) {
            double start = TAU * fraction * i / steps - Math.PI / 2;
            double end = TAU * fraction * (i + 1) / steps - Math.PI / 2;
            addBand(triangles, point(start, 1), point(end, 1), point(start, 0.68), point(end, 0.68));
        }
        return FilledGeometry.ofTriangles(triangles);
    }

    private static Point<Local> point(double angle, double radius) {
        return new Point<>(radius * Math.cos(angle), radius * Math.sin(angle));
    }

    private static void addBand(List<Triangle<Local>> triangles, Point<Local> outer, Point<Local> nextOuter,
                                Point<Local> inner, Point<Local> nextInner) {
        triangles.add(new Triangle<>(outer, nextOuter, inner));
        triangles.add(new Triangle<>(nextOuter, nextInner, inner));
    }

    static float cycle(long time, long period) {
        return Math.floorMod(time, period) / (float) period;
    }

    static float highlight(float center, long time, boolean reducedMotion) {
        if (reducedMotion) return 0f;
        float sweep = GuiMotion.lerp(-0.4f, 1.4f, cycle(time, 1800L));
        float light = 1f - GuiMotion.clamp01(Math.abs(sweep - center) / 0.4f);
        return light * light * (3f - 2f * light);
    }

    /** The perimeter is tessellated on layout. Only its color and glow change during extraction. */
    static final class CardEdge {
        private static final int STRAIGHT_STEPS = 12;
        private static final int CORNER_STEPS = 6;
        private final Bounds bounds;
        private final List<EdgeSegment> segments;

        CardEdge(Bounds bounds) {
            this.bounds = bounds;
            var outer = contour(bounds.width(), bounds.height(), 0);
            var inner = contour(bounds.width(), bounds.height(), 1.25);
            var parts = new ArrayList<EdgeSegment>(outer.size());
            for (int i = 0; i < outer.size(); i++) {
                int next = (i + 1) % outer.size();
                Point<Local> from = outer.get(i), to = outer.get(next);
                double x = (from.x() + to.x() - bounds.width()) * 0.5;
                double y = (from.y() + to.y() - bounds.height()) * 0.5;
                var band = new ArrayList<Triangle<Local>>(2);
                addBand(band, from, to, inner.get(i), inner.get(next));
                parts.add(new EdgeSegment(Math.atan2(y, x), FilledGeometry.ofTriangles(band)));
            }
            segments = List.copyOf(parts);
        }

        void extract(GuiGraphicsExtractor graphics, UiTheme theme, UiPreferences preferences, long time, int text) {
            int x = bounds.x(), y = bounds.y(), width = bounds.width(), height = bounds.height();
            int fill = theme.background() | 0xFF000000;
            if (preferences.highContrast()) {
                RoundedCorners.draw(graphics, x, y, width, height, CARD_RADIUS, 1, text, fill);
                return;
            }

            float turn = preferences.reducedMotion() ? 0.12f : cycle(time, 8000L);
            float breath = preferences.reducedMotion() ? 0.5f : (float) (0.5 - 0.5 * Math.cos(2 * TAU * turn));
            int glow = theme.accent() & 0xFFFFFF;
            for (int spread = 3; spread >= 1; spread--) {
                int alpha = Math.round((4 - spread) * (8 + 10 * breath));
                RoundedCorners.draw(graphics, x - spread, y - spread + 2,
                        width + 2 * spread, height + 2 * spread, CARD_RADIUS + spread, 0, 0, alpha << 24 | glow);
            }
            RoundedCorners.draw(graphics, x, y, width, height, CARD_RADIUS, 0, 0, fill);
            var pose = graphics.pose();
            pose.pushMatrix();
            try {
                pose.translate(x, y);
                for (var segment : segments) {
                    double angle = segment.angle() - TAU * turn;
                    float sweep = (float) (0.5 + 0.5 * Math.cos(angle));
                    float hue = (float) (0.5 + 0.5 * Math.sin(angle));
                    int gradient = GuiMotion.lerpColor(0xFFA880FA, 0xFF45D1FF, hue);
                    int color = GuiMotion.lerpColor(theme.border() | 0xFF000000, gradient,
                            0.30f + 0.66f * sweep * sweep);
                    GuiGeometry.fill(graphics, segment.mesh(), color);
                }
            } finally {
                pose.popMatrix();
            }
        }

        private static List<Point<Local>> contour(int width, int height, double inset) {
            double radius = CARD_RADIUS - inset;
            var vertices = new ArrayList<Point<Local>>(2 * STRAIGHT_STEPS + 4 * CORNER_STEPS + 2);
            for (int corner = 0; corner < 4; corner++) {
                double x = corner < 2 ? width - CARD_RADIUS : CARD_RADIUS;
                double y = corner == 0 || corner == 3 ? CARD_RADIUS : height - CARD_RADIUS;
                double angle = (corner - 1) * Math.PI / 2;
                for (int step = 0; step <= CORNER_STEPS; step++) {
                    double a = angle + step * Math.PI / (2 * CORNER_STEPS);
                    vertices.add(new Point<>(x + radius * Math.cos(a), y + radius * Math.sin(a)));
                }
                if (corner == 1 || corner == 3) {
                    double right = width - CARD_RADIUS;
                    double edgeY = corner == 1 ? height - inset : inset;
                    for (int step = 1; step < STRAIGHT_STEPS; step++) {
                        double t = step / (double) STRAIGHT_STEPS;
                        double edgeX = corner == 1 ? right + (CARD_RADIUS - right) * t
                                : CARD_RADIUS + (right - CARD_RADIUS) * t;
                        vertices.add(new Point<>(edgeX, edgeY));
                    }
                }
            }
            return vertices;
        }

        private record EdgeSegment(double angle, FilledGeometry<Local> mesh) {}
    }
}
