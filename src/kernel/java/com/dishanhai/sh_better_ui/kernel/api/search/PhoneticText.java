// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.search;

import java.util.List;
import java.util.Objects;

/** Prepared, normalized syllables for full, initial and mixed-prefix text search. */
public final class PhoneticText {
    private final List<String> syllables;
    private final String full;
    private final String initials;

    /** Callers supply syllables and queries with the same case and accent normalization. */
    public PhoneticText(List<String> syllables) {
        this.syllables = List.copyOf(syllables);
        StringBuilder first = new StringBuilder();
        for (String syllable : this.syllables) {
            if (syllable.isEmpty()) throw new IllegalArgumentException("syllables must not be empty");
            first.appendCodePoint(syllable.codePointAt(0));
        }
        full = String.join("", this.syllables);
        initials = first.toString();
    }

    /** Matches consecutive syllables, consuming at least one prefix character from each. */
    public boolean contains(String query) {
        Objects.requireNonNull(query, "query");
        if (query.isEmpty() || query.length() > full.length()) return false;
        if (full.contains(query) || initials.contains(query)) return true;

        // Track all prefix lengths instead of expanding exponentially many spelling keys.
        boolean[] reachable = new boolean[query.length() + 1];
        boolean[] next = new boolean[query.length() + 1];
        for (String syllable : syllables) {
            java.util.Arrays.fill(next, false);
            for (int offset = 0; offset < query.length(); offset++) {
                if (offset != 0 && !reachable[offset]) continue;
                int length = Math.min(syllable.length(), query.length() - offset);
                for (int i = 0; i < length && syllable.charAt(i) == query.charAt(offset + i); i++) {
                    if (offset + i + 1 == query.length()) return true;
                    next[offset + i + 1] = true;
                }
            }
            boolean[] swap = reachable;
            reachable = next;
            next = swap;
        }
        return false;
    }
}
