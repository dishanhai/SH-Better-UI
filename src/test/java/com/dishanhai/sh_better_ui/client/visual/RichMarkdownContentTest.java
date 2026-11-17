// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RichMarkdownContentTest {
    private static UiId id(String path) { return UiId.parse("test:" + path); }
    private static String text(RichTextComponents.Snapshot snapshot) {
        return RichTextComponents.toComponent(snapshot).getString();
    }
    @Test void mixedNodesPreserveOrderStylesAndStableIds() {
        var image = new ImageNode(id("image"), id("textures/sample.png"), 16, 16, "picture");
        var first = RichMarkdownContent.builder(id("page")).markdown("# Title\n\n**Bold**")
                .node(image).markdown("After").build();
        var second = RichMarkdownContent.builder(id("page")).markdown("# Title\n\n**Bold**")
                .node(image).markdown("After").build();
        assertEquals(first, second);
        assertTrue(first.document().nodes().contains(image));
        assertTrue(text(first).indexOf("Bold") < text(first).indexOf("picture"));
        assertTrue(text(first).indexOf("picture") < text(first).indexOf("After"));
        assertTrue(first.document().nodes().stream().filter(TextRun.class::isInstance)
                .map(TextRun.class::cast).anyMatch(r -> r.text().equals("Bold") && r.style().bold()));
    }
    @Test void passiveMarkdownCannotInstantiateVisualsOrCommands() {
        var result = RichMarkdownContent.builder(id("page"))
                .markdown("![remote](https://example.com/a.png) [bad](javascript:alert) <rich:item=minecraft:diamond>").build();
        assertTrue(result.document().nodes().stream().allMatch(n -> n instanceof TextRun || n instanceof ParagraphBreak));
        assertTrue(result.nativeStyles().values().stream().allMatch(s -> s.getClickEvent() == null));
    }
    @Test void linksKeepMetadataWithoutDispatchingAndCodeIsLiteral() {
        var result = RichMarkdownContent.builder(id("page"))
                .markdown("[site](https://example.com)\n\n```\n**literal**\n```").build();
        assertTrue(result.nativeStyles().values().stream().anyMatch(s -> s.getClickEvent() != null));
        assertTrue(text(result).contains("**literal**"));
    }
    @Test void tablesUseCompleteLabelledRowsAndKeepEmptyHeaders() {
        var result = RichMarkdownContent.builder(id("page"))
                .markdown("| Name | Value |\n| --- | --- |\n| 海 | 42 |\n| é | 🌊 |")
                .markdown("| Empty |\n| --- |").build();
        assertTrue(text(result).contains("Name: 海\nValue: 42"));
        assertTrue(text(result).contains("Name: é\nValue: 🌊"));
        assertTrue(text(result).contains("Empty"));
    }
    @Test void duplicateAppendIsAtomicAndSnapshotIsImmutable() {
        var builder = RichMarkdownContent.builder(id("page")).markdown("before");
        var before = builder.build();
        var collision = before.document().nodes().get(0);
        assertThrows(IllegalArgumentException.class, () -> builder.document(new RichDocument(List.of(
                new TextRun(id("extra"), "extra"), collision))));
        assertEquals(before, builder.build());
        builder.markdown("after");
        assertFalse(text(before).contains("after"));
    }
    @Test void aggregateBudgetRejectsRatherThanTruncates() {
        var builder = RichMarkdownContent.builder(id("page")).markdown("a".repeat(32768));
        builder.markdown("b".repeat(32768));
        assertThrows(IllegalArgumentException.class, () -> builder.markdown("x"));
    }
    @Test void nodeBudgetRejectsExtraNode() {
        var builder = RichMarkdownContent.builder(id("page"));
        for (int i = 0; i < RichMarkdownContent.MAX_NODES; i++) builder.node(new TextRun(id("n" + i), ""));
        assertThrows(IllegalArgumentException.class, () -> builder.node(new TextRun(id("extra"), "")));
        assertEquals(RichMarkdownContent.MAX_NODES, builder.build().document().nodes().size());
    }
    @Test void nativeFontBridgeDoesNotReinterpretLiteralMarkdown() {
        var result = RichMarkdownContent.builder(id("page"))
                .markdown("```\n<rich:item=minecraft:diamond> {fire}literal{/}\n```").build();
        var component = RichTextComponents.toComponent(result);
        var prepared = com.dishanhai.sh_better_ui.client.gui.text.UiRichText.prepare(component);
        assertEquals(component.getString(), prepared.getString());
        var output = new StringBuilder();
        var sequence = com.dishanhai.sh_better_ui.client.gui.text.UiRichText.prepare(component.getVisualOrderText());
        sequence.accept((index, style, cp) -> { output.appendCodePoint(cp); return true; });
        assertEquals(component.getString(), output.toString());
        assertEquals(component.getString(), com.dishanhai.sh_better_ui.client.gui.text.UiTextEffects.normalize(component).getString());
    }
    @Test void amplifiedTablesAreRejectedBeforeBuildingOutput() {
        var builder = RichMarkdownContent.builder(id("page")).markdown("safe");
        var before = builder.build();
        String attack = "| " + "H".repeat(60000) + " |\n| --- |\n" + "| v |\n".repeat(256);
        assertThrows(IllegalArgumentException.class, () -> builder.markdown(attack));
        assertEquals(before, builder.build());
    }
    @Test void snapshotRejectsOrphanStyle() {
        var builder = RichMarkdownContent.builder(id("page"));
        assertThrows(IllegalArgumentException.class, () -> builder.snapshot(new RichTextComponents.Snapshot(
                new RichDocument(List.of()), java.util.Map.of(id("missing"), net.minecraft.network.chat.Style.EMPTY))));
        assertTrue(builder.build().document().nodes().isEmpty());
    }
}
