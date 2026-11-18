// Author: dishanhai
package com.dishanhai.sh_better_ui.client.compat.text26;

import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure instance association tests; real GuideME layout/render acceptance is a separate game probe. */
class GuideMe26TextTest {
    @Test void unmarkedUnknownAndUnsupportedTextIsNotRewritten() {
        assertNull(GuideMe26Text.prepare("ordinary text"));
        assertNull(GuideMe26Text.prepare("&$not_a_theme-unknown"));
        int before = GuideMe26Text.UNSUPPORTED.get();
        assertEquals("&~$rainbow-§lbold", GuideMe26Text.prepare("&~$rainbow-§lbold").text());
        assertEquals(before + 1, GuideMe26Text.UNSUPPORTED.get());
    }
    @Test void identicalTextStaysScopedToItsOwnLayout() {
        var first = GuideMe26Text.prepare("&~$rainbow-same same");
        var second = GuideMe26Text.prepare("&~$fire-same same");
        assertNotNull(first); assertNotNull(second);
        var a = first.take("same");
        var b = second.take("same");
        assertEquals("same", a.getString()); assertEquals("same", b.getString());
        var ai = a.visit((style, text) -> java.util.Optional.ofNullable(style.getInsertion()), Style.EMPTY).orElseThrow();
        var bi = b.visit((style, text) -> java.util.Optional.ofNullable(style.getInsertion()), Style.EMPTY).orElseThrow();
        assertNotEquals(ai, bi);
        assertEquals("same", first.take("same").getString());
        assertEquals("same", second.take("same").getString());
    }
    @Test void consumesNativeWhitespaceCollapseAndLineBreaksWithoutSplittingUnicode() {
        var layout = GuideMe26Text.prepare("&~$rainbow-  A   e\u0301\n🌊  B");
        assertEquals("A e\u0301 ", layout.take("A e\u0301 ").getString());
        assertEquals("🌊 B", layout.take("🌊 B").getString());
    }
    @Test void mismatchedVisibleCharactersInvalidateRatherThanAttachingAnotherRun() {
        var layout = GuideMe26Text.prepare("&~$rainbow-AB");
        int before = GuideMe26Text.UNSUPPORTED.get();
        assertNull(layout.take("B"));
        assertNull(layout.take("AB"));
        assertEquals(before + 1, GuideMe26Text.UNSUPPORTED.get());
    }
    @Test void unsupportedFinalSequencePreservesSectionCharactersStylesAndLogicalWidth() {
        String raw = "&~$rainbow-§lA🌊 e\u0301 אב";
        var layout = GuideMe26Text.prepare(raw);
        assertTrue(layout.literalFallback());
        Style host = Style.EMPTY.withBold(true).withItalic(true);
        var sequence = GuideMe26Text.renderSequence(layout.take(raw), raw, host, layout.literalFallback());
        var actual = new StringBuilder();
        sequence.accept((index, style, point) -> {
            assertEquals(host, style);
            actual.appendCodePoint(point);
            return true;
        });
        assertEquals(raw, actual.toString()); // § and its following l must both survive; RTL stays logical.
        var metrics = new net.minecraft.client.StringSplitter((point, style) ->
                (point > 0xffff ? 9.25f : 5.5f) + (style.isBold() ? 1 : 0));
        float expected = (float) raw.codePoints().mapToDouble(point -> (point > 0xffff ? 9.25 : 5.5) + 1).sum();
        assertEquals(expected, metrics.stringWidth(sequence), 0.0001f);
        // Even if native whitespace/run mapping is unsupported, the raw fallback cannot re-enter formatted parsing.
        assertNull(layout.take("unexpected"));
        assertEquals(expected, metrics.stringWidth(GuideMe26Text.renderSequence(null, raw, host, true)), 0.0001f);
    }
}
