// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.rule;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** save 必须全量原子替换；失败不可被调用者误认为已经持久化。 */
public interface RuleStore {
    Snapshot load() throws IOException;
    void save(Snapshot snapshot) throws IOException;

    enum TaskState { SUBMITTING, RETRYABLE, RUNNING, UNKNOWN, SUCCEEDED, FAILED }

    record Task(UUID intentId, UUID ruleId, AutomationAction action, TaskState state, String taskId,
                int writeAttempts, int queryFailures, long nextAttemptAt) {
        public Task {
            Objects.requireNonNull(intentId); Objects.requireNonNull(ruleId);
            Objects.requireNonNull(action); Objects.requireNonNull(state);
            if (writeAttempts < 0 || queryFailures < 0) throw new IllegalArgumentException("Negative attempts");
            if (state == TaskState.RUNNING && (taskId == null || taskId.isBlank())) {
                throw new IllegalArgumentException("Running task needs an id");
            }
        }
        public boolean blocksTarget() { return state != TaskState.SUCCEEDED && state != TaskState.FAILED; }
    }

    record Snapshot(int formatVersion, List<ReplenishmentRule> rules, List<Task> tasks, Set<UUID> paused) {
        public Snapshot {
            if (formatVersion != 1) throw new IllegalArgumentException("Unsupported rule store version");
            rules = List.copyOf(rules); tasks = List.copyOf(tasks); paused = Set.copyOf(paused);
        }
        public static Snapshot empty() { return new Snapshot(1, List.of(), List.of(), Set.of()); }
    }
}
