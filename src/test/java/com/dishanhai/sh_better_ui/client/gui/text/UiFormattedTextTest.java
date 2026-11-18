package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证混合视觉序列、Unicode 码点及偏好回退。 */
class UiFormattedTextTest {
    @Test
    void continuousCombiningLinesDoNotMistakeLineIndexResetForBidi() {
        Style marked = Style.EMPTY.withInsertion("fcs:rainbow");
        var lines = UiFormattedText.colorizeLines(List.of(
                FormattedCharSequence.forward("e\u0301A", marked), FormattedCharSequence.forward("BC", marked)),
                0xffffffff, 137, UiPreferences.DEFAULT, UiTextEffects.Timing.DEFAULT);
        var expected = collect(UiFormattedText.colorize(FormattedCharSequence.forward("e\u0301ABC", marked),
                0xffffffff, 137, UiPreferences.DEFAULT));
        assertEquals(expected.subList(0, 3), collect(lines.get(0)));
        assertEquals(expected.subList(3, 5), collect(lines.get(1)));
        var indices = new ArrayList<Integer>();
        lines.get(1).accept((index, style, cp) -> { indices.add(index); return true; });
        assertEquals(List.of(0, 1), indices);
    }

    @Test
    void effectsUseGraphemeOrdinalsWithoutDiscardingNativeStyles() {
        var effect = ResourceLocation.tryParse("test:formatted_graphemes");
        var seen = new ArrayList<UiTextEffects.GlyphContext>();
        UiTextEffects.registerEffect(effect, context -> {
            seen.add(context);
            return new UiTextEffects.Glyph(0x110000 + context.index(), context.index(), 0, false, false);
        });
        try {
            var source = UiTextEffects.apply(Component.literal("e\u0301👩\u200d🚀!")
                    .withStyle(Style.EMPTY.withBold(true).withItalic(true)), effect).getVisualOrderText();
            var frame = collect(UiFormattedText.colorize(source, 0xffffffff, 1234, UiPreferences.DEFAULT));
            assertEquals(List.of(0, 0, 1, 1, 1, 2), seen.stream().map(UiTextEffects.GlyphContext::index).toList());
            assertTrue(seen.stream().allMatch(context -> context.length() == 3));
            assertTrue(frame.stream().allMatch(glyph -> glyph.style().isBold() && glyph.style().isItalic()));
            assertEquals(frame.get(0).style().getColor(), frame.get(1).style().getColor());
            assertEquals(frame.get(2).style().getColor(), frame.get(4).style().getColor());
        } finally {
            UiTextEffects.unregister(effect);
        }
    }

    @Test
    void continuousLinesMatchOneVisualRunWithTheSameClockAndDirection() {
        Style marked = Style.EMPTY.withInsertion("fcs:rainbow");
        var timing = new UiTextEffects.Timing(137, true);
        var continuous = UiFormattedText.colorizeLines(List.of(
                FormattedCharSequence.forward("山🌊", marked), FormattedCharSequence.forward("海", marked)),
                0xffffffff, 137, UiPreferences.DEFAULT, timing);
        var single = collect(UiFormattedText.colorize(FormattedCharSequence.forward("山🌊海", marked),
                0xffffffff, 137, UiPreferences.DEFAULT, timing));
        assertEquals(single.subList(0, 2), collect(continuous.getFirst()));
        assertEquals(single.subList(2, 3), collect(continuous.getLast()));
        assertNotEquals(single, collect(UiFormattedText.colorize(FormattedCharSequence.forward("山🌊海", marked),
                0xffffffff, 137, UiPreferences.DEFAULT, new UiTextEffects.Timing(137, false))));
    }

