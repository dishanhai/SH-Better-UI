// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.protocol;

/** A host may tighten these per-connection budgets. Wire-format limits are independent. */
public record UiProtocolLimits(int maxPages, int maxHuds, int maxActionReceipts, int inboundFramesPerSecond,
                               int actionsPerSecond, int snapshotsPerSecond, int updatesPerSecond,
                               int notificationsPerFiveSeconds, int maxActionRegistrations) {
    public static final UiProtocolLimits DEFAULT = new UiProtocolLimits(8, 3, 128, 192, 8, 8, 20, 4);

    public UiProtocolLimits(int maxPages, int maxHuds, int maxActionReceipts, int inboundFramesPerSecond,
                            int actionsPerSecond, int snapshotsPerSecond, int updatesPerSecond,
                            int notificationsPerFiveSeconds) {
        this(maxPages, maxHuds, maxActionReceipts, inboundFramesPerSecond, actionsPerSecond, snapshotsPerSecond,
                updatesPerSecond, notificationsPerFiveSeconds, 128);
    }

    public UiProtocolLimits {
        if (maxPages < 1 || maxPages > 8 || maxHuds < 0 || maxHuds > 3 || maxHuds > maxPages
                || maxActionReceipts < 1 || maxActionReceipts > 128
                || inboundFramesPerSecond < 1 || inboundFramesPerSecond > 192
                || actionsPerSecond < 1 || actionsPerSecond > 8
                || snapshotsPerSecond < 1 || snapshotsPerSecond > 8
                || updatesPerSecond < 1 || updatesPerSecond > 20
                || notificationsPerFiveSeconds < 0 || notificationsPerFiveSeconds > 4
                || maxActionRegistrations < 1 || maxActionRegistrations > 128) {
            throw new IllegalArgumentException("Page protocol budget is out of range");
        }
    }
}
