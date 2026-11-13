// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.layout;

/** Finds unobstructed space to the left of an anchor, avoiding a host container. */
public final class PopupArea {
    private PopupArea() {}

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) throw new IllegalArgumentException("negative size");
        }
    }

    /** Returns the larger area below or to the right of the host, bounded by the anchor. */
    public static Rect leftOf(int screenWidth, int screenHeight, int anchorX, Rect host, int margin) {
        return leftOf(screenWidth, screenHeight, anchorX, host, margin, 1, 1);
    }

    /** Chooses the region that best fits the desired panel without enlarging it. */
    public static Rect leftOf(int screenWidth, int screenHeight, int anchorX, Rect host, int margin,
                              int desiredWidth, int desiredHeight) {
        int left = Math.max(0, margin);
        int top = left;
        int right = Math.max(left, Math.min(screenWidth - left, anchorX - left));
        int bottom = Math.max(top, screenHeight - left);
        if (host == null || host.width() == 0 || host.height() == 0
                || host.x() >= right || host.x() + host.width() <= left
                || host.y() >= bottom || host.y() + host.height() <= top) {
            return new Rect(left, top, right - left, bottom - top);
        }
        int hostRight = Math.max(left, Math.min(right, host.x() + host.width() + left));
        int hostBottom = Math.max(top, Math.min(bottom, host.y() + host.height() + left));
        Rect below = new Rect(left, hostBottom, right - left, bottom - hostBottom);
        Rect beside = new Rect(hostRight, top, right - hostRight, bottom - top);
        float belowFit = Math.min(1, Math.min((float) below.width() / Math.max(1, desiredWidth),
                (float) below.height() / Math.max(1, desiredHeight)));
        float besideFit = Math.min(1, Math.min((float) beside.width() / Math.max(1, desiredWidth),
                (float) beside.height() / Math.max(1, desiredHeight)));
        if (belowFit != besideFit) return belowFit > besideFit ? below : beside;
        return (long) below.width() * below.height() >= (long) beside.width() * beside.height() ? below : beside;
    }
}
