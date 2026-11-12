// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Splits an already-shaped visual sink without reordering or replacing its entries. */
final class NativeTextClusters {
    private static final int MAX_GLYPHS = 256;

    private NativeTextClusters() {}

    static List<Cluster> measure(FormattedCharSequence sequence, float x, int base,
                                 Advance advance) {
        var glyphs = new ArrayList<Glyph>();
        var visual = new StringBuilder();
        boolean complete = sequence.accept((index, style, codePoint) -> {
            if (glyphs.size() == MAX_GLYPHS || !Character.isValidCodePoint(codePoint)
                    || style.isObfuscated() || style.isUnderlined() || style.isStrikethrough()
                    || style.getInsertion() != null) return false;
            glyphs.add(new Glyph(index, style, codePoint));
            visual.appendCodePoint(codePoint);
            return true;
        });
        if (!complete || glyphs.size() < 2 || !Float.isFinite(x)) return List.of();

        var boundaries = BreakIterator.getCharacterInstance(ULocale.ROOT);
        boundaries.setText(visual.toString());
        // Vanilla can emit an odd bidi run backwards, including its combining marks.
        // For that case the visual sink has no logical cluster map to recover attachment from.
        if (ambiguousBidi(glyphs)) return List.of();

        var clusters = new ArrayList<Cluster>();
        int glyph = 0;
        int offset = 0;
        float pen = x;
        for (int end = boundaries.next(); end != BreakIterator.DONE; end = boundaries.next()) {
            int start = glyph;
            float left = pen;
            int color = glyphs.get(start).color(base);
            boolean whitespace = true;
            while (offset < end) {
                var entry = glyphs.get(glyph++);
                float width = advance.measure(entry.sequence());
                if (!Float.isFinite(width) || width < 0 || entry.color(base) != color) return List.of();
                pen += width;
                offset += Character.charCount(entry.codePoint());
                whitespace &= Character.isWhitespace(entry.codePoint()) || Character.isSpaceChar(entry.codePoint());
            }
            if (!Float.isFinite(pen) || pen <= left) return List.of();
            if (!whitespace) {
                clusters.add(new Cluster(start, left, pen, color, List.copyOf(glyphs.subList(start, glyph))));
            }
        }
        return List.copyOf(clusters);
    }

    private static boolean ambiguousBidi(List<Glyph> glyphs) {
        boolean rtl = false;
        boolean attached = false;
        boolean reversed = false;
        int previousIndex = -1;
        for (var glyph : glyphs) {
            int code = glyph.codePoint();
            int direction = Character.getDirectionality(code);
            int type = Character.getType(code);
            rtl |= direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
            attached |= type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                    || type == Character.COMBINING_SPACING_MARK || type == Character.FORMAT;
            reversed |= glyph.index() < previousIndex;
            previousIndex = glyph.index();
        }
        return attached && (rtl || reversed);
    }

    @FunctionalInterface
    interface Advance {
        float measure(FormattedCharSequence glyph);
    }

    record Glyph(int index, Style style, int codePoint) {
        int color(int base) {
            var inline = style.getColor();
            return inline == null ? base : (base & 0xFF000000) | (inline.getValue() & 0xFFFFFF);
        }

        FormattedCharSequence sequence() {
            return sink -> sink.accept(index, style, codePoint);
        }
    }

    record Cluster(int index, float left, float right, int color, List<Glyph> glyphs) {
        FormattedCharSequence sequence() {
            return sink -> {
                for (var glyph : glyphs) {
                    if (!sink.accept(glyph.index(), glyph.style(), glyph.codePoint())) return false;
                }
                return true;
            };
        }
    }
}
