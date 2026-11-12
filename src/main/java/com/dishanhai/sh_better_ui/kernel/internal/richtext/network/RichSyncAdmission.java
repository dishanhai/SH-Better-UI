// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.richtext.network;

import net.minecraft.network.Connection;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Bounds decoded serverbound work before it is admitted to the game-thread queue. */
final class RichSyncAdmission {
    private static final int MAX_PEERS = 2048;
    private static final int MAX_PENDING = 512;
    private static final int MAX_PENDING_PER_PEER = 16;
    private static final int MAX_PER_SECOND = 64;
    private static final long SECOND = 1_000_000_000L;
    private static final Map<Connection, Budget> PEERS = new WeakHashMap<>();
    private static int pending;

    private RichSyncAdmission() {}

    static void schedule(Connection source, Function<Runnable, CompletableFuture<Void>> queue, Runnable action) {
        var budget = reserve(source);
        if (budget == null) return;
        try {
            queue.apply(action).whenComplete((ignored, failure) -> release(budget));
        } catch (RuntimeException | Error failure) {
            release(budget);
            throw failure;
        }
    }

    private static synchronized Budget reserve(Connection source) {
        if (pending >= MAX_PENDING) return null;
        long now = System.nanoTime();
        var budget = PEERS.get(source);
        if (budget == null) {
            if (PEERS.size() >= MAX_PEERS) return null;
            budget = new Budget(now);
            PEERS.put(source, budget);
        }
        if (now - budget.start >= SECOND) {
            budget.start = now;
            budget.received = 0;
        }
        if (budget.pending >= MAX_PENDING_PER_PEER || budget.received >= MAX_PER_SECOND) return null;
        budget.received++;
        budget.pending++;
        pending++;
        return budget;
    }

    private static synchronized void release(Budget budget) {
        budget.pending--;
        pending--;
    }

    static synchronized void forget(Connection connection) { PEERS.remove(connection); }

    private static final class Budget {
        private long start;
        private int pending;
        private int received;

        private Budget(long start) { this.start = start; }
    }
}
