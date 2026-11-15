// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.automation.rule;

import com.dishanhai.sh_better_ui.kernel.api.automation.rule.*;
import com.dishanhai.sh_better_ui.kernel.api.automation.rule.RuleStore.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {
    private final UUID owner = UUID.randomUUID();
    private final Fake adapter = new Fake();
    private final Memory store = new Memory();
    private long now;
    private RuleEngine engine() throws IOException { return new RuleEngine(adapter, store, () -> now, Runnable::run); }
    private ReplenishmentRule rule(ReplenishmentRule.Mode mode) {
        return new ReplenishmentRule(UUID.randomUUID(), 1, owner, "grid", "minecraft:iron_ingot", 32, 64, mode, true);
    }

    @Test void lifecyclePersistsBeforeSubmitAndPauseDoesNotCancelOriginalTask() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        assertTrue(e.put(owner, rule));
        adapter.onSubmit = () -> assertEquals(TaskState.SUBMITTING, store.value.tasks().get(0).state());
        e.tick(); assertEquals(1, adapter.submissions); assertEquals(64, adapter.last.amount());
        assertEquals(TaskState.RUNNING, e.snapshot().tasks().get(0).state());
        assertTrue(e.pause(owner, rule.id()));
        adapter.status = RuleAdapter.TaskStatus.SUCCEEDED;
        now = 2000; e.tick();
        assertEquals(TaskState.SUCCEEDED, e.snapshot().tasks().get(0).state());
        assertEquals(1, adapter.submissions);
    }

    @Test void duplicateRulesAndGridAliasesShareLockButDifferentItemsCanProceed() throws Exception {
        RuleEngine e = engine(); var a = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        var b = new ReplenishmentRule(UUID.randomUUID(), 1, owner, "alias", a.item(), 32, 64, a.mode(), true);
        var c = new ReplenishmentRule(UUID.randomUUID(), 1, owner, "grid", "minecraft:gold_ingot", 32, 64, a.mode(), true);
        e.put(owner, a); e.put(owner, b); e.put(owner, c); e.tick(); e.tick();
        assertEquals(2, adapter.submissions);
    }

    @Test void knownPendingQuantityCountsAndForgedActionCannotEscapeRule() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE); e.put(owner, rule);
        adapter.inventory = new RuleAdapter.Inventory(5, 10);
        assertEquals(RuleEngine.Execution.BLOCKED, e.execute(UUID.randomUUID(), rule.id(), AutomationAction.replenish(rule, 1)));
        var forged = new AutomationAction(owner, "elsewhere", "replenish", Map.of("item", rule.item()), 100000);
        assertEquals(RuleEngine.Execution.BLOCKED, e.execute(owner, rule.id(), forged));
        assertEquals(RuleEngine.Execution.SUBMITTED, e.execute(owner, rule.id(), AutomationAction.replenish(rule, Long.MAX_VALUE)));
        assertEquals(49, adapter.last.amount());
    }

    @Test void pendingAboveThresholdDoesNotSubmit() throws Exception {
        RuleEngine e = engine(); e.put(owner, rule(ReplenishmentRule.Mode.PLAYER_ONLINE));
        adapter.inventory = new RuleAdapter.Inventory(10, 22); e.tick(); assertEquals(0, adapter.submissions);
    }

    @Test void pageLeaseExpiresAndLogoutRequiresRevalidation() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PAGE_OPEN); e.put(owner, rule);
        e.tick(); assertEquals(0, adapter.submissions);
        assertFalse(e.serverPageOpened(UUID.randomUUID(), rule.id(), 1000));
        assertFalse(e.serverPageOpened(owner, rule.id(), RuleEngine.MAX_PAGE_LEASE_MILLIS + 1));
        assertTrue(e.serverPageOpened(owner, rule.id(), 1000));
        now = 1000; e.tick(); assertEquals(0, adapter.submissions);
        e.serverPageOpened(owner, rule.id(), 1000); e.serverPlayerOffline(owner);
        assertTrue(e.resume(owner, rule.id())); e.tick(); assertEquals(0, adapter.submissions);
        e.serverPageOpened(owner, rule.id(), 1000); e.tick(); assertEquals(1, adapter.submissions);
    }

    @Test void permissionRevocationPausesAndCannotResumeOrSubmitOffline() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE); e.put(owner, rule);
        adapter.allowed = false; e.tick(); assertEquals(0, adapter.submissions);
        assertTrue(e.snapshot().paused().contains(rule.id())); assertFalse(e.resume(owner, rule.id()));
        adapter.allowed = true; adapter.online = false;
        assertFalse(e.resume(owner, rule.id())); e.tick(); assertEquals(0, adapter.submissions);
        adapter.online = true; assertTrue(e.resume(owner, rule.id())); e.tick(); assertEquals(1, adapter.submissions);
    }

    @Test void crashDuringAsyncPlanningBecomesUnknownWithoutReplay() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE); e.put(owner, rule);
        adapter.pending = new CompletableFuture<>(); e.tick();
        assertEquals(TaskState.SUBMITTING, store.value.tasks().get(0).state());
        RuleEngine restarted = engine();
        assertEquals(TaskState.UNKNOWN, restarted.snapshot().tasks().get(0).state());
        assertFalse(restarted.resume(owner, rule.id())); now = 100000; restarted.tick(); assertEquals(1, adapter.submissions);
    }

    @Test void receiptIsAppliedOnlyThroughServerExecutor() throws Exception {
        List<Runnable> serverQueue = new ArrayList<>();
        RuleEngine e = new RuleEngine(adapter, store, () -> now, serverQueue::add);
        e.put(owner, rule(ReplenishmentRule.Mode.PLAYER_ONLINE)); e.tick();
        assertEquals(TaskState.SUBMITTING, store.value.tasks().get(0).state());
        serverQueue.remove(0).run(); assertEquals(TaskState.RUNNING, store.value.tasks().get(0).state());
    }

    @Test void restartReconcilesOriginalTaskBeforeAnyNewWrite() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE); e.put(owner, rule); e.tick();
        RuleEngine restarted = engine(); now = 4000; restarted.tick(); assertEquals(1, adapter.submissions);
        adapter.status = RuleAdapter.TaskStatus.UNKNOWN;
        assertFalse(restarted.resume(owner, rule.id())); assertEquals(1, adapter.submissions);
        adapter.status = RuleAdapter.TaskStatus.RUNNING;
        now += 100000;
        assertTrue(restarted.resume(owner, rule.id())); restarted.tick(); assertEquals(1, adapter.submissions);
        assertTrue(adapter.queries > 0);
    }

    @Test void onlyExplicitNonExecutionAllowsBoundedWriteRetry() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE); e.put(owner, rule);
        adapter.receipt = new RuleAdapter.Submission(RuleAdapter.SubmissionStatus.DEFINITELY_NOT_EXECUTED, null);
        e.tick(); now = 2000; e.tick(); now = 6000; e.tick(); now = 100000; e.tick();
        assertEquals(3, adapter.submissions); assertEquals(TaskState.FAILED, e.snapshot().tasks().get(0).state());
        assertTrue(e.snapshot().paused().contains(rule.id()));
    }

    @Test void ambiguousWriteNeverRetriesAndQueryFailuresStopAfterBudget() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE); e.put(owner, rule);
        adapter.receipt = new RuleAdapter.Submission(RuleAdapter.SubmissionStatus.UNKNOWN, null);
        e.tick(); now = 100000; e.tick(); assertEquals(1, adapter.submissions);
        assertEquals(TaskState.UNKNOWN, e.snapshot().tasks().get(0).state());
        Memory another = new Memory(); adapter.receipt = new RuleAdapter.Submission(RuleAdapter.SubmissionStatus.ACCEPTED, "task");
        RuleEngine q = new RuleEngine(adapter, another, () -> now, Runnable::run); q.put(owner, rule); q.tick();
        adapter.status = RuleAdapter.TaskStatus.UNKNOWN;
        for (int i = 0; i < 8; i++) { now += 100000; q.tick(); }
        assertEquals(5, adapter.queries); assertEquals(TaskState.UNKNOWN, q.snapshot().tasks().get(0).state());
    }

    @Test void intentStorageFailurePreventsSubmissionAndReceiptFailurePreventsReplay() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE); e.put(owner, rule);
        store.fail = true; e.tick(); assertEquals(0, adapter.submissions); assertFalse(e.storageHealthy());
        store.fail = false; e = engine(); assertTrue(e.resume(owner, rule.id()));
        adapter.onSubmit = () -> store.fail = true; e.tick();
        assertEquals(1, adapter.submissions); assertFalse(e.storageHealthy());
        store.fail = false; RuleEngine restarted = engine();
        assertEquals(TaskState.UNKNOWN, restarted.snapshot().tasks().get(0).state());
        assertFalse(restarted.resume(owner, rule.id()));
    }

    @Test void ruleRevisionOwnershipAndFilePersistence(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("rules.bin"); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        try (AtomicFileRuleStore disk = new AtomicFileRuleStore(file)) {
            RuleEngine e = new RuleEngine(adapter, disk, () -> now, Runnable::run);
            assertFalse(e.put(UUID.randomUUID(), rule)); assertTrue(e.put(owner, rule)); assertFalse(e.put(owner, rule));
            e.tick(); assertEquals(e.snapshot(), disk.load());
            assertThrows(IOException.class, () -> new AtomicFileRuleStore(file));
            byte[] encoded = AtomicFileRuleStore.encode(e.snapshot());
            assertEquals(e.snapshot(), AtomicFileRuleStore.decode(encoded));
            assertThrows(IOException.class, () -> AtomicFileRuleStore.decode(Arrays.copyOf(encoded, encoded.length - 1)));
            encoded[4] = 127; assertThrows(IOException.class, () -> AtomicFileRuleStore.decode(encoded));
        }
        try (AtomicFileRuleStore disk = new AtomicFileRuleStore(file)) {
            assertEquals(1, disk.load().tasks().size());
        }
    }

    @Test void reconnectAutomaticallyReconcilesButNeverUnpausesManualPause() throws Exception {
        RuleEngine e = engine(); var active = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        var manual = new ReplenishmentRule(UUID.randomUUID(), 1, owner, "grid", "minecraft:gold_ingot", 32, 64,
                ReplenishmentRule.Mode.PLAYER_ONLINE, true);
        e.put(owner, active); e.put(owner, manual); e.pause(owner, manual.id());
        e.serverPlayerOffline(owner); e.reconcileOwner(owner); e.tick();
        assertEquals(1, adapter.submissions); assertTrue(e.snapshot().paused().contains(manual.id()));
        RuleEngine restarted = engine(); restarted.reconcileOwner(owner);
        assertTrue(restarted.snapshot().paused().contains(manual.id()));
    }

    @Test void mutationFromWorkerThreadIsRejected() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        var failure = new CompletableFuture<Throwable>();
        Thread worker = new Thread(() -> {
            try { e.put(owner, rule); failure.complete(null); }
            catch (Throwable t) { failure.complete(t); }
        });
        worker.start(); worker.join();
        assertInstanceOf(IllegalStateException.class, failure.join()); assertTrue(e.snapshot().rules().isEmpty());
    }

    @Test void automaticTriggerPolicyDoesNotBlockExplicitExecutionOrOriginalTaskQueries() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        e.put(owner, rule); e.setAutomaticTrigger(candidate -> false); e.tick();
        assertEquals(0, adapter.submissions);
        assertEquals(RuleEngine.Execution.SUBMITTED, e.execute(owner, rule.id(), AutomationAction.replenish(rule, 64)));
        adapter.status = RuleAdapter.TaskStatus.SUCCEEDED; now = 2000; e.tick();
        assertEquals(1, adapter.submissions); assertEquals(TaskState.SUCCEEDED, e.snapshot().tasks().get(0).state());
    }

    @Test void completedHistoryCanKeepOldTargetAcrossRealFileRestart(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("retarget.bin"); var first = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        var updated = new ReplenishmentRule(first.id(), 2, owner, "new-terminal", "minecraft:gold_ingot",
                16, 48, first.mode(), true);
        try (AtomicFileRuleStore disk = new AtomicFileRuleStore(file)) {
            RuleEngine e = new RuleEngine(adapter, disk, () -> now, Runnable::run);
            e.put(owner, first); e.tick(); e.pause(owner, first.id());
            adapter.status = RuleAdapter.TaskStatus.SUCCEEDED; now = 2000; e.tick();
            assertTrue(e.put(owner, updated));
        }
        try (AtomicFileRuleStore disk = new AtomicFileRuleStore(file)) {
            RuleEngine restarted = new RuleEngine(adapter, disk, () -> now, Runnable::run);
            assertEquals(updated, restarted.snapshot().rules().get(0));
            assertEquals("grid", restarted.snapshot().tasks().get(0).action().targetKey());
            assertEquals(TaskState.SUCCEEDED, restarted.snapshot().tasks().get(0).state());
            assertTrue(restarted.storageHealthy());
        }
    }

    @Test void terminalHistoryIsBoundedWhileEveryUnresolvedIntentSurvivesRestart() throws Exception {
        var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        List<Task> history = new ArrayList<>();
        for (int i = 0; i < RuleEngine.MAX_TERMINAL_HISTORY + 30; i++) {
            history.add(new Task(UUID.randomUUID(), rule.id(), AutomationAction.replenish(rule, 64),
                    i % 2 == 0 ? TaskState.SUCCEEDED : TaskState.FAILED, "old-" + i, 1, 0, 0));
        }
        Set<UUID> retained = new HashSet<>();
        for (TaskState state : List.of(TaskState.RUNNING, TaskState.UNKNOWN, TaskState.SUBMITTING, TaskState.RETRYABLE)) {
            UUID id = UUID.randomUUID(); retained.add(id);
            history.add(new Task(id, rule.id(), AutomationAction.replenish(rule, 64), state, "live", 1, 0, 0));
        }
        store.value = new Snapshot(1, List.of(rule), history, Set.of());
        RuleEngine e = engine();
        assertEquals(RuleEngine.MAX_TERMINAL_HISTORY + 4, e.snapshot().tasks().size());
        assertTrue(e.snapshot().tasks().stream().map(Task::intentId).toList().containsAll(retained));
        assertFalse(e.snapshot().tasks().stream().anyMatch(t -> t.intentId().equals(history.get(0).intentId())));
        assertEquals(e.snapshot(), store.value);
    }

    @Test void restoredTasksAlwaysValidateOwnerAndActiveTargetAndStoredAction() throws Exception {
        var rule = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        var wrongOwner = new AutomationAction(UUID.randomUUID(), rule.targetKey(), "replenish", Map.of("item", rule.item()), 64);
        var wrongTarget = new AutomationAction(owner, "old-target", "replenish", Map.of("item", rule.item()), 64);
        var wrongAction = new AutomationAction(owner, rule.targetKey(), "arbitrary-write", Map.of("item", rule.item()), 64);
        for (var entry : Map.of(wrongOwner, TaskState.SUCCEEDED, wrongTarget, TaskState.RUNNING,
                wrongAction, TaskState.SUCCEEDED).entrySet()) {
            store.value = new Snapshot(1, List.of(rule), List.of(new Task(UUID.randomUUID(), rule.id(), entry.getKey(),
                    entry.getValue(), "task", 1, 0, 0)), Set.of());
            assertThrows(IOException.class, this::engine);
        }
    }

    @Test void closingPageCancelsOnlyPendingPlanningAndGuardRejectsExpiredLeaseImmediately() throws Exception {
        RuleEngine e = engine(); var rule = rule(ReplenishmentRule.Mode.PAGE_OPEN); e.put(owner, rule);
        adapter.pending = new CompletableFuture<>(); adapter.cancelPlanning = true;
        e.serverPageOpened(owner, rule.id(), 1000); e.tick();
        UUID intent = e.snapshot().tasks().get(0).intentId();
        assertTrue(e.mayFinishSubmission(intent)); now = 1000;
        assertFalse(e.mayFinishSubmission(intent)); // 不必等待 engine.tick。
        e.serverPageClosed(owner, rule.id());
        assertEquals(1, adapter.cancellations);
        assertEquals(TaskState.RETRYABLE, e.snapshot().tasks().get(0).state());
        assertFalse(e.mayFinishSubmission(intent)); now = 100000; e.tick(); assertEquals(1, adapter.submissions);
    }

    @Test void pauseAndLogoutCancelPlanningButDoNotCancelAcceptedTasks() throws Exception {
        RuleEngine e = engine(); var first = rule(ReplenishmentRule.Mode.PLAYER_ONLINE); e.put(owner, first);
        adapter.pending = new CompletableFuture<>(); adapter.cancelPlanning = true;
        e.tick(); e.pause(owner, first.id()); assertEquals(1, adapter.cancellations);
        adapter.pending = new CompletableFuture<>();
        var second = new ReplenishmentRule(UUID.randomUUID(), 1, owner, "grid", "minecraft:gold_ingot",
                32, 64, first.mode(), true);
        e.put(owner, second); e.tick(); e.serverPlayerOffline(owner); assertEquals(2, adapter.cancellations);
        adapter.pending = null; e.reconcileOwner(owner); now = 10000; e.tick();
        assertEquals(TaskState.RUNNING, e.snapshot().tasks().stream().filter(t -> t.ruleId().equals(second.id())).findFirst().orElseThrow().state());
        e.pause(owner, second.id()); assertEquals(2, adapter.cancellations);
    }

    @Test void unresolvedIntentFreezesEveryExecutionParameterWhileEnabledCanChange() throws Exception {
        RuleEngine e = engine(); var original = rule(ReplenishmentRule.Mode.PLAYER_ONLINE);
        e.put(owner, original); adapter.pending = new CompletableFuture<>(); e.tick();
        var variants = List.of(
                new ReplenishmentRule(original.id(), 2, owner, "other", original.item(), 32, 64, original.mode(), true),
                new ReplenishmentRule(original.id(), 2, owner, "grid", "minecraft:gold_ingot", 32, 64, original.mode(), true),
                new ReplenishmentRule(original.id(), 2, owner, "grid", original.item(), 16, 64, original.mode(), true),
                new ReplenishmentRule(original.id(), 2, owner, "grid", original.item(), 32, 128, original.mode(), true),
                new ReplenishmentRule(original.id(), 2, owner, "grid", original.item(), 32, 64, ReplenishmentRule.Mode.PAGE_OPEN, true));
        for (ReplenishmentRule changed : variants) assertFalse(e.put(owner, changed));
        assertTrue(e.put(owner, new ReplenishmentRule(original.id(), 2, owner, "grid", original.item(),
                32, 64, original.mode(), false)));
        UUID intent = e.snapshot().tasks().get(0).intentId(); assertFalse(e.mayFinishSubmission(intent));
        assertTrue(e.put(owner, new ReplenishmentRule(original.id(), 3, owner, "grid", original.item(),
                32, 64, original.mode(), true)));
        assertTrue(e.mayFinishSubmission(intent)); assertEquals(64, adapter.last.amount());
    }

    private static final class Memory implements RuleStore {
        Snapshot value = Snapshot.empty(); boolean fail;
        public Snapshot load() { return value; }
        public void save(Snapshot value) throws IOException { if (fail) throw new IOException("disk full"); this.value = value; }
    }
    private static final class Fake implements RuleAdapter {
        boolean allowed = true, online = true, cancelPlanning; int submissions, queries, cancellations;
        Inventory inventory = new Inventory(0, 0); AutomationAction last;
        Runnable onSubmit = () -> {};
        Submission receipt = new Submission(SubmissionStatus.ACCEPTED, "task");
        TaskStatus status = TaskStatus.RUNNING;
        CompletableFuture<Submission> pending;
        public boolean isOnline(UUID owner) { return online; }
        public boolean authorized(UUID owner, String targetKey) { return allowed; }
        public boolean dependencyAvailable(String targetKey) { return true; }
        public String canonicalTarget(UUID owner, String targetKey) { return "same-grid"; }
        public Inventory inventory(UUID owner, String targetKey, String item) { return inventory; }
        public CompletionStage<Submission> submit(UUID intent, AutomationAction action) {
            submissions++; last = action; onSubmit.run();
            return pending != null ? pending : CompletableFuture.completedFuture(receipt);
        }
        public TaskStatus query(UUID owner, String taskId) { queries++; return status; }
        public boolean cancelPending(UUID intentId) {
            if (!cancelPlanning || pending == null || pending.isDone()) return false;
            cancellations++;
            return pending.complete(new Submission(SubmissionStatus.DEFINITELY_NOT_EXECUTED, null));
        }
    }
}
