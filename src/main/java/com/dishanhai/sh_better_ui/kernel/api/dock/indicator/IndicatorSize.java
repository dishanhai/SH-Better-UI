// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

/** Preferred size in logical GUI pixels, including any padding the indicator draws itself. */
public record IndicatorSize(int width, int height) {
    static final IndicatorSize EMPTY = new IndicatorSize(0, 0);

    public IndicatorSize {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative indicator size");
    }
}
