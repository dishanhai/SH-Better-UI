// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.*;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService.Plan;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/** Scope-owned activity and notification state. The kernel pumps it after its state-mutation lane. */
final class LiveSurfaceRuntime implements ActivityService, NotificationService, LiveSurfaceService {
    private static final long NO_DEADLINE = Long.MAX_VALUE;
    private static final long NOT_SHOWN = Long.MIN_VALUE;
    private static final long NOTIFICATION_QUEUE_LIMIT = Duration.ofMinutes(1).toNanos();
    private static final long RATE_WINDOW = Duration.ofSeconds(1).toNanos();
    private static final long CONTENT_INTERVAL = Duration.ofMillis(16).toNanos();
    private static final long SHORTCUT_HINT_RETRACTION = Duration.ofMillis(180).toNanos();
    private static final Set<UIDirtyFlag> PRESENTATION_DIRTY = Set.of(UIDirtyFlag.MEASURE, UIDirtyFlag.LAYOUT,
            UIDirtyFlag.PAINT, UIDirtyFlag.HIT_TEST, UIDirtyFlag.ACCESSIBILITY);
    private final KernelHost host;
    private final Map<UiId, Entry> entries = new LinkedHashMap<>();
    private final Map<Long, Entry> instances = new HashMap<>();
    private final Map<Dedup, Entry> deduplicated = new HashMap<>();
    private final Map<UiId, Rate> notificationRates = new HashMap<>();
    private final Map<UiId, Space> spaces = new LinkedHashMap<>();
    private final Set<Entry> changed = new LinkedHashSet<>();
    private final LongAdder coalesced = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private volatile Plan plan = Plan.EMPTY;
    private volatile View view = View.EMPTY;
    private volatile int activityCount;
    private volatile int notificationCount;
    private volatile long stateNanos, layoutNanos, animationNanos, renderNanos, inputNanos;
    private List<SurfacePlacement.Rect> occupied = List.of();
    private List<LiveActivityArbiter.Candidate> rank = List.of();
    private Policy policy = Policy.DEFAULT;
    private long nextInstance, primary, primarySince, pinned, expanded;
    private long shownSecondary;
    private long nextDeadline = NO_DEADLINE, nextDecision = NO_DEADLINE;
    private long pendingStateNanos;
    private boolean planDirty, rankDirty, protectedInput, acceptsInput;

    LiveSurfaceRuntime(KernelHost host) { this.host = host; }

    @Override public ActivityService.Handle publish(ModuleContext module, ScopeHandle scope, ActivityService.Request request) {
        return publishActivity(module, scope, request, Kind.ACTIVITY);
    }

    @Override public TaskHandle updateAll(ModuleContext module, ScopeHandle scope, Map<ActivityService.Handle, Content> contents) {
        var target = host.ownedScope(module, scope);
        if (contents.isEmpty() || contents.size() > 128) throw new IllegalArgumentException("Invalid live activity batch size");
        var batch = new ArrayList<BatchItem>(contents.size());
        contents.forEach((handle, content) -> {
            if (!(handle instanceof ActivityHandle owned) || owned.entry.scope != target) {
                throw new SecurityException("Live activity batch requires handles owned by the supplied scope");
            }
            batch.add(new BatchItem(owned.entry, Objects.requireNonNull(content, "content")));
        });
        batch.sort(java.util.Comparator.comparingLong(item -> item.entry.snapshot.instance()));
        return module.engine().scheduler().submit(UITask.ui(target, UIPhase.STATE_MUTATION, UIPriority.NORMAL, () -> {
            for (var item : batch) {
                var entry = item.entry;
                if (entries.get(entry.snapshot.id()) != entry || entry.snapshot.lifecycle().terminal()) {
                    throw new IllegalStateException("Live activity batch contains an ended publication");
                }
            }
            for (var item : batch) item.entry.applyContent(item.content);
        }));
    }

    ActivityService.Handle publishRemote(ModuleContext module, ScopeHandle scope, ActivityService.Request request) {
        return publishActivity(module, scope, request, Kind.REMOTE);
    }

    private ActivityService.Handle publishActivity(ModuleContext module, ScopeHandle scope, ActivityService.Request request, Kind kind) {
        var target = authorize(module, scope, request.id(), request.priority());
        checkCapacity(target.owner(), request.id(), kind, policy.maxActivities, 128);
        var entry = new Entry(module, target, request.id(), kind, request.initial(), request.priority(),
                request.timeout().toNanos(), request.interruptible(), request.onAction(), null);
        add(entry);
        return new ActivityHandle(entry);
    }

