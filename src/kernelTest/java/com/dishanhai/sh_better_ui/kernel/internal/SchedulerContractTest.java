// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.dishanhai.sh_better_ui.kernel.api.UIPhase.*;
import static com.dishanhai.sh_better_ui.kernel.api.UIPriority.*;
import static com.dishanhai.sh_better_ui.kernel.internal.TestModules.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerContractTest {
    @Test
    void replaysPhasesDependenciesPriorityAndStableFrameTime() {
        assertEquals(replay(), replay());
        assertEquals(List.of("input", "first@0", "dependent", "second@0", "render", "next"), replay());
    }

    private List<String> replay() {
        var calls = new ArrayList<String>();
        try (var fixture = new Fixture()) {
            var ui = fixture.ui();
            var screen = fixture.screen();
            fixture.submit(RENDER, CRITICAL, () -> calls.add("render"));
            fixture.submit(INPUT, NORMAL, () -> calls.add("input"));
            var first = fixture.submit(STATE_MUTATION, NORMAL, () -> {
                calls.add("first@" + ui.nowNanos());
                fixture.clock.set(20);
                fixture.submit(STATE_MUTATION, CRITICAL, () -> calls.add("next"));
            });
            fixture.submit(STATE_MUTATION, NORMAL, () -> calls.add("second@" + ui.nowNanos()));
            ui.submit(UITask.ui(screen, STATE_MUTATION, CRITICAL, () -> calls.add("dependent")).after(first));
            fixture.host.runFrame();
            assertEquals(5, calls.size());
            fixture.host.runFrame();
        }
        return calls;
    }

    @Test
    void rejectsOverloadButPreservesReservedPhasesAndAdvancesOldWork() {
        try (var fixture = new Fixture(new SchedulerBudget(2, 2, 1, 1, 2, 1, 100))) {
            var calls = new ArrayList<String>();
            fixture.submit(STATE_MUTATION, BACKGROUND, () -> calls.add("old"));
            fixture.submit(STATE_MUTATION, CRITICAL, () -> calls.add("new"));
            assertThrows(IllegalStateException.class,
                    () -> fixture.submit(STATE_MUTATION, NORMAL, () -> fail("queue exceeded its limit")));
            fixture.submit(INPUT, USER_INTERACTION, () -> calls.add("input"));
            fixture.submit(RENDER, CRITICAL, () -> calls.add("render"));
            fixture.host.runFrame();
            assertEquals(List.of("input", "new", "render"), calls);
            for (int frame = 0; frame < 12 && !calls.contains("old"); frame++) {
                fixture.submit(STATE_MUTATION, CRITICAL, () -> calls.add("new"));
                fixture.host.runFrame();
            }
            assertTrue(calls.contains("old"));
            assertEquals(1, fixture.ui().snapshot().rejected());
            assertTrue(fixture.ui().snapshot().deferred() > 0);
        }
    }

    @Test
    void phaseTimeLimitDefersWorkWithoutBlockingRenderOrCleanup() {
        try (var fixture = new Fixture(new SchedulerBudget(8, 8, 1, 8, 8, 8, 10))) {
            fixture.submit(STATE_MUTATION, NORMAL, () -> fixture.clock.set(15));
            var delayed = fixture.submit(STATE_MUTATION, NORMAL, () -> {});
            var render = fixture.submit(RENDER, NORMAL, () -> {});
            var cleanup = fixture.submit(CLEANUP, NORMAL, () -> {});
            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.PENDING, delayed.status());
            assertEquals(TaskHandle.Status.SUCCEEDED, render.status());
            assertEquals(TaskHandle.Status.SUCCEEDED, cleanup.status());
            assertEquals(1, fixture.ui().snapshot().phases().get(STATE_MUTATION).overBudget());
            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.SUCCEEDED, delayed.status());
        }
    }

    @Test
    void delayTimeoutReplacementAndDependencyCancellationReleaseCapacity() {
        try (var fixture = new Fixture(new SchedulerBudget(2, 2, 1, 8, 8, 8, 100))) {
            var screen = fixture.screen();
            var ui = fixture.ui();
            var id = UiId.parse("example:replace");
            var old = ui.submit(UITask.ui(screen, STATE_MUTATION, NORMAL, () -> fail("replaced")).replacing(id));
            var waiting = ui.submit(UITask.ui(screen, STATE_MUTATION, NORMAL, () -> fail("cancelled dependency")).after(old));
            var replacement = ui.submit(UITask.ui(screen, STATE_MUTATION, NORMAL, () -> {}).replacing(id).delayed(10));
            assertEquals(TaskHandle.Status.CANCELLED, old.status());
            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.CANCELLED, waiting.status());
            var timed = ui.submit(UITask.ui(screen, STATE_MUTATION, NORMAL, () -> fail("timed out"))
                    .delayed(10).withTimeout(5));
            fixture.clock.set(10);
            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.TIMED_OUT, timed.status());
            assertEquals(TaskHandle.Status.SUCCEEDED, replacement.status());
            assertFalse(replacement.cancel());
            assertEquals(0, ui.snapshot().queued());
        }
    }

    @Test
    void runningUiTaskCanScheduleTheSameReplacementKeyWithoutCancellingItsDependents() {
        try (var fixture = new Fixture(new SchedulerBudget(1, 2, 1, 8, 8, 8, 100))) {
            var ui = fixture.ui();
            var screen = fixture.screen();
            var key = UiId.parse("example:repeat");
            var second = new AtomicReference<TaskHandle>();
            var calls = new ArrayList<String>();
            var first = ui.submit(UITask.ui(screen, STATE_MUTATION, NORMAL, () -> {
                calls.add("first");
                second.set(ui.submit(UITask.ui(screen, STATE_MUTATION, NORMAL, () -> calls.add("second"))
                        .replacing(key)));
            }).replacing(key));
            var dependent = ui.submit(UITask.ui(screen, LAYOUT, NORMAL, () -> calls.add("dependent")).after(first));

            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.SUCCEEDED, first.status());
            assertEquals(TaskHandle.Status.SUCCEEDED, dependent.status());
            assertEquals(TaskHandle.Status.PENDING, second.get().status());
            assertEquals(List.of("first", "dependent"), calls);

            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.SUCCEEDED, second.get().status());
            assertEquals(List.of("first", "dependent", "second"), calls);
        }
    }

    @Test
    void backgroundResultsCannotRunAfterCloseOrRemount() {
        try (var fixture = new Fixture()) {
            var ui = fixture.ui();
            var screen = fixture.screen();
            var result = ui.submit(UITask.background(screen, STATE_MUTATION, NORMAL, handle -> () -> fail("stale result")));
            fixture.host.runFrame();
            assertEquals(1, ui.snapshot().backgroundInFlight());
            CompletableFuture.runAsync(fixture.workers.removeFirst()).join();
            screen.close();
            assertEquals(TaskHandle.Status.CANCELLED, result.status());
            var current = fixture.submit(STATE_MUTATION, NORMAL, () -> {});
            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.SUCCEEDED, current.status());
            assertEquals(0, ui.snapshot().queued());
        }
    }

    @Test
    void backgroundAdmissionCannotUseReservedInputOrRenderLanes() {
        try (var fixture = new Fixture(new SchedulerBudget(1, 1, 1, 8, 8, 8, 100))) {
            for (UIPhase phase : List.of(INPUT, RENDER, CLEANUP)) {
                assertThrows(IllegalArgumentException.class, () -> fixture.ui().submit(
                        UITask.background(fixture.screen(), phase, BACKGROUND, handle -> null)));
            }
            var input = fixture.submit(INPUT, CRITICAL, () -> {});
            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.SUCCEEDED, input.status());
        }
    }

    @Test
    void workerCompletionDuringHandoffStartsAtTheNextFrame() {
        try (var fixture = new Fixture(SchedulerBudget.DEFAULT,
                runnable -> CompletableFuture.runAsync(runnable).join())) {
            var calls = new ArrayList<String>();
            var handle = fixture.ui().submit(UITask.background(fixture.screen(), RENDER_PREPARATION, NORMAL,
                    task -> () -> calls.add("prepared")));
            fixture.host.runFrame();
            assertTrue(calls.isEmpty());
            fixture.host.runFrame();
            assertEquals(List.of("prepared"), calls);
            assertEquals(TaskHandle.Status.SUCCEEDED, handle.status());
        }
    }

    @Test
    void laterOrdinaryPhaseMakesProgressWithASaturatedFrameBudget() {
        try (var fixture = new Fixture(new SchedulerBudget(4, 2, 1, 1, 4, 4, 100))) {
            var late = fixture.submit(GEOMETRY_UPDATE, BACKGROUND, () -> {});
            for (int frame = 0; frame < 10 && !late.status().isTerminal(); frame++) {
                fixture.submit(EVENT_DISPATCH, CRITICAL, () -> {});
                fixture.host.runFrame();
            }
            assertEquals(TaskHandle.Status.SUCCEEDED, late.status());
        }
    }

    @Test
    void stoppingModuleCancelsConcurrentSubmissionsAndRunningWorkerResults() throws Exception {
        try (var fixture = new Fixture()) {
            var started = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var ui = fixture.ui();
            var screen = fixture.screen();
            var running = ui.submit(UITask.background(screen, STATE_MUTATION, NORMAL, handle -> {
                assertFalse(ui.isOnUIThread());
                started.countDown();
                try {
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("release timeout");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                return () -> fail("stopped module result");
            }));
            fixture.host.runFrame();
            var worker = CompletableFuture.runAsync(fixture.workers.removeFirst());
            try {
                assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
                var submitted = CompletableFuture.supplyAsync(() ->
                        ui.submit(UITask.ui(screen, STATE_MUTATION, NORMAL, () -> fail("stopped module task")))).join();
                fixture.host.stopModule(fixture.context.get().descriptor().id());
                assertEquals(TaskHandle.Status.CANCELLED, submitted.status());
                assertEquals(TaskHandle.Status.CANCELLED, running.status());
                assertThrows(IllegalStateException.class, () ->
                        ui.submit(UITask.ui(screen, STATE_MUTATION, NORMAL, () -> {})));
            } finally {
                release.countDown();
                worker.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            fixture.host.runFrame();
            assertEquals(0, fixture.global().scheduler().snapshot().backgroundInFlight());
        }
    }

    @Test
    void taskFailureIsReportedAndDoesNotSkipFollowingWork() {
        try (var fixture = new Fixture()) {
            var failed = fixture.submit(STATE_MUTATION, NORMAL, () -> { throw new IllegalArgumentException("task boom"); });
            var next = fixture.submit(STATE_MUTATION, NORMAL, () -> {});
            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.FAILED, failed.status());
            assertEquals(TaskHandle.Status.SUCCEEDED, next.status());
            assertTrue(fixture.host.lifecycle().snapshot().diagnostics().stream().anyMatch(message -> message.contains("task boom")));
        }
    }

    @Test
    void anonymousViewAndForgedOrForeignScopesCannotSubmit() {
        try (var fixture = new Fixture(); var other = new Fixture()) {
            var task = UITask.ui(fixture.screen(), STATE_MUTATION, NORMAL, () -> {});
            assertThrows(SecurityException.class, () -> fixture.global().scheduler().submit(task));
            assertThrows(SecurityException.class, () -> other.ui().submit(task));
            var hostScreen = fixture.host.rootScope().openChild(ScopeKind.SCREEN, UiId.parse("sh_better_ui:test"));
            assertThrows(SecurityException.class, () -> fixture.ui().submit(UITask.ui(hostScreen, STATE_MUTATION, NORMAL, () -> {})));
            var offThread = CompletableFuture.runAsync(fixture.ui()::assertUIThread);
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(java.util.concurrent.CompletionException.class, offThread::join).getCause());
        }
    }

    @Test
    void invalidationsMergeOrderAndRespectBoundariesAcrossFrames() {
        try (var fixture = new Fixture()) {
            var tree = fixture.screen().openChild(ScopeKind.UI_TREE, UiId.parse("example:tree"));
            var parent = tree.openChild(ScopeKind.COMPONENT, UiId.parse("example:parent"));
            var child = parent.openChild(ScopeKind.COMPONENT, UiId.parse("example:child"));
            var invalidations = fixture.context.get().engine().invalidations();
            fixture.host.dirty().boundaries(parent, Set.of(UIDirtyFlag.MEASURE, UIDirtyFlag.PAINT));
            invalidations.invalidate(child, EnumSet.of(UIDirtyFlag.MEASURE, UIDirtyFlag.PAINT, UIDirtyFlag.ACCESSIBILITY));
            invalidations.invalidate(child, Set.of(UIDirtyFlag.MEASURE));
            assertEquals(List.of(child, parent), fixture.host.dirty().drain(UIDirtyFlag.MEASURE));
            assertEquals(List.of(parent, child), fixture.host.dirty().drain(UIDirtyFlag.PAINT));
            assertEquals(List.of(child, parent, tree), fixture.host.dirty().drain(UIDirtyFlag.ACCESSIBILITY));
            invalidations.invalidate(child, Set.of(UIDirtyFlag.MEASURE));
            assertTrue(fixture.host.dirty().drain(UIDirtyFlag.MEASURE).isEmpty());
            fixture.host.runFrame();
            assertEquals(List.of(child, parent), fixture.host.dirty().drain(UIDirtyFlag.MEASURE));

            invalidations.invalidate(tree, Set.of(UIDirtyFlag.STYLE, UIDirtyFlag.LAYOUT));
            assertEquals(List.of(tree, parent, child), fixture.host.dirty().drain(UIDirtyFlag.STYLE));
            assertEquals(List.of(tree, parent, child), fixture.host.dirty().drain(UIDirtyFlag.TRANSFORM));
            child.close();
            assertEquals(List.of(tree, parent), fixture.host.dirty().drain(UIDirtyFlag.HIT_TEST));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicLong clock = new AtomicLong();
        final ArrayDeque<Runnable> workers = new ArrayDeque<>();
        final AtomicReference<ModuleContext> context = new AtomicReference<>();
        final EnginePublication publication = new EnginePublication();
        final KernelHost host;
        ScopeHandle screen;

        Fixture() {
            this(SchedulerBudget.DEFAULT);
        }

        Fixture(SchedulerBudget budget) {
            this(budget, null);
        }

        Fixture(SchedulerBudget budget, java.util.concurrent.Executor executor) {
            host = new KernelHost(ModuleDescriptor.Platform.FORGE_1_20_1, publication, clock::get,
                    executor == null ? workers::addLast : executor, budget);
            host.start(List.of(candidate(module("scheduler").requestPermission(UIPermission.REGISTER_RENDER_PASS).build(),
                    Set.of(UIPermission.REGISTER_RENDER_PASS), false, ctx -> {}, context::set, ctx -> {})));
        }

        UIEngine global() {
            return publication.current();
        }

        UIScheduler ui() {
            return context.get().engine().scheduler();
        }

        ScopeHandle screen() {
            if (screen == null || !screen.isOpen()) {
                screen = context.get().scope().openChild(ScopeKind.SCREEN, UiId.parse("example:screen"));
            }
            return screen;
        }

        TaskHandle submit(UIPhase phase, UIPriority priority, Runnable run) {
            return ui().submit(UITask.ui(screen(), phase, priority, run));
        }

        @Override
        public void close() {
            host.close();
        }
    }
}
