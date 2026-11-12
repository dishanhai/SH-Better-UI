// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ImageNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.ParagraphBreak;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextMarkup;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichTextInputsTest {
    private static final UiId PREFIX = UiId.parse("test:rich");

    @Test
    void idsAreStableAndBuilderPublishesImmutableSnapshots() {
        var explicit = UiId.parse("test:rich/node_0");
        var builder = RichText.document(PREFIX).text(explicit, "山", RichStyle.EMPTY).text("e\u0301🌊");
        var first = builder.build();
        builder.paragraph(3);
        assertEquals(2, first.nodes().size());
        assertEquals(explicit, first.nodes().get(0).id());
        assertEquals(UiId.parse("test:rich/node_1"), first.nodes().get(1).id());
        assertThrows(IllegalArgumentException.class, () -> builder.text(explicit, "duplicate", RichStyle.EMPTY));
        assertEquals(first, RichText.document(PREFIX).text(explicit, "山", RichStyle.EMPTY).text("e\u0301🌊").build());
    }

    @Test
    void markupParsesNestedStylesImagesParagraphsAndLiteralBrackets() {
        String source = "[color=#19AaF0][b]漢[[[/b]字[/color][p][img=test:img.png;24;12;海;波][br]🙂";
        var document = RichTextMarkup.parse(PREFIX, source);
        assertEquals(document, RichTextMarkup.parse(PREFIX, source));
        var first = (TextRun) document.nodes().get(0);
        assertEquals("漢[", first.text());
        assertEquals(0x19aaf0, first.style().color());
        assertTrue(first.style().bold());
        assertFalse(((TextRun) document.nodes().get(1)).style().bold());
        assertEquals(4, ((ParagraphBreak) document.nodes().get(2)).spacing());
        assertEquals("海;波", ((ImageNode) document.nodes().get(3)).altText());
        assertEquals("\n🙂", ((TextRun) document.nodes().get(4)).text());
    }

    @Test
    void markupRejectsExecutableUnknownAndMalformedTokens() {
        for (String input : new String[] {
                "[action=server:command]", "[b][i]text[/b][/i]", "[img=javascript:abc;1;1;]",
                "[img=test:../outside.png;1;1;]", "[img=test:img.png;0;1;]",
                "[img=https://example.org/image.gif;1;1;]", "[img=test:image.svg;1;1;]", "[b]incomplete", "[", "\ud800"
        }) {
            assertThrows(IllegalArgumentException.class, () -> RichTextMarkup.parse(PREFIX, input));
        }
    }

    @Test
    void markupAcceptsOnlyLocalAnimatedImageReferences() {
        var document = RichTextMarkup.parse(PREFIX, "[img=test:anim.gif;16;16;GIF][img=test:anim.apng;16;16;APNG]");
        assertEquals(UiId.parse("test:anim.gif"), ((ImageNode) document.nodes().get(0)).texture());
        assertEquals(UiId.parse("test:anim.apng"), ((ImageNode) document.nodes().get(1)).texture());
    }

    @Test
    void markupRetainsTranslationKeyAndLiteralArguments() {
        var document = RichTextMarkup.parse(PREFIX, "[i][tr=test.message;Fallback %s;漢字][/i]");
        var node = (LocalizedText) document.nodes().get(0);
        assertEquals("test.message", node.key());
        assertEquals("Fallback %s", node.fallback());
        assertEquals(List.of("漢字"), node.arguments());
        assertTrue(node.style().italic());
    }

    @Test
    void escapedResourceTextRemainsLiteralInsideStyleTags() {
        String raw = "literal [/b] and [action=mod:wrong]";
        var document = RichTextMarkup.parse(PREFIX, "[b]" + RichTextMarkup.escape(raw) + "[/b]");
        assertEquals(1, document.nodes().size());
        assertEquals(raw, ((TextRun) document.nodes().get(0)).text());
    }
}
