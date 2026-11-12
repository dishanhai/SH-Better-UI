// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.page;

/** Validation budgets may be tightened for a host or connection. */
public record UiPageLimits(int maxNodes, int maxDepth, int maxChildren, int maxProperties, int maxDataFields,
                           int maxActions) {
    public static final UiPageLimits DEFAULT = new UiPageLimits(256, 16, 64, 32, 128, 64);

    public UiPageLimits {
        if (maxNodes < 1 || maxNodes > 1024 || maxDepth < 1 || maxDepth > 32
                || maxChildren < 0 || maxChildren > PageNames.MAX_COLLECTION_SIZE
                || maxProperties < 1 || maxProperties > PageNames.MAX_COLLECTION_SIZE
                || maxDataFields < 0 || maxDataFields > PageNames.MAX_COLLECTION_SIZE
                || maxActions < 0 || maxActions > PageNames.MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("Page validation budget is out of range");
        }
    }
}
