// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.List;
import java.util.Objects;

/** Platform-independent PillNav layout, selection, disclosure and two-edge spring. GUI units, monotonic ms. */
public final class PillNavState {
    public enum Layout { SPLIT, JOINED }
    public enum Surface { GLASS, SOLID, NONE }
    public enum ItemStyle { PILL, PLAIN }
    public enum HoverEffect { FILL, GLIDE, NONE }
    public enum Indicator { DOT, PILL, NONE }
    public enum Collapse { AUTO, ALWAYS, NEVER }
    public enum Size {
        SM(36, 3, 12, 13, 6, 18), MD(44, 4, 16, 14, 8, 22), LG(52, 5, 20, 15, 10, 26);
        public final int height, padding, horizontal, font, gap, mark;
        Size(int height, int padding, int horizontal, int font, int gap, int mark) {
            this.height = height; this.padding = padding; this.horizontal = horizontal;
            this.font = font; this.gap = gap; this.mark = mark;
        }
        public int inner() { return height - 2 * padding; }
    }

    public record Options(Layout layout, Size size, Surface surface, ItemStyle itemStyle, HoverEffect hoverEffect,
                          Indicator indicator, Collapse collapse, float bounce, float stretch, boolean intro) {
        public static final Options DEFAULT = new Options(Layout.SPLIT, Size.MD, Surface.GLASS, ItemStyle.PILL,
                HoverEffect.FILL, Indicator.DOT, Collapse.AUTO, .3f, .5f, true);
        public Options {
            Objects.requireNonNull(layout); Objects.requireNonNull(size); Objects.requireNonNull(surface);
            Objects.requireNonNull(itemStyle); Objects.requireNonNull(hoverEffect);
            Objects.requireNonNull(indicator); Objects.requireNonNull(collapse);
            if (!Float.isFinite(bounce) || !Float.isFinite(stretch)) throw new IllegalArgumentException("Non-finite motion");
            bounce = unit(bounce); stretch = unit(stretch);
        }
    }

    public record Slot(float left, float width) {}
    public record Metrics(boolean collapsed, float width, float barLeft, float trackLeft, List<Slot> slots,
                          int rowHeight, int visibleRows) {}
    public record Frame(float left, float width, float scale, float squash, float alpha,
                        float dotX, float dotStretch, float dotAlpha, float panel, float intro, long introAge) {}

    private final int count;
    private Options options;
    private Metrics metrics;
    private int selected, focus, hover = -1, pressed = -1, firstRow;
    private boolean controlled, open;
    private final Axis left = new Axis(), right = new Axis(), dot = new Axis(), press = new Axis(1);
    private float alpha, dotAlpha, pop = 1, panel;
    private long previous = -1, started = -1;

    public PillNavState(int count, int selected, Options options) {
        if (count < 0) throw new IllegalArgumentException("Negative item count");
        this.count = count;
        this.selected = valid(selected) ? selected : -1;
        focus = count == 0 ? -1 : Math.max(0, this.selected);
        this.options = Objects.requireNonNull(options);
    }

    public Options options() { return options; }
    public void setOptions(Options value) {
        if (!options.intro() && value.intro()) { started = -1; previous = -1; }
        options = Objects.requireNonNull(value); cancel();
    }
    public int selected() { return selected; }
    public int focused() { return focus; }
    public int hovered() { return hover; }
    public int pressed() { return pressed; }
    public boolean isOpen() { return open; }
    public int firstRow() { return firstRow; }
    public Metrics metrics() { return metrics; }

