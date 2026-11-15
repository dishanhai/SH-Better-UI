// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.automation.rule;

import com.dishanhai.sh_better_ui.kernel.api.automation.rule.*;
import com.dishanhai.sh_better_ui.kernel.api.automation.rule.RuleStore.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * 服务端规则执行器。所有入口由服务端会话传入真实玩家 UUID；客户端不得直接访问此对象。
 * executor 必须回到同一服务端线程；异步规划期间持久化 SUBMITTING，重启后绝不重放。
 * 所有写入统一经过 execute，包括模板、图和脚本生成的补货动作。
 */
public final class RuleEngine {
    public static final long MAX_PAGE_LEASE_MILLIS = 60_000;
    public static final int MAX_TERMINAL_HISTORY = 1024;
    private static final int MAX_WRITE_ATTEMPTS = 3;
    private static final int MAX_QUERY_FAILURES = 5;
    private final RuleAdapter adapter;
    private final RuleStore store;
    private final ServerClock clock;
    private final Executor serverExecutor;
    private final Thread ownerThread = Thread.currentThread();
    private final Map<UUID, ReplenishmentRule> rules = new LinkedHashMap<>();
    private final Map<UUID, Task> tasks = new LinkedHashMap<>();
    private final Set<UUID> paused = new HashSet<>();
    private final Set<UUID> needsReconciliation = new HashSet<>();
    private final Map<UUID, Long> pageLeases = new HashMap<>();
    private boolean storageHealthy = true;
    private Predicate<ReplenishmentRule> automaticTrigger = rule -> true;

    public enum Execution { SUBMITTED, BLOCKED, BELOW_TRIGGER_NOT_MET, STORAGE_FAILED }

    public RuleEngine(RuleAdapter adapter, RuleStore store, ServerClock clock, Executor serverExecutor)
            throws IOException {
        this.adapter = Objects.requireNonNull(adapter);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.serverExecutor = Objects.requireNonNull(serverExecutor);
        Snapshot loaded = store.load();
        for (ReplenishmentRule rule : loaded.rules()) {
            if (rules.put(rule.id(), rule) != null) throw new IOException("Duplicate rule id");
        }
        paused.addAll(loaded.paused());
        // 运行前核对与用户暂停分开；正常规则核对后可自动恢复，手动暂停保持不变。
        needsReconciliation.addAll(rules.keySet());
        for (Task task : loaded.tasks()) {
            ReplenishmentRule rule = rules.get(task.ruleId());
            if (rule == null || !rule.owner().equals(task.action().owner()) || !validStoredAction(task.action())
                    || (task.blocksTarget() && !matches(rule, task.action()))) {
                throw new IOException("Task does not belong to its rule");
            }
            Task restored = task.state() == TaskState.SUBMITTING
                    ? copy(task, TaskState.UNKNOWN, task.taskId(), task.writeAttempts(), task.queryFailures(), 0)
                    : task;
            if (tasks.put(task.intentId(), restored) != null) throw new IOException("Duplicate intent id");
            if (restored.state() == TaskState.UNKNOWN) paused.add(restored.ruleId());
        }
        if (!rules.isEmpty()) persist();
    }

    /** 新规则 revision=1；编辑严格递增，规则 owner 不可变。 */
    public synchronized boolean put(UUID authenticatedActor, ReplenishmentRule rule) {
        checkThread();
        if (!storageHealthy || !authenticatedActor.equals(rule.owner()) || !authorized(rule)) return false;
        ReplenishmentRule old = rules.get(rule.id());
        if (old == null ? rule.revision() != 1
                : !old.owner().equals(authenticatedActor) || rule.revision() != old.revision() + 1) return false;
        // 活动意图冻结执行参数，避免规划期间改数量/模式后重新启用并提交旧量；仅允许 enabled 开关。
        if (old != null && tasks.values().stream().anyMatch(t -> t.ruleId().equals(rule.id()) && t.blocksTarget())
                && (!old.targetKey().equals(rule.targetKey()) || !old.item().equals(rule.item())
                || old.lower() != rule.lower() || old.target() != rule.target() || old.mode() != rule.mode())) return false;
        rules.put(rule.id(), rule);
        boolean saved = persist();
        if (saved && !active(rule)) cancelPending(rule.id());
        return saved && storageHealthy;
    }

    /** 仅供服务端真实页面打开/心跳调用；租约不持久化且有明确上限。 */
    public synchronized boolean serverPageOpened(UUID authenticatedActor, UUID ruleId, long leaseMillis) {
        checkThread();
        ReplenishmentRule rule = owned(authenticatedActor, ruleId);
        if (rule == null || !authorized(rule) || !adapter.isOnline(authenticatedActor)
                || leaseMillis <= 0 || leaseMillis > MAX_PAGE_LEASE_MILLIS) return false;
        pageLeases.put(ruleId, addTime(clock.nowMillis(), leaseMillis));
        return true;
    }

