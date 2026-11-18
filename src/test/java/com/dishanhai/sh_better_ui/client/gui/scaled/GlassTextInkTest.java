// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlassTextInkTest {
    @Test void coloredChildrenAndTranslatedArgumentsUseFinalSafeInk() {
        var argument = Component.literal("translated").withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.ITALIC);
        var text = Component.literal("Heading ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.translatableWithFallback("glass.test.argument", "%s", argument));
        var glyphs = Language.getInstance().getVisualOrder(text);
        var safe = GlassTextInk.apply(glyphs, 0xffeeeeee, true);
        var result = new StringBuilder();
        var bold = new java.util.concurrent.atomic.AtomicBoolean();
        var italic = new java.util.concurrent.atomic.AtomicBoolean();
        safe.accept((index, style, point) -> {
            assertEquals(0xeeeeee, style.getColor().getValue());
            if (style.isBold()) bold.set(true);
            if (style.isItalic()) italic.set(true);
            result.appendCodePoint(point);
            return true;
        });
        assertEquals("Heading translated", result.toString());
        assertTrue(bold.get()); assertTrue(italic.get());
    }

    @Test void legacyColorsAndResetCannotOverrideTitleOrSelectedInk() {
        var glyphs = Language.getInstance().getVisualOrder(Component.literal("§1Dark §rplain"));
        for (int color : new int[]{0xffeeeeee, 0xff102030}) {
            var text = new StringBuilder();
            GlassTextInk.apply(glyphs, color, true).accept((index, style, point) -> {
                assertEquals(color & 0xffffff, style.getColor().getValue());
                text.appendCodePoint(point); return true;
            });
            assertEquals("Dark plain", text.toString());
        }
    }

    @Test void ordinaryRenderingKeepsTheExactOriginalSequenceAndColor() {
        var original = Language.getInstance().getVisualOrder(Component.literal("Custom").withStyle(ChatFormatting.RED));
        assertSame(original, GlassTextInk.apply(original, -1, false));
        original.accept((index, style, point) -> {
            assertEquals(ChatFormatting.RED.getColor(), Integer.valueOf(style.getColor().getValue()));
            return true;
        });
    }

    @Test void theWrapperPreservesEveryNonColorStylePropertyAndGlyphOrder() {
        var style = net.minecraft.network.chat.Style.EMPTY.withBold(true).withItalic(true).withUnderlined(true)
                .withStrikethrough(true).withObfuscated(true).withInsertion("retained");
        net.minecraft.util.FormattedCharSequence input = sink -> sink.accept(7, style, 'A') && sink.accept(3, style, 'B');
        var indices = new java.util.ArrayList<Integer>();
        GlassTextInk.apply(input, 0xffabcdef, true).accept((index, actual, point) -> {
            assertEquals(style, actual.withColor((net.minecraft.network.chat.TextColor) null));
            assertEquals(style.getFont(), actual.getFont());
            indices.add(index); return true;
        });
        assertEquals(java.util.List.of(7, 3), indices);
    }
}
