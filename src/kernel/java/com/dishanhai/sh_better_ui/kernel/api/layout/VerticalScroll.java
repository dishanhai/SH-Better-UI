// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

/** Platform-independent vertical viewport and draggable scrollbar geometry. */
public final class VerticalScroll {
    private double viewport, content, offset, dragOffset;
    private boolean dragging;

    public void resize(double viewport, double content) {
        if (!Double.isFinite(viewport) || !Double.isFinite(content) || viewport < 0 || content < 0)
            throw new IllegalArgumentException("Invalid scroll dimensions");
        cancelDrag();
        this.viewport = viewport;
        this.content = content;
        scrollTo(offset);
    }
    public double offset() { return offset; }
    public double maxOffset() { return Math.max(0, content - viewport); }
    public boolean scrollTo(double value) {
        if (!Double.isFinite(value)) return false;
        double previous = offset;
        offset = Math.max(0, Math.min(maxOffset(), value));
        return previous != offset;
    }
    public boolean scrollBy(double delta) {
        return Double.isFinite(delta) && scrollTo(offset + delta);
    }
    public int thumbHeight(int trackHeight) {
        if (trackHeight <= 0) return 0;
        return Math.min(trackHeight, Math.max(8, (int) Math.round(trackHeight * viewport / Math.max(1, content))));
    }
    public int thumbTop(int trackHeight) {
        int travel = Math.max(0, trackHeight - thumbHeight(trackHeight));
        return maxOffset() == 0 ? 0 : (int) Math.round(travel * offset / maxOffset());
    }
    public boolean beginDrag(double y, int trackHeight) {
        if (!Double.isFinite(y) || maxOffset() == 0 || trackHeight <= 0) return false;
        dragging = true;
        dragOffset = offset;
        int top = thumbTop(trackHeight), height = thumbHeight(trackHeight);
        grab = y >= top && y < top + height ? y - top : height / 2.0;
        dragTo(y, trackHeight);
        return true;
    }
    private double grab;
    public boolean dragTo(double y, int trackHeight) {
        if (!dragging || !Double.isFinite(y)) return false;
        int travel = trackHeight - thumbHeight(trackHeight);
        return travel > 0 && scrollTo((y - grab) * maxOffset() / travel);
    }
    public boolean endDrag() { boolean was = dragging; dragging = false; return was; }
    public boolean cancelDrag() {
        if (!dragging) return false;
        dragging = false;
        scrollTo(dragOffset);
        return true;
    }
    public boolean dragging() { return dragging; }
}
