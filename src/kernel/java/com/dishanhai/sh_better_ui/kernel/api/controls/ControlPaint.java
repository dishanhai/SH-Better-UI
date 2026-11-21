// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

/** SHBUI control geometry, independent of the host rendering backend. */
public final class ControlPaint {
    @FunctionalInterface
    public interface Rectangle {
        void draw(double x, double y, double width, double height, int color);
    }

    @FunctionalInterface
    public interface Rounded {
        void draw(double x, double y, double width, double height, double radius,
                  double stroke, int edge, int fill);
    }

    private ControlPaint() {}

    /** The 22 by 12 NetworkToggle track, scaled uniformly by the host. */
    public static void toggle(Rounded canvas, double x, double y, double scale, double progress,
                              int edge, int track, int knob) {
        double t = Math.max(0, Math.min(1, progress));
        double inset = 2 * scale;
        double knobSize = 8 * scale;
        canvas.draw(x, y, 22 * scale, 12 * scale, 6 * scale, scale, edge, track);
        canvas.draw(x + inset + 10 * scale * t, y + inset, knobSize, knobSize,
                4 * scale, 0, 0, knob);
    }

    /** The UiSlider track and rectangular thumb. The input y is the track's top edge. */
    public static void slider(Rectangle canvas, double x, double y, double width, double progress,
                              double scale, int track, int accent) {
        double t = Math.max(0, Math.min(1, progress));
        double thumbX = x + Math.round(t * width);
        canvas.draw(x, y, width, scale, track);
        if (thumbX > x) canvas.draw(x, y, thumbX - x, scale, accent);
        canvas.draw(thumbX - 2 * scale, y - scale, 4 * scale, 3 * scale, accent);
    }
}
