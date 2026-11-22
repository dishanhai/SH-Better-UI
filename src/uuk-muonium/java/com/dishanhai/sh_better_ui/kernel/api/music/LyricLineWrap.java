// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import com.muoniumplayer.core.rendering.font.CFontRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Keeps measured timing units intact and prefers dictionary words at legal line breaks. */
public final class LyricLineWrap {
    private LyricLineWrap() {}

    public static int[] rows(List<String> units, double[] advances, double width) {
        int count = units.size();
        int[] rows = new int[count];
        if (count < 2 || Arrays.stream(advances).sum() <= width) return rows;
        String text = String.join("", units);
        var words = BreakIterator.getWordInstance(ULocale.CHINESE);
        var lines = BreakIterator.getLineInstance(ULocale.CHINESE);
        words.setText(text);
        lines.setText(text);
        boolean[] legal = new boolean[count + 1];
        boolean[] preferred = new boolean[count + 1];
        int offset = 0;
        for (int i = 0; i < count; i++) {
            offset += units.get(i).length();
            legal[i + 1] = lines.isBoundary(offset);
            preferred[i + 1] = legal[i + 1] && words.isBoundary(offset);
        }
        int start = 0;
        for (int row = 0; start < count; row++) {
            int end = start;
            int lastWord = start, lastLegal = start;
            double used = 0;
            while (end < count && (end == start || used + advances[end] <= width)) {
                used += advances[end++];
                if (legal[end]) lastLegal = end;
                if (preferred[end]) lastWord = end;
            }
            if (end < count) {
                if (lastWord > start) end = lastWord;
                else if (lastLegal > start) end = lastLegal;
            }
            // A space immediately after a full row must not become a row of its own.
            while (end < count && units.get(end).isBlank()) end++;
            Arrays.fill(rows, start, end, row);
            start = end;
        }
        return rows;
    }

    public static String[] plain(CFontRenderer font, String text, double width) {
        if (text.isEmpty() || text.indexOf('§') >= 0) return font.fitWidth(text, width);
        if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0 && font.getStringWidthD(text) <= width)
            return new String[]{text};
        var output = new ArrayList<String>();
        for (String paragraph : text.split("\\R", -1)) {
            if (font.getStringWidthD(paragraph) <= width) {
                output.add(paragraph);
                continue;
            }
            var glyphs = BreakIterator.getCharacterInstance(ULocale.ROOT);
            glyphs.setText(paragraph);
            var units = new ArrayList<String>();
            for (int start = 0, end = glyphs.next(); end != BreakIterator.DONE; start = end, end = glyphs.next()) {
                units.add(paragraph.substring(start, end));
            }
            double[] widths = new double[units.size()];
            int offset = 0;
            for (int i = 0; i < units.size(); i++) {
                int end = offset + units.get(i).length();
                widths[i] = font.advanceInRun(paragraph, offset, end);
                offset = end;
            }
            int[] rows = rows(units, widths, width);
            for (int start = 0; start < units.size();) {
                int end = start + 1;
                while (end < units.size() && rows[end] == rows[start]) end++;
                output.add(String.join("", units.subList(start, end)).stripTrailing());
                start = end;
            }
        }
        return output.toArray(String[]::new);
    }
}
