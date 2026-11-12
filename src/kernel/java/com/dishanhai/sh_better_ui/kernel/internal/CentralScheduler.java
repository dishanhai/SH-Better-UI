// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.kernel.api.TaskHandle.Status.*;

/** Queue and handle state share one lock. No module code runs while that lock is held. */
final class CentralScheduler {
    private static final UIPhase[] PHASES = UIPhase.values();
    private static final Predicate<ScopeLifecycle.Scope> ALL_SURFACES = scope -> true;
    private static final List<UIPhase> ORDINARY = List.of(UIPhase.EVENT_DISPATCH, UIPhase.STATE_MUTATION,
            UIPhase.STYLE_RESOLUTION, UIPhase.MEASURE, UIPhase.LAYOUT, UIPhase.ANIMATION,
            UIPhase.GEOMETRY_UPDATE, UIPhase.RENDER_PREPARATION);

    private final Thread uiThread = Thread.currentThread();
    private final Object identity = new Object();
    private final Object lock = new Object();
    private final LongSupplier clock;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final SchedulerBudget budget;
    private final BiConsumer<String, Throwable> failures;
    private final Map<Handle, Job> jobs = new HashMap<>();
    private final Map<ScopeLifecycle.Scope, Set<Job>> byScope = new HashMap<>();
    private final Map<Replacement, Job> replacements = new HashMap<>();
    private final Map<UIPhase, Lane> phases = new EnumMap<>(UIPhase.class);
    private final Set<Job> background = new LinkedHashSet<>();
    private final Set<Job> workers = new LinkedHashSet<>();
    private int backgroundReservations;
    private long sequence;
    private long frame;
    private long frameTime;
    private long succeeded;
    private long cancelled;
    private long timedOut;
    private long failed;
    private long rejected;
    private long deferred;
    private boolean closed;

    private boolean advancing;
    private boolean rendered;
    private UIPhase phase;
    private int ordinaryRemaining;
    private UIPhase reservedOrdinaryPhase;

