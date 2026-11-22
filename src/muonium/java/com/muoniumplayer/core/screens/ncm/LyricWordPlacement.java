package com.muoniumplayer.core.screens.ncm;

import com.muoniumplayer.core.rendering.font.CFontRenderer;
import java.util.Arrays;
import java.util.List;

/** Immutable qplayer geometry, independent of playhead, opacity and spring state. */
final class LyricWordPlacement {
    final double[] x, widths;
    final int[] row, first, last;
    final String[][] glyphs;
    final double[][] glyphAdvances;
    final long[][] glyphStarts;
    final double columnLeft, columnWidth;
    final boolean rightAligned;
    final CFontRenderer font;
    final long fontRevision, lineRevision;

    private LyricWordPlacement(LyricLine line, CFontRenderer font, double columnLeft,
                               double columnWidth, boolean rightAligned, double[] x,
                               double[] widths, int[] row, int rowCount) {
        this.font = font;
        this.fontRevision = font.metricsRevision();
        this.lineRevision = line.layoutRevision();
        this.columnLeft = columnLeft;
        this.columnWidth = columnWidth;
        this.rightAligned = rightAligned;
        this.x = x;
        this.widths = widths;
        this.row = row;
        first = new int[rowCount];
        last = new int[rowCount];
        Arrays.fill(first, -1);
        Arrays.fill(last, -1);
        glyphs = new String[x.length][];
        glyphAdvances = new double[x.length][];
        glyphStarts = new long[x.length][];
        for (int i = 0; i < x.length; i++) {
            if (first[row[i]] < 0) first[row[i]] = i;
            last[row[i]] = i;
            String text = line.words.get(i).word;
            String[] clusters = LyricGraphemeTimeline.graphemes(text).toArray(String[]::new);
            glyphs[i] = clusters;
            glyphAdvances[i] = new double[clusters.length];
            glyphStarts[i] = new long[clusters.length];
            LyricLine.Word word = line.words.get(i);
            int offset = 0;
            for (int c = 0; c < clusters.length; c++) {
                String cluster = clusters[c];
                int end = offset + cluster.length();
                glyphAdvances[i][c] = font.advanceInRun(text, offset, end);
                glyphStarts[i][c] = word.timestamp + Math.max(0L, word.duration) * c / clusters.length;
                font.prepareText(cluster);
                offset = end;
            }
        }
    }

    boolean matches(LyricLine line, CFontRenderer font, double left, double width, boolean right) {
        return this.font == font && fontRevision == font.metricsRevision()
                && lineRevision == line.layoutRevision() && x.length == line.words.size()
                && rightAligned == right && Math.abs(columnLeft - left) <= .5
                && Math.abs(columnWidth - width) <= .5;
    }

    static LyricWordPlacement build(LyricLine line, CFontRenderer font, double left,
                                    double width, boolean rightAligned) {
        List<LyricLine.Word> words = line.words;
        int count = words.size();
        var layout = line.wordRows(font, width);
        double[] widths = layout.widths();
        int[] rows = layout.rows();
        double[] rowWidths = new double[layout.count()];
        for (int i = 0; i < count; i++) {
            rowWidths[rows[i]] += widths[i];
        }
        double[] xs = new double[count];
        double[] rowCursor = new double[layout.count()];
        for (int i = 0; i < rowCursor.length; i++) {
            rowCursor[i] = rightAligned ? left + width - Math.min(rowWidths[i], width) : left;
        }
        for (int i = 0; i < count; i++) {
            xs[i] = rowCursor[rows[i]];
            rowCursor[rows[i]] += widths[i];
        }
        return new LyricWordPlacement(line, font, left, width, rightAligned, xs, widths, rows, layout.count());
    }
}
