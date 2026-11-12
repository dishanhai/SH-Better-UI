// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import java.util.Objects;
import java.util.function.Supplier;

/** Renders bounded, cached spectrum bars from amplitudes sampled by the caller. */
public final class SpectrumIndicator implements CompactIndicator {
    private static final int MAX_BANDS = 64;
    private static final int LOW_POWER_BANDS = 8;
    private static final double SMOOTHING_NANOS = 80_000_000.0;

    private final IndicatorSize size;
    private final Supplier<float[]> source;
    private final int argb;
    private final float[] bands = new float[MAX_BANDS];
    private int bandCount;
    private long lastSample;

    /**
     * Samples finite amplitudes in [0, 1] on the client UI thread. The source must stay stable until the
     * sample is copied. An empty array or null clears the bars. Audio acquisition belongs to the caller.
     */
    public SpectrumIndicator(int width, int height, Supplier<float[]> source, int argb) {
        size = new IndicatorSize(width, height);
        this.source = Objects.requireNonNull(source, "source");
        this.argb = argb;
    }

    @Override public IndicatorSize measure(IndicatorConstraints constraints) { return constraints.fit(size); }

    @Override public long minimumUpdateIntervalNanos() { return 33_333_333L; }

    @Override public void update(IndicatorContext context) {
        float[] sample = source.get();
        int maxBands = context.lowPower() ? LOW_POWER_BANDS : MAX_BANDS;
        int nextCount = sample == null ? 0 : Math.min(maxBands, sample.length);
        double mix = context.reducedMotion() || context.lowPower() || nextCount != bandCount ? 1
                : 1 - Math.exp(-Math.max(0L, context.nowNanos() - lastSample) / SMOOTHING_NANOS);
        for (int i = 0; i < nextCount; i++) {
            int index = (int) ((long) i * sample.length / nextCount);
            float amplitude = sample[index];
            float target = Float.isFinite(amplitude) ? Math.max(0, Math.min(1, amplitude)) : 0;
            bands[i] += (target - bands[i]) * (float) mix;
        }
        bandCount = nextCount;
        lastSample = context.nowNanos();
    }

    @Override public void render(IndicatorRenderContext context) {
        int visible = Math.min(bandCount, context.width());
        int height = context.height();
        for (int i = 0; i < visible; i++) {
            int band = i * bandCount / visible;
            int left = (int) ((long) i * context.width() / visible);
            int right = (int) ((long) (i + 1) * context.width() / visible);
            if (right - left > 1) right--;
            int pixels = Math.round(height * bands[band]);
            context.fill(left, height - pixels, right, height, argb);
        }
    }
}