    /** Measured label+icon widths, excluding horizontal padding. Height reserves room for the menu. */
    public Metrics layout(float availableWidth, int availableHeight, boolean logo, float[] contentWidths) {
        if (contentWidths.length != count || !Float.isFinite(availableWidth) || availableWidth < 0) {
            throw new IllegalArgumentException("Invalid PillNav measurement");
        }
        var size = options.size();
        float gap = options.itemStyle() == ItemStyle.PILL ? 3 : 2;
        float listWidth = 0;
        for (float width : contentWidths) {
            if (!Float.isFinite(width) || width < 0) throw new IllegalArgumentException("Invalid label width");
            listWidth += width + size.horizontal * 2;
        }
        listWidth += Math.max(0, count - 1) * gap;
        boolean joined = options.layout() == Layout.JOINED;
        float barLeft = logo ? joined ? size.padding + size.inner() + 2 : size.height + size.gap : 0;
        float trackLeft = barLeft + (joined && logo ? 0 : size.padding);
        float needed = trackLeft + listWidth + size.padding;
        boolean collapsed = options.collapse() == Collapse.ALWAYS
                || options.collapse() == Collapse.AUTO && needed > availableWidth + .5f;
        var slots = new java.util.ArrayList<Slot>();
        float x = trackLeft;
        for (float width : contentWidths) {
            slots.add(new Slot(x, width + size.horizontal * 2));
            x += width + size.horizontal * 2 + gap;
        }
        int rows = Math.max(0, Math.min(count, (availableHeight - size.height - 16) / (size.height + 4)));
        boolean changed = metrics == null || metrics.collapsed() != collapsed || metrics.visibleRows() != rows;
        if (metrics != null && metrics.collapsed() != collapsed) {
            open = false; cancel(); hover = -1; previous = -1; alpha = dotAlpha = 0;
        }
        if (!collapsed || rows == 0) open = false;
        metrics = new Metrics(collapsed, collapsed ? availableWidth : needed, barLeft, trackLeft,
                List.copyOf(slots), size.height + 4, rows);
        if (changed) ensureFocusVisible();
        return metrics;
    }

    /** External controlled value; -1 represents an unknown href. Does not emit an activation. */
    public void setSelected(int index, boolean controlled) {
        selected = valid(index) ? index : -1;
        this.controlled = controlled;
        if (valid(index)) focus = index;
        ensureFocusVisible();
    }

    /** User activation. Returns the activated index even in controlled mode; host owns navigation. */
    public int activate(int index) {
        if (!valid(index)) return -1;
        if (!controlled) selected = index;
        focus = index;
        open = false;
        cancel();
        return index;
    }

    public void hover(int index) { hover = valid(index) ? index : -1; }
    public boolean press(int index) {
        if (!valid(index) && index != count && index != count + 1) return false;
        pressed = index;
        return true;
    }
    public int release(int index) {
        int prior = pressed;
        cancel();
        return prior >= 0 && prior == index ? prior : -1;
    }
    public boolean cancel() { boolean had = pressed >= 0; pressed = -1; return had; }
    public boolean dismiss() { boolean had = open; open = false; cancel(); return had; }
    public boolean toggle() {
        if (metrics == null || !metrics.collapsed() || count == 0 || metrics.visibleRows() == 0) return false;
        open = !open; ensureFocusVisible(); return true;
    }
    public void moveFocus(int delta) {
        if (count == 0) return;
        focus = Math.floorMod(focus + delta, count); ensureFocusVisible();
    }
    public void focusEdge(boolean end) { focus = count == 0 ? -1 : end ? count - 1 : 0; ensureFocusVisible(); }
    public void scroll(int delta) {
        if (metrics != null && open) firstRow = Math.max(0, Math.min(Math.max(0, count - metrics.visibleRows()), firstRow + delta));
    }
    private void ensureFocusVisible() {
        if (metrics == null) return;
        if (focus < firstRow) firstRow = Math.max(0, focus);
        if (focus >= firstRow + metrics.visibleRows()) firstRow = Math.max(0, focus - metrics.visibleRows() + 1);
        firstRow = Math.min(firstRow, Math.max(0, count - metrics.visibleRows()));
    }

