// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.controls;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Bounded page links and non-interactive ellipses; never allocates the entire page count. */
public final class PaginationRange {
    private PaginationRange() {}
    /** Zero denotes an ellipsis; all actual page links are one-based. */
    public record Item(long page) {
        public Item { if (page < 0) throw new IllegalArgumentException("Negative page"); }
        public boolean ellipsis() { return page == 0; }
    }
    public static List<Item> items(long count, long current, int siblings, int boundaries) {
        if (count < 0 || siblings < 0 || siblings > 100 || boundaries < 0 || boundaries > 100)
            throw new IllegalArgumentException("Invalid pagination range");
        if (count == 0) return List.of();
        current = Math.max(1, Math.min(count, current));
        var pages = new TreeSet<Long>();
        for (int i = 0; i < boundaries && i < count; i++) {
            pages.add(1L + i);
            pages.add(count - i);
        }
        for (int i = -siblings; i <= siblings; i++) {
            if (i < 0 && current <= -i || i > 0 && current > count - i) continue;
            pages.add(current + i);
        }
        var result = new ArrayList<Item>();
        long previous = 0;
        for (long page : pages) {
            if (previous > 0 && page - previous == 2) result.add(new Item(previous + 1));
            else if (previous > 0 && page - previous > 2) result.add(new Item(0));
            result.add(new Item(page));
            previous = page;
        }
        return List.copyOf(result);
    }
}
