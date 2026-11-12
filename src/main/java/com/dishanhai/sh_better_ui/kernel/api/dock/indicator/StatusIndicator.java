// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import java.util.Objects;
import java.util.function.Supplier;

/** Displays a status glyph whose shape also distinguishes states when colors are hard to distinguish. */
public final class StatusIndicator implements CompactIndicator {
    /** Supported glyphs. */
    public enum Shape { READY, WORKING, WARNING, OFFLINE }

    /** Snapshot of one status glyph and its ARGB color. */
    public record State(Shape shape, int argb) {
        public State { Objects.requireNonNull(shape, "shape"); }
    }

    private final int size;
    private final Supplier<? extends State> source;
    private State state;

    /** Supplies the latest status snapshot on the client UI thread. */
    public StatusIndicator(int size, Supplier<? extends State> source) {
        if (size < 0) throw new IllegalArgumentException("Negative status indicator size");
        this.size = size;
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override public IndicatorSize measure(IndicatorConstraints constraints) {
        int side = Math.min(size, Math.min(constraints.maxWidth(), constraints.maxHeight()));
        return new IndicatorSize(side, side);
    }

    @Override public long minimumUpdateIntervalNanos() { return 100_000_000L; }

    @Override public void update(IndicatorContext context) {
        state = Objects.requireNonNull(source.get(), "state");
    }

    @Override public void render(IndicatorRenderContext context) {
        if (state == null) return;
        int side = Math.min(size, Math.min(context.width(), context.height()));
        if (side == 0) return;
        int left = (context.width() - side) / 2;
        int top = (context.height() - side) / 2;
        int right = left + side;
        int bottom = top + side;
        int middleX = left + side / 2;
        int middleY = top + side / 2;
        int color = state.argb();
        switch (state.shape()) {
            case READY -> context.fill(left, top, right, bottom, color);
            case WORKING -> {
                context.fill(left, middleY, right, middleY + 1, color);
                context.fill(middleX, top, middleX + 1, middleY, color);
                context.fill(middleX, middleY + 1, middleX + 1, bottom, color);
            }
            case WARNING -> {
                context.fill(middleX, top, middleX + 1, bottom - 2, color);
                context.fill(middleX, bottom - 1, middleX + 1, bottom, color);
            }
            case OFFLINE -> {
                context.fill(left, top, right, top + 1, color);
                if (side > 1) context.fill(left, bottom - 1, right, bottom, color);
                context.fill(left, top + 1, left + 1, bottom - 1, color);
                if (side > 1) context.fill(right - 1, top + 1, right, bottom - 1, color);
            }
        }
    }
}
