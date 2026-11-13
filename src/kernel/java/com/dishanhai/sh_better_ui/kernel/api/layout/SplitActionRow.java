// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

/** Non-overlapping action and disclosure hit areas for a settings row. */
public record SplitActionRow(int actionWidth, int disclosureX, int disclosureWidth) {
    public static SplitActionRow of(int width, int requestedDisclosureWidth, int gap) {
        if (width < 0 || requestedDisclosureWidth < 0 || gap < 0) throw new IllegalArgumentException();
        int disclosure = Math.min(width, requestedDisclosureWidth);
        int actualGap = Math.min(Math.max(0, width - disclosure), gap);
        int action = width - disclosure - actualGap;
        return new SplitActionRow(action, action + actualGap, disclosure);
    }
}
