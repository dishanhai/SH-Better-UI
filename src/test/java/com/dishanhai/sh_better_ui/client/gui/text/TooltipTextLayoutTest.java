// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

class TooltipTextLayoutTest {
    private static FormattedCharSequence text(String text) {
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }
    // Deterministic native-width stand-in, not a real-font integration test.
    private static final ToIntFunction<FormattedCharSequence> WIDTH = sequence -> {
        int[] width = {0};
        sequence.accept((i, style, cp) -> { width[0] += style.isBold() ? 7 : 6; return true; });
        return width[0];
    };

    @Test void identicalTextInIndependentTooltipsCannotShareAlignmentOrContainerWidth() {
        var same = text("same");
        var small = new TooltipTextLayout(List.of(same), TooltipTextLayout.Alignment.CENTER, 31);
        var large = new TooltipTextLayout(List.of(same, text("much longer")), TooltipTextLayout.Alignment.RIGHT, 0);
        assertEquals(3.5f, small.offset(0, WIDTH));
        assertEquals(42f, large.offset(0, WIDTH));
        assertEquals(31, small.width(WIDTH));
        assertEquals(3.5f, small.offset(0, WIDTH)); // interleaving changes nothing
    }

    @Test void nativeWidthsAndFractionalCenteringUseGuiUnitsWithoutDoubleScaling() {
        var lines = List.of(text("a"), text("abc"));
        for (var alignment : TooltipTextLayout.Alignment.values()) {
            var layout = new TooltipTextLayout(lines, alignment, 19);
            assertEquals(19, layout.width(WIDTH));
            float expected = alignment == TooltipTextLayout.Alignment.LEFT ? 0 :
                    alignment == TooltipTextLayout.Alignment.CENTER ? 6.5f : 13f;
            assertEquals(expected, layout.offset(0, WIDTH));
            // A resource/font metric change is remeasured, not stale-cached or tied to physical pixels.
            assertEquals(36, layout.width(s -> WIDTH.applyAsInt(s) * 2));
        }
        var maximum = new TooltipTextLayout(List.of(text("")), TooltipTextLayout.Alignment.CENTER, Integer.MAX_VALUE);
        assertTrue(Float.isFinite(maximum.offset(0, WIDTH)));
        assertEquals(Integer.MAX_VALUE, maximum.width(WIDTH));
    }

    @Test void snapshotsRetainNativeStyleSourceIndicesSupplementaryAndCombiningGlyphs() {
        Style marker = UiTextEffects.mark(Style.EMPTY.withBold(true).withItalic(true), "rainbow", "~");
        var glyphs = new ArrayList<Integer>(List.of(0x1f30a, 0x301));
        FormattedCharSequence producer = sink -> {
            for (int i = 0; i < glyphs.size(); i++) if (!sink.accept(i * 2, marker, glyphs.get(i))) return false;
            return true;
        };
        var source = new ArrayList<FormattedCharSequence>(List.of(producer));
        var layout = new TooltipTextLayout(source, TooltipTextLayout.Alignment.LEFT, 0);
        source.clear(); glyphs.clear();
        var points = new ArrayList<Integer>(); var indices = new ArrayList<Integer>();
        layout.lines().get(0).accept((i, style, cp) -> {
            assertEquals(marker, style); indices.add(i); points.add(cp); return true;
        });
        assertEquals(List.of(0x1f30a, 0x301), points);
        assertEquals(List.of(0, 2), indices);
        assertEquals(14, layout.width(WIDTH));
        assertThrows(UnsupportedOperationException.class, () -> layout.lines().clear());
        int[] visited = {0};
        assertFalse(layout.lines().get(0).accept((i, style, cp) -> { visited[0]++; return false; }));
        assertEquals(1, visited[0]);
    }

    @Test void emptyRowsAreNotDroppedAndEmptyTooltipDoesNotInventARow() {
        var layout = new TooltipTextLayout(List.of(text(""), text("a"), text("")), TooltipTextLayout.Alignment.CENTER, 0);
        assertEquals(3, layout.lines().size());
        assertEquals(3f, layout.offset(0, WIDTH));
        assertEquals(0f, layout.offset(1, WIDTH));
        assertEquals(3f, layout.offset(2, WIDTH));
        assertEquals(0, new TooltipTextLayout(List.of(), TooltipTextLayout.Alignment.LEFT, 0).width(WIDTH));
        assertTrue(UiTextTooltip.lines(List.of(), TooltipTextLayout.Alignment.LEFT, 0).isEmpty());
        var rows = UiTextTooltip.lines(List.of(text(""), text("x")), TooltipTextLayout.Alignment.CENTER, 40);
        assertEquals(2, rows.size());
        assertThrows(UnsupportedOperationException.class, rows::clear);
    }

    @Test void invalidWidthRejectedBeforeTouchingFontOrGame() {
        assertThrows(IllegalArgumentException.class, () -> new TooltipTextLayout(List.of(), TooltipTextLayout.Alignment.LEFT, -1));
        assertThrows(IllegalArgumentException.class, () -> UiTextTooltip.wrap(null, null, 0, TooltipTextLayout.Alignment.CENTER));
        assertThrows(IllegalArgumentException.class, () -> UiTextTooltip.wrap(null, null, -100, TooltipTextLayout.Alignment.CENTER));
    }
}