    CentralScheduler(LongSupplier clock, Executor executor, SchedulerBudget budget,
                     BiConsumer<String, Throwable> failures) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.failures = failures;
        // Only the host pump can hand off work, with at most backgroundWorkers outstanding.
        ownedExecutor = executor == null ? Executors.newFixedThreadPool(budget.backgroundWorkers(), action -> {
            var worker = new Thread(action, "SH-Better-UI kernel worker");
            worker.setDaemon(true);
            return worker;
        }) : null;
        this.executor = executor == null ? ownedExecutor : executor;
        for (UIPhase phase : PHASES) {
            phases.put(phase, new Lane());
        }
    }

    UIScheduler view(ModuleAccess access) {
        return new UIScheduler() {
            @Override
            public TaskHandle submit(UITask task) {
                return CentralScheduler.this.submit(access, task);
            }

            @Override
            public SchedulerSnapshot snapshot() {
                return CentralScheduler.this.snapshot();
            }

            @Override
            public long nowNanos() {
                return CentralScheduler.this.nowNanos();
            }

            @Override
            public boolean isOnUIThread() {
                return Thread.currentThread() == uiThread;
            }
        };
    }

    void assertMutationPhase() {
        assertOwner();
        if ((advancing || phase != null) && phase != UIPhase.STATE_MUTATION) {
            throw new IllegalStateException("UI state can only be committed between frames or in STATE_MUTATION");
        }
    }

    boolean inPhase(UIPhase expected) {
        return Thread.currentThread() == uiThread && phase == expected;
    }

    long nowNanos() {
        return Thread.currentThread() == uiThread && advancing ? frameTime : clock.getAsLong();
    }

    long frameNumber() { return frame; }

    boolean canBeginNativeFrame() {
        assertOwner();
        return !advancing && phase == null;
    }

    boolean canRouteNativeInput() {
        assertOwner();
        return phase == null || phase == UIPhase.INPUT;
    }

    <T> T nativeInput(Supplier<T> route) {
        assertOwner();
        if (phase == UIPhase.INPUT) return route.get();
        if (phase != null) throw new IllegalStateException("Native input cannot interrupt " + phase);
        phase = UIPhase.INPUT;
        try {
            return route.get();
        } finally {
            phase = null;
        }
    }

    void runFrame(Runnable beginFrame, Consumer<UIPhase> phaseWork) {
        beginNativeFrame(beginFrame, phaseWork);
        try {
            renderNativeFrame(ALL_SURFACES, phaseWork);
        } finally {
            finishNativeFrame(phaseWork);
        }
    }

    void beginNativeFrame(Runnable beginFrame, Consumer<UIPhase> phaseWork) {
        assertOwner();
        if (!canBeginNativeFrame()) {
            throw new IllegalStateException("Reentrant UI frame");
        }
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("UI scheduler is closed");
            }
            frame++;
            frameTime = clock.getAsLong();
            reservedOrdinaryPhase = selectReservedPhase();
        }
        advancing = true;
        rendered = false;
        ordinaryRemaining = budget.frameTasks();
        try {
            beginFrame.run();
            for (UIPhase next : PHASES) {
                if (next == UIPhase.RENDER) break;
                runPhase(next, phaseWork, ALL_SURFACES);
            }
        } catch (RuntimeException | Error failure) {
            advancing = false;
            throw failure;
        }
    }

    boolean renderNativeFrame(Predicate<ScopeLifecycle.Scope> surface, Consumer<UIPhase> phaseWork) {
        assertOwner();
        if (!advancing || rendered) return false;
        if (phase != null) throw new IllegalStateException("Render cannot interrupt " + phase);
        rendered = true;
        runPhase(UIPhase.RENDER, phaseWork, surface);
        return true;
    }

    void finishNativeFrame(Consumer<UIPhase> phaseWork) {
        assertOwner();
        if (!advancing) return;
        if (phase != null) throw new IllegalStateException("Cleanup cannot interrupt " + phase);
        try {
            if (!rendered) {
                synchronized (lock) {
                    var render = phases.get(UIPhase.RENDER).pending;
                    if (!render.isEmpty()) {
                        long now = clock.getAsLong();
                        for (var job : List.copyOf(render)) if (eligible(job, now)) deferred++;
                    }
                }
            }
            runPhase(UIPhase.CLEANUP, phaseWork, ALL_SURFACES);
        } finally {
            advancing = false;
        }
    }

    private void runPhase(UIPhase next, Consumer<UIPhase> phaseWork, Predicate<ScopeLifecycle.Scope> surface) {
        phase = next;
        try {
            if (next == UIPhase.RENDER_PREPARATION) dispatchWorkers();
            phaseWork.accept(next);
            drain(next, phases.get(next), surface);
        } finally {
            phase = null;
        }
    }

    private UIPhase selectReservedPhase() {
        int start = (int) ((frame - 1) % ORDINARY.size());
        for (int i = 0; i < ORDINARY.size(); i++) {
            UIPhase candidate = ORDINARY.get((start + i) % ORDINARY.size());
            for (Job job : phases.get(candidate).pending) {
                if (frameTime - job.submittedAt >= job.task.delayNanos()
                        && (job.predecessor == null || job.predecessor.status == SUCCEEDED)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    TaskHandle submit(ModuleAccess access, UITask task) {
        Objects.requireNonNull(task, "task");
        access.assertSideEffectAllowed();
        synchronized (lock) {
            var plan = prepare(access, task);
            Job old = plan.old();
            var lane = phases.get(task.phase());
            int phaseUsed = lane.reservations - (old != null && old.lane == lane ? 1 : 0);
            int backgroundUsed = backgroundReservations - (old != null && old.background ? 1 : 0);
            requireCapacity(plan, phaseUsed, backgroundUsed);
            return enqueue(plan, clock.getAsLong());
        }
    }

    CommandResult commitEffects(ModuleAccess access, List<UITask> effects, LongSupplier publish) {
        assertOwner();
        synchronized (lock) {
            var plans = new ArrayList<Admission>();
            var admitted = new EnumMap<UIPhase, Integer>(UIPhase.class);
            int backgroundUsed = backgroundReservations;
            for (UITask task : effects) {
                if (task.replacementKey() != null || task.dependency() != null) {
                    throw new IllegalArgumentException("Transactional effects must be independent tasks");
                }
                var plan = prepare(access, task);
                requireCapacity(plan, phases.get(task.phase()).reservations + admitted.getOrDefault(task.phase(), 0),
                        backgroundUsed);
                admitted.merge(task.phase(), 1, Integer::sum);
                if (plan.worker()) {
                    backgroundUsed++;
                }
                plans.add(plan);
            }
            long now = clock.getAsLong();
            long stateVersion = publish.getAsLong();
            var handles = new ArrayList<TaskHandle>(plans.size());
            for (Admission plan : plans) {
                handles.add(enqueue(plan, now));
            }
            return new CommandResult(stateVersion, handles);
        }
    }

    private Admission prepare(ModuleAccess access, UITask task) {
        var issuer = access.requireIssuer();
        var target = access.write(task.scope());
        boolean worker = task.work() instanceof UITask.BackgroundWork;
        if (worker && task.phase() != UIPhase.STATE_MUTATION && task.phase() != UIPhase.RENDER_PREPARATION) {
            throw new IllegalArgumentException("Background UI continuations require STATE_MUTATION or RENDER_PREPARATION");
        }
        if (task.phase() == UIPhase.RENDER) {
            access.require(UIPermission.REGISTER_RENDER_PASS, target);
        }
        if (closed) {
            throw new IllegalStateException("UI scheduler is closed");
        }
        Handle predecessor = dependency(issuer, task.dependency());
        var replacement = task.replacementKey() == null ? null
                : new Replacement(issuer.generation(), target.generation(), task.replacementKey());
        Job old = replacement == null ? null : replacements.get(replacement);
        if (old != null && old.handle == predecessor) {
            throw new IllegalArgumentException("A UI task cannot depend on the task it replaces");
        }
        return new Admission(access, issuer, target, task, predecessor, replacement, old, worker);
    }

    private void requireCapacity(Admission plan, int phaseUsed, int backgroundUsed) {
        if (phaseUsed >= budget.phaseCapacity() || plan.worker() && backgroundUsed >= budget.backgroundCapacity()) {
            rejected++;
            throw new IllegalStateException("UI task queue capacity exceeded in " + plan.task().phase());
        }
    }

    private Handle enqueue(Admission plan, long now) {
        var lane = phases.get(plan.task().phase());
        var handle = new Handle(this, identity, plan.issuer().generation(), ++sequence);
        var job = new Job(plan, lane, handle, now, frame);
        if (Thread.currentThread() == uiThread && advancing && phase != null
                && plan.task().phase().ordinal() <= phase.ordinal()) {
            job.readyFrame = frame + 1;
        }
        if (plan.old() != null) {
            finish(plan.old(), CANCELLED);
        }
        jobs.put(handle, job);
        byScope.computeIfAbsent(job.issuer, ignored -> new LinkedHashSet<>()).add(job);
        byScope.computeIfAbsent(job.target, ignored -> new LinkedHashSet<>()).add(job);
        lane.reservations++;
        if (job.background) {
            backgroundReservations++;
            background.add(job);
        } else {
            lane.pending.add(job);
        }
        if (job.replacement != null) {
            replacements.put(job.replacement, job);
        }
        return handle;
    }

    private Handle dependency(ScopeLifecycle.Scope issuer, TaskHandle predecessor) {
        if (predecessor == null) {
            return null;
        }
        if (predecessor instanceof Handle handle && handle.identity == identity
                && handle.issuerGeneration == issuer.generation()) {
            return handle;
        }
        throw new SecurityException("UI task dependency belongs to another module or engine");
    }

    private void drain(UIPhase current, Lane lane, Predicate<ScopeLifecycle.Scope> surface) {
        long started = clock.getAsLong();
        List<Job> candidates;
        PriorityQueue<Job> ready;
        Map<Handle, List<Job>> dependents;
        synchronized (lock) {
            if (lane.pending.isEmpty()) {
                return;
            }
            candidates = new ArrayList<>(lane.pending);
            ready = new PriorityQueue<>(preference());
            dependents = new HashMap<>();
            for (Job job : candidates) {
                if (!eligible(job, started) || !surface.test(job.target)) {
                    continue;
                }
                if (job.predecessor == null || job.predecessor.status == SUCCEEDED) {
                    ready.add(job);
                } else {
                    dependents.computeIfAbsent(job.predecessor, ignored -> new ArrayList<>()).add(job);
                }
            }
        }

        boolean reserved = !ORDINARY.contains(current);
        int limit = reserved ? budget.phaseTasks()
                : Math.min(budget.phaseTasks(), Math.max(0, ordinaryRemaining - reserveForLater(current)));
        var moduleCalls = new HashMap<ScopeLifecycle.Scope, Integer>();
        int callbacks = 0;
        while (!ready.isEmpty() && callbacks < limit) {
            if (callbacks > 0 && clock.getAsLong() - started >= budget.phaseNanos()) {
                break;
            }
            Job job = ready.remove();
            if (moduleCalls.getOrDefault(job.issuer, 0) >= budget.modulePhaseTasks() || !runUi(job)) {
                continue;
            }
            callbacks++;
            moduleCalls.merge(job.issuer, 1, Integer::sum);
            if (job.handle.status == SUCCEEDED) {
                List<Job> unblocked = dependents.remove(job.handle);
                if (unblocked != null) {
                    ready.addAll(unblocked);
                }
            }
        }
        if (!reserved) {
            ordinaryRemaining -= callbacks;
        }
        long elapsed = clock.getAsLong() - started;
        synchronized (lock) {
            lane.callbacks += callbacks;
            lane.elapsed += elapsed;
            if (elapsed >= budget.phaseNanos() && callbacks > 0) {
                lane.overBudget++;
            }
            for (Job job : candidates) {
                if (lane.pending.contains(job) && started - job.submittedAt >= job.task.delayNanos()) {
                    deferred++;
                }
            }
        }
    }

    private int reserveForLater(UIPhase current) {
        return reservedOrdinaryPhase != null && current.ordinal() < reservedOrdinaryPhase.ordinal() ? 1 : 0;
    }

    private Comparator<Job> preference() {
        long currentFrame = frame;
        return Comparator.<Job>comparingInt(job ->
                        Math.max(0, job.task.priority().ordinal()
                                - (int) Math.max(0, Math.min(4, currentFrame - job.submittedFrame - 1))))
                .thenComparingLong(job -> job.handle.sequence);
    }

    private boolean eligible(Job job, long now) {
        if (!live(job, now)) {
            return false;
        }
        if (job.predecessor != null && job.predecessor.status.isTerminal() && job.predecessor.status != SUCCEEDED) {
            finish(job, CANCELLED);
            return false;
        }
        return frame >= job.readyFrame && now - job.submittedAt >= job.task.delayNanos();
    }

    private boolean live(Job job, long now) {
        if (job.handle.status.isTerminal()) {
            return false;
        }
        if (!job.authorized()) {
            finish(job, CANCELLED);
            return false;
        }
        if (job.task.timeoutNanos() != 0 && now - job.submittedAt >= job.task.timeoutNanos()) {
            finish(job, TIMED_OUT);
            return false;
        }
        return true;
    }

    private boolean runUi(Job job) {
        Runnable action;
        synchronized (lock) {
            if (!eligible(job, clock.getAsLong())) {
                return false;
            }
            job.lane.pending.remove(job);
            job.lane.reservations--;
            if (job.replacement != null) {
                replacements.remove(job.replacement, job);
            }
            job.handle.status = RUNNING;
            action = job.continuation;
            job.continuation = null;
        }
        TaskHandle.Status result = FAILED;
        try {
            action.run();
            result = SUCCEEDED;
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            failures.accept("UI task " + job.handle.sequence + " in " + job.issuer.id(), failure);
        } finally {
            synchronized (lock) {
                finish(job, result);
            }
        }
        return true;
    }

    private void dispatchWorkers() {
        List<Job> ready;
        synchronized (lock) {
            if (background.isEmpty() && workers.isEmpty()) {
                return;
            }
            ready = new ArrayList<>();
            long now = clock.getAsLong();
            for (Job worker : workers) {
                live(worker, now);
            }
            for (Job job : List.copyOf(background)) {
                if (eligible(job, now) && (job.predecessor == null || job.predecessor.status == SUCCEEDED)) {
                    ready.add(job);
                }
            }
            ready.sort(preference());
        }
        for (Job job : ready) {
            synchronized (lock) {
                if (workers.size() >= budget.backgroundWorkers()) {
                    deferred++;
                    continue;
                }
                if (!live(job, clock.getAsLong())) {
                    continue;
                }
                background.remove(job);
                workers.add(job);
                job.handle.status = RUNNING;
                job.readyFrame = frame + 1;
            }
            try {
                executor.execute(() -> compute(job));
            } catch (RuntimeException | LinkageError failure) {
                synchronized (lock) {
                    workers.remove(job);
                    finish(job, FAILED);
                }
                failures.accept("Dispatching background UI task " + job.handle.sequence, failure);
            }
        }
    }

    private void compute(Job job) {
        Runnable continuation = null;
        boolean completed = false;
        try {
            synchronized (lock) {
                if (!live(job, clock.getAsLong())) {
                    return;
                }
            }
            if (Thread.currentThread() == uiThread) {
                throw new IllegalStateException("UI executor ran background work on the owner thread");
            }
            continuation = ((UITask.BackgroundWork) job.task.work()).compute().apply(job.handle);
            completed = true;
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            failures.accept("Background UI task " + job.handle.sequence + " in " + job.issuer.id(), failure);
        } finally {
            synchronized (lock) {
                workers.remove(job);
                if (live(job, clock.getAsLong())) {
                    if (completed && continuation != null) {
                        job.background = false;
                        backgroundReservations--;
                        job.continuation = continuation;
                        job.handle.status = PENDING;
                        job.lane.pending.add(job);
                    } else {
                        finish(job, completed ? SUCCEEDED : FAILED);
                    }
                }
            }
        }
    }

    private boolean cancel(Handle handle) {
        synchronized (lock) {
            Job job = jobs.get(handle);
            if (job == null || job.handle.status == RUNNING && !job.background) {
                return false;
            }
            job.access.assertSideEffectAllowed();
            finish(job, CANCELLED);
            return true;
        }
    }

    void permissionsChanged(ModuleAccess access) {
        synchronized (lock) {
            var owned = byScope.get(access.defaultScope());
            if (owned != null) {
                List.copyOf(owned).stream().filter(job -> job.access == access && !job.authorized())
                        .forEach(job -> finish(job, CANCELLED));
            }
        }
    }

    void cancelScope(ScopeLifecycle.Scope scope) {
        synchronized (lock) {
            Set<Job> owned = byScope.get(scope);
            if (owned != null) {
                List.copyOf(owned).forEach(job -> finish(job, CANCELLED));
            }
        }
    }

    void close() {
        assertOwner();
        synchronized (lock) {
            closed = true;
            List.copyOf(jobs.values()).forEach(job -> finish(job, CANCELLED));
        }
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
    }

    private void finish(Job job, TaskHandle.Status status) {
        if (job.handle.status.isTerminal()) {
            return;
        }
        if (job.background || job.handle.status != RUNNING) {
            job.lane.reservations--;
        }
        job.handle.status = status;
        job.handle.scheduler = null;
        job.continuation = null;
        job.lane.pending.remove(job);
        if (job.background) {
            backgroundReservations--;
            background.remove(job);
        }
        jobs.remove(job.handle);
        if (job.replacement != null) {
            replacements.remove(job.replacement, job);
        }
        removeBinding(job.issuer, job);
        if (job.target != job.issuer) {
            removeBinding(job.target, job);
        }
        switch (status) {
            case SUCCEEDED -> succeeded++;
            case CANCELLED -> cancelled++;
            case TIMED_OUT -> timedOut++;
            case FAILED -> failed++;
            default -> throw new IllegalArgumentException("Task has not finished");
        }
    }

    private void removeBinding(ScopeLifecycle.Scope scope, Job job) {
        Set<Job> owned = byScope.get(scope);
        owned.remove(job);
        if (owned.isEmpty()) {
            byScope.remove(scope);
        }
    }

    private SchedulerSnapshot snapshot() {
        synchronized (lock) {
            var metrics = new EnumMap<UIPhase, SchedulerSnapshot.PhaseMetrics>(UIPhase.class);
            phases.forEach((phase, lane) -> metrics.put(phase,
                    new SchedulerSnapshot.PhaseMetrics(lane.callbacks, lane.elapsed, lane.overBudget)));
            int queued = background.size() + phases.values().stream().mapToInt(lane -> lane.pending.size()).sum();
            return new SchedulerSnapshot(frame, frameTime, queued, workers.size(), succeeded, cancelled, timedOut,
                    failed, rejected, deferred, metrics);
        }
    }

    private void assertOwner() {
        if (Thread.currentThread() != uiThread) {
            throw new IllegalStateException("UI scheduler operation requires the owner thread");
        }
    }

    private record Replacement(long issuer, long target, UiId id) {}

    private record Admission(ModuleAccess access, ScopeLifecycle.Scope issuer, ScopeLifecycle.Scope target,
                             UITask task, Handle predecessor, Replacement replacement, Job old, boolean worker) {}

    private static final class Lane {
        final Set<Job> pending = new LinkedHashSet<>();
        int reservations;
        long callbacks;
        long elapsed;
        long overBudget;
    }

    private static final class Handle implements TaskHandle {
        private volatile CentralScheduler scheduler;
        private final Object identity;
        private final long issuerGeneration;
        private final long sequence;
        private volatile Status status = PENDING;

        Handle(CentralScheduler scheduler, Object identity, long issuerGeneration, long sequence) {
            this.scheduler = scheduler;
            this.identity = identity;
            this.issuerGeneration = issuerGeneration;
            this.sequence = sequence;
        }

        @Override
        public long sequence() {
            return sequence;
        }

        @Override
        public Status status() {
            return status;
        }

        @Override
        public boolean cancel() {
            CentralScheduler owner = scheduler;
            return owner != null && owner.cancel(this);
        }
    }

    private static final class Job {
        final ModuleAccess access;
        final ScopeLifecycle.Scope issuer;
        final ScopeLifecycle.Scope target;
        final UITask task;
        final Handle predecessor;
        final Replacement replacement;
        final Lane lane;
        final Handle handle;
        final long submittedAt;
        final long submittedFrame;
        long readyFrame;
        boolean background;
        Runnable continuation;

        Job(Admission plan, Lane lane, Handle handle, long submittedAt, long submittedFrame) {
            access = plan.access();
            issuer = plan.issuer();
            target = plan.target();
            task = plan.task();
            predecessor = plan.predecessor();
            replacement = plan.replacement();
            this.lane = lane;
            this.handle = handle;
            this.submittedAt = submittedAt;
            this.submittedFrame = submittedFrame;
            background = plan.worker();
            continuation = task.work() instanceof UITask.UIWork ui ? ui.action() : null;
        }

        boolean authorized() {
            return access.allowsScope(target) && (task.phase() != UIPhase.RENDER
                    || access.decision(UIPermission.REGISTER_RENDER_PASS, target).allowed());
        }
    }
}
