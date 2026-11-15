// Author: dishanhai
package com.dishanhai.sh_better_ui.automation.ae2;

import com.dishanhai.sh_better_ui.kernel.api.automation.rule.RuleAdapter.Submission;
import com.dishanhai.sh_better_ui.kernel.api.automation.rule.RuleAdapter.SubmissionStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Bounded receipts for effects that happened or might have happened. No time-based eviction is safe. */
final class Ae2ReceiptCache {
    private final int capacity;
    private final Map<UUID, Submission> receipts = new HashMap<>();

    Ae2ReceiptCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Non-positive receipt capacity");
        this.capacity = capacity;
    }

    Submission get(UUID intentId) { return receipts.get(intentId); }

    boolean isEmpty() { return receipts.isEmpty(); }

    /** Each pending plan reserves one future accepted/unknown receipt before any side effect is attempted. */
    boolean hasCapacity(int pendingCount) {
        if (pendingCount < 0) throw new IllegalArgumentException("Negative pending count");
        return pendingCount < capacity - receipts.size();
    }

    void record(UUID intentId, Submission submission) {
        Objects.requireNonNull(intentId);
        Objects.requireNonNull(submission);
        // A proven non-execution must remain retryable under the engine's bounded retry policy.
        if (submission.status() == SubmissionStatus.DEFINITELY_NOT_EXECUTED) return;
        // Never erase prior evidence of a possible effect with a later or stale callback.
        if (receipts.containsKey(intentId)) return;
        if (!hasCapacity(0)) throw new IllegalStateException("AE2 receipt capacity reached");
        receipts.put(intentId, submission);
    }

    void forEach(BiConsumer<UUID, Submission> consumer) { receipts.forEach(consumer); }
}