    public Frame sample(long now, boolean reducedMotion, boolean keyboardFocus) {
        if (metrics == null) throw new IllegalStateException("Measure before sampling");
        if (started < 0) started = now;
        float dt = previous < 0 ? 1 / 60f : Math.max(0, Math.min(.05f, (now - previous) / 1000f));
        previous = now;
        float intro = !options.intro() || reducedMotion ? 1 : ease((now - started) / 760f);
        boolean waiting = intro < 1 && now - started < 420;
        int lit = hover >= 0 ? hover : keyboardFocus ? focus : -1;
        int glide = options.hoverEffect() == HoverEffect.FILL ? -1
                : options.hoverEffect() == HoverEffect.GLIDE && lit >= 0 ? lit
                : options.indicator() == Indicator.PILL ? selected : -1;
        Slot target = !metrics.collapsed() && valid(glide) && !waiting ? metrics.slots().get(glide) : null;
        if (target != null) {
            if (alpha < .02f) { left.snap(target.left()); right.snap(target.left() + target.width()); pop = .9f; }
            float direction = Math.signum(target.left() + target.width() / 2 - (left.x + right.x) / 2);
            float lead = 520 * (1 + .9f * options.stretch()), trail = 520 * (1 - .5f * options.stretch());
            left.step(target.left(), direction < 0 ? lead : direction > 0 ? trail : 520, 1 - .55f * options.bounce(), dt, reducedMotion);
            right.step(target.left() + target.width(), direction > 0 ? lead : direction < 0 ? trail : 520, 1 - .55f * options.bounce(), dt, reducedMotion);
        }
        alpha = approach(alpha, target == null ? 0 : 1, dt, .06f, reducedMotion);
        pop = approach(pop, 1, dt, .09f, reducedMotion);
        press.step(pressed >= 0 ? .95f : 1, 700, .567f, dt, reducedMotion);
        Slot dotTarget = !metrics.collapsed() && options.indicator() == Indicator.DOT && valid(selected) && !waiting
                ? metrics.slots().get(selected) : null;
        if (dotTarget != null) {
            float center = dotTarget.left() + dotTarget.width() / 2;
            if (dotAlpha < .02f) dot.snap(center);
            dot.step(center, 360, 1 - .5f * options.bounce(), dt, reducedMotion);
        }
        dotAlpha = approach(dotAlpha, dotTarget == null ? 0 : 1, dt, .1f, reducedMotion);
        panel = approach(panel, open ? 1 : 0, dt, open ? .1f : .07f, reducedMotion);
        float width = Math.max(0, right.x - left.x);
        float squash = target == null ? 1 : 1 - Math.max(0, Math.min(.14f, (width / Math.max(1, target.width()) - 1) * .3f));
        return new Frame(left.x, width, press.x * pop, squash, alpha, dot.x,
                1 + Math.min(2.2f, Math.abs(dot.v) / 700), dotAlpha, panel, intro, Math.max(0, now - started));
    }

    private boolean valid(int index) { return index >= 0 && index < count; }
    /** Local hit test for the same capsule silhouette used by native items, logo and toggle. */
    public static boolean containsPill(double x, double y, double width, double height) {
        if (!(x >= 0 && y >= 0 && x < width && y < height)) return false;
        double r = Math.min(width, height) / 2;
        double dx = x < r ? x - r : x > width - r ? x - width + r : 0;
        double dy = y < r ? y - r : y > height - r ? y - height + r : 0;
        return dx * dx + dy * dy <= r * r;
    }
    public static float unit(float value) { return Math.max(0, Math.min(1, value)); }
    public static float ease(float value) { float rest = 1 - unit(value); return 1 - rest * rest * rest; }
    public static float approach(float value, float target, float dt, float time, boolean reduced) {
        float result = reduced ? target : value + (target - value) * (1 - (float) Math.exp(-dt / time));
        return Math.abs(target - result) < .0001f ? target : result;
    }
    private static final class Axis {
        float x, v;
        Axis() {}
        Axis(float x) { this.x = x; }
        void snap(float target) { x = target; v = 0; }
        void step(float target, float stiffness, float damping, float dt, boolean reduced) {
            if (reduced) { snap(target); return; }
            int steps = Math.max(1, (int) Math.ceil(dt * 240));
            float h = dt / steps;
            for (int i = 0; i < steps; i++) {
                v += (-stiffness * (x - target) - 2 * damping * (float) Math.sqrt(stiffness) * v) * h;
                x += v * h;
            }
            if (Math.abs(target - x) < .01f && Math.abs(v) < .05f) snap(target);
        }
    }
}
