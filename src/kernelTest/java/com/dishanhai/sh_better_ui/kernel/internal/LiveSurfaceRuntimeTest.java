// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePresentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.*;
import static org.junit.jupiter.api.Assertions.*;

final class LiveSurfaceRuntimeTest {
    private static final UiId FIRST = UiId.parse("example:first");
    private static final UiId SECOND = UiId.parse("example:second");
    private static final UiId OPEN = UiId.parse("example:open");
    private static final Action OPEN_ACTION = new Action(OPEN, -1);
    private final AtomicLong clock = new AtomicLong();
    private KernelHost host;
    private LiveSurfaceRuntime live;
    private ModuleContext owner;
    private ModuleContext other;

    @BeforeEach void start() {
        host = new KernelHost(ModuleDescriptor.Platform.FORGE_1_20_1, new EnginePublication(), clock::get,
                Runnable::run, SchedulerBudget.DEFAULT);
        live = new LiveSurfaceRuntime(host);
        host.start(List.of(
                TestModules.candidate(TestModules.module("owner").build(), Set.of(), false,
                        ignored -> {}, ctx -> owner = ctx, ignored -> {}),
                TestModules.candidate(TestModules.module("other").build(), Set.of(), false,
                        ignored -> {}, ctx -> other = ctx, ignored -> {})));
    }

    @AfterEach void stop() { host.close(); }

    @Test void notificationShortcutHintsRetractHalfwayThroughTheVisibleLifetime() {
        var request = new NotificationService.Request(FIRST, content("Warning"), Priority.HIGH,
                Duration.ofSeconds(30), SECOND, NotificationService.Merge.REPLACE, null);
        var handle = live.publish(owner, owner.scope(), request);
        frame(4_000);
        frame(18_999);
        assertEquals(0, live.shortcutHintRetraction(handle.snapshot(), true));
        frame(19_090);
        assertEquals(.5, live.shortcutHintRetraction(handle.snapshot(), true));
        assertEquals(1, live.shortcutHintRetraction(handle.snapshot(), false));
        assertSame(handle, live.publish(owner, owner.scope(), request));
        frame(19_180);
        assertEquals(1, live.shortcutHintRetraction(handle.snapshot(), true));
        assertEquals(FIRST, live.plan().primary().id());
        frame(34_000);
        assertEquals(Lifecycle.TIMED_OUT, handle.snapshot().lifecycle());
    }

    @Test void timedAndPersistentActivitiesUseTheirAvailablePresentationWindow() {
        var timed = live.publish(owner, owner.scope(), new ActivityService.Request(FIRST, content("Timed"),
                Priority.NORMAL, Duration.ofSeconds(8), true, null));
        frame(2_000);
        frame(4_999);
        assertEquals(0, live.shortcutHintRetraction(timed.snapshot(), false));
        frame(5_000);
        assertEquals(1, live.shortcutHintRetraction(timed.snapshot(), false));
        timed.cancel();

        var persistent = live.publish(owner, owner.scope(), request(SECOND, Priority.NORMAL));
        frame(5_100);
        frame(7_099);
        assertEquals(0, live.shortcutHintRetraction(persistent.snapshot(), false));
        frame(7_100);
        assertEquals(1, live.shortcutHintRetraction(persistent.snapshot(), false));
    }

