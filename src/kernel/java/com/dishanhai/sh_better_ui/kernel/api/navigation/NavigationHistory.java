// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Independent navigation history; values can include any caller-owned scroll or filter state. */
public final class NavigationHistory<T> {
    private final List<T> pages = new ArrayList<>();
    private int index;

    public NavigationHistory(T initial) { pages.add(Objects.requireNonNull(initial, "initial")); }
    public T current() { return pages.get(index); }
    public boolean canBack() { return index > 0; }
    public boolean canForward() { return index + 1 < pages.size(); }
    public T back() { if (canBack()) index--; return current(); }
    public T forward() { if (canForward()) index++; return current(); }

    /** Adds a distinct destination and discards the now-unreachable forward branch. */
    public boolean push(T value) {
        Objects.requireNonNull(value, "value");
        if (current().equals(value)) return false;
        pages.subList(index + 1, pages.size()).clear();
        pages.add(value);
        index++;
        return true;
    }
}
