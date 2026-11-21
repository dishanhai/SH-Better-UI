// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.visual.MarkdownView;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMarkdownScreenTest {
    @Test void screenScrollsWithinBoundsAndRetainsPositionAcrossReflow() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var screen = new MarkdownExampleScreen(null, "# Heading\n\n" + "- Item **bold**\n".repeat(100));
            screen.width = 320; screen.height = 200; screen.init();
            assertDoesNotThrow(() -> screen.extractBackground(null,0,0,0));
            assertFalse(screen.mouseScrolled(-1,100,0,-1));
            assertTrue(screen.mouseScrolled(40,80,0,-1));
            assertEquals(24, field(screen,"scroll"));
            screen.width = 208; screen.init();
            assertEquals(24, field(screen,"scroll"));
            assertTrue(screen.keyPressed(new KeyEvent(269,0,0)));
            assertEquals(field(screen,"maxScroll"),field(screen,"scroll"));
            assertTrue(screen.keyPressed(new KeyEvent(268,0,0)));
            assertEquals(0,field(screen,"scroll"));
        }
    }

    @Test void narrowTablesReflowAndLinksStayWebOnlyInReadingOrder() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            String source = "# Header\n\n| Name | Value |\n| --- | --- |\n| One with several words to wrap | Two with several more words to wrap |\n\n"
                    + "[Web](https://example.org) [Again](https://example.org) [Command](run_command:/op)\n"
                    + "![Remote image](https://example.org/cover.png)";
            var document = ShBetterUiApi.markdown().document(source);
            var wide = new MarkdownView(fixture.client().font,document,320);
            var narrow = new MarkdownView(fixture.client().font,document,64);
            assertTrue(document.blocks().stream().anyMatch(com.dishanhai.sh_better_ui.kernel.api.richtext.RichMarkdown.Table.class::isInstance));
            assertTrue(narrow.height() > wide.height(), "Long table cells must wrap in the narrow labelled layout");
            assertEquals(java.util.List.of("https://example.org"),wide.links());
            assertEquals(wide.links(),narrow.links());
            assertNull(narrow.styleAt(-1,0));
        }
    }

    @Test void linkHitTestingUsesTheSameGlyphWidthsAsLayout() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var view = new MarkdownView(fixture.client().font,
                    ShBetterUiApi.markdown().document("[AB](https://example.org) Z"), 200);
            assertInstanceOf(net.minecraft.network.chat.ClickEvent.OpenUrl.class, view.styleAt(1, 1).getClickEvent());
            assertInstanceOf(net.minecraft.network.chat.ClickEvent.OpenUrl.class, view.styleAt(9, 1).getClickEvent());
            assertNull(view.styleAt(10, 1).getClickEvent());
            assertNull(view.styleAt(11, 1).getClickEvent());
            assertNull(view.styleAt(200, 1));
            assertNull(view.styleAt(1, 100));
        }
    }

    @Test void staleDocumentCloseDoesNotReplaceTheCurrentPage() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var previous = new MarkdownExampleScreen(null, "Old");
            var current = new MarkdownExampleScreen(null, "Current");
            fixture.screen(current);
            previous.onClose();
            assertSame(current, fixture.screen());
        }
    }

    @Test void supplementaryGlyphHitTestingUsesCodePointsNotUtf16Offsets() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var view = new MarkdownView(fixture.client().font,
                    ShBetterUiApi.markdown().document("[😀](https://example.org) Z"), 200);
            assertInstanceOf(net.minecraft.network.chat.ClickEvent.OpenUrl.class, view.styleAt(4, 1).getClickEvent());
            assertNull(view.styleAt(5, 1).getClickEvent());
        }
    }

    private static int field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.getInt(owner);
    }
}
