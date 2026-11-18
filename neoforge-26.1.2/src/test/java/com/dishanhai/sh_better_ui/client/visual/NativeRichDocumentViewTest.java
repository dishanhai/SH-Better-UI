// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.api.NativeTestClient;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.richtext.*;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeRichDocumentViewTest {
    private static UiId id(String path) { return UiId.parse("test:" + path); }
    @Test void styledFontRoundTripsThroughNativeResourceDescription() {
        var style = new RichStyle(0xabcdef, true, true, true, false, false, id("font"));
        var nativeStyle = RichTextComponents.nativeStyle(style);
        assertEquals(new FontDescription.Resource(Identifier.parse("test:font")), nativeStyle.getFont());
        assertEquals(style, RichTextComponents.style(nativeStyle));
    }
    @Test void mixedDocumentRetainsImageGeometryAndAccessibleAlternative() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var image = new ImageNode(id("image"),id("textures/absent.png"),80,40,"Image alternative");
            var document = new RichDocument(List.of(new TextRun(id("first"), "Text "), image,
                    new ParagraphBreak(id("paragraph"),4),
                    new LocalizedText(id("translated"),"missing.test.translation","Fallback",List.of(),RichStyle.EMPTY)));
            var view = new NativeRichDocumentView(fixture.client().font,document,200);
            var fragment = view.layout().lines().stream().flatMap(line -> line.fragments().stream())
                    .filter(f -> f.node() instanceof ImageNode).findFirst().orElseThrow();
            assertEquals((fixture.client().font.lineHeight + 3) * 2,fragment.bounds().width());
            assertEquals(fixture.client().font.lineHeight + 3,fragment.bounds().height());
            assertEquals("Text Image alternative\nFallback",view.narration().getString());
            assertTrue(NativeRichDocumentView.isStaticImage(image));
            assertFalse(NativeRichDocumentView.isStaticImage(new ImageNode(id("gif"),id("textures/animation.gif"),80,40,"Animated")));
            assertTrue(view.layout().hitTest(fragment.bounds().x(), fragment.bounds().y()).isPresent());
        }
    }
    @Test void narrowLayoutWrapsTextAndRejectsUnboundedContent() throws Exception {
        try (var fixture = NativeTestClient.install()) {
            var document = new RichDocument(List.of(new TextRun(id("text"),"Many words for a bounded document reader ".repeat(20))));
            assertTrue(new NativeRichDocumentView(fixture.client().font,document,60).height()
                    > new NativeRichDocumentView(fixture.client().font,document,240).height());
            assertThrows(IllegalArgumentException.class, () -> new NativeRichDocumentView(fixture.client().font,
                    new RichDocument(List.of(new TextRun(id("too_long"),"x".repeat(131073)))),200));
            assertThrows(IllegalArgumentException.class, () -> new NativeRichDocumentView(fixture.client().font,
                    new RichDocument(List.of(new ImageNode(id("huge"), id("textures/large.png"),5000,1,"Large"))),200));
        }
    }
    @Test void animatedResourcesKeepStableLayoutAndAlternativeWithoutLegacyOptIn() throws Exception {
        try(var client=NativeTestClient.install()) {
            for(String extension:List.of("gif","apng","png")) {
                var image=new ImageNode(id("media"),id("textures/sample."+extension),128,32,"Accessible media");
                assertTrue(NativeRichDocumentView.isResourceMedia(image));
                assertEquals(extension.equals("png"),NativeRichDocumentView.isStaticImage(image));
                var view=new NativeRichDocumentView(client.client().font,new RichDocument(List.of(image)),200);
                assertEquals("Accessible media",view.narration().getString());
                var fragment=view.layout().lines().getFirst().fragments().getFirst();
                assertEquals(fragment.bounds().height()*4,fragment.bounds().width());
            }
            assertFalse(NativeRichDocumentView.isResourceMedia(new ImageNode(id("bad"),id("other/movie.gif"),1,1,"No")));
            assertFalse(NativeRichDocumentView.isResourceMedia(new ImageNode(id("bad"),id("textures/movie.mp4"),1,1,"No")));
        }
    }
    @Test void bundledAnimationIsOriginalBoundedFourFrameResource() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/sh_better_ui/textures/gui/rich/native-motion.gif")) {
            assertNotNull(input);
            var sheet=RichMediaDecoder.decode(input.readAllBytes(),null);
            assertEquals(4,sheet.frames().size());
            assertNotEquals(sheet.frameAt(0,false),sheet.frameAt(400,false));
            assertEquals(sheet.frameAt(0,false),sheet.frameAt(400,true));
        }
    }

}
