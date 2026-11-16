// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import com.dishanhai.sh_better_ui.kernel.api.layout.SmoothVerticalScroll;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalEdgeFade;
import com.dishanhai.sh_better_ui.kernel.api.layout.ViewportReveal;
import com.dishanhai.sh_better_ui.kernel.api.motion.SelectionIndicator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Pure UUK animated-list layout, selection and frame state. Uses monotonic milliseconds. */
public final class AnimatedListState {
    public enum Navigation { UP, DOWN, HOME, END }
    public record Options(boolean selectOnHover, boolean arrowNavigation, boolean loop,
                          ViewportReveal.Animation animation, boolean animateOnce, long staggerMs,
                          boolean showGradients, double fadeSize) {
        public static final Options DEFAULT = new Options(true, true, false, ViewportReveal.Animation.POP,
                false, 50, true, 56);
        public Options {
            Objects.requireNonNull(animation);
            if (staggerMs < 0 || staggerMs > 60_000 || !Double.isFinite(fadeSize) || fadeSize < 0)
                throw new IllegalArgumentException("Invalid animated-list options");
        }
    }

    private Options options;
    private List<String> keys = List.of();
    private List<ViewportReveal> reveals = List.of();
    private double[] tops = {}, heights = {};
    private double content, viewport;
    private final VerticalScroll scroll = new VerticalScroll();
    private final SmoothVerticalScroll smooth = new SmoothVerticalScroll(scroll);
    private final SelectionIndicator indicator = new SelectionIndicator();
    private int selected = -1;
    private boolean keyboard, pointerKnown;
    private double pointerX, pointerY;
    private boolean entrance = true;

    public AnimatedListState(Options options) { this.options = Objects.requireNonNull(options); }
    public Options options() { return options; }
    public void options(Options value) { options = Objects.requireNonNull(value); }
    public int size() { return keys.size(); }
    public int selected() { return selected; }
    public double top(int row) { return tops[row]; }
    public double height(int row) { return heights[row]; }
    public double viewport() { return viewport; }
    public VerticalScroll scroll() { return scroll; }
    public SelectionIndicator indicator() { return indicator; }
    public VerticalEdgeFade fades() { return VerticalEdgeFade.of(scroll.offset(), content, viewport, options.fadeSize(), options.showGradients()); }

    /** Stable unique keys preserve selection and animateOnce history through edits/reordering. */
    public void layout(List<String> rowKeys, double[] rowHeights, double gap, double viewport) {
        var copy = List.copyOf(rowKeys);
        if (copy.size() != rowHeights.length || new HashSet<>(copy).size() != copy.size()
                || !Double.isFinite(gap) || gap < 0 || !Double.isFinite(viewport) || viewport < 0)
            throw new IllegalArgumentException("Invalid list layout");
        double[] nextHeights = rowHeights.clone(), nextTops = new double[rowHeights.length];
        double total = 0;
        for (int i = 0; i < nextHeights.length; i++) {
            if (!Double.isFinite(nextHeights[i]) || nextHeights[i] <= 0) throw new IllegalArgumentException("Invalid row height");
            nextTops[i] = total;
            total += nextHeights[i] + (i + 1 < nextHeights.length ? gap : 0);
        }
        if (total > Float.MAX_VALUE) throw new IllegalArgumentException("List too tall");
        String selectedKey = selected >= 0 ? keys.get(selected) : null;
        var old = new HashMap<String, ViewportReveal>();
        for (int i = 0; i < keys.size(); i++) old.put(keys.get(i), reveals.get(i));
        var next = new ArrayList<ViewportReveal>();
        for (String key : copy) next.add(old.containsKey(key) ? old.get(key) : new ViewportReveal());
        keys = copy;
        reveals = next;
        heights = nextHeights;
        tops = nextTops;
        content = total;
        this.viewport = viewport;
        selected = selectedKey == null ? -1 : keys.indexOf(selectedKey);
        scroll.resize(viewport, total);
        smooth.synchronize();
        indicator.hide(); // layout changes snap; selection changes retain the trajectory
        entrance = true;
    }

    public void select(int index) { selected = index >= 0 && index < size() ? index : -1; }

    /** Feed actual pointer coordinates, not just the row under a scrolling stationary pointer. */
    public void pointer(double x, double y, int row) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        boolean moved = !pointerKnown || x != pointerX || y != pointerY;
        pointerKnown = true;
        pointerX = x;
        pointerY = y;
        if (keyboard && !moved) return;
        if (moved) keyboard = false;
        if (options.selectOnHover() && row >= 0 && row < size()) select(row);
    }

    public boolean navigate(Navigation command, long now, boolean reducedMotion) {
        Objects.requireNonNull(command);
        if (!options.arrowNavigation() || size() == 0 || scroll.dragging()) return false;
        int next = switch (command) {
            case HOME -> 0;
            case END -> size() - 1;
            case DOWN -> selected < 0 ? 0 : selected + 1;
            case UP -> selected < 0 ? size() - 1 : selected - 1;
        };
        select(options.loop() ? Math.floorMod(next, size()) : Math.max(0, Math.min(size() - 1, next)));
        keyboard = true;
        double margin = Math.min(options.showGradients() ? Math.max(12, options.fadeSize()) : 12,
                Math.max(0, (viewport - height(selected)) / 2));
        double top = top(selected), bottom = top + height(selected);
        if (top < scroll.offset() + margin) smooth.scrollTo(top - margin, now, reducedMotion);
        else if (bottom > scroll.offset() + viewport - margin) smooth.scrollTo(bottom - viewport + margin, now, reducedMotion);
        return true;
    }

    public boolean scrollBy(double delta, long now, boolean reducedMotion) { return smooth.scrollBy(delta, now, reducedMotion); }
    public void synchronizeScroll() { smooth.synchronize(); }
    public ViewportReveal.Pose pose(int row, long now) { return reveals.get(row).sample(now); }

    public void advance(long now, boolean reducedMotion) {
        smooth.advance(now, reducedMotion);
        int order = 0;
        for (int i = 0; i < size(); i++) {
            var reveal = reveals.get(i);
            boolean seen = reveal.seen();
            long delay = !entrance || seen ? 0 : order * options.staggerMs();
            reveal.update(top(i) - scroll.offset(), height(i), viewport, options.animation(),
                    options.animateOnce(), delay, now, reducedMotion);
            if (!seen && reveal.seen()) order++;
        }
        entrance = false;
        if (selected < 0) indicator.hide();
        else indicator.target(top(selected), height(selected), now, reducedMotion);
        indicator.advance(now);
    }

    /** Content-local hit testing excludes gaps. */
    public int indexAt(double contentY) {
        if (!Double.isFinite(contentY) || contentY < 0 || contentY >= content) return -1;
        int lo = 0, hi = size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (contentY < tops[mid]) hi = mid - 1;
            else if (contentY >= tops[mid] + heights[mid]) lo = mid + 1;
            else return mid;
        }
        return -1;
    }

    /** Visual hit test follows the animated row, including pop scaling and viewport clipping. */
    public int hit(double x, double y, double width, long now) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                || width <= 0 || x < 0 || x >= width || y < 0 || y >= viewport) return -1;
        for (int i = size() - 1; i >= 0; i--) {
            var pose = pose(i, now);
            if (pose.opacity() < .01) continue;
            double left = width * (1 - pose.scale()) / 2;
            double top = top(i) - scroll.offset() + pose.y() + height(i) * (1 - pose.scale()) / 2;
            if (x >= left && x < width - left && y >= top && y < top + height(i) * pose.scale()) return i;
        }
        return -1;
    }
}