    @Override public NotificationService.Handle publish(ModuleContext module, ScopeHandle scope, NotificationService.Request request) {
        var target = authorize(module, scope, request.id(), request.priority());
        if (!policy.notifications) throw new IllegalStateException("Live notifications are disabled");
        if (request.deduplicationKey() != null) requireNamespace(target, request.deduplicationKey());
        var key = policy.deduplicate && request.deduplicationKey() != null
                ? new Dedup(target.generation(), request.deduplicationKey()) : null;
        var existing = key == null ? null : deduplicated.get(key);
        if (existing != null) {
            existing.merge(request);
            return existing.notification;
        }
        checkCapacity(target.owner(), request.id(), Kind.NOTIFICATION, policy.maxNotifications, 32);
        long now = host.nowNanos();
        var rate = notificationRates.computeIfAbsent(target.owner(), ignored -> new Rate(now));
        if (now - rate.start >= RATE_WINDOW) {
            rate.start = now;
            rate.count = 0;
        }
        if (rate.count >= 8) throw new IllegalStateException("Live notification rate exceeded for " + target.owner());
        rate.count++;
        long duration = request.duration().isZero() ? policy.defaultDurationNanos : request.duration().toNanos();
        var entry = new Entry(module, target, request.id(), Kind.NOTIFICATION, request.content(), request.priority(),
                duration, true, request.onAction(), key);
        entry.notification = new NotificationHandle(entry);
        add(entry);
        if (key != null) deduplicated.put(key, entry);
        return entry.notification;
    }

    @Override public void dismissAll(ModuleContext module, ScopeHandle scope) {
        var target = host.ownedScope(module, scope);
        host.moduleAccess(module).assertSideEffectAllowed();
        for (var entry : List.copyOf(entries.values())) {
            if (entry.scope == target && entry.notification != null) entry.finish(Lifecycle.DISMISSED, true);
        }
    }

    @Override public Reservation reserve(ModuleContext module, ScopeHandle scope, UiId id, SurfacePlacement.Rect bounds) {
        var target = authorize(module, scope, id, Priority.NORMAL);
        if (spaces.size() >= 128) throw new IllegalStateException("Live surface reservation limit reached");
        if (spaces.containsKey(id)) throw new IllegalArgumentException("Duplicate live surface reservation " + id);
        var space = new Space(id, Objects.requireNonNull(bounds, "bounds"));
        space.release = host.onScopeClose(target, () -> {
            spaces.remove(id, space);
            refreshOccupied();
        });
        spaces.put(id, space);
        refreshOccupied();
        return space;
    }

    private ScopeLifecycle.Scope authorize(ModuleContext module, ScopeHandle scope, UiId id, Priority priority) {
        var target = host.ownedScope(module, scope);
        var access = host.moduleAccess(module);
        access.assertSideEffectAllowed();
        requireNamespace(target, id);
        if (priority == Priority.CRITICAL) access.require(UIPermission.PUBLISH_CRITICAL_ACTIVITY, target);
        return target;
    }

    private static void requireNamespace(ScopeHandle scope, UiId id) {
        if (!scope.owner().namespace().equals(id.namespace())) {
            throw new SecurityException("Live resource " + id + " is outside " + scope.owner() + " namespace");
        }
    }

    private void checkCapacity(UiId owner, UiId id, Kind kind, int capacity, int ownerCapacity) {
        if (entries.containsKey(id)) throw new IllegalArgumentException("Duplicate live publication " + id);
        boolean notification = kind == Kind.NOTIFICATION;
        if ((notification ? notificationCount : activityCount) >= capacity) {
            throw new IllegalStateException("Live publication limit reached");
        }
        int owned = 0;
        for (var entry : entries.values()) {
            if (entry.snapshot.owner().equals(owner) && (entry.notification != null) == notification
                    && ++owned >= ownerCapacity) {
                throw new IllegalStateException("Live publication limit reached for " + owner);
            }
        }
    }

    private void add(Entry entry) {
        entry.release = host.onScopeClose(entry.scope, () -> entry.finish(Lifecycle.CANCELLED, false));
        entries.put(entry.snapshot.id(), entry);
        instances.put(entry.snapshot.instance(), entry);
        if (entry.notification != null) notificationCount++;
        else activityCount++;
        nextDeadline = Math.min(nextDeadline, entry.deadline());
        rankDirty = true;
    }

