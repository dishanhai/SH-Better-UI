// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RichMarkdownTest {
    @Test void parsesCommonBlocksAndKeepsInlineSource() {
        var blocks = RichMarkdown.parse("# **标题** #\r\n正文\r续行\n\n> 引用\n- [x] 完成\n  2. 第二项\n---\n```java\n**literal**\n``` ").blocks();
        assertEquals(new RichMarkdown.Heading(1, "**标题**"), blocks.get(0));
        assertEquals(new RichMarkdown.Paragraph("正文\n续行"), blocks.get(1));
        assertEquals(new RichMarkdown.Quote("引用"), blocks.get(2));
        assertEquals(new RichMarkdown.ListItem(0, "-", "完成", true), blocks.get(3));
        assertEquals(new RichMarkdown.ListItem(2, "2.", "第二项", null), blocks.get(4));
        assertInstanceOf(RichMarkdown.ThematicBreak.class, blocks.get(5));
        assertEquals(new RichMarkdown.CodeBlock("java", "**literal**"), blocks.get(6));
    }
    @Test void tablesHaveAlignmentAndPreserveEscapedPipesAndCode() {
        var table = (RichMarkdown.Table) RichMarkdown.parse("| Name | Value | Notes |\n| :--- | ---: | :---: |\n| 山海 | 12 | a\\|b |\n| code | 24 | `a|b` |").blocks().get(0);
        assertEquals(List.of(RichMarkdown.Alignment.LEFT, RichMarkdown.Alignment.RIGHT, RichMarkdown.Alignment.CENTER), table.alignments());
        assertEquals(List.of("山海", "12", "a\\|b"), table.rows().get(0));
        assertEquals("`a|b`", table.rows().get(1).get(2));
        assertThrows(UnsupportedOperationException.class, () -> table.rows().get(0).set(0, "oops"));
    }
    @Test void malformedAndUnsupportedSyntaxIsNotSilentlyDiscarded() {
        String raw = "<script>alert(1)</script>\n![remote](https://host/image)\n$$formula$$\n[chart=untrusted]";
        assertEquals(new RichMarkdown.Paragraph(raw), RichMarkdown.parse(raw).blocks().get(0));
        assertEquals(new RichMarkdown.Paragraph("a | b\n-- | ---"), RichMarkdown.parse("a | b\n-- | ---").blocks().get(0));
        var blocks = RichMarkdown.parse("a | b\n--- | ---\n1 | 2 | extra").blocks();
        assertEquals(new RichMarkdown.Paragraph("1 | 2 | extra"), blocks.get(1));
    }
    @Test void fencesRequireSameCharacterAndSufficientLength() {
        assertEquals(new RichMarkdown.CodeBlock("", "~~~\n```\nend"), RichMarkdown.parse("````\n~~~\n```\nend").blocks().get(0));
        assertEquals(new RichMarkdown.Paragraph("``` bad`info"), RichMarkdown.parse("``` bad`info").blocks().get(0));
    }
    @Test void boundsRejectWithoutTruncation() {
        assertThrows(IllegalArgumentException.class, () -> RichMarkdown.parse("x".repeat(RichMarkdown.MAX_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> RichMarkdown.parse("# x\n".repeat(RichMarkdown.MAX_BLOCKS + 1)));
        assertThrows(IllegalArgumentException.class, () -> RichMarkdown.parse("| a |\n| --- |\n" + "| b |\n".repeat(RichMarkdown.MAX_TABLE_ROWS + 1)));
        assertThrows(IllegalArgumentException.class, () -> RichMarkdown.parse("a |".repeat(33) + "\n" + "--- |".repeat(33)));
        assertThrows(NullPointerException.class, () -> RichMarkdown.parse(null));
        assertTrue(RichMarkdown.parse("").blocks().isEmpty());
    }
    @Test void longDelimiterRunsAreBoundedAndDoNotOverflowRegexStack() {
        assertInstanceOf(RichMarkdown.ThematicBreak.class, RichMarkdown.parse("* ".repeat(30_000)).blocks().get(0));
        StringBuilder adversarial = new StringBuilder();
        for (int i = 1; i < 250; i++) adversarial.append("`".repeat(i)).append("|");
        assertDoesNotThrow(() -> RichMarkdown.parse(adversarial.toString()));
    }
    @Test void unmatchedTicksAndEscapedDelimitersRemainContent() {
        var table = (RichMarkdown.Table) RichMarkdown.parse("a|b\n---|---\n`unclosed|yes\n``a|b``|yes").blocks().get(0);
        assertEquals(List.of("`unclosed", "yes"), table.rows().get(0));
        assertEquals(List.of("``a|b``", "yes"), table.rows().get(1));
    }
}