    public synchronized void serverPageClosed(UUID authenticatedActor, UUID ruleId) {
        checkThread();
        ReplenishmentRule rule = owned(authenticatedActor, ruleId);
        if (rule != null) {
            pageLeases.remove(ruleId);
            if (rule.mode() == ReplenishmentRule.Mode.PAGE_OPEN) cancelPending(ruleId);
        }
    }

    /** 在真实登出事件调用；重登后需 resume，不能沿用旧页面或自动重试写入。 */
    public synchronized void serverPlayerOffline(UUID owner) {
        checkThread();
        for (ReplenishmentRule rule : rules.values()) if (rule.owner().equals(owner)) {
            pageLeases.remove(rule.id()); needsReconciliation.add(rule.id());
            cancelPending(rule.id());
        }
    }

    /** 暂停只阻止新提交，不取消已被外部系统接受的任务。 */
    public synchronized boolean pause(UUID authenticatedActor, UUID ruleId) {
        checkThread();
        if (owned(authenticatedActor, ruleId) == null) return false;
        paused.add(ruleId);
        boolean saved = persist();
        cancelPending(ruleId);
        return saved && storageHealthy;
    }

    /** 先重新确认权限、依赖和每个原任务；不明任务没有 taskId 时无法自动恢复。 */
    public synchronized boolean resume(UUID authenticatedActor, UUID ruleId) {
        checkThread();
        ReplenishmentRule rule = owned(authenticatedActor, ruleId);
        if (!storageHealthy || rule == null || !authorized(rule) || !adapter.isOnline(rule.owner())) return false;
        for (Task task : List.copyOf(tasks.values())) {
            if (!task.ruleId().equals(ruleId) || !task.blocksTarget()) continue;
            if (task.state() == TaskState.SUBMITTING) return false;
            if (task.state() == TaskState.RETRYABLE) continue; // 有明确未执行证明，受有限重试约束。
            if (task.taskId() == null || task.taskId().isBlank()) return false;
            if (task.queryFailures() > 0 && clock.nowMillis() < task.nextAttemptAt()) return false;
            if (!query(task)) return false;
        }
        paused.remove(ruleId);
        needsReconciliation.remove(ruleId);
        return persist();
    }

