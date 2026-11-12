// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import java.util.Arrays;

/** Default extended grapheme boundaries (Unicode 17.0, UAX #29 §3.1.1). */
final class RichTextGraphemes {
    private static final int TYPE_MASK = 0x0F;
    private static final int INDIC_MASK = 0x30;
    private static final int PICTOGRAPHIC = 0x40;
    private static final int CR = 1, LF = 2, CONTROL = 3;
    private static final int L = 4, V = 5, T = 6, LV = 7, LVT = 8;
    private static final int EXTEND = 9, ZWJ = 10, SPACING_MARK = 11, PREPEND = 12, RI = 13;
    private static final int INDIC_CONSONANT = 0x10, INDIC_EXTEND = 0x20, INDIC_LINKER = 0x30;

    private RichTextGraphemes() {}

    /** Positive UTF-16 offsets of all cluster ends in ascending order. */
    static int[] ends(String text) {
        int[] ends = new int[text.length()];
        if (text.isEmpty()) return ends;
        var cursor = new Cursor(text);
        int count = 0;
        int end;
        do {
            end = cursor.nextBoundary();
            ends[count++] = end;
        } while (end < text.length());
        return count == ends.length ? ends : Arrays.copyOf(ends, count);
    }

    static boolean isBoundary(String text, int offset) {
        return offset == 0 || offset == text.length()
                || offset > 0 && offset < text.length() && boundaryAtOrAfter(text, offset) == offset;
    }

    static int boundaryAtOrAfter(String text, int offset) {
        if (offset == 0 || offset == text.length()) return offset;
        var cursor = new Cursor(text);
        int boundary;
        do {
            boundary = cursor.nextBoundary();
        } while (boundary < offset);
        return boundary;
    }

    private static int properties(int codePoint) {
        if (codePoint >= 0x80) return UnicodeGraphemeData.properties(codePoint);
        if (codePoint == '\r') return CR;
        if (codePoint == '\n') return LF;
        return codePoint < 0x20 || codePoint == 0x7F ? CONTROL : 0;
    }

    private static boolean isControl(int type) {
        return type >= CR && type <= CONTROL;
    }

    private static final class Cursor {
        private final String text;
        private int offset;
        private int previousType;
        private boolean oddRegionalIndicators;
        private boolean pictographWithExtenders;
        private boolean emojiJoiner;
        private boolean indicConsonant;
        private boolean indicLinker;

        Cursor(String text) {
            this.text = text;
            int point = text.codePointAt(0);
            advance(point, properties(point));
        }

        int nextBoundary() {
            while (offset < text.length()) {
                int boundary = offset;
                int point = text.codePointAt(offset);
                int current = properties(point);
                boolean separates = separates(current);
                advance(point, current);
                if (separates) return boundary;
            }
            return text.length();
        }

        private boolean separates(int current) {
            int type = current & TYPE_MASK;
            if (previousType == CR && type == LF) return false; // GB3
            if (isControl(previousType) || isControl(type)) return true; // GB4–5
            if (previousType == L && (type == L || type == V || type == LV || type == LVT)
                    || (previousType == LV || previousType == V) && (type == V || type == T)
                    || (previousType == LVT || previousType == T) && type == T) return false; // GB6–8
            if (type == EXTEND || type == ZWJ || type == SPACING_MARK || previousType == PREPEND) return false; // GB9–9b
            if (indicLinker && (current & INDIC_MASK) == INDIC_CONSONANT) return false; // GB9c
            if (emojiJoiner && (current & PICTOGRAPHIC) != 0) return false; // GB11
            return type != RI || !oddRegionalIndicators; // GB12–13, GB999
        }

        private void advance(int point, int current) {
            offset += Character.charCount(point);
            previousType = current & TYPE_MASK;
            oddRegionalIndicators = previousType == RI && !oddRegionalIndicators;
            emojiJoiner = previousType == ZWJ && pictographWithExtenders;
            pictographWithExtenders = (current & PICTOGRAPHIC) != 0
                    || previousType == EXTEND && pictographWithExtenders;
            switch (current & INDIC_MASK) {
                case INDIC_CONSONANT -> {
                    indicConsonant = true;
                    indicLinker = false;
                }
                case INDIC_LINKER -> indicLinker = indicConsonant;
                case INDIC_EXTEND -> { }
                default -> {
                    indicConsonant = false;
                    indicLinker = false;
                }
            }
        }
    }
}
