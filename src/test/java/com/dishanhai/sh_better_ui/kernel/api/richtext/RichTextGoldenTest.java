// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.richtext;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RichTextGoldenTest {
    private static final RichExtensionInput PASSIVE = new RichExtensionInput(Map.of());
    private static final String FIXTURE = """
            {"version":1,"nodes":[{"id":"golden:lead","type":"text","text":"A漢","style":{"color":6729727,"bold":true}},
            {"id":"golden:media","type":"image","texture":"golden:textures/gui/sample.gif","width":64,"height":32,"alt":"[GIF]"},
            {"id":"golden:meter","type":"extension","extension":"golden:meter","version":2,"width":3,"height":1,"logicalHeight":6,"alt":"[Meter]","payload":"energy"},
            {"id":"golden:break","type":"paragraph","spacing":3},{"id":"golden:tail","type":"text","text":"é 👩‍💻"}]}
            """;
    private static final RichTextLayout.FontMetrics FONT = new RichTextLayout.FontMetrics() {
        @Override public int ascent() { return 8; }
        @Override public int descent() { return 2; }
        @Override public int width(String text) { return text.codePointCount(0, text.length()) * 4; }
    };

    @Test void passiveFixtureRoundTripsCanonicallyWithoutActivatingItsExtension() {
        var document = RichDocumentCodec.decode(FIXTURE, PASSIVE);
        assertFalse(assertInstanceOf(RichExtensionNode.class, document.nodes().get(2)).renderable());
        assertEquals(FIXTURE.replace("\n", ""), RichDocumentCodec.encode(document, PASSIVE));
        assertThrows(IllegalArgumentException.class, () -> RichDocumentCodec.decode(FIXTURE));
    }

    @Test void wideAndNarrowGoldenUseTheSameReservationAndClippedHitGeometry() {
        var document = RichDocumentCodec.decode(FIXTURE, PASSIVE);
        var wide = RichTextLayout.layout(document, 96, FONT);
        assertEquals("""
                96x25
                0/10/12 golden:lead@0(0,2,8,10)[A漢] golden:media@0(8,0,20,10)[] golden:meter@0(28,4,18,6)[]
                15/23/10 golden:tail@0(0,15,24,10)[é 👩‍💻]
                """, signature(wide));
        assertEquals("golden:media", wide.hitTest(8, 9.99).orElseThrow().node().id().toString());
        assertEquals("golden:meter", wide.hitTest(28, 4).orElseThrow().node().id().toString());
        assertTrue(wide.hitTest(20, 10).isEmpty());
        assertTrue(wide.visibleLines(12, 15).isEmpty());

        var narrow = RichTextLayout.layout(document, 24, FONT);
        assertEquals("""
                24x45
                0/8/10 golden:lead@0(0,0,8,10)[A漢]
                10/20/12 golden:media@0(0,10,20,10)[]
                22/30/10 golden:meter@0(0,24,18,6)[]
                35/43/10 golden:tail@0(0,35,24,10)[é 👩‍💻]
                """, signature(narrow));
        assertTrue(narrow.hitTest(24, 40).isEmpty());
        assertEquals(narrow.lines().subList(1, 3), narrow.visibleLines(10, 32));
    }

    private static String signature(RichLayoutSnapshot snapshot) {
        var out = new StringBuilder().append(snapshot.width()).append('x').append(snapshot.height()).append('\n');
        for (var line : snapshot.lines()) {
            out.append(line.y()).append('/').append(line.baseline()).append('/').append(line.height());
            for (var fragment : line.fragments()) {
                var box = fragment.bounds();
                out.append(' ').append(fragment.node().id()).append('@').append(fragment.textOffset())
                        .append('(').append(box.x()).append(',').append(box.y()).append(',')
                        .append(box.width()).append(',').append(box.height()).append(")[")
                        .append(fragment.text()).append(']');
            }
            out.append('\n');
        }
        return out.toString();
    }
}
