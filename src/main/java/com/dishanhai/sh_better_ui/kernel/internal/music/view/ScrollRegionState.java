// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;

/** Integer-row scrolling with fractional wheel input retained between events. */
final class ScrollRegionState {
    private final VerticalScroll scroll = new VerticalScroll();
    private int total, visible, step = 1;
    private double remainder;
    void configure(int offset, int total, int visible) {
        total = Math.max(0, total); visible = Math.max(0, visible);
        if (this.total != total || this.visible != visible) {
            this.total = total; this.visible = visible; remainder = 0;
            scroll.resize(visible, total);
        }
        if (offset != offset()) remainder = 0;
        scroll.scrollTo(visible == 0 ? 0 : offset);
    }
    int offset() { return (int) Math.round(scroll.offset()); }
    int maximum() { return visible == 0 ? 0 : Math.max(0, total - visible); }
    int page() { return Math.max(1, visible); }
    int step() { return step; }
    void setStep(int step) { this.step = Math.max(1, step); remainder = 0; }
    boolean moveTo(long value) {
        remainder = 0;
        return scroll.scrollTo(Math.max(0L, Math.min(maximum(), value)));
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
        if (whole != 0) scroll.scrollTo(Math.max(0L, Math.min(maximum(), (long) offset() + whole)));
        return true;
    }
    int thumbTop(int height) { return scroll.thumbTop(height); }
    int thumbHeight(int height) { return scroll.thumbHeight(height); }
    boolean beginDrag(double y, int height) {
        remainder = 0;
        return maximum() > 0 && scroll.beginDrag(y, height);
    }
    void drag(double y, int height) { scroll.dragTo(y, height); }
    boolean dragging() { return scroll.dragging(); }
    void endDrag() { scroll.endDrag(); }
    boolean cancelDrag() { return scroll.cancelDrag(); }
}
