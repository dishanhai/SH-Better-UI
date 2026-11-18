// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.live.*;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

final class NativeLiveActivityTest {
    private final AtomicLong now = new AtomicLong();
    private KernelHost kernel;
    private ClientLiveActivities nativeHost;
    private ModuleContext owner, other;
    private static final UiId ID = UiId.parse("owner:task"), ACTION = UiId.parse("owner:open");

    @BeforeEach void start() {
        kernel = new KernelHost(ModuleDescriptor.Platform.NEOFORGE_26_1_2, new EnginePublication(), now::get,
                Runnable::run, SchedulerBudget.DEFAULT);
        nativeHost = new ClientLiveActivities(kernel, LiveSurfaceSettings.DEFAULT);
        kernel.start(List.of(candidate("owner", context -> owner = context), candidate("other", context -> other = context)));
    }
    @AfterEach void stop() { kernel.close(); }

    private ModuleDiscovery.Candidate candidate(String name, java.util.function.Consumer<ModuleContext> started) {
        var descriptor = ModuleDescriptor.builder().id(new UiId(name, "module")).version("1.0.0")
                .platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2)).build();
        return new ModuleDiscovery.Candidate(name, "native-test", new UIModule() {
            public ModuleDescriptor descriptor() { return descriptor; }
            public void register(ModuleContext context) {}
            public void start(ModuleContext context) { started.accept(context); }
            public void stop(ModuleContext context) {}
        }, Set.of(), false);
    }

    private ActivityService.Request request(Duration duration, java.util.function.Consumer<ActivityService.Action> action) {
        var content = new ActivityService.Content(ActivityService.Text.literal("Work"), ActivityService.Text.literal("Details"),
                null, .25, true, List.of(new ActivityService.Control(ACTION, ActivityService.Text.literal("Open"))), List.of());
        return new ActivityService.Request(ID, content, ActivityService.Priority.NORMAL, duration, true, action);
    }
    private void frame(long millis) {
        now.set(Duration.ofMillis(millis).toNanos());
        kernel.runFrame(phase -> { if (phase == UIPhase.MEASURE) nativeHost.runtime().advance(false, true); });
    }

    @Test void allServicesShareOneArbiterAndOnlySupportedSingleSurfacePolicy() {
        assertSame(nativeHost.activities(), nativeHost.notifications());
        assertSame(nativeHost.activities(), nativeHost.surfaces());
        assertFalse(nativeHost.runtime().policy().split());
    }
    @Test void minimalPublicationAndDisabledCompactPolicyUseMinimalGeometry() {
        var handle = nativeHost.activities().publish(owner, owner.scope(), request(Duration.ZERO, null));
        handle.requestPresentation(SurfacePresentation.MINIMAL);
        frame(1);
        var requested = nativeHost.runtime().plan().requested();
        assertEquals(SurfacePresentation.MINIMAL, requested);
        var minimal = ClientLiveActivities.candidate(requested, SurfaceTokens.DARK, LiveSurfaceSettings.DEFAULT.layout(), 640, 480);
        assertEquals(SurfacePresentation.MINIMAL, minimal.presentation());
        assertEquals(14, minimal.geometry().height());
        assertEquals(14, minimal.geometry().width());
        var defaults = LiveSurfaceSettings.DEFAULT;
        var layout = new LiveSurfaceSettings.Layout(true, LiveSurfaceSettings.Anchor.TOP_CENTER, 1, 128, false, true, false);
        nativeHost.settings(new LiveSurfaceSettings(true, false, true, layout, defaults.motion(), defaults.theme(),
                defaults.notifications(), defaults.input(), defaults.performance()));
        handle.requestPresentation(SurfacePresentation.COMPACT); frame(2);
        assertEquals(SurfacePresentation.MINIMAL, nativeHost.runtime().plan().requested());
        assertEquals(SurfacePresentation.MINIMAL, ClientLiveActivities.candidate(nativeHost.runtime().plan().requested(),
                SurfaceTokens.DARK, layout, 640, 480).presentation());
    }

    @Test void rejectsForeignScopeNamespaceAndUngrantableCriticalPublication() {
        assertThrows(SecurityException.class, () -> nativeHost.activities().publish(owner, other.scope(), request(Duration.ZERO, null)));
        var wrong = new ActivityService.Request(UiId.parse("other:task"), ActivityService.Content.label(ActivityService.Text.literal("Work")),
                ActivityService.Priority.NORMAL, Duration.ZERO, true, null);
        assertThrows(SecurityException.class, () -> nativeHost.activities().publish(owner, owner.scope(), wrong));
        var critical = new ActivityService.Request(ID, wrong.initial(), ActivityService.Priority.CRITICAL, Duration.ZERO, true, null);
        assertThrows(SecurityException.class, () -> nativeHost.activities().publish(owner, owner.scope(), critical));
    }
    @Test void pauseResumeTimeoutAndScopeCloseCannotLeaveVisibleActivity() {
        var scope = owner.scope().openChild(ScopeKind.SCREEN, UiId.parse("owner:screen"));
        var handle = nativeHost.activities().publish(owner, scope, request(Duration.ofMillis(100), null));
        frame(1); assertNotNull(nativeHost.runtime().plan().primary());
        handle.pause(); frame(2); assertNull(nativeHost.runtime().plan().primary());
        handle.resume(); frame(3); assertNotNull(nativeHost.runtime().plan().primary());
        frame(101); assertEquals(ActivityService.Lifecycle.TIMED_OUT, handle.snapshot().lifecycle());
        assertNull(nativeHost.runtime().plan().primary());
        var again = nativeHost.activities().publish(owner, scope, request(Duration.ZERO, null));
        frame(102); scope.close(); frame(103);
        assertTrue(again.snapshot().lifecycle().terminal());
        assertNull(nativeHost.runtime().plan().primary());
    }
    @Test void oldSnapshotCannotDispatchAfterScopeClosedOrIdRepublished() {
        var actions = new ArrayList<ActivityService.Action>();
        var first = nativeHost.activities().publish(owner, owner.scope(), request(Duration.ZERO, actions::add));
        frame(1); var stale = first.snapshot(); first.cancel();
        nativeHost.activities().publish(owner, owner.scope(), request(Duration.ZERO, actions::add)); frame(2);
        assertFalse(nativeHost.runtime().dispatch(stale, new ActivityService.Action(ACTION, -1)));
        assertTrue(actions.isEmpty());
    }
    @Test void dismissHidesButDoesNotCancelOwnerActivityAndActionsUseScheduler() {
        var actions = new ArrayList<ActivityService.Action>();
        var handle = nativeHost.activities().publish(owner, owner.scope(), request(Duration.ZERO, actions::add));
        frame(1);
        assertTrue(nativeHost.runtime().dispatch(handle.snapshot(), new ActivityService.Action(ActivityService.SEEK, .7)));
        assertTrue(actions.isEmpty()); frame(2); assertEquals(1, actions.size());
        assertTrue(nativeHost.runtime().dismiss(handle.snapshot())); frame(3);
        assertEquals(ActivityService.Lifecycle.RUNNING, handle.snapshot().lifecycle());
        assertNull(nativeHost.runtime().plan().primary());
        handle.showPresentation(); frame(4); assertNotNull(nativeHost.runtime().plan().primary());
    }
}
