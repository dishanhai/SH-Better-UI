// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/** Displays a cached progress value as a horizontal bar. */
public final class ProgressIndicator implements CompactIndicator {
    private final IndicatorSize size;
    private final DoubleSupplier value;
    private final int foreground;
    private final int background;
    private double progress;

    /** Supplies progress as a fraction. Finite values are clamped to [0, 1] and non-finite values clear the bar. */
    public ProgressIndicator(int width, int height, DoubleSupplier value, int foregroundArgb, int backgroundArgb) {
        size = new IndicatorSize(width, height);
        this.value = Objects.requireNonNull(value, "value");
        foreground = foregroundArgb;
        background = backgroundArgb;
    }

    @Override public IndicatorSize measure(IndicatorConstraints constraints) { return constraints.fit(size); }

    @Override public long minimumUpdateIntervalNanos() { return 50_000_000L; }

    @Override public void update(IndicatorContext context) {
        double next = value.getAsDouble();
        progress = Double.isFinite(next) ? Math.max(0, Math.min(1, next)) : 0;
    }

    @Override public void render(IndicatorRenderContext context) {
        context.fill(0, 0, context.width(), context.height(), background);
        context.fill(0, 0, (int) Math.round(context.width() * progress), context.height(), foreground);
    }
}
