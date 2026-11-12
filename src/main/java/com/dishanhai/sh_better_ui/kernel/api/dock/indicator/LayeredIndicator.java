// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Composites independently measured and clipped layers at local offsets, from lowest to highest Z order. */
public final class LayeredIndicator implements CompactIndicator {
    /**
     * One owned indicator and its placement. Equal Z values preserve insertion order. Optional layers
     * are skipped in low-power mode without changing the composition's nominal measurement.
     */
    public record Layer(CompactIndicator indicator, int x, int y, int zIndex, float opacity,
                        long minimumUpdateIntervalNanos, boolean optional) {
        public Layer {
            Objects.requireNonNull(indicator, "indicator");
            if (x < 0 || y < 0 || !Float.isFinite(opacity) || opacity < 0 || opacity > 1
                    || minimumUpdateIntervalNanos < 0) {
                throw new IllegalArgumentException("Invalid indicator layer placement or refresh cadence");
            }
        }

        /** Creates a normal layer with full opacity and the indicator's default refresh cadence. */
        public Layer(CompactIndicator indicator, int x, int y) {
            this(indicator, x, y, 0, 1, 0, false);
        }
    }

    private record RuntimeLayer(Layer layer, IndicatorRenderer renderer) {
        IndicatorSize measure(int maxWidth, int maxHeight) {
            if (layer.x() >= maxWidth || layer.y() >= maxHeight || layer.opacity() == 0) {
                return IndicatorSize.EMPTY;
            }
            return renderer.measure(new IndicatorConstraints(maxWidth - layer.x(), maxHeight - layer.y()));
        }
    }

    private final List<RuntimeLayer> layers;

    /** Creates a composition with logging for any failed child. */
    public LayeredIndicator(List<Layer> layers) {
        this(layers, IndicatorRenderer::logFailure);
    }

    /** Creates a composition with a custom failure callback shared by its immediate children. */
    public LayeredIndicator(List<Layer> layers, Consumer<? super Throwable> reportFailure) {
        Objects.requireNonNull(reportFailure, "reportFailure");
        this.layers = List.copyOf(layers).stream()
                .sorted(Comparator.comparingInt(Layer::zIndex))
                .map(layer -> new RuntimeLayer(layer, new IndicatorRenderer(layer.indicator(),
                        layer.minimumUpdateIntervalNanos(), reportFailure)))
                .toList();
    }

    @Override public IndicatorSize measure(IndicatorConstraints constraints) {
        int width = 0;
        int height = 0;
        for (RuntimeLayer layer : layers) {
            IndicatorSize size = layer.measure(constraints.maxWidth(), constraints.maxHeight());
            if (size.width() == 0 || size.height() == 0) continue;
            width = Math.max(width, layer.layer().x() + size.width());
            height = Math.max(height, layer.layer().y() + size.height());
        }
        return new IndicatorSize(width, height);
    }

    @Override public void render(IndicatorRenderContext context) {
        for (RuntimeLayer runtime : layers) {
            Layer layer = runtime.layer();
            if (layer.optional() && context.frame().lowPower()) continue;
            IndicatorSize size = runtime.measure(context.width(), context.height());
            if (size.width() == 0 || size.height() == 0) continue;
            context.layer(runtime.renderer(), layer.x(), layer.y(), size, layer.opacity());
        }
    }
}
