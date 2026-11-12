// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.dock.indicator;

/** Maximum available width and height in logical GUI pixels. A zero dimension means no available space. */
public record IndicatorConstraints(int maxWidth, int maxHeight) {
    public IndicatorConstraints {
        if (maxWidth < 0 || maxHeight < 0) throw new IllegalArgumentException("Negative indicator constraints");
    }

    /** Clamps the preferred size to these constraints. */
    public IndicatorSize fit(int width, int height) {
        return fit(new IndicatorSize(width, height));
    }

    /** Clamps the preferred size to these constraints. */
    public IndicatorSize fit(IndicatorSize size) {
        return new IndicatorSize(Math.min(size.width(), maxWidth), Math.min(size.height(), maxHeight));
    }
}
