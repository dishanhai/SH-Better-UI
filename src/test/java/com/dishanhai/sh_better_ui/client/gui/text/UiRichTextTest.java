// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.client.StringSplitter;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiRichTextTest {
    @Test
    void parsesBothItemFormsAndKeepsSurroundingNativeStyles() {
        Style style = Style.EMPTY.withBold(true).withColor(0xabcdef).withInsertion("author");
        var source = Component.literal("前<rich:item=minecraft:diamond>🌊<rich:item>minecraft:apple</rich:item>后").setStyle(style);
        var parsed = UiRichText.prepare(source);
        var glyphs = collect(Language.getInstance().getVisualOrder(parsed));
        assertEquals(List.of((int)'前', 0xfffc, 0x1f30a, 0xfffc, (int)'后'), glyphs.stream().map(Glyph::cp).toList());
        assertEquals("minecraft:diamond", UiRichText.itemId(glyphs.get(1).style(), 0xfffc));
        assertEquals("minecraft:apple", UiRichText.itemId(glyphs.get(3).style(), 0xfffc));
        assertFalse(glyphs.get(1).style().isBold());
        assertEquals(style, glyphs.get(2).style());
        assertSame(parsed, UiRichText.prepare(parsed));
        assertEquals("前<rich:item=minecraft:diamond>🌊<rich:item>minecraft:apple</rich:item>后", source.getString());
    }

    @Test
    void rawSequenceKeepsUnicodeIndicesAndSupportsEarlyTermination() {
        var source = FormattedCharSequence.forward("🌊<rich:item=minecraft:diamond>尾", Style.EMPTY);
        var prepared = UiRichText.prepare(source);
        var glyphs = collect(prepared);
        assertEquals(List.of(0x1f30a, 0xfffc, (int)'尾'), glyphs.stream().map(Glyph::cp).toList());
        assertEquals(2, glyphs.get(1).index());
        assertEquals("🌊<rich:item=minecraft:diamond>".length(), glyphs.get(2).index());
        int[] calls = {0};
        assertFalse(prepared.accept((index, style, cp) -> ++calls[0] < 2));
        assertEquals(2, calls[0]);
        assertSame(prepared, UiRichText.prepare(prepared));
    }

    @Test
    void invalidAndUnknownTagsStayLiteralWithoutAllocatingAReplacement() {
        for (String raw : List.of("plain", "<rich:item>", "<rich:item=Bad:ID>", "<rich:item=../x>",
                "<rich:item>minecraft:apple", "<other=minecraft:diamond>", "<item=minecraft:diamond>",
                "<item>minecraft:apple</item>", "<rich:unknown>", "<rich:vmaterial-shop,rndr:unknown>",
                "<rich:item=minecraft:diamond,rndr:energy_wave>")) {
            var component = Component.literal(raw);
            var sequence = FormattedCharSequence.forward(raw, Style.EMPTY);
            assertSame(component, UiRichText.prepare(component));
            assertSame(sequence, UiRichText.prepare(sequence));
        }
        assertThrows(IllegalArgumentException.class, () -> UiRichText.item("Bad:ID"));
        assertNull(UiRichText.itemId(Style.EMPTY.withInsertion("shbui:inline_item:minecraft:diamond"), 0xfffc));
    }

    @Test
    void itemIsAtomicForNativeWidthWrappingAndTruncation() {
        var itemFont = UiRichText.item("minecraft:diamond").getStyle().getFont();
        var splitter = new StringSplitter((cp, style) -> cp == 0xfffc && style.getFont().equals(itemFont) ? 10 : 1);
        var component = UiRichText.prepare(Component.literal("A<rich:item=minecraft:diamond>B"));
        assertEquals(12, splitter.stringWidth(component));
        assertEquals("A", splitter.headByWidth(component, 10, Style.EMPTY).getString());
        assertEquals("A\ufffc", splitter.headByWidth(component, 11, Style.EMPTY).getString());
        var lines = splitter.splitLines(component, 10, Style.EMPTY);
        assertEquals(List.of("A", "\ufffc", "B"), lines.stream().map(FormattedText::getString).toList());
        var original = Language.getInstance().getVisualOrder(component);
        var outline = UiRichText.withoutItems(original);
        assertEquals(splitter.stringWidth(original), splitter.stringWidth(outline));
        assertTrue(collect(outline).stream().allMatch(glyph -> UiRichText.itemId(glyph.style(), glyph.cp()) == null));
        // The font and code point, which determine the real native advance, stay intact.
        assertEquals(collect(original).get(1).style().getFont(), collect(outline).get(1).style().getFont());
    }

    @Test
    void translationArgumentsAndExistingEffectsCanContainItems() {
        var component = Component.translatableWithFallback("test.inline", "物品 %s", "<rich:item=minecraft:diamond>");
        assertEquals("物品 \ufffc", UiRichText.prepare(component).getString());
        var effects = UiRichText.parse("{rainbow}A<rich:item=minecraft:diamond>B{/}");
        var glyphs = collect(Language.getInstance().getVisualOrder(effects));
        assertEquals(List.of((int)'A', 0xfffc, (int)'B'), glyphs.stream().map(Glyph::cp).toList());
        assertNotNull(UiTextEffects.format(glyphs.getFirst().style()));
        assertNotNull(UiTextEffects.format(glyphs.getLast().style()));
        assertEquals("minecraft:diamond", UiRichText.itemId(glyphs.get(1).style(), 0xfffc));
    }

    @Test
    void pngAliasAndRendererSurviveNativeSequencesAndOutlines() {
        var source = UiRichText.parse("A<rich:vmaterial-shop,rndr:energy_wave>B");
        var glyphs = collect(Language.getInstance().getVisualOrder(source));
        assertEquals(List.of((int)'A', 0xfffc, (int)'B'), glyphs.stream().map(Glyph::cp).toList());
        var icon = glyphs.get(1);
        assertEquals("sh_better_ui:textures/gui/rich/material-shop.png", UiRichText.imageTexture(icon.style(), icon.cp()));
        assertEquals(UiRichIconRenderers.Effect.ENERGY_WAVE, UiRichText.renderer(icon.style(), icon.cp()).effect());
        var raw = UiRichText.prepare(FormattedCharSequence.forward("<rich:vmaterial-shop,rndr:energy_wave>", Style.EMPTY));
        assertEquals(UiRichIconRenderers.Effect.ENERGY_WAVE, UiRichText.renderer(collect(raw).getFirst().style(), 0xfffc).effect());
        var outline = collect(UiRichText.withoutObjects(raw)).getFirst();
        assertFalse(UiRichText.isObject(outline.style(), outline.cp()));
        assertEquals(icon.style().getFont(), outline.style().getFont());
        var spin = collect(UiRichText.prepare(FormattedCharSequence.forward(
                "<rich:item,rndr:spin>minecraft:diamond</rich:item>", Style.EMPTY))).getFirst();
        assertEquals("minecraft:diamond", UiRichText.itemId(spin.style(), spin.cp()));
        assertNotNull(UiRichText.renderer(spin.style(), spin.cp()).motion());
    }

    @Test
    void imageAndRendererRegistrationsAreScopedAndOldCleanupCannotRemoveReplacements() throws Exception {
        var image = UiRichText.registerImage("test-icon", "test:textures/icon.png");
        var renderer = UiRichIconRenderers.register("test:icon", new UiRichIconRenderers.Renderer(UiRichIconRenderers.Effect.HOLOGRAM, null));
        try {
            assertEquals("\ufffc", UiRichText.parse("<rich:test-icon,rndr:test:icon>").getString());
            assertThrows(IllegalArgumentException.class, () -> UiRichText.registerImage("test-icon", "test:other.png"));
            assertThrows(IllegalArgumentException.class, () -> UiRichText.registerImage("item", "test:other.png"));
            assertThrows(IllegalArgumentException.class, () -> UiRichText.registerImage("remote", "https://example.com/image.png"));
        } finally { image.close(); renderer.close(); }
        assertEquals("<rich:test-icon>", UiRichText.parse("<rich:test-icon>").getString());
        try (var newImage = UiRichText.registerImage("test-icon", "test:textures/icon.png");
             var newRenderer = UiRichIconRenderers.register("test:icon", new UiRichIconRenderers.Renderer(UiRichIconRenderers.Effect.HOLOGRAM, null))) {
            image.close(); renderer.close();
            assertEquals("\ufffc", UiRichText.parse("<rich:test-icon,rndr:test:icon>").getString());
        }
        for (var effect : UiRichIconRenderers.Effect.values()) {
            assertEquals(effect.phase(0, true), effect.phase(9000, true));
            if (effect != UiRichIconRenderers.Effect.NONE) {
                String name = effect.name().toLowerCase(java.util.Locale.ROOT);
                assertEquals("\ufffc", UiRichText.parse("<rich:vmaterial-shop,rndr:" + name + ">").getString());
            }
        }
    }

    @Test
    void itemLabelKeepsChineseTextInItsOwnFont() {
        var name = Component.literal("石头").withStyle(style -> style.withBold(true).withColor(0x66ccff));
        var label = UiRichText.withItem("minecraft:stone", name);
        var glyphs = collect(Language.getInstance().getVisualOrder(label));
        assertEquals("minecraft:stone", UiRichText.itemId(glyphs.getFirst().style(), 0xfffc));
        var letters = glyphs.stream().filter(glyph -> glyph.cp() == '石' || glyph.cp() == '头').toList();
        assertEquals(2, letters.size());
        for (var glyph : letters) {
            assertEquals(Style.EMPTY.getFont(), glyph.style().getFont());
            assertTrue(glyph.style().isBold());
            assertEquals(name.getStyle().getColor(), glyph.style().getColor());
        }
        assertEquals("石头", name.getString());
    }

    private static List<Glyph> collect(FormattedCharSequence text) {
        var glyphs = new ArrayList<Glyph>();
        text.accept((index, style, cp) -> { glyphs.add(new Glyph(index, style, cp)); return true; });
        return glyphs;
    }

    private record Glyph(int index, Style style, int cp) {}
}