    void policy(Policy next) {
        if (policy.equals(next)) return;
        policy = next;
        rankDirty = true;
    }

    Policy policy() { return policy; }

    void advance(boolean interacting) { advance(interacting, true); }

    void advance(boolean interacting, boolean inputAvailable) {
        long now = host.nowNanos();
        if (!planDirty && !rankDirty && changed.isEmpty() && now < nextDeadline && now < nextDecision
                && protectedInput == interacting && acceptsInput == inputAvailable) {
            if (pendingStateNanos != 0) {
                stateNanos = pendingStateNanos;
                pendingStateNanos = 0;
            }
            return;
        }
        long start = System.nanoTime();
        if (now >= nextDeadline) {
            nextDeadline = NO_DEADLINE;
            for (var entry : List.copyOf(entries.values())) {
                long deadline = entry.deadline();
                if (now >= deadline) entry.finish(Lifecycle.TIMED_OUT, true);
                else nextDeadline = Math.min(nextDeadline, deadline);
            }
        }
        // Observer-induced work belongs to the next drain. The same entry is delivered once per frame.
        if (!changed.isEmpty()) {
            var batch = List.copyOf(changed);
            changed.clear();
            for (var entry : batch) entry.notifyObservers();
        }
        if (rankDirty || now >= nextDecision || protectedInput != interacting) {
            var eligible = new ArrayList<LiveActivityArbiter.Candidate>(entries.size());
            for (var entry : entries.values()) {
                var snapshot = entry.snapshot;
                if (snapshot.lifecycle() != Lifecycle.RUNNING || !snapshot.presented()
                        || entry.notification != null && (!policy.notifications || policy.quiet && snapshot.priority() != Priority.CRITICAL)) continue;
                var priority = policy.priorities.getOrDefault(snapshot.id(), snapshot.priority());
                eligible.add(new LiveActivityArbiter.Candidate(snapshot.instance(), priority, snapshot.kind(),
                        entry.waitingSince, entry.updatedAt, entry.interruptible));
            }
            rank = LiveActivityArbiter.rank(eligible, primary, primarySince, pinned, interacting, now);
            long selected = rank.isEmpty() ? 0 : rank.get(0).instance();
            if (primary != selected) {
                var old = find(primary);
                if (old != null) old.waitingSince = now;
                primary = selected;
                primarySince = now;
            }
            nextDecision = rank.size() > 1 ? deadline(now, LiveActivityArbiter.FAIRNESS_INTERVAL) : NO_DEADLINE;
            rankDirty = false;
        }
        protectedInput = interacting;
        acceptsInput = inputAvailable;
        publishPlan(inputAvailable);
        planDirty = false;
        stateNanos = pendingStateNanos + System.nanoTime() - start;
        pendingStateNanos = 0;
    }

    private void publishPlan(boolean inputAvailable) {
        var first = find(primary);
        if (first == null || !policy.enabled) {
            shownSecondary = 0;
            plan = Plan.EMPTY;
            return;
        }
        boolean expand = inputAvailable && policy.expanded && (expanded == primary || expanded == 0 && first.expandedHint());
        Entry second = null;
        var waiting = new ArrayList<UiId>();
        for (int i = 1; i < rank.size(); i++) {
            var entry = find(rank.get(i).instance());
            if (entry == null) continue;
            if (second == null && !expand && first.hint == SurfacePresentation.COMPACT && policy.split && policy.compact
                    && first.notification == null && entry.notification == null) second = entry;
            else waiting.add(entry.snapshot.id());
        }
        var mode = expand ? (first.hint == SurfacePresentation.FLOATING ? first.hint : SurfacePresentation.EXPANDED)
                : second != null ? SurfacePresentation.SPLIT
                : policy.compact && first.hint != SurfacePresentation.MINIMAL ? SurfacePresentation.COMPACT : SurfacePresentation.MINIMAL;
        shownSecondary = second == null ? 0 : second.snapshot.instance();
        plan = new Plan(first.snapshot, second == null ? null : second.snapshot, waiting, mode,
                inputAvailable, !protectedInput && first.interruptible);
    }

    private Entry find(long instance) {
        return instances.get(instance);
    }

