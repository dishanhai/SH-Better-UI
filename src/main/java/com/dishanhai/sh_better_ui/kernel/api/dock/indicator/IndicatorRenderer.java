// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import com.dishanhai.sh_better_ui.kernel.api.RenderService;

import java.util.Objects;
import java.util.function.Consumer;

/** Client-thread owner of one indicator's measurements, refresh cadence, and failure boundary. */
public final class IndicatorRenderer {
    private static final System.Logger LOGGER = System.getLogger(IndicatorRenderer.class.getName());
    private static final long LOW_POWER_INTERVAL_NANOS = 250_000_000L;

    private final CompactIndicator indicator;
    private final Consumer<? super Throwable> reportFailure;
    private final long minimumUpdateIntervalNanos;
    private IndicatorConstraints measuredConstraints;
    private IndicatorSize measuredSize;
    private long lastUpdate;
    private float lastProgress = -1;
    private boolean updated;
    private boolean failed;

    /** Creates a renderer that reports failures to the system logger. */
    public IndicatorRenderer(CompactIndicator indicator) {
        this(indicator, IndicatorRenderer::logFailure);
    }

    /** Creates a renderer that reports an indicator failure once, then disables that indicator instance. */
    public IndicatorRenderer(CompactIndicator indicator, Consumer<? super Throwable> reportFailure) {
        this(indicator, 0, reportFailure);
    }

    IndicatorRenderer(CompactIndicator indicator, long minimumUpdateIntervalNanos,
                      Consumer<? super Throwable> reportFailure) {
        this.indicator = Objects.requireNonNull(indicator, "indicator");
        this.reportFailure = Objects.requireNonNull(reportFailure, "reportFailure");
        long interval = minimumUpdateIntervalNanos;
        try {
            long preferredInterval = indicator.minimumUpdateIntervalNanos();
            if (preferredInterval < 0) throw new IllegalArgumentException("Negative indicator update interval");
            interval = Math.max(interval, preferredInterval);
        } catch (RuntimeException | LinkageError | AssertionError error) {
            disable(error);
        }
        this.minimumUpdateIntervalNanos = interval;
    }

    /** Returns a bounded measurement, reusing the last result until constraints change. */
    public IndicatorSize measure(IndicatorConstraints constraints) {
        Objects.requireNonNull(constraints, "constraints");
        if (failed) return IndicatorSize.EMPTY;
        if (constraints.equals(measuredConstraints)) return measuredSize;
        try {
            measuredSize = constraints.fit(indicator.measure(constraints));
            measuredConstraints = constraints;
            return measuredSize;
        } catch (RuntimeException | LinkageError | AssertionError error) {
            disable(error);
            return IndicatorSize.EMPTY;
        }
    }

    /** Samples and draws the indicator within the window's current GUI rectangle during a kernel render pass. */
    public void render(RenderService.Context canvas, int x, int y, int width, int height, float opacity,
                       IndicatorContext frame) {
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(frame, "frame");
        if (width < 0 || height < 0 || !Float.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("Invalid indicator rectangle or opacity");
        }
        if (failed || width == 0 || height == 0 || opacity == 0) return;

        int right = Math.addExact(x, width);
        int bottom = Math.addExact(y, height);
        try {
            sample(frame);
        } catch (RuntimeException | LinkageError | AssertionError error) {
            disable(error);
            return;
        }
        var context = new IndicatorRenderContext(canvas, x, y, width, height, opacity, frame);
        try {
            canvas.clip(x, y, right, bottom, () -> {
                try {
                    indicator.render(context);
                } finally {
                    context.close();
                }
            });
        } catch (RuntimeException | LinkageError | AssertionError error) {
            disable(error);
        }
    }

    private void sample(IndicatorContext frame) {
        if (lastProgress != frame.expandProgress()) {
            indicator.onExpandProgress(frame.expandProgress());
            lastProgress = frame.expandProgress();
        }
        long interval = frame.lowPower() ? Math.max(LOW_POWER_INTERVAL_NANOS, minimumUpdateIntervalNanos)
                : Math.max(1, minimumUpdateIntervalNanos);
        if (!updated || frame.nowNanos() - lastUpdate >= interval) {
            indicator.update(frame);
            lastUpdate = frame.nowNanos();
            updated = true;
        }
    }

    private void disable(Throwable error) {
        failed = true;
        try {
            reportFailure.accept(error);
        } catch (RuntimeException | LinkageError | AssertionError reporterFailure) {
            logFailure(error);
            logFailure(reporterFailure);
        }
    }

    static void logFailure(Throwable error) {
        LOGGER.log(System.Logger.Level.WARNING, "Compact indicator disabled after a callback failed", error);
    }
}