    @Test void explicitExpansionKeepsDetailsOpenOnRepeatedRequests() {
        var activity = live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL));
        frame(0);
        assertTrue(live.expand(activity.snapshot()));
        frame(10);
        assertEquals(SurfacePresentation.EXPANDED, live.plan().requested());
        assertTrue(live.expand(activity.snapshot()));
        frame(20);
        assertEquals(SurfacePresentation.EXPANDED, live.plan().requested());
        activity.cancel();
        assertFalse(live.expand(activity.snapshot()));
    }

    @Test void backgroundUpdatesCoalesceAndNotifyOnOwnerThread() {
        var handle = live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL));
        var revisions = new ArrayList<Long>();
        Thread ownerThread = Thread.currentThread();
        var subscription = handle.observe(snapshot -> {
            assertSame(ownerThread, Thread.currentThread());
            revisions.add(snapshot.revision());
        });
        CompletableFuture.runAsync(() -> {
            for (int index = 0; index < 1000; index++) handle.update(content("Step " + index));
        }).join();
        assertEquals("Initial", handle.snapshot().content().title().value());
        frame(0);
        assertEquals("Step 999", handle.snapshot().content().title().value());
        assertEquals(List.of(2L), revisions);
        assertTrue(live.diagnostics().coalescedUpdates() >= 999);
        handle.update(content("Step 999"));
        frame(10);
        assertEquals(2, handle.snapshot().revision());
        subscription.close();
        handle.complete();
        frame(20);
        assertEquals(List.of(2L), revisions);
        assertEquals(Lifecycle.COMPLETED, handle.snapshot().lifecycle());
    }

    @Test void closingScopeCancelsResourcesAndPendingUpdates() {
        var scope = owner.scope().openChild(ScopeKind.CLIENT_SESSION, FIRST);
        var handle = live.publish(owner, scope, request(FIRST, Priority.NORMAL));
        handle.update(content("Pending"));
        scope.close();
        frame(20);
        assertEquals(Lifecycle.CANCELLED, handle.snapshot().lifecycle());
        assertEquals("Initial", handle.snapshot().content().title().value());
        assertEquals(0, live.diagnostics().activities());
        assertThrows(IllegalStateException.class, () -> handle.update(content("Late")));
    }

    @Test void updateRateIsBoundedAndLatestProgressSurvives() {
        var handle = live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL));
        handle.update(progress(.1));
        frame(0);
        clock.set(Duration.ofMillis(1).toNanos());
        handle.update(progress(.2));
        handle.update(progress(.3));
        frame(8);
        assertEquals(.1, handle.snapshot().content().progress());
        frame(20);
        assertEquals(.3, handle.snapshot().content().progress());
        assertEquals(Set.of(UIDirtyFlag.PAINT), handle.snapshot().invalidations());
    }

    @Test void publishRejectsForeignScopeDuplicateIdAndCriticalPriority() {
        assertThrows(SecurityException.class, () -> live.publish(other, owner.scope(), request(FIRST, Priority.NORMAL)));
        assertThrows(SecurityException.class, () -> live.publish(owner, owner.scope(), request(FIRST, Priority.CRITICAL)));
        live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL)));
    }

    @Test void dismissalKeepsTheActivityAliveAndPauseCanResume() {
        var handle = live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL));
        frame(0);
        assertEquals(FIRST, live.plan().primary().id());
        handle.dismissPresentation();
        frame(10);
        assertNull(live.plan().primary());
        assertEquals(Lifecycle.RUNNING, handle.snapshot().lifecycle());
        handle.showPresentation();
        handle.pause();
        frame(20);
        assertNull(live.plan().primary());
        handle.resume();
        frame(30);
        assertEquals(FIRST, live.plan().primary().id());
    }

    @Test void notificationMergesWithinOneOwnerAndRestoresPrimaryAfterExpiry() {
        live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL));
        live.publish(owner, owner.scope(), request(SECOND, Priority.LOW));
        frame(0);
        assertEquals(FIRST, live.plan().primary().id());
        assertEquals(SECOND, live.plan().secondary().id());

        var key = UiId.parse("example:warnings");
        var id = UiId.parse("example:warning");
        var request = new NotificationService.Request(id, content("Warning"), Priority.HIGH, Duration.ofSeconds(1),
                key, NotificationService.Merge.COUNT, null);
        var first = live.publish(owner, owner.scope(), request);
        var merged = live.publish(owner, owner.scope(), request);
        assertSame(first, merged);
        assertEquals(2, merged.snapshot().occurrences());
        frame(650);
        assertEquals(id, live.plan().primary().id());
        frame(1651);
        assertEquals(Lifecycle.TIMED_OUT, merged.snapshot().lifecycle());
        assertEquals(FIRST, live.plan().primary().id());
    }

    @Test void replacedNotificationRejectsAnActionFromThePreviouslyPaintedRevision() {
        var invoked = new ArrayList<String>();
        var handle = live.publish(owner, owner.scope(), notice("First", invoked));
        frame(0);
        var painted = live.plan().primary();
        assertSame(handle, live.publish(owner, owner.scope(), notice("Second", invoked)));
        assertEquals(painted.instance(), handle.snapshot().instance());
        assertFalse(live.isInputCurrent(painted));
        assertTrue(live.isInputCurrent(handle.snapshot()));
        assertFalse(live.dispatch(painted, OPEN_ACTION));
        assertTrue(live.dispatch(handle.snapshot(), OPEN_ACTION));
        frame(1);
        assertEquals(List.of("Second"), invoked);
    }

    @Test void replacingANotificationCancelsActionsAlreadyQueuedForItsPreviousCallback() {
        var invoked = new ArrayList<String>();
        live.publish(owner, owner.scope(), notice("First", invoked));
        frame(0);
        assertTrue(live.dispatch(live.plan().primary(), OPEN_ACTION));
        live.publish(owner, owner.scope(), notice("Second", invoked));
        frame(1);
        assertTrue(invoked.isEmpty());
    }

    @Test void refreshingProgressKeepsTheExistingSeekGestureValid() {
        var sought = new ArrayList<Double>();
        var handle = live.publish(owner, owner.scope(), new ActivityService.Request(FIRST, seekable(.2),
                Priority.NORMAL, Duration.ZERO, true, action -> sought.add(action.fraction())));
        var pressed = handle.snapshot();
        handle.update(seekable(.4));
        frame(0);
        assertTrue(handle.snapshot().revision() > pressed.revision());
        assertTrue(live.dispatch(pressed, new Action(ActivityService.SEEK, .6)));
        frame(1);
        assertEquals(List.of(.6), sought);
    }

    @Test void stableMinimumDwellAndFairnessKeepLowPriorityEligible() {
        live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL));
        frame(0);
        live.publish(owner, owner.scope(), request(SECOND, Priority.HIGH));
        frame(100);
        assertEquals(FIRST, live.plan().primary().id());
        frame(700);
        assertEquals(SECOND, live.plan().primary().id());
        frame(60_000);
        assertEquals(FIRST, live.plan().primary().id());
    }

    @Test void appliesBatchBeforeObserversAndRejectsForeignHandle() {
        var scope = owner.scope();
        var a = live.publish(owner, scope, request(FIRST, Priority.NORMAL));
        var b = live.publish(owner, scope, request(SECOND, Priority.LOW));
        assertThrows(SecurityException.class, () -> live.updateAll(other, other.scope(), Map.of(a, content("Foreign"))));
        var observed = new ArrayList<String>();
        a.observe(value -> observed.add(b.snapshot().content().title().value()));
        var update = live.updateAll(owner, scope, Map.of(a, content("New first"), b, content("New second")));
        assertEquals(TaskHandle.Status.PENDING, update.status());
        frame(10);
        assertEquals(TaskHandle.Status.SUCCEEDED, update.status());
        assertEquals("New first", a.snapshot().content().title().value());
        assertEquals(List.of("New second"), observed);
    }

    @Test void staleBatchLeavesOtherMembersAtTheirPriorRevision() {
        var a = live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL));
        var b = live.publish(owner, owner.scope(), request(SECOND, Priority.LOW));
        var update = live.updateAll(owner, owner.scope(), Map.of(a, content("New first"), b, content("New second")));
        b.cancel();
        frame(10);
        assertEquals(TaskHandle.Status.FAILED, update.status());
        assertEquals(1, a.snapshot().revision());
    }

    @Test void ownerPresentationHintsYieldToUserCollapse() {
        var handle = live.publish(owner, owner.scope(), request(FIRST, Priority.NORMAL));
        handle.requestPresentation(SurfacePresentation.FLOATING);
        frame(0);
        assertEquals(SurfacePresentation.FLOATING, live.plan().requested());
        assertTrue(live.collapse());
        frame(10);
        assertEquals(SurfacePresentation.COMPACT, live.plan().requested());
        assertThrows(IllegalArgumentException.class, () -> handle.requestPresentation(SurfacePresentation.SUSPENDED));
    }

    @Test void spaceReservationUsesItsScopeAndReleasesImmediately() {
        var scope = owner.scope().openChild(ScopeKind.CLIENT_SESSION, FIRST);
        var initial = new SurfacePlacement.Rect(2, 2, 20, 15);
        var reservation = live.reserve(owner, scope, FIRST, initial);
        assertEquals(List.of(initial), live.occupied());
        var moved = new SurfacePlacement.Rect(5, 8, 20, 15);
        reservation.update(moved);
        assertEquals(List.of(moved), live.occupied());
        scope.close();
        assertTrue(live.occupied().isEmpty());
        assertThrows(IllegalStateException.class, () -> reservation.update(initial));
    }

    private void frame(long milliseconds) {
        clock.set(Duration.ofMillis(milliseconds).toNanos());
        host.runFrame(phase -> {
            if (phase == UIPhase.MEASURE) live.advance(false);
        });
        if (live.plan().primary() != null) live.markPainted(live.plan().primary());
    }

    private static ActivityService.Request request(UiId id, Priority priority) {
        return new ActivityService.Request(id, content("Initial"), priority, Duration.ZERO, true, null);
    }

    private static Content content(String title) { return Content.label(Text.literal(title)); }
    private static NotificationService.Request notice(String title, List<String> invoked) {
        var content = new Content(Text.literal(title), Text.literal(""), null, -1, false,
                List.of(new Control(OPEN, Text.literal(title))), List.of());
        return new NotificationService.Request(FIRST, content, Priority.NORMAL, Duration.ofSeconds(1),
                SECOND, NotificationService.Merge.REPLACE, action -> invoked.add(title));
    }
    private static Content seekable(double progress) {
        return new Content(Text.literal("Initial"), Text.literal(""), null, progress, true, List.of(), List.of());
    }
    private static Content progress(double value) {
        return new Content(Text.literal("Initial"), Text.literal(""), null, value, false, List.of(), List.of());
    }
}
