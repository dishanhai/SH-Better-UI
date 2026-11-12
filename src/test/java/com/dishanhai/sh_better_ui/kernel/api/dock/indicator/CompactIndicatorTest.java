// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CompactIndicatorTest {
    @Test
    void layersAreMeasuredWithinTheAvailableSpaceAndDrawnInAscendingZOrder() {
        int bottomColor = 0xff123456;
        int topColor = 0xffabcdef;
        var bottom = new LayeredIndicator.Layer(block(20, 10, bottomColor), 0, 1, -1, 1, 0, false);
        var top = new LayeredIndicator.Layer(block(5, 3, topColor), 2, 0, 2, 0.5f, 0, false);
        var renderer = new IndicatorRenderer(new LayeredIndicator(List.of(top, bottom)));
        assertEquals(new IndicatorSize(9, 7), renderer.measure(new IndicatorConstraints(9, 7)));

        var canvas = new Canvas();
        renderer.render(canvas, 10, 20, 9, 7, 0.5f, frame(0, 0, false));

        assertEquals(List.of(new Fill(10, 21, 19, 27, 0x80123456),
                new Fill(12, 20, 17, 23, 0x40abcdef)), canvas.fills);
        assertEquals(0, canvas.clips.size());
    }

    @Test
    void failureInOneLayerUnwindsItsClipAndDoesNotDisableSiblingLayers() {
        var failures = new ArrayList<Throwable>();
        var attempts = new AtomicInteger();
        var leaking = new AtomicReference<IndicatorRenderContext>();
        var broken = new CompactIndicator() {
            @Override public IndicatorSize measure(IndicatorConstraints constraints) { return constraints.fit(2, 2); }
            @Override public void render(IndicatorRenderContext canvas) {
                leaking.set(canvas);
                attempts.incrementAndGet();
                throw new AssertionError("bad indicator");
            }
        };
        var renderer = new IndicatorRenderer(new LayeredIndicator(List.of(
                new LayeredIndicator.Layer(broken, 0, 0),
                new LayeredIndicator.Layer(block(3, 3, 0xfffefefe), 2, 2)), failure -> {
                    assertThrows(IllegalStateException.class, () -> leaking.get().fill(0, 0, 1, 1, 0xffffffff));
                    failures.add(failure);
                }));
        var canvas = new Canvas();

        renderer.render(canvas, 0, 0, 5, 5, 1, frame(0, 0, false));
        renderer.render(canvas, 0, 0, 5, 5, 1, frame(20_000_000, 0, false));

        assertEquals(1, attempts.get());
        assertEquals(1, failures.size());
        assertEquals(List.of(new Fill(2, 2, 5, 5, 0xfffefefe),
                new Fill(2, 2, 5, 5, 0xfffefefe)), canvas.fills);
        assertEquals(0, canvas.clips.size());
        assertThrows(IllegalStateException.class, () -> leaking.get().fill(0, 0, 1, 1, 0xffffffff));
    }

    @Test
    void dataUpdatesAreThrottledWhileProgressRemainsFrameResponsive() {
        var samples = new ArrayList<Long>();
        var progress = new ArrayList<Float>();
        var measurements = new AtomicInteger();
        var indicator = new CompactIndicator() {
            @Override public IndicatorSize measure(IndicatorConstraints constraints) {
                measurements.incrementAndGet();
                return constraints.fit(3, 3);
            }
            @Override public void update(IndicatorContext context) { samples.add(context.nowNanos()); }
            @Override public void onExpandProgress(float value) { progress.add(value); }
            @Override public void render(IndicatorRenderContext canvas) { }
        };
        var renderer = new IndicatorRenderer(new LayeredIndicator(List.of(
                new LayeredIndicator.Layer(indicator, 0, 0, 0, 1, 30_000_000, false))));
        var canvas = new Canvas();
        renderer.render(canvas, 0, 0, 5, 5, 1, frame(0, 0, false));
        renderer.render(canvas, 0, 0, 5, 5, 1, frame(10_000_000, 0.3f, false));
        renderer.render(canvas, 0, 0, 5, 5, 1, frame(30_000_000, 0.6f, false));
        renderer.render(canvas, 0, 0, 5, 5, 1, frame(65_000_000, 0.7f, true));
        renderer.render(canvas, 0, 0, 5, 5, 1, frame(280_000_000, 0.7f, true));

        assertEquals(List.of(0L, 30_000_000L, 280_000_000L), samples);
        assertEquals(List.of(0f, 0.3f, 0.6f, 0.7f), progress);
        assertEquals(1, measurements.get());
    }

    @Test
    void lowPowerSkipsOptionalLayersAndResumesThemWhenPowerReturns() {
        var layer = new LayeredIndicator.Layer(block(2, 2, 0xff123456), 0, 0, 0, 1, 0, true);
        var renderer = new IndicatorRenderer(new LayeredIndicator(List.of(layer)));
        var canvas = new Canvas();
        renderer.render(canvas, 0, 0, 2, 2, 1, frame(0, 0, true));
        assertTrue(canvas.fills.isEmpty());
        renderer.render(canvas, 0, 0, 2, 2, 1, frame(10_000_000, 0, false));
        assertEquals(List.of(new Fill(0, 0, 2, 2, 0xff123456)), canvas.fills);
    }

    @Test
    void spectrumUsesASanitizedSnapshotOfExternalSamples() {
        var bands = new float[]{1, Float.NaN, 0.5f};
        var renderer = new IndicatorRenderer(new SpectrumIndicator(6, 10, () -> bands, 0xfffedcba));
        var canvas = new Canvas();
        renderer.render(canvas, 0, 0, 6, 10, 1, frame(0, 0, false));
        var expected = List.of(new Fill(0, 0, 1, 10, 0xfffedcba),
                new Fill(4, 5, 5, 10, 0xfffedcba));
        assertEquals(expected, canvas.fills);
        bands[0] = 0;
        canvas.fills.clear();
        renderer.render(canvas, 0, 0, 6, 10, 1, frame(1_000_000, 0, false));
        assertEquals(expected, canvas.fills);
    }

    @Test
    void progressClearsNonFiniteSamplesAndClampsOutOfRangeValues() {
        var progress = new double[]{2};
        var renderer = new IndicatorRenderer(new ProgressIndicator(6, 2, () -> progress[0],
                0xff112233, 0xff445566));
        var canvas = new Canvas();
        renderer.render(canvas, 0, 0, 6, 2, 1, frame(0, 0, false));
        assertEquals(List.of(new Fill(0, 0, 6, 2, 0xff445566), new Fill(0, 0, 6, 2, 0xff112233)), canvas.fills);

        progress[0] = Double.NaN;
        canvas.fills.clear();
        renderer.render(canvas, 0, 0, 6, 2, 1, frame(50_000_000, 0, false));
        assertEquals(List.of(new Fill(0, 0, 6, 2, 0xff445566)), canvas.fills);
    }

    @Test
    void notificationAggregationIgnoresNegativeSourcesAndSaturates() {
        var aggregate = NotificationIndicator.aggregate(List.of(() -> -4, () -> Integer.MAX_VALUE - 1, () -> 3));
        assertEquals(Integer.MAX_VALUE, aggregate.getAsInt());
        assertEquals(3, NotificationIndicator.aggregate(List.of(() -> -4, () -> 3)).getAsInt());
    }

    private static CompactIndicator block(int width, int height, int color) {
        return new CompactIndicator() {
            @Override public IndicatorSize measure(IndicatorConstraints constraints) {
                return constraints.fit(width, height);
            }
            @Override public void render(IndicatorRenderContext canvas) {
                canvas.fill(-100, -100, 100, 100, color);
            }
        };
    }

    private static IndicatorContext frame(long nanos, float progress, boolean lowPower) {
        return new IndicatorContext(nanos, progress, false, lowPower);
    }

    private record Fill(int x, int y, int right, int bottom, int color) { }
    private record Clip(int x, int y, int right, int bottom) { }

    private static final class Canvas implements RenderService.Context {
        final List<Fill> fills = new ArrayList<>();
        final List<Clip> clips = new ArrayList<>();

        @Override public RenderService.Frame frame() {
            return new RenderService.Frame(RenderService.Surface.HUD, 320, 240, 1, 0);
        }

        @Override public void fill(int x, int y, int right, int bottom, int color) {
            assertFalse(clips.isEmpty());
            for (Clip clip : clips) {
                assertTrue(x >= clip.x() && y >= clip.y() && right <= clip.right() && bottom <= clip.bottom());
            }
            fills.add(new Fill(x, y, right, bottom, color));
        }

        @Override public void clip(int x, int y, int right, int bottom, Runnable draw) {
            clips.add(new Clip(x, y, right, bottom));
            try {
                draw.run();
            } finally {
                clips.remove(clips.size() - 1);
            }
        }

        @Override public void transform(double x, double y, float scale, Runnable draw) {
            fail("Unexpected native transform");
        }
    }
}
