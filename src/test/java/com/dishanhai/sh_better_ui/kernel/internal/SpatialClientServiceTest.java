// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityStatus;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene.Spec;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Quality;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Settings;
import com.dishanhai.sh_better_ui.spatial.SpatialUI;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpatialClientServiceTest {
    @Test
    void resolvesThroughTheKernelAndClosesWithItsScope() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var engine = fixture.publication.current();
            assertEquals(CapabilityStatus.AVAILABLE, engine.capabilities().status(UIApis.SPATIAL_SCENE));
            var scope = fixture.tree();
            var api = engine.require(SpatialUI.KEY);
            var scene = api.mount(fixture.module, scope);
            var node = scene.create(spec());
            var track = api.motion(scene, node, id("height"), 0, SpatialUI.SpringSpec.INTERACTION, node::elevation);
            assertThrows(IllegalStateException.class,
                    () -> api.motion(scene, node, id("height"), 0, SpatialUI.SpringSpec.INTERACTION, node::elevation));
            node.close();
            assertThrows(IllegalStateException.class, track::value);
            var replacement = scene.create(spec());
            scope.close();
            assertFalse(replacement.isOpen());
            assertThrows(IllegalStateException.class, () -> scene.find(replacement.id()));
        }
    }

    @Test
    void retargetAndPausePreserveTheDisplayedValueAndReducedMotionSettles() {
        var original = ClientUiSettings.current();
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            ClientUiSettings.update(original.withDisplay(false, false, true));
            var api = fixture.publication.current().require(SpatialUI.KEY);
            var scene = api.mount(fixture.module, fixture.tree());
            var node = scene.create(spec());
            var track = api.motion(scene, node, id("height"), 0, SpatialUI.SpringSpec.INTERACTION, node::elevation);
            track.play(8, SpatialUI.MotionPolicy.RETARGET, 0);
            fixture.time.set(40_000_000);
            fixture.client.runFrame();
            float visible = track.value();
            assertTrue(visible > 0 && visible < 8);
            assertEquals(visible, node.elevation());
            long revision = track.play(2, SpatialUI.MotionPolicy.RETARGET, 0);
            fixture.client.runFrame();
            assertEquals(visible, track.value());
            track.pause();
            fixture.time.addAndGet(100_000_000);
            fixture.client.runFrame();
            assertEquals(visible, track.value());
            track.resume();
            ClientUiSettings.update(original.withDisplay(false, true, true));
            fixture.client.runFrame();
            assertEquals(2, track.value());
            assertTrue(track.takeCompletion(revision));
        } finally {
            ClientUiSettings.update(original);
        }
    }

    @Test
    void hotSettingsOverrideSceneQualityAndKeepOtherClientPreferences() {
        var original = ClientUiSettings.current();
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var api = fixture.publication.current().require(SpatialUI.KEY);
            var scene = api.mount(fixture.module, fixture.tree());
            scene.settings(new Settings(true, true, false, true, Quality.ULTRA));
            var settings = new Settings(true, false, true, true, Quality.LOW);
            ClientUiSettings.update(original.withSpatial(settings).withDisplay(false, true, true));
            fixture.client.runFrame();
            assertEquals(settings, ClientUiSettings.current().spatial());
            assertEquals(new Settings(true, false, false, true, Quality.LOW), scene.settings());
        } finally {
            ClientUiSettings.update(original);
        }
    }

    @Test
    void pathShadowCapabilityHonorsGlobalSettings() {
        var original = ClientUiSettings.current();
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var api = fixture.publication.current().require(SpatialUI.KEY);
            var capability = SpatialUI.Capability.ARBITRARY_PATH_SHADOWS;
            ClientUiSettings.update(original.withSpatial(new Settings(true, false, true, true, Quality.HIGH)));
            assertEquals(SpatialUI.Support.DISABLED, api.capability(capability));
            ClientUiSettings.update(original.withSpatial(new Settings(false, true, true, true, Quality.HIGH)));
            assertEquals(SpatialUI.Support.DISABLED, api.capability(capability));
        } finally {
            ClientUiSettings.update(original);
        }
    }

    private static Spec spec() {
        return new Spec(id("node"), null, Shape.rounded(20, 10, 3), Transform.IDENTITY, 0, 0, true, false);
    }

    private static UiId id(String path) { return new UiId("example", path); }
}