    @Test
    void effectsStayInsideTheMarkedRunAndKeepUnicodeAndNativeStyle() {
        var effect = ResourceLocation.tryParse("test:formatted_text_unicode");
        var seen = new ArrayList<UiTextEffects.GlyphContext>();
        UiTextEffects.registerEffect(effect, context -> {
            seen.add(context);
            return new UiTextEffects.Glyph(0x44ddbb, 0, 1, false, false);
        });
        Component marked = UiTextEffects.apply(Component.literal("山🌊").withStyle(Style.EMPTY.withBold(true)), effect);
        var original = Component.empty()
                .append(Component.literal("前").withStyle(Style.EMPTY.withInsertion("vanilla insertion")))
                .append(marked).append(Component.literal("後")).getVisualOrderText();

        var frame = UiFormattedText.colorize(original, 0xffffffff, 350, UiPreferences.DEFAULT);
        var glyphs = collect(frame);
        assertEquals(List.of((int) '前', (int) '山', 0x1f30a, (int) '後'),
                glyphs.stream().map(StyledGlyph::codePoint).toList());
        assertEquals(List.of(0, 1), seen.stream().map(UiTextEffects.GlyphContext::index).toList());
        assertTrue(seen.stream().allMatch(context -> context.length() == 2 && context.timeMillis() == 350));
        assertEquals("vanilla insertion", glyphs.getFirst().style().getInsertion());
        assertTrue(glyphs.get(1).style().isBold());
        assertEquals(0x44ddbb, glyphs.get(2).style().getColor().getValue());
        assertNull(glyphs.get(2).style().getInsertion());
        assertNull(glyphs.getLast().style().getColor());
        assertFalse(frame.accept((index, style, codePoint) -> false));
    }

    @Test
    void highContrastKeepsTheNativeColorAndPlainTextPassesThrough() {
        var plain = FormattedCharSequence.forward("plain", Style.EMPTY);
        assertSame(plain, UiFormattedText.colorize(plain, 0xffffffff, 0, UiPreferences.DEFAULT));
        var plainComponent = Component.literal("plain").getVisualOrderText();
        assertSame(plainComponent, UiFormattedText.colorize(plainComponent, 0xffffffff, 0, UiPreferences.DEFAULT));

        var marked = FormattedCharSequence.forward("山", Style.EMPTY.withColor(0xaabbcc)
                .withInsertion("fcs:shanhai_dawn|~"));
        var frame = UiFormattedText.colorize(marked, 0xffffffff, 250, new UiPreferences(true, false, true));
        Style style = collect(frame).getFirst().style();
        assertEquals(0xaabbcc, style.getColor().getValue());
        assertNull(style.getInsertion());
    }

    @Test
    void lyricColorsFollowVisualOrderAndKeepNativeStyle() {
        var style = Style.EMPTY.withBold(true).withItalic(true).withInsertion("keep");
        FormattedCharSequence visual = sink -> sink.accept(5, style, 0x1f30a)
                && sink.accept(2, Style.EMPTY, '山') && sink.accept(0, Style.EMPTY, '海');
        var colored = UiFormattedText.colorizeLyric(visual, .5f, 0xffffff, 0x000000);
        var glyphs = collect(colored);

        assertEquals(List.of(0x1f30a, (int) '山', (int) '海'),
                glyphs.stream().map(StyledGlyph::codePoint).toList());
        assertEquals(List.of(0xffffff, 0x808080, 0), glyphs.stream()
                .map(glyph -> glyph.style().getColor().getValue()).toList());
        assertEquals(style.withColor(0xffffff), glyphs.getFirst().style());
        var indices = new ArrayList<Integer>();
        colored.accept((index, ignored, point) -> indices.add(index));
        assertEquals(List.of(5, 2, 0), indices);
        assertFalse(colored.accept((index, ignored, point) -> false));

        var timed = UiFormattedText.colorizeLyric(visual, index -> index == 1 ? 2 : Double.NaN,
                0xff0000, 0x0000ff);
        assertEquals(List.of(0x0000ff, 0xff0000, 0x0000ff), collect(timed).stream()
                .map(glyph -> glyph.style().getColor().getValue()).toList());
    }

    private static List<StyledGlyph> collect(FormattedCharSequence text) {
        var glyphs = new ArrayList<StyledGlyph>();
        text.accept((index, style, codePoint) -> {
            glyphs.add(new StyledGlyph(codePoint, style));
            return true;
        });
        return glyphs;
    }

    private record StyledGlyph(int codePoint, Style style) {}
}
