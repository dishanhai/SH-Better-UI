// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import com.dishanhai.sh_better_ui.kernel.api.layout.SmoothVerticalScroll;

/** Shared viewport, fractional paint offset and direct scrollbar dragging. */
final class ScrollRegionState {
    private final VerticalScroll scroll = new VerticalScroll();
    private int total, visible, step = 1;
    private double remainder;
    private final SmoothVerticalScroll motion = new SmoothVerticalScroll(scroll);
    private double target;
    private boolean coasting, pixelRows;

    void pixelRows() { pixelRows = true; }
    int shift(int pitch) { return pixelRows ? Math.min(pitch - 1, (int)((scroll.offset() - offset()) * pitch)) : 0; }

    boolean wheel(double delta, long now, boolean reducedMotion) {
        if (reducedMotion) { if (coasting) stopMotion(); return wheel(delta); }
        if (!Double.isFinite(delta) || delta == 0 || maximum() == 0 || dragging()) return false;
        if (coasting) advance(now);
        if (!coasting) target = scroll.offset();
        double next = Math.max(0, Math.min(maximum(), target - delta * step));
        if (next == target) return coasting;
        target = next;
        motion.scrollTo(target, now, false);
        coasting = true;
        return true;
    }

    void advance(long now) { advance(now, false); }

    void advance(long now, boolean reducedMotion) {
        if (!coasting) return;
        motion.advance(now, reducedMotion);
        if (Math.abs(scroll.offset() - target) < .001) {
            // Exact endpoint is necessary to reveal the last sliver of the final row.
            scroll.scrollTo(target);
            motion.synchronize();
            coasting = false;
        }
    }

    void stopMotion() {
        if (coasting) scroll.scrollTo(offset());
        freezeMotion();
    }

    /** Keep the last painted offset: this frame's row may already hold a pointer press. */
    void freezeMotion() {
        coasting = false;
        target = scroll.offset();
        motion.synchronize();
        remainder = 0;
    }

    boolean atForwardLimit() { return !coasting && scroll.offset() >= maximum(); }

    void configure(int offset, int total, int visible) {
        total = Math.max(0, total); visible = Math.max(0, visible);
        if (this.total != total || this.visible != visible) {
            double sampled = scroll.offset();
            this.total = total; this.visible = visible; stopMotion();
            scroll.resize(visible, total);
            // Appending rows may finish during a wheel reversal. Preserve the
            // sampled pixel instead of snapping to its containing row.
            if (pixelRows) restoreViewportOffset(sampled);
        }
        int wanted = Math.max(0, Math.min(maximum(), offset));
        if (wanted != offset()) {
            stopMotion();
            scroll.scrollTo(wanted);
            motion.synchronize();
        }
    }
    double viewportOffset() { return scroll.offset(); }

    void restoreViewportOffset(double value) {
        stopMotion();
        if (visible > 0) scroll.scrollTo(Math.max(0, Math.min(maximum(), value)));
        motion.synchronize();
    }

    int offset() { return pixelRows ? (int) scroll.offset() : (int) Math.round(scroll.offset()); }
    int maximum() { return visible == 0 ? 0 : Math.max(0, total - visible); }
    int page() { return Math.max(1, visible); }
    int step() { return step; }
    void setStep(int step) { this.step = Math.max(1, step); remainder = 0; }
    boolean moveTo(long value) {
        stopMotion();
        boolean changed = scroll.scrollTo(Math.max(0L, Math.min(maximum(), value)));
        motion.synchronize();
        return changed;
    }
    boolean wheel(double delta) {
        if (!Double.isFinite(delta) || delta == 0 || maximum() == 0) return false;
        double direction = -Math.signum(delta);
        if ((offset() == 0 && direction < 0) || (offset() == maximum() && direction > 0)) {
            remainder = 0; return false;
        }
        if (Math.signum(remainder) != direction) remainder = 0;
        remainder += Math.max(-maximum(), Math.min(maximum(), -delta * step));
        int whole = (int) remainder;
        remainder -= whole;
        if (whole != 0) {
            scroll.scrollTo(Math.max(0L, Math.min(maximum(), (long) offset() + whole)));
            motion.synchronize();
        }
        return true;
    }
    int thumbTop(int height) { return scroll.thumbTop(height); }
    int thumbHeight(int height) { return scroll.thumbHeight(height); }
    boolean beginDrag(double y, int height) {
        stopMotion();
        return maximum() > 0 && scroll.beginDrag(y, height);
    }
    void drag(double y, int height) { scroll.dragTo(y, height); }
    boolean dragging() { return scroll.dragging(); }
    void endDrag() { scroll.endDrag(); motion.synchronize(); }
    boolean cancelDrag() { boolean changed = scroll.cancelDrag(); motion.synchronize(); return changed; }
}
