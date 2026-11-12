// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Reusable text layout decisions independent of the renderer and font. */
public final class TextFit {
    private TextFit() {}

    /** Sizes are relative to the native font. Pixel density is the GUI projection scale. */
    public record Scale(float requested, float minimum, double pixelsPerGui) {
        public Scale {
            if (!Float.isFinite(requested) || !Float.isFinite(minimum) || minimum <= 0 || requested < minimum
                    || !Double.isFinite(pixelsPerGui) || pixelsPerGui <= 0) {
                throw new IllegalArgumentException("Invalid text scale");
            }
        }

        /**
         * Fits within a measured scale limit, or returns zero when the minimum does not fit.
         * Only automatic reductions snap down to whole physical pixels, subject to the minimum.
         */
        public float within(double limit) {
            if (Double.isNaN(limit) || limit < 0) throw new IllegalArgumentException("Invalid text scale limit");
            if (limit < minimum) return 0;
            if (limit >= requested) return requested;
            double pixels = Math.floor(limit * pixelsPerGui + 0.000001);
            double snapped = pixels / pixelsPerGui;
            float result = (float) (snapped >= minimum ? Math.min(limit, snapped) : limit);
            return result > limit ? Math.nextDown(result) : result;
        }
    }

    /**
     * Selects rows greedily using the supplied measurement. Lower priorities are tried first,
     * ties favor the original order, and the output retains that order. The fit predicate must be stable
     * for the call and must not make a larger layout fit by adding more rows.
     * When rows are omitted, a non-null notice reserves its own space and is never included in the result.
     */
    public static <T> Rows<T> selectRows(List<T> rows, ToIntFunction<? super T> priority,
                                         Predicate<List<T>> fits, T notice) {
        List<T> source = List.copyOf(rows);
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(fits, "fits");
        if (source.isEmpty() || fits.test(source)) return new Rows<>(source, 0);
        if (notice != null && !fits.test(List.of(notice))) return new Rows<>(List.of(), source.size());

        var order = new ArrayList<Integer>(source.size());
        for (int i = 0; i < source.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt(index -> priority.applyAsInt(source.get(index))));
        var selected = new ArrayList<Integer>();
        for (int index : order) {
            // Measure in visual order so padding, row types and the trailing notice can be handled by the caller.
            int slot = -Collections.binarySearch(selected, index) - 1;
            selected.add(slot, index);
            var measured = new ArrayList<T>(selected.size() + 1);
            for (int kept : selected) measured.add(source.get(kept));
            if (notice != null) measured.add(notice);
            if (!fits.test(List.copyOf(measured))) selected.remove(slot);
        }
        return new Rows<>(selected.stream().map(source::get).toList(), source.size() - selected.size());
    }

    public record Rows<T>(List<T> visible, int omitted) {
        public Rows { visible = List.copyOf(visible); }
    }
}
