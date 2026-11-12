// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextGraphemeConformanceTest {
    @Test void unicode17GraphemeBreakTest() {
        var cases = UnicodeGraphemeTestData.CASES.lines().toList();
        assertEquals(766, cases.size());
        for (var line : cases) {
            var text = new StringBuilder();
            var boundaries = new ArrayList<Integer>();
            for (var token : line.split("\\s+")) {
                if (token.equals("|")) {
                    if (!text.isEmpty()) boundaries.add(text.length());
                } else if (!token.equals("+")) {
                    text.appendCodePoint(Integer.parseInt(token, 16));
                }
            }
            String input = text.toString();
            int[] ends = boundaries.stream().mapToInt(Integer::intValue).toArray();
            assertArrayEquals(ends, RichTextGraphemes.ends(input), line);
            for (int offset = 0; offset <= input.length(); offset++) {
                assertEquals(offset == 0 || Arrays.binarySearch(ends, offset) >= 0,
                        RichTextGraphemes.isBoundary(input, offset), line + " at " + offset);
            }
        }
    }

    @Test void emptyTextHasOnlyTheZeroBoundary() {
        assertArrayEquals(new int[0], RichTextGraphemes.ends(""));
        assertTrue(RichTextGraphemes.isBoundary("", 0));
        assertFalse(RichTextGraphemes.isBoundary("", -1));
        assertFalse(RichTextGraphemes.isBoundary("", 1));
    }
}