    /** 登录后核对正常规则，绝不自动解除用户暂停或不明状态暂停。 */
    public synchronized void reconcileOwner(UUID authenticatedActor) {
        checkThread();
        for (ReplenishmentRule rule : List.copyOf(rules.values())) {
            if (rule.owner().equals(authenticatedActor) && needsReconciliation.contains(rule.id())
                    && !paused.contains(rule.id())) resume(authenticatedActor, rule.id());
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(1, List.copyOf(rules.values()), List.copyOf(tasks.values()), paused);
    }

    public synchronized boolean storageHealthy() { return storageHealthy; }

    /** 适配器在异步规划完成、实际写入前立即调用，消除 tick 间隔内的租约/暂停窗口。 */
    public synchronized boolean mayFinishSubmission(UUID intentId) {
        checkThread();
        Task task = tasks.get(intentId);
        if (task == null || task.state() != TaskState.SUBMITTING) return false;
        ReplenishmentRule rule = rules.get(task.ruleId());
        return rule != null && matches(rule, task.action()) && active(rule);
    }

    /** 只控制 tick 新建动作，显式 execute 与原任务核对不受影响。 */
    public synchronized void setAutomaticTrigger(Predicate<ReplenishmentRule> automaticTrigger) {
        checkThread();
        this.automaticTrigger = Objects.requireNonNull(automaticTrigger);
    }

    public synchronized void tick() {
        checkThread();
        if (!storageHealthy) return;
        long now = clock.nowMillis();
        for (ReplenishmentRule rule : List.copyOf(rules.values())) {
            if (!adapter.isOnline(rule.owner())) {
                pageLeases.remove(rule.id());
                needsReconciliation.add(rule.id());
                cancelPending(rule.id());
                continue;
            }
            if (!authorized(rule)) {
                if (paused.add(rule.id()) && !persist()) return;
                cancelPending(rule.id());
                continue;
            }
            if (needsReconciliation.contains(rule.id()) && !paused.contains(rule.id())) {
                if (!resume(rule.owner(), rule.id())) continue;
            }
            if (!active(rule)) cancelPending(rule.id());
            for (Task task : List.copyOf(tasks.values())) {
                if (!task.ruleId().equals(rule.id()) || now < task.nextAttemptAt()) continue;
                if (task.state() == TaskState.RUNNING) query(task);
                else if (task.state() == TaskState.RETRYABLE && active(rule)) retry(task, rule);
                if (!storageHealthy) return;
            }
            if (active(rule) && automaticTrigger.test(rule)) {
                execute(rule.owner(), rule.id(), AutomationAction.replenish(rule, rule.target()));
            }
        }
    }

    /**
     * 不信任 action.amount；服务端重新读取库存并计算实际数量。
     * 首版通用入口只允许规则声明的 replenish 动作，其它动作须先扩展服务端校验策略。
     */
    public synchronized Execution execute(UUID authenticatedActor, UUID ruleId, AutomationAction action) {
        checkThread();
        ReplenishmentRule rule = owned(authenticatedActor, ruleId);
        if (!storageHealthy) return Execution.STORAGE_FAILED;
        if (rule == null || !active(rule) || !matches(rule, action) || targetBusy(action)) return Execution.BLOCKED;
        long amount;
        try {
            long available = adapter.inventory(rule.owner(), rule.targetKey(), rule.item()).available();
            if (available >= rule.lower()) return Execution.BELOW_TRIGGER_NOT_MET;
            amount = rule.target() - available;
        } catch (Exception e) {
            paused.add(rule.id()); persist(); return Execution.BLOCKED;
        }
        Task task = new Task(UUID.randomUUID(), rule.id(), AutomationAction.replenish(rule, amount),
                TaskState.SUBMITTING, null, 1, 0, 0);
        tasks.put(task.intentId(), task);
        if (!persist()) return Execution.STORAGE_FAILED;
        submit(task);
        return storageHealthy ? Execution.SUBMITTED : Execution.STORAGE_FAILED;
    }

    private void retry(Task task, ReplenishmentRule rule) {
        if (task.writeAttempts() >= MAX_WRITE_ATTEMPTS) return;
        // 明确未执行才可重试；仍重新读取数量，不能重放过时的补货量。
        long amount;
        try {
            long available = adapter.inventory(rule.owner(), rule.targetKey(), rule.item()).available();
            if (available >= rule.lower()) {
                tasks.put(task.intentId(), copy(task, TaskState.FAILED, null, task.writeAttempts(), 0, 0));
                persist(); return;
            }
            amount = rule.target() - available;
        } catch (Exception e) { paused.add(rule.id()); persist(); return; }
        Task next = new Task(task.intentId(), task.ruleId(), AutomationAction.replenish(rule, amount),
                TaskState.SUBMITTING, null, task.writeAttempts() + 1, 0, 0);
        tasks.put(next.intentId(), next);
        if (persist()) submit(next);
    }

    private void submit(Task task) {
        try {
            adapter.submit(task.intentId(), task.action()).whenComplete((receipt, failure) -> {
                try {
                    serverExecutor.execute(() -> {
                        try { completed(task, receipt, failure); }
                        catch (RuntimeException invalidExecutor) { synchronized (this) { storageHealthy = false; } }
                    });
                } catch (RuntimeException rejected) {
                    // 回调无法回服务端时保留磁盘 SUBMITTING；禁止本进程继续发起写入。
                    synchronized (this) { storageHealthy = false; }
                }
            });
        } catch (Exception failure) { completed(task, null, failure); }
    }

    private synchronized void completed(Task original, RuleAdapter.Submission receipt, Throwable failure) {
        checkThread();
        Task current = tasks.get(original.intentId());
        if (current == null || current.state() != TaskState.SUBMITTING || !storageHealthy) return;
        TaskState state = TaskState.UNKNOWN;
        String taskId = null;
        long next = 0;
        if (failure == null && receipt != null) {
            taskId = receipt.taskId();
            state = switch (receipt.status()) {
                case ACCEPTED -> TaskState.RUNNING;
                case UNKNOWN -> TaskState.UNKNOWN;
                case DEFINITELY_NOT_EXECUTED -> original.writeAttempts() < MAX_WRITE_ATTEMPTS
                        ? TaskState.RETRYABLE : TaskState.FAILED;
            };
            next = addTime(clock.nowMillis(), backoff(original.writeAttempts()));
        }
        tasks.put(original.intentId(), copy(original, state, taskId, original.writeAttempts(), 0, next));
        if (state == TaskState.UNKNOWN || state == TaskState.FAILED) paused.add(original.ruleId());
        persist();
    }

    private boolean query(Task task) {
        try {
            RuleAdapter.TaskStatus status = adapter.query(task.action().owner(), task.taskId());
            if (status == null || status == RuleAdapter.TaskStatus.UNKNOWN) return queryFailed(task);
            TaskState state = switch (status) {
                case RUNNING -> TaskState.RUNNING;
                case SUCCEEDED -> TaskState.SUCCEEDED;
                case FAILED -> TaskState.FAILED;
                case UNKNOWN -> TaskState.UNKNOWN;
            };
            tasks.put(task.intentId(), copy(task, state, task.taskId(), task.writeAttempts(), 0,
                    addTime(clock.nowMillis(), 1000)));
            if (state == TaskState.FAILED) paused.add(task.ruleId());
            return persist();
        } catch (Exception e) { return queryFailed(task); }
    }

    private boolean queryFailed(Task task) {
        int failures = Math.min(MAX_QUERY_FAILURES, task.queryFailures() + 1);
        TaskState state = failures >= MAX_QUERY_FAILURES ? TaskState.UNKNOWN : task.state();
        tasks.put(task.intentId(), copy(task, state, task.taskId(), task.writeAttempts(), failures,
                addTime(clock.nowMillis(), backoff(failures))));
        if (state == TaskState.UNKNOWN) paused.add(task.ruleId());
        persist();
        return false;
    }

    private boolean targetBusy(AutomationAction action) {
        String target = adapter.canonicalTarget(action.owner(), action.targetKey());
        return tasks.values().stream().anyMatch(t -> t.blocksTarget()
                && Objects.equals(adapter.canonicalTarget(t.action().owner(), t.action().targetKey()), target)
                && Objects.equals(t.action().arguments().get("item"), action.arguments().get("item")));
    }

    private boolean matches(ReplenishmentRule rule, AutomationAction action) {
        return rule.owner().equals(action.owner()) && rule.targetKey().equals(action.targetKey())
                && "replenish".equals(action.kind()) && action.arguments().equals(Map.of("item", rule.item()));
    }

    private static boolean validStoredAction(AutomationAction action) {
        String item = action.arguments().get("item");
        return "replenish".equals(action.kind()) && action.arguments().size() == 1
                && item != null && !item.isBlank();
    }

    private void cancelPending(UUID ruleId) {
        for (Task task : List.copyOf(tasks.values())) {
            if (task.ruleId().equals(ruleId) && task.state() == TaskState.SUBMITTING) {
                try { adapter.cancelPending(task.intentId()); }
                catch (RuntimeException uncertain) {
                    tasks.put(task.intentId(), copy(task, TaskState.UNKNOWN, task.taskId(),
                            task.writeAttempts(), task.queryFailures(), 0));
                    paused.add(ruleId); persist();
                }
            }
        }
    }

    private ReplenishmentRule owned(UUID actor, UUID id) {
        ReplenishmentRule rule = rules.get(id);
        return rule != null && rule.owner().equals(actor) ? rule : null;
    }

    private boolean authorized(ReplenishmentRule rule) {
        return adapter.authorized(rule.owner(), rule.targetKey()) && adapter.dependencyAvailable(rule.targetKey());
    }

    private boolean active(ReplenishmentRule rule) {
        return storageHealthy && rule.enabled() && !paused.contains(rule.id())
                && !needsReconciliation.contains(rule.id()) && adapter.isOnline(rule.owner())
                && authorized(rule) && (rule.mode() == ReplenishmentRule.Mode.PLAYER_ONLINE
                || pageLeases.getOrDefault(rule.id(), Long.MIN_VALUE) > clock.nowMillis());
    }

    private boolean persist() {
        if (!storageHealthy) return false;
        trimTerminalHistory();
        try { store.save(snapshot()); return true; }
        catch (IOException | RuntimeException e) { storageHealthy = false; return false; }
    }

    /** 只裁剪最老终态记录，绝不移除任何在途、可重试或 UNKNOWN 意图。 */
    private void trimTerminalHistory() {
        long remove = tasks.values().stream().filter(t -> !t.blocksTarget()).count() - MAX_TERMINAL_HISTORY;
        if (remove <= 0) return;
        Iterator<Task> iterator = tasks.values().iterator();
        while (iterator.hasNext() && remove > 0) {
            if (!iterator.next().blocksTarget()) { iterator.remove(); remove--; }
        }
    }

    private static Task copy(Task t, TaskState state, String taskId, int writes, int queries, long next) {
        return new Task(t.intentId(), t.ruleId(), t.action(), state, taskId, writes, queries, next);
    }
    private static long backoff(int attempts) { return Math.min(30_000, 1000L << Math.min(attempts, 5)); }
    private static long addTime(long now, long delay) { return now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay; }
    private void checkThread() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Rule engine requires its server thread");
    }
}
