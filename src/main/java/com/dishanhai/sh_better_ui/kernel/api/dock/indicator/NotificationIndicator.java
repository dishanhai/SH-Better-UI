// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Shows a localized count for pending notifications and leaves the area blank at zero. */
public final class NotificationIndicator implements CompactIndicator {
    private final IndicatorSize size;
    private final IntSupplier source;
    private final IntFunction<? extends Component> label;
    private final int argb;
    private int count;
    private Component text;

    /** The formatter receives a positive count and returns a localized label that fits the allocation. */
    public NotificationIndicator(int width, int height, IntSupplier source, IntFunction<? extends Component> label,
                                 int argb) {
        size = new IndicatorSize(width, height);
        this.source = Objects.requireNonNull(source, "source");
        this.label = Objects.requireNonNull(label, "label");
        this.argb = argb;
    }

    /** Returns a source that adds the supplied counts on the UI thread, ignoring negatives and saturating on overflow. */
    public static IntSupplier aggregate(List<? extends IntSupplier> sources) {
        List<? extends IntSupplier> snapshot = List.copyOf(sources);
        return () -> {
            int total = 0;
            for (IntSupplier source : snapshot) {
                int contribution = Math.max(0, source.getAsInt());
                if (Integer.MAX_VALUE - total <= contribution) return Integer.MAX_VALUE;
                total += contribution;
            }
            return total;
        };
    }

    @Override public IndicatorSize measure(IndicatorConstraints constraints) { return constraints.fit(size); }

    @Override public long minimumUpdateIntervalNanos() { return 100_000_000L; }

    @Override public void update(IndicatorContext context) {
        int next = Math.max(0, source.getAsInt());
        if (next != count) {
            text = next == 0 ? null : Objects.requireNonNull(label.apply(next), "notification label");
            count = next;
        }
    }

    @Override public void render(IndicatorRenderContext context) {
        if (text != null) TextIndicator.drawLabel(context, text, argb);
    }
}
