// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry for a single-column card/field settings subpage. */
public record SettingsDetailLayout(List<Card> cards, int width, int contentHeight) {
    public static final int PADDING = 10, ROW = 32, HEADER = 26, GAP = 12;
    public SettingsDetailLayout { cards = List.copyOf(cards); }
    public record Card(int top, int height, int fieldCount) {
        public int fieldTop(int index) {
            if (index < 0 || index >= fieldCount) throw new IndexOutOfBoundsException(index);
            return top + HEADER + index * ROW;
        }
    }
    public static SettingsDetailLayout of(int width, List<Integer> fieldCounts) {
        if (width < 0) throw new IllegalArgumentException("Negative width");
        var cards = new ArrayList<Card>();
        int next = 0;
        for (int count : fieldCounts) {
            if (count < 0) throw new IllegalArgumentException("Negative field count");
            int height = Math.addExact(HEADER + PADDING, Math.multiplyExact(count, ROW));
            cards.add(new Card(next, height, count));
            next = Math.addExact(next, height + GAP);
        }
        return new SettingsDetailLayout(cards, width, cards.isEmpty() ? 0 : next - GAP);
    }
    public boolean stacked() { return width < 300; }
    public int controlWidth() { return Math.max(0, stacked() ? width - 2 * PADDING : Math.min(120, width / 3)); }
    public int controlX() { return Math.max(PADDING, width - PADDING - controlWidth()); }
}