    private Entry current(Snapshot snapshot) {
        var entry = entries.get(snapshot.id());
        return entry != null && entry.snapshot.instance() == snapshot.instance() && entry.scope.isOpen()
                && entry.snapshot.lifecycle() == Lifecycle.RUNNING && entry.snapshot.presented() ? entry : null;
    }

    boolean hasActions(Snapshot snapshot) {
        return currentActionBinding(snapshot) != null;
    }

    boolean isInputCurrent(Snapshot snapshot) { return currentInput(snapshot) != null; }

    boolean dispatch(Snapshot snapshot, Action action) {
        var entry = currentActionBinding(snapshot);
        if (entry == null || !allowsAction(entry.snapshot.content(), action)) return false;
        try {
            entry.scheduler.submit(UITask.ui(entry.scope, UIPhase.STATE_MUTATION, UIPriority.USER_INTERACTION, () -> {
                var active = currentActionBinding(snapshot);
                if (active == null || !allowsAction(active.snapshot.content(), action)) return;
                try {
                    active.onAction.accept(action);
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    host.report("Live activity action " + snapshot.id(), failure);
                }
            }));
            return true;
        } catch (IllegalStateException | SecurityException failure) {
            host.report("Live activity action admission " + snapshot.id(), failure);
            return false;
        }
    }

    private Entry currentActionBinding(Snapshot snapshot) {
        var entry = currentInput(snapshot);
        return entry != null && entry.onAction != null ? entry : null;
    }

    private Entry currentInput(Snapshot snapshot) {
        var entry = current(snapshot);
        return entry != null && snapshot.revision() >= entry.firstInputRevision ? entry : null;
    }

    private static boolean allowsAction(Content content, Action action) {
        if (action.id().equals(SEEK)) return content.seekable();
        return content.controls().stream().anyMatch(control -> control.id().equals(action.id()));
    }

    boolean toggleExpanded(Snapshot snapshot) {
        var entry = current(snapshot);
        if (!policy.expanded || entry == null) return false;
        if (expanded != snapshot.instance() && (expanded != 0 || !entry.expandedHint())) {
            return expand(snapshot);
        }
        expanded = 0;
        entry.hint = SurfacePresentation.COMPACT;
        planDirty = true;
        return true;
    }

    boolean expand(Snapshot snapshot) {
        if (!policy.expanded || current(snapshot) == null) return false;
        if (expanded == snapshot.instance()) return true;
        expanded = snapshot.instance();
        // Selecting a visible sidecar is an explicit user preference for the next plan.
        if (expanded != primary) {
            pinned = expanded;
            rankDirty = true;
        }
        planDirty = true;
        return true;
    }

    boolean collapse() {
        var entry = find(primary);
        if (expanded == 0 && (entry == null || !entry.expandedHint())) return false;
        if (entry != null && entry.expandedHint()) entry.hint = SurfacePresentation.COMPACT;
        expanded = 0;
        planDirty = true;
        return true;
    }

    void togglePin(Snapshot snapshot) {
        if (current(snapshot) == null) return;
        pinned = pinned == snapshot.instance() ? 0 : snapshot.instance();
        rankDirty = true;
    }

    boolean dismiss(Snapshot snapshot) {
        var entry = current(snapshot);
        if (entry == null) return false;
        if (entry.notification != null) entry.finish(Lifecycle.DISMISSED, true);
        else entry.visible(false);
        return true;
    }

    Plan plan() { return plan; }
    boolean hasResources() { return activityCount + notificationCount > 0; }
    void markPainted(Snapshot snapshot) {
        var entry = current(snapshot);
        if (entry == null || entry.firstShown != NOT_SHOWN) return;
        entry.firstShown = host.nowNanos();
        if (entry.notification != null) nextDeadline = Math.min(nextDeadline, entry.deadline());
    }

    boolean hasPainted(Snapshot snapshot) {
        var entry = current(snapshot);
        return entry != null && entry.firstShown != NOT_SHOWN;
    }

    /** Zero while the hints are fully visible and one once they have retracted. */
    double shortcutHintRetraction(Snapshot snapshot, boolean animate) {
        var entry = current(snapshot);
        if (entry == null || entry.firstShown == NOT_SHOWN) return 1;
        long visibleDuration = entry.duration == 0 ? policy.defaultDurationNanos : entry.deadline() - entry.firstShown;
        if (visibleDuration <= 0) return 1;
        long halfway = visibleDuration / 2;
        long pastHalfway = host.nowNanos() - entry.firstShown - halfway;
        if (pastHalfway < 0) return 0;
        if (!animate) return 1;
        long retraction = Math.min(SHORTCUT_HINT_RETRACTION, visibleDuration - halfway);
        return Math.min(1, pastHalfway / (double) retraction);
    }

