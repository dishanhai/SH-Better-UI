// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Supplier;

/** Draws a localized label in a fixed logical size so text updates leave window geometry stable. */
public final class TextIndicator implements CompactIndicator {
    static final int TEXT_HEIGHT = 9;

    private final IndicatorSize size;
    private final Supplier<? extends Component> source;
    private final int argb;
    private Component text;

    /** The supplier returns a ready-to-render component snapshot on the client UI thread. */
    public TextIndicator(int width, int height, Supplier<? extends Component> source, int argb) {
        this.size = new IndicatorSize(width, height);
        this.source = Objects.requireNonNull(source, "source");
        this.argb = argb;
    }

    @Override public IndicatorSize measure(IndicatorConstraints constraints) { return constraints.fit(size); }

    @Override public long minimumUpdateIntervalNanos() { return 100_000_000L; }

    @Override public void update(IndicatorContext context) {
        text = Objects.requireNonNull(source.get(), "label");
    }

    @Override public void render(IndicatorRenderContext context) {
        if (text != null) drawLabel(context, text, argb);
    }

    static void drawLabel(IndicatorRenderContext context, Component label, int argb) {
        context.text(label, 0, Math.max(0, (context.height() - TEXT_HEIGHT) / 2), argb);
    }
}
