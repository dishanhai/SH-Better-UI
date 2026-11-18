// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeFormattedTextTest {
    @Test void ordinaryStringRemainsLiteralAndKnownMarkupIsNormalizedBeforeLayout() {
        assertNull(UiFormattedText.parseLegacy("hello & welcome"));
        assertNull(UiFormattedText.parseLegacy("&$unknown-palette-name"));
        assertEquals("prefix 山🌊", UiFormattedText.parseLegacy("prefix &$rainbow-山🌊").getString());
    }

    @Test void frameRetainsSupplementaryGlyphsFontStyleAndNativeInsertion() {
        var font = new FontDescription.Resource(Identifier.parse("minecraft:uniform"));
        var styled = Style.EMPTY.withBold(true).withItalic(true).withFont(font).withInsertion("fcs:rainbow|~");
        FormattedCharSequence visual = sink -> sink.accept(0, Style.EMPTY.withInsertion("keep"), 'A')
                && sink.accept(1, styled, 0x1f30a) && sink.accept(3, styled, '海');
        var frame = UiFormattedText.sample(visual, 0xffabcdef, UiPreferences.DEFAULT, null, 500);
        assertEquals(List.of((int)'A', 0x1f30a, (int)'海'), frame.stream().map(UiFormattedText.Glyph::codePoint).toList());
        assertEquals(List.of(0, 1, 3), frame.stream().map(UiFormattedText.Glyph::index).toList());
        assertEquals("keep", frame.getFirst().style().getInsertion());
        assertNull(frame.get(1).style().getInsertion());
        assertTrue(frame.get(1).style().isBold());
        assertTrue(frame.get(1).style().isItalic());
        assertEquals(font, frame.get(1).style().getFont());
    }

    @Test void combiningSequenceSharesMotionAndColorPhase() {
        var style = Style.EMPTY.withInsertion("fcs:rainbow|~");
        var frame = UiFormattedText.sample(FormattedCharSequence.forward("e\u0301X", style),
                0xffffffff, UiPreferences.DEFAULT, null, 215);
        assertEquals(frame.get(0).cluster(), frame.get(1).cluster());
        assertEquals(frame.get(0).x(), frame.get(1).x());
        assertEquals(frame.get(0).y(), frame.get(1).y());
        assertEquals(frame.get(0).style().getColor(), frame.get(1).style().getColor());
        assertNotEquals(frame.get(1).cluster(), frame.get(2).cluster());
    }

    @Test void reversedCombiningRunKeepsOriginalOrderAsOnePositionedGroup() {
        var style = Style.EMPTY.withInsertion("fcs:rainbow|~");
        FormattedCharSequence visual = sink -> sink.accept(2, style, 0x05b0)
                && sink.accept(1, style, 0x05d0) && sink.accept(0, style, 0x05d1);
        var frame = UiFormattedText.sample(visual, 0xffffffff, UiPreferences.DEFAULT, null, 100);
        assertEquals(List.of(2, 1, 0), frame.stream().map(UiFormattedText.Glyph::index).toList());
        assertTrue(frame.stream().allMatch(g -> g.cluster() == frame.getFirst().cluster()));
    }

    @Test void highContrastRemovesMotionAndKeepsAuthoredColor() {
        var style = Style.EMPTY.withColor(0x123456).withBold(true).withInsertion("fcs:rainbow|~#");
        var frame = UiFormattedText.sample(FormattedCharSequence.forward("字", style), 0xffffffff,
                new UiPreferences(true, false, false), null, 215);
        var glyph = frame.getFirst();
        assertEquals(style.withInsertion(null), glyph.style());
        assertEquals(0, glyph.x()); assertEquals(0, glyph.y()); assertFalse(glyph.outline());
    }

    @Test void dynamicFramesChangeWhileReducedMotionKeepsAStableReadableFrame() {
        var source = FormattedCharSequence.forward("text", Style.EMPTY.withInsertion("fcs:rainbow|~"));
        var first = UiFormattedText.sample(source, 0xffffffff, UiPreferences.DEFAULT, null, 0);
        var later = UiFormattedText.sample(source, 0xffffffff, UiPreferences.DEFAULT, null, 400);
        assertNotEquals(first, later);
        var reduced = new UiPreferences(false, true, false);
        var quietFirst = UiFormattedText.sample(source, 0xffffffff, reduced, null, 0);
        var quietLater = UiFormattedText.sample(source, 0xffffffff, reduced, null, 400);
        assertEquals(quietFirst, quietLater);
        assertTrue(quietFirst.stream().allMatch(g -> g.x() == 0 && g.y() == 0));
    }

    @Test void colorizeLinesSharesClockDirectionAndUnicodePhase() {
        var style = Style.EMPTY.withInsertion("fcs:rainbow");
        var timing = new UiTextEffects.Timing(137, true);
        var lines = UiFormattedText.colorizeLines(List.of(FormattedCharSequence.forward("山🌊", style),
                FormattedCharSequence.forward("海", style)), 0xffffffff, 137, UiPreferences.DEFAULT, timing);
        var single = UiFormattedText.colorize(FormattedCharSequence.forward("山🌊海", style),
                0xffffffff, 137, UiPreferences.DEFAULT, timing);
        var colors = colors(single);
        assertEquals(colors.subList(0, 2), colors(lines.getFirst()));
        assertEquals(colors.subList(2, 3), colors(lines.getLast()));
        assertNotEquals(colors, colors(UiFormattedText.colorize(FormattedCharSequence.forward("山🌊海", style),
                0xffffffff, 137, UiPreferences.DEFAULT, new UiTextEffects.Timing(137, false))));
    }

    @Test void gradientLyricsAndRevealRetainVisualIndicesAndStyles() {
        var style = Style.EMPTY.withBold(true);
        FormattedCharSequence source = sink -> sink.accept(4, style, 0x1f30a)
                && sink.accept(2, style, '山') && sink.accept(0, style, '海');
        assertEquals(List.of(0xff0000, 0x800080, 0x0000ff),
                colors(UiFormattedText.gradientFiller(source, 0xff0000, 0x0000ff)));
        assertEquals(List.of(0xffffff, 0x808080, 0),
                colors(UiFormattedText.colorizeLyric(source, .5f, 0xffffff, 0)));
        assertEquals(List.of(0, 0xffffff, 0),
                colors(UiFormattedText.colorizeLyric(source, i -> i == 1 ? 2 : Double.NaN, 0xffffff, 0)));
        var revealed = UiFormattedText.reveal(source, 1, 0, 0, UiPreferences.DEFAULT);
        var points = new java.util.ArrayList<Integer>();
        var indices = new java.util.ArrayList<Integer>();
        revealed.accept((index, current, cp) -> { indices.add(index); points.add(cp); assertTrue(current.isBold()); return true; });
        assertEquals(List.of(4, 2, 0), indices);
        assertEquals(List.of(0x1f30a, (int)'　', (int)'　'), points);
        assertFalse(revealed.accept((index, current, cp) -> false));
    }

    @Test void unmarkedColorizeReturnsTheSameNativeSequence() {
        var source = FormattedCharSequence.forward("plain", Style.EMPTY);
        assertSame(source, UiFormattedText.colorize(source, 0xffffffff, 215, UiPreferences.DEFAULT));
        assertTrue(UiFormattedText.colorizeLines(List.of(), 0xffffffff, 0, UiPreferences.DEFAULT,
                UiTextEffects.Timing.DEFAULT).isEmpty());
    }

    @Test void customOffsetsAndOutlineWithinAClusterRemainSeparateDrawGroups() {
        var id = Identifier.parse("test:native_cluster_offsets");
        UiTextEffects.registerEffect(id, context -> new UiTextEffects.Glyph(context.rgb(),
                context.codePoint() == 'e' ? 1 : 2, 0, context.codePoint() == 0x301, false));
        try {
            var source = UiTextEffects.apply(net.minecraft.network.chat.Component.literal("e\u0301"), id)
                    .getVisualOrderText();
            var frame = UiFormattedText.sample(source, 0xffffffff, UiPreferences.DEFAULT, null, 0);
            assertEquals(frame.get(0).cluster(), frame.get(1).cluster());
            assertEquals(1, UiFormattedText.drawingGroupEnd(frame, 0));
            var sameOffsetDifferentOutline = List.of(
                    new UiFormattedText.Glyph(0, Style.EMPTY, 'e', 0, 0, false, 0),
                    new UiFormattedText.Glyph(1, Style.EMPTY, 0x301, 0, 0, true, 0));
            assertEquals(1, UiFormattedText.drawingGroupEnd(sameOffsetDifferentOutline, 0));
        } finally { UiTextEffects.unregister(id); }
    }

    @Test void combiningMarksAcrossLinesKeepSharedPhaseWithoutLosingLineLocalIndices() {
        var style = Style.EMPTY.withInsertion("fcs:rainbow");
        var lines = UiFormattedText.colorizeLines(List.of(FormattedCharSequence.forward("e\u0301", style),
                FormattedCharSequence.forward("X\u0301", style)), 0xffffffff, 215,
                UiPreferences.DEFAULT, UiTextEffects.Timing.DEFAULT);
        var expected = colors(UiFormattedText.colorize(FormattedCharSequence.forward("e\u0301X\u0301", style),
                0xffffffff, 215, UiPreferences.DEFAULT));
        assertEquals(expected.subList(0, 2), colors(lines.getFirst()));
        assertEquals(expected.subList(2, 4), colors(lines.getLast()));
        for (var line : lines) {
            var indices = new java.util.ArrayList<Integer>();
            line.accept((index, current, cp) -> { indices.add(index); return true; });
            assertEquals(List.of(0, 1), indices);
        }
    }

    @Test void physicalLineBreaksPreventCombiningMarksAndFlagsFromJoiningPreviousLine() {
        var id = Identifier.parse("test:native_line_boundaries");
        var contexts = new java.util.ArrayList<UiTextEffects.GlyphContext>();
        UiTextEffects.registerEffect(id, context -> {
            contexts.add(context);
            return new UiTextEffects.Glyph(context.index() + 1, 0, 0, false, false);
        });
        try {
            var style = Style.EMPTY.withInsertion("shbui:fcs:" + id);
            var marks = UiFormattedText.colorizeLines(List.of(FormattedCharSequence.forward("A", style),
                    FormattedCharSequence.forward("\u0301B", style)), 0xffffffff, 0,
                    UiPreferences.DEFAULT, UiTextEffects.Timing.DEFAULT);
            assertEquals(List.of(1), colors(marks.getFirst()));
            assertEquals(List.of(2, 3), colors(marks.getLast()));
            assertTrue(contexts.stream().allMatch(context -> context.length() == 3));
            contexts.clear();
            var flags = UiFormattedText.colorizeLines(List.of(FormattedCharSequence.forward("🇨", style),
                    FormattedCharSequence.forward("🇳", style)), 0xffffffff, 0,
                    UiPreferences.DEFAULT, UiTextEffects.Timing.DEFAULT);
            assertEquals(List.of(1), colors(flags.getFirst()));
            assertEquals(List.of(2), colors(flags.getLast()));
            assertTrue(contexts.stream().allMatch(context -> context.length() == 2));
        } finally { UiTextEffects.unregister(id); }
    }

    private static List<Integer> colors(FormattedCharSequence source) {
        var result = new java.util.ArrayList<Integer>();
        source.accept((index, style, cp) -> { result.add(style.getColor().getValue()); return true; });
        return result;
    }

    @Test void sampledMarkersAreRemovedBeforeRecursiveNativeSubmission() {
        var frame = UiFormattedText.sample(FormattedCharSequence.forward("字", Style.EMPTY.withInsertion("fcs:rainbow")),
                0xffffffff, UiPreferences.DEFAULT, null, 215);
        assertNull(UiTextEffects.format(frame.getFirst().style()));
    }
}
