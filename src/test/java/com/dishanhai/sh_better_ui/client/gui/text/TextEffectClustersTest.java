package com.dishanhai.sh_better_ui.client.gui.text;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextEffectClustersTest {
    private static int[] clusters(String text) {
        int[] points = text.codePoints().toArray();
        int[] indices = new int[points.length];
        for (int i = 1; i < points.length; i++) indices[i] = indices[i - 1] + Character.charCount(points[i - 1]);
        return TextEffectClusters.ordinals(points, indices);
    }

    @Test void supplementaryAndCombiningCharactersStayTogether() {
        assertArrayEquals(new int[]{0, 1, 1, 2}, clusters("🌊e\u0301山"));
        assertArrayEquals(new int[]{0, 0, 0, 1}, clusters("👩\u200d🚀!"));
        assertArrayEquals(new int[]{0, 0, 1}, clusters("👍🏽!"));
        assertArrayEquals(new int[]{0, 0, 1}, clusters("🇨🇳!"));
    }

    @Test void preservesVisualOrderAndUsesWholeRunForAmbiguousMarks() {
        assertArrayEquals(new int[]{0, 1, 2}, TextEffectClusters.ordinals(
                new int[]{'ג', 'ב', 'א'}, new int[]{2, 1, 0}));
        assertArrayEquals(new int[]{0, 0, 0}, TextEffectClusters.ordinals(
                new int[]{0x05b0, 'ב', 'א'}, new int[]{2, 1, 0}));
        assertArrayEquals(new int[]{0, 0, 0}, TextEffectClusters.ordinals(
                new int[]{0x0301, 'e', 'a'}, new int[]{2, 1, 0}));
    }

    @Test void siblingIndicesCanRestartWithoutReversingTheText() {
        assertArrayEquals(new int[]{0, 0, 1, 2, 3}, TextEffectClusters.ordinals(
                new int[]{'e', 0x0301, 'A', 'B', 'C'}, new int[]{0, 1, 2, 0, 1}));
    }

    @Test void hardLinesNeverJoinFlagsOrCombiningMarks() {
        assertArrayEquals(new int[]{0, 1}, TextEffectClusters.ordinals(
                new int[]{0x1f1e8, 0x1f1f3}, new int[]{0, 2}, new int[]{1}));
        assertArrayEquals(new int[]{0, 1, 2}, TextEffectClusters.ordinals(
                new int[]{'A', 0x0301, 'B'}, new int[]{0, 1, 2}, new int[]{1}));
    }

    @Test void emptyAndLineBoundaries() {
        assertArrayEquals(new int[0], clusters(""));
        assertArrayEquals(new int[]{0, 1, 1, 2}, clusters("a\r\nb"));
        assertThrows(IllegalArgumentException.class, () -> TextEffectClusters.ordinals(new int[]{'a'}, new int[0]));
    }
}
