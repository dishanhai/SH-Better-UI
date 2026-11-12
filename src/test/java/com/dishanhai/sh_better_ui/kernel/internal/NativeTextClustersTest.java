// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeTextClustersTest {
    private static final int BASE = 0xCCFFFFFF;

    @Test
    void measuresTheNativePenAndRetainsVisualOrderAndInlineStyles() {
        var bold = Style.EMPTY.withBold(true).withColor(0xEE7744);
        FormattedCharSequence visual = sink -> sink.accept(3, bold, 'ב') && sink.accept(2, bold, 'א')
                && sink.accept(1, Style.EMPTY, ' ') && sink.accept(0, Style.EMPTY, 'M');

        var clusters = NativeTextClusters.measure(visual, 0.2f, BASE, NativeTextClustersTest::advance);

        var entries = new ArrayList<NativeTextClusters.Glyph>();
        for (var cluster : clusters) {
            cluster.sequence().accept((index, style, codePoint) ->
                    entries.add(new NativeTextClusters.Glyph(index, style, codePoint)));
        }
        assertEquals(List.of(new NativeTextClusters.Glyph(3, bold, 'ב'), new NativeTextClusters.Glyph(2, bold, 'א'),
                new NativeTextClusters.Glyph(0, Style.EMPTY, 'M')), entries);
        assertEquals(List.of(0, 1, 3), clusters.stream().map(NativeTextClusters.Cluster::index).toList());
        assertEquals(0xCCEE7744, clusters.get(0).color());
        assertEquals(BASE, clusters.get(2).color());
        assertEquals(0.2f + 3.3f + 3.3f + 2.1f, clusters.get(2).left());
        assertEquals(clusters.get(2).left() + 2.1f, clusters.get(2).right());
    }

    @Test
    void keepsCombiningMarksAndSurrogatePairsWithTheirVisualCluster() {
        var visual = FormattedCharSequence.forward("e\u0301\ud83d\ude00Z", Style.EMPTY);
        var clusters = NativeTextClusters.measure(visual, 0, BASE, NativeTextClustersTest::advance);

        assertEquals(List.of(2, 1, 1), clusters.stream().map(cluster -> cluster.glyphs().size()).toList());
        assertEquals(clusters.get(0).right(), clusters.get(1).left());
        assertEquals(0x1F600, clusters.get(1).glyphs().get(0).codePoint());
        assertEquals(4, clusters.get(2).glyphs().get(0).index());
    }

    @Test
    void fallsBackForUnverifiableOrJoinedNativeGeometry() {
        var underlined = FormattedCharSequence.forward("ab", Style.EMPTY.withUnderlined(true));
        var reversedCombining = FormattedCharSequence.backward("a\u0301b", Style.EMPTY);
        var mixedCluster = FormattedCharSequence.composite(
                FormattedCharSequence.forward("e", Style.EMPTY),
                FormattedCharSequence.forward("\u0301z", Style.EMPTY.withColor(0xDD5533)));
        for (var unsafe : List.of(underlined, reversedCombining, mixedCluster)) {
            assertTrue(NativeTextClusters.measure(unsafe, 0, BASE, NativeTextClustersTest::advance).isEmpty());
        }
        assertTrue(NativeTextClusters.measure(FormattedCharSequence.forward("ab", Style.EMPTY), 0, BASE,
                glyph -> Float.NaN).isEmpty());
    }

    private static float advance(FormattedCharSequence glyph) {
        float[] width = {0};
        glyph.accept((index, style, codePoint) -> {
            if (Character.getType(codePoint) != Character.NON_SPACING_MARK) {
                width[0] = style.isBold() ? 3.3f : 2.1f;
            }
            return true;
        });
        return width[0];
    }
}