    List<SurfacePlacement.Rect> occupied() { return occupied; }
    private void refreshOccupied() { occupied = spaces.values().stream().map(space -> space.bounds).toList(); }
    void present(View next) { view = next; }
    @Override public View view() { return view; }
    @Override public Diagnostics diagnostics() {
        return new Diagnostics(activityCount, notificationCount, coalesced.sum(), rejected.sum(),
                stateNanos, layoutNanos, animationNanos, renderNanos, inputNanos);
    }
    void layoutMeasured(long nanos) { layoutNanos = nanos; }
    void animationMeasured(long nanos) { animationNanos = nanos; }
    void renderMeasured(long nanos) { renderNanos = nanos; }
    void inputMeasured(long nanos) { inputNanos = nanos; }

    void clearPresentation() {
        for (var entry : List.copyOf(changed)) entry.detachObservers();
        plan = Plan.EMPTY;
        view = View.EMPTY;
    }

    private static long deadline(long start, long duration) {
        return duration == 0 || start > Long.MAX_VALUE - duration ? NO_DEADLINE : start + duration;
    }

    private static Set<UIDirtyFlag> diff(Content before, Content after) {
        var dirty = EnumSet.of(UIDirtyFlag.PAINT);
        if (!before.title().equals(after.title()) || !before.detail().equals(after.detail())
                || !Objects.equals(before.icon(), after.icon()) || !before.controls().equals(after.controls())
                || (before.progress() < 0) != (after.progress() < 0) || before.seekable() != after.seekable()
                || before.rhythm().isEmpty() != after.rhythm().isEmpty()
                || !Objects.equals(before.appearance(), after.appearance())) dirty.addAll(PRESENTATION_DIRTY);
        return dirty;
    }

    record Policy(boolean enabled, boolean compact, boolean expanded, boolean split, boolean notifications,
                  boolean deduplicate, boolean quiet, int maxActivities, int maxNotifications, long defaultDurationNanos,
                  Map<UiId, Priority> priorities) {
        static final Policy DEFAULT = new Policy(true, true, true, true, true, true, false, 128, 48,
                Duration.ofSeconds(4).toNanos(), Map.of());
        Policy { priorities = Map.copyOf(priorities); }
    }

    private record Dedup(long scopeGeneration, UiId key) {}
    private record BatchItem(Entry entry, Content content) {}

    private static final class Rate {
        long start;
        int count;
        Rate(long start) { this.start = start; }
    }

    private final class Space implements Reservation {
        private final UiId id;
        private SurfacePlacement.Rect bounds;
        private Subscription release;
        Space(UiId id, SurfacePlacement.Rect bounds) { this.id = id; this.bounds = bounds; }
        @Override public void update(SurfacePlacement.Rect next) {
            host.assertOwner();
            if (!release.isActive()) throw new IllegalStateException("Closed live surface reservation " + id);
            if (bounds.equals(next)) return;
            bounds = Objects.requireNonNull(next, "bounds");
            refreshOccupied();
        }
        @Override public void close() { release.close(); }
    }

    private final class Entry {
        private final ScopeLifecycle.Scope scope;
        private final UIScheduler scheduler;
        private final long createdAt, duration;
        private final boolean interruptible;
        private final Dedup key;
        private final List<Watch> observers = new ArrayList<>();
        private volatile Snapshot snapshot;
        private Consumer<Action> onAction;
        private long firstInputRevision = 1;
        private Subscription release;
        private NotificationHandle notification;
        private Content pending;
        private TaskHandle pendingTask;
        private SurfacePresentation hint = SurfacePresentation.COMPACT;
        private long waitingSince, updatedAt, firstShown = NOT_SHOWN;
        private volatile long lastContentCommit = Long.MIN_VALUE;

        Entry(ModuleContext module, ScopeLifecycle.Scope scope, UiId id, Kind kind, Content initial, Priority priority,
              long duration, boolean interruptible, Consumer<Action> onAction, Dedup key) {
            this.scope = scope;
            scheduler = module.engine().scheduler();
            this.duration = duration;
            this.interruptible = interruptible;
            this.onAction = onAction;
            this.key = key;
            createdAt = waitingSince = updatedAt = host.nowNanos();
            snapshot = new Snapshot(id, scope.owner(), ++nextInstance, 1, kind, Lifecycle.RUNNING, priority,
                    initial, true, 1, PRESENTATION_DIRTY);
        }

