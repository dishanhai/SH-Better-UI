// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.protocol;

import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionProof;
import com.dishanhai.sh_better_ui.kernel.api.protocol.UiInteractionTarget;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Connection-local, owner-thread ledger of recent, one-time interaction attempts. */
public final class UiInteractionLedger {
    private static final int CAPACITY = 8;
    private static final int RECENT_TOKENS = 64;
    private static final long TTL = Duration.ofSeconds(3).toNanos();

    private final Map<Long, Entry> pending = new LinkedHashMap<>();
    private final Map<UUID, Long> recentTokens = new LinkedHashMap<>();
    private long highWatermark;

    public Optional<UiInteractionProof> observe(UiInteractionTarget target, long now) {
        var proof = new UiInteractionProof(Math.incrementExact(highWatermark), UUID.randomUUID(), target);
        return record(proof, now) ? Optional.of(proof) : Optional.empty();
    }

    public boolean record(UiInteractionProof proof, long now) {
        if (proof.attempt() <= highWatermark) return false;
        expire(now);
        if (recentTokens.size() >= RECENT_TOKENS || recentTokens.containsKey(proof.token())) return false;
        highWatermark = proof.attempt();
        recentTokens.put(proof.token(), now);
        if (pending.size() >= CAPACITY) pending.remove(pending.keySet().iterator().next());
        pending.put(proof.attempt(), new Entry(proof, now));
        return true;
    }

    public Optional<UiInteractionProof> take(UiInteractionTarget target, long now) {
        expire(now);
        Entry match = null;
        for (Entry entry : pending.values()) {
            if (entry.proof.target().equals(target)) match = entry;
        }
        if (match == null) return Optional.empty();
        pending.remove(match.proof.attempt());
        return Optional.of(match.proof);
    }

    public boolean consume(UiInteractionProof proof, long now) {
        return consumeDeadline(proof, now).isPresent();
    }

    public OptionalLong consumeDeadline(UiInteractionProof proof, long now) {
        expire(now);
        Entry entry = pending.get(proof.attempt());
        if (entry == null || !entry.proof.equals(proof)) return OptionalLong.empty();
        pending.remove(proof.attempt());
        return OptionalLong.of(entry.observed + TTL);
    }

    public void expire(long now) {
        pending.values().removeIf(entry -> now - entry.observed >= TTL);
        recentTokens.values().removeIf(observed -> now - observed >= TTL);
    }

    public void clear() {
        pending.clear();
        recentTokens.clear();
        highWatermark = 0;
    }

    private record Entry(UiInteractionProof proof, long observed) {}
}
