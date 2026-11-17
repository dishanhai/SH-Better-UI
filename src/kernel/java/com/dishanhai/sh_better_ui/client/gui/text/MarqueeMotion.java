package com.dishanhai.sh_better_ui.client.gui.text;

import java.util.Objects;

/**
 * Renderer-independent, frame-rate independent looping overflow-label motion.
 * Inspired by MuoniumPlayer ScrollLabelWidget; independently implemented for UUK.
 * A view owns one instance per label and supplies its animation clock in milliseconds.
 */
public final class MarqueeMotion {
    public record Frame(double offset, double repeatOffset, boolean overflowing, boolean moving) {}

    private final double pixelsPerSecond;
    private final long holdMillis;
    private final double gap;
    private Object contentKey;
    private double contentWidth = -1;
    private double viewportWidth = -1;
    private double elapsedMillis;
    private long lastTime;
    private boolean initialized;

    public MarqueeMotion() { this(24, 2500, 24); }

    public MarqueeMotion(double pixelsPerSecond, long holdMillis, double gap) {
        if (!Double.isFinite(pixelsPerSecond) || pixelsPerSecond <= 0 || holdMillis < 0
                || !Double.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException("Positive finite speed, nonnegative hold and gap required");
        }
        this.pixelsPerSecond = pixelsPerSecond;
        this.holdMillis = holdMillis;
        this.gap = gap;
    }

    /**
     * Content/measurement changes restart the leading hold. Paused/hidden views freeze time;
     * reduced motion pins to the start. A backwards clock restarts rather than jumping.
     * Widths are measured in the same logical coordinates used for rendering.
     */
    public Frame sample(Object key, double width, double viewport, long nowMillis,
                        boolean paused, boolean reducedMotion) {
        if (!Double.isFinite(width) || !Double.isFinite(viewport) || width <= 0 || viewport <= 0
                || !Double.isFinite(width + gap)) {
            reset();
            return new Frame(0, 0, false, false);
        }
        if (!initialized || !Objects.equals(contentKey, key) || contentWidth != width
                || viewportWidth != viewport || nowMillis < lastTime) {
            contentKey = key;
            contentWidth = width;
            viewportWidth = viewport;
            lastTime = nowMillis;
            elapsedMillis = 0;
            initialized = true;
        }
        double delta = Math.max(0, (double) nowMillis - (double) lastTime);
        lastTime = nowMillis;
        boolean overflow = width > viewport;
        double distance = width + gap;
        if (!overflow || reducedMotion) {
            elapsedMillis = 0;
            return new Frame(0, distance, overflow, false);
        }
        double travelMillis = distance / pixelsPerSecond * 1000;
        double cycle = holdMillis + travelMillis;
        if (!Double.isFinite(cycle) || cycle <= 0) {
            return new Frame(0, distance, true, false);
        }
        if (!paused) elapsedMillis = (elapsedMillis + delta) % cycle;
        double offset = elapsedMillis <= holdMillis ? 0
                : -(elapsedMillis - holdMillis) * pixelsPerSecond / 1000;
        return new Frame(offset, offset + distance, true, !paused && offset < 0);
    }

    public void reset() {
        initialized = false;
        contentKey = null;
        elapsedMillis = 0;
    }
}