        long deadline() {
            if (notification == null) return LiveSurfaceRuntime.deadline(createdAt, duration);
            return firstShown == NOT_SHOWN ? LiveSurfaceRuntime.deadline(createdAt, NOTIFICATION_QUEUE_LIMIT)
                    : LiveSurfaceRuntime.deadline(firstShown, duration);
        }

        synchronized void enqueue(Content content) {
            Objects.requireNonNull(content, "content");
            if (snapshot.lifecycle().terminal() || !scope.isOpen()) throw new IllegalStateException("Live publication has ended");
            if (pendingTask != null && !pendingTask.status().isTerminal()) {
                if (!content.equals(pending)) pending = content;
                coalesced.increment();
                return;
            }
            if (snapshot.content().equals(content)) return;
            pending = content;
            try {
                long last = lastContentCommit;
                long delay = last == Long.MIN_VALUE ? 0 : Math.max(0, CONTENT_INTERVAL - (scheduler.nowNanos() - last));
                pendingTask = scheduler.submit(UITask.ui(scope, UIPhase.STATE_MUTATION, UIPriority.NORMAL, this::commit)
                        .delayed(delay));
            } catch (RuntimeException failure) {
                pending = null;
                pendingTask = null;
                rejected.increment();
                throw failure;
            }
        }

        private synchronized void commit() {
            var content = pending;
            pending = null;
            pendingTask = null;
            if (content != null) applyContent(content);
        }

        private void applyContent(Content content) {
            var before = snapshot;
            if (before.lifecycle().terminal() || before.content().equals(content)) return;
            long start = System.nanoTime();
            updatedAt = lastContentCommit = host.nowNanos();
            set(content, before.priority(), before.lifecycle(), before.presented(), before.occurrences(), diff(before.content(), content));
            pendingStateNanos += System.nanoTime() - start;
        }

        private synchronized void merge(NotificationService.Request request) {
            if (request.merge() == NotificationService.Merge.KEEP) return;
            var before = snapshot;
            var content = request.merge() == NotificationService.Merge.REPLACE ? request.content() : before.content();
            int count = request.merge() == NotificationService.Merge.COUNT
                    ? Math.min(999, before.occurrences() + 1) : before.occurrences();
            if (request.merge() == NotificationService.Merge.COUNT && count == before.occurrences()) return;
            if (request.merge() == NotificationService.Merge.REPLACE) {
                if (pendingTask != null) pendingTask.cancel();
                pending = null;
                pendingTask = null;
                onAction = request.onAction();
                firstInputRevision = before.revision() + 1;
                updatedAt = host.nowNanos();
            }
            var priority = request.merge() == NotificationService.Merge.REPLACE ? request.priority() : before.priority();
            rankDirty |= priority != before.priority();
            set(content, priority, before.lifecycle(), before.presented(), count, diff(before.content(), content));
        }

        private void set(Content content, Priority priority, Lifecycle lifecycle, boolean visible, int count, Set<UIDirtyFlag> flags) {
            var before = snapshot;
            snapshot = new Snapshot(before.id(), before.owner(), before.instance(), before.revision() + 1,
                    before.kind(), lifecycle, priority, content, visible, count, flags);
            if (before.instance() == primary || before.instance() == shownSecondary) planDirty = true;
            if (!observers.isEmpty()) changed.add(this);
        }

        void lifecycle(Lifecycle next) {
            host.assertOwner();
            var before = snapshot;
            if (before.lifecycle().terminal() || before.lifecycle() == next) return;
            waitingSince = host.nowNanos();
            set(before.content(), before.priority(), next, before.presented(), before.occurrences(), PRESENTATION_DIRTY);
            rankDirty = true;
        }

        void visible(boolean visible) {
            host.assertOwner();
            var before = snapshot;
            if (before.lifecycle().terminal() || before.presented() == visible) return;
            waitingSince = host.nowNanos();
            set(before.content(), before.priority(), before.lifecycle(), visible, before.occurrences(), PRESENTATION_DIRTY);
            if (!visible && expanded == before.instance()) expanded = 0;
            if (!visible && pinned == before.instance()) pinned = 0;
            rankDirty = true;
        }

