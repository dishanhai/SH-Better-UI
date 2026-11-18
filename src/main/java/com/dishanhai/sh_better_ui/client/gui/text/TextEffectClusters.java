package com.dishanhai.sh_better_ui.client.gui.text;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;

/** Visual-order cluster indices. Never reorders a shaped Minecraft sequence. */
final class TextEffectClusters {
    private TextEffectClusters() {}

    /** Hard line boundaries must not become emoji/combining clusters across rows. */
    static int[] ordinals(int[] codePoints, int[] sourceIndices, int[] boundaries) {
        if (codePoints.length != sourceIndices.length) throw new IllegalArgumentException("Mismatched glyph arrays");
        int[] result = new int[codePoints.length];
        int from = 0;
        int ordinal = 0;
        for (int part = 0; part <= boundaries.length; part++) {
            int to = part == boundaries.length ? codePoints.length : boundaries[part];
            if (to < from || to > codePoints.length) throw new IllegalArgumentException("Invalid line boundary");
            if (to == from) continue;
            int[] segment = ordinals(java.util.Arrays.copyOfRange(codePoints, from, to),
                    java.util.Arrays.copyOfRange(sourceIndices, from, to));
            for (int i = 0; i < segment.length; i++) result[from + i] = ordinal + segment[i];
            ordinal += segment[segment.length - 1] + 1;
            from = to;
        }
        return result;
    }

    static int[] ordinals(int[] codePoints, int[] sourceIndices) {
        if (codePoints.length != sourceIndices.length) throw new IllegalArgumentException("Mismatched glyph arrays");
        int[] result = new int[codePoints.length];
        StringBuilder visual = new StringBuilder();
        boolean attachment = false;
        boolean reversed = codePoints.length > 1;
        boolean rtl = false;
        for (int i = 0; i < codePoints.length; i++) {
            int cp = codePoints[i];
            visual.appendCodePoint(cp);
            int type = Character.getType(cp);
            attachment |= type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK || type == Character.FORMAT;
            byte direction = Character.getDirectionality(cp);
            rtl |= direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
            if (i > 0) reversed &= sourceIndices[i] < sourceIndices[i - 1];
        }
        // A visual sequence can emit marks before their logical base. Without the logical
        // shaping map, move this run together rather than guess and detach its marks.
        if (attachment && (reversed || rtl)) return result;
        BreakIterator boundaries = BreakIterator.getCharacterInstance(ULocale.ROOT);
        boundaries.setText(visual.toString());
        int offset = 0;
        int glyph = 0;
        int ordinal = 0;
        for (int end = boundaries.next(); end != BreakIterator.DONE; end = boundaries.next()) {
            while (offset < end) {
                result[glyph] = ordinal;
                offset += Character.charCount(codePoints[glyph++]);
            }
            ordinal++;
        }
        return result;
    }
}
