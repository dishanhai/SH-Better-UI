// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseService;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseSpec;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.kernel.api.dock.CollapseState.*;
import static org.junit.jupiter.api.Assertions.*;

class CollapseServiceTest {
    @Test
    void defaultTransitionsUseTheirDurationsAndEmitOnlyStateChanges() {
        try (var fixture = fixture()) {
            var handle = fixture.publication.current().require(UIApis.COLLAPSE)
                    .create(fixture.module, fixture.tree(), new CollapseSpec());
            var changes = new ArrayList<CollapseState>();
            handle.onStateChanged(changes::add);
            assertEquals(COLLAPSED, handle.state());
            handle.expand();
            handle.expand();
            assertEquals(EXPANDING, handle.state());
            frame(fixture, 120);
            assertEquals(.5f, handle.progress());
            frame(fixture, 240);
            assertEquals(EXPANDED, handle.state());
            assertEquals(1f, handle.progress());
            handle.collapse();
            frame(fixture, 340);
            assertEquals(.5f, handle.progress());
            frame(fixture, 440);
            assertEquals(COLLAPSED, handle.state());
            assertEquals(0f, handle.progress());
            assertEquals(List.of(EXPANDING, EXPANDED, COLLAPSING, COLLAPSED), changes);
        }
    }

    @Test
    void springRetargetKeepsProgressAndVelocityAtTheInterruption() {
        try (var fixture = fixture()) {
            var spec = new CollapseSpec(CollapseSpec.Direction.HORIZONTAL, CollapseSpec.Curve.SPRING,
                    240, 200, false, false);
            var handle = fixture.publication.current().require(UIApis.COLLAPSE)
                    .create(fixture.module, fixture.tree(), spec);
            handle.expand();
            frame(fixture, 35);
            var before = handle.progress();
            assertTrue(before > 0f && before < 1f);
            handle.toggle();
            assertFalse(handle.targetExpanded());
            assertEquals(COLLAPSING, handle.state());
            assertEquals(before, handle.progress());
            frame(fixture, 36);
            assertTrue(handle.progress() > before, "the existing outward velocity survives the retarget");
            frame(fixture, 236);
            frame(fixture, 436);
            assertEquals(COLLAPSED, handle.state());
            assertEquals(0f, handle.progress());
        }
    }

    @Test
    void linearMotionStartsAtConstantSpeedAndCarriesItIntoTheReversal() {
        try (var fixture = fixture()) {
            var spec = new CollapseSpec(CollapseSpec.Direction.HORIZONTAL, CollapseSpec.Curve.LINEAR,
                    240, 200, false, false);
            var handle = fixture.publication.current().require(UIApis.COLLAPSE)
                    .create(fixture.module, fixture.tree(), spec);
            handle.expand();
            frame(fixture, 60);
            assertEquals(.25f, handle.progress());
            handle.collapse();
            assertEquals(.25f, handle.progress());
            frame(fixture, 61);
            assertTrue(handle.progress() > .25f);
            frame(fixture, 261);
            frame(fixture, 461);
            assertEquals(COLLAPSED, handle.state());
            assertEquals(0f, handle.progress());
        }
    }

    @Test
    void controlledProgressClampsAndFollowsItsSeparateTarget() {
        try (var fixture = fixture()) {
            var spec = new CollapseSpec(CollapseSpec.Direction.VERTICAL, CollapseSpec.Curve.LINEAR,
                    240, 200, false, true);
            var handle = fixture.publication.current().require(UIApis.COLLAPSE)
                    .create(fixture.module, fixture.tree(), spec);
            handle.setExpanded(true);
            frame(fixture, 900);
            assertEquals(EXPANDING, handle.state());
            assertEquals(0f, handle.progress());
            handle.setProgress(.6f);
            assertEquals(.6f, handle.progress());
            handle.setLocked(true);
            handle.toggle();
            handle.setProgress(.9f);
            assertTrue(handle.targetExpanded());
            assertEquals(.6f, handle.progress());
            handle.setLocked(false);
            handle.setProgress(2f);
            assertEquals(1f, handle.progress());
            assertEquals(EXPANDED, handle.state());
            handle.collapse();
            handle.setProgress(-1f);
            assertEquals(COLLAPSED, handle.state());
            assertEquals(0f, handle.progress());
            assertThrows(IllegalArgumentException.class, () -> handle.setProgress(Float.NaN));
        }
    }

    @Test
    void handlesOnTheSameScopeStayIndependentAndCloseWithoutEvents() {
        try (var fixture = fixture()) {
            var service = fixture.publication.current().require(UIApis.COLLAPSE);
            var scope = fixture.tree();
            var first = service.create(fixture.module, scope, new CollapseSpec());
            var second = service.create(fixture.module, scope, new CollapseSpec());
            var firstEvents = new ArrayList<CollapseState>();
            var secondEvents = new ArrayList<CollapseState>();
            var firstListener = first.onStateChanged(firstEvents::add);
            var secondListener = second.onStateChanged(secondEvents::add);
            first.expand();
            second.expand();
            first.close();
            assertFalse(firstListener.isActive());
            assertTrue(secondListener.isActive());
            frame(fixture, 120);
            assertEquals(.5f, second.progress());
            scope.close();
            frame(fixture, 240);
            assertFalse(secondListener.isActive());
            assertEquals(List.of(EXPANDING), firstEvents);
            assertEquals(List.of(EXPANDING), secondEvents);
            assertThrows(IllegalStateException.class, second::collapse);
        }
    }

    @Test
    void reducedMotionFinishesAnInProgressTransition() {
        var original = ClientUiSettings.current();
        try (var fixture = fixture()) {
            var handle = fixture.publication.current().require(UIApis.COLLAPSE)
                    .create(fixture.module, fixture.tree(), new CollapseSpec());
            handle.expand();
            frame(fixture, 100);
            assertEquals(EXPANDING, handle.state());
            ClientUiSettings.update(original.withDisplay(original.highContrast(), true, original.widgetBackdropBlur()));
            frame(fixture, 101);
            assertEquals(EXPANDED, handle.state());
            assertEquals(1f, handle.progress());
        } finally {
            ClientUiSettings.update(original);
        }
    }

    @Test
    void missingAnimationClaimFailsWithoutCreatingAnActiveHandle() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var service = fixture.publication.current().require(UIApis.COLLAPSE);
            var scope = fixture.tree();
            assertThrows(SecurityException.class, () -> service.create(fixture.module, scope, new CollapseSpec()));
            assertTrue(scope.isOpen());
        }
    }

    private static ClientServiceFixture fixture() {
        return new ClientServiceFixture(Set.of(CollapseService.PROGRESS_CLAIM), Set.of());
    }

    private static void frame(ClientServiceFixture fixture, long millis) {
        fixture.time.set(millis * 1_000_000);
        fixture.client.runFrame();
    }
}
