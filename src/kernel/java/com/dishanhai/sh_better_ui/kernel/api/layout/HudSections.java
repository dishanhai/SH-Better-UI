// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Expands visible HUD rows into section headings and divider positions after filtering or paging. */
public final class HudSections {
    private HudSections() {}

    public static <T, K> Plan<T> layout(List<T> rows, Function<T, K> section,
                                         Predicate<K> showHeading, Function<K, T> heading,
                                         boolean separators) {
        var expanded = new ArrayList<T>();
        var boundaries = new ArrayList<Integer>();
        K previous = null;
        boolean first = true;
        for (T row : rows) {
            K key = Objects.requireNonNull(section.apply(row), "section");
            if (first || !key.equals(previous)) {
                if (!first && separators) boundaries.add(expanded.size());
                if (showHeading.test(key)) expanded.add(Objects.requireNonNull(heading.apply(key), "heading"));
                previous = key;
                first = false;
            }
            expanded.add(row);
        }
        return new Plan<>(List.copyOf(expanded), List.copyOf(boundaries));
    }

    public record Plan<T>(List<T> rows, List<Integer> boundaries) {}
}