        void presentation(SurfacePresentation requested) {
            host.assertOwner();
            Objects.requireNonNull(requested, "presentation");
            if (requested == SurfacePresentation.SPLIT || requested == SurfacePresentation.SUSPENDED) {
                throw new IllegalArgumentException("The kernel selects " + requested);
            }
            if (snapshot.lifecycle().terminal()) return;
            if (requested == SurfacePresentation.HIDDEN) {
                visible(false);
                return;
            }
            hint = requested;
            if (expanded == snapshot.instance()) expanded = 0;
            planDirty = true;
            visible(true);
        }

        boolean expandedHint() { return hint == SurfacePresentation.EXPANDED || hint == SurfacePresentation.FLOATING; }

        synchronized void finish(Lifecycle terminal, boolean deliver) {
            host.assertOwner();
            var before = snapshot;
            if (before.lifecycle().terminal()) return;
            set(before.content(), before.priority(), terminal, false, before.occurrences(), PRESENTATION_DIRTY);
            if (pendingTask != null) pendingTask.cancel();
            pending = null;
            pendingTask = null;
            onAction = null;
            if (!deliver || !scope.isOpen()) detachObservers();
            entries.remove(before.id(), this);
            instances.remove(before.instance(), this);
            if (key != null) deduplicated.remove(key, this);
            if (notification != null) notificationCount--;
            else activityCount--;
            if (pinned == before.instance()) pinned = 0;
            if (expanded == before.instance()) expanded = 0;
            rankDirty = true;
            release.close();
        }

        Subscription observe(Consumer<Snapshot> listener) {
            host.assertOwner();
            Objects.requireNonNull(listener, "listener");
            if (!scope.isOpen() || snapshot.lifecycle().terminal()) throw new IllegalStateException("Live publication has ended");
            if (observers.size() >= 16) throw new IllegalStateException("Live activity observer limit reached");
            var watch = new Watch(this, listener);
            observers.add(watch);
            return watch;
        }

        void notifyObservers() {
            var value = snapshot;
            for (var watch : List.copyOf(observers)) {
                if (!watch.active || !scope.isOpen() || watch.lastRevision >= value.revision()) continue;
                watch.lastRevision = value.revision();
                try {
                    watch.listener.accept(value);
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    host.report("Live activity observer " + value.id(), failure);
                    watch.close();
                }
            }
            if (value.lifecycle().terminal() || !scope.isOpen()) detachObservers();
        }

        private void detachObservers() {
            for (var watch : observers) {
                watch.active = false;
                watch.listener = null;
            }
            observers.clear();
            changed.remove(this);
        }
    }

    private final class Watch implements Subscription {
        private final Entry entry;
        private Consumer<Snapshot> listener;
        private volatile boolean active = true;
        private long lastRevision;
        Watch(Entry entry, Consumer<Snapshot> listener) {
            this.entry = entry;
            this.listener = listener;
            lastRevision = entry.snapshot.revision();
        }
        @Override public boolean isActive() { return active && entry.scope.isOpen(); }
        @Override public void close() {
            host.assertOwner();
            active = false;
            listener = null;
            entry.observers.remove(this);
        }
    }

    private record ActivityHandle(Entry entry) implements ActivityService.Handle {
        @Override public Snapshot snapshot() { return entry.snapshot; }
        @Override public void update(Content content) { entry.enqueue(content); }
        @Override public Subscription observe(Consumer<Snapshot> listener) { return entry.observe(listener); }
        @Override public void pause() { entry.lifecycle(Lifecycle.PAUSED); }
        @Override public void resume() { entry.lifecycle(Lifecycle.RUNNING); }
        @Override public void complete() { entry.finish(Lifecycle.COMPLETED, true); }
        @Override public void cancel() { entry.finish(Lifecycle.CANCELLED, true); }
        @Override public void dismissPresentation() { entry.visible(false); }
        @Override public void showPresentation() { entry.visible(true); }
        @Override public void requestPresentation(SurfacePresentation presentation) { entry.presentation(presentation); }
    }

    private record NotificationHandle(Entry entry) implements NotificationService.Handle {
        @Override public Snapshot snapshot() { return entry.snapshot; }
        @Override public void update(Content content) { entry.enqueue(content); }
        @Override public Subscription observe(Consumer<Snapshot> listener) { return entry.observe(listener); }
        @Override public void dismiss() { entry.finish(Lifecycle.DISMISSED, true); }
    }
}
