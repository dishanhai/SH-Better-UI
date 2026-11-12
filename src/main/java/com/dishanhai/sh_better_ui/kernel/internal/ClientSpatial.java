// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.spatial.SpatialRenderer;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.ThemeService;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialUI;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Settings;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Theme;
import com.dishanhai.sh_better_ui.spatial.internal.Scene;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleConsumer;

final class ClientSpatial implements SpatialUI {
    private final KernelHost host;
    private final ThemeManager themes;
    private final Map<Scene, Binding> scenes = new IdentityHashMap<>();
    private final Map<MotionKey, Track> tracks = new HashMap<>();
    private final Set<Track> active = new LinkedHashSet<>();

    ClientSpatial(KernelHost host, ThemeManager themes) {
        this.host = host;
        this.themes = themes;
    }

    @Override public SpatialScene mount(ModuleContext module, ScopeHandle scope) {
        var access = host.moduleAccess(module);
        access.assertSideEffectAllowed();
        var target = access.write(scope);
        target.assertOwner();
        if (target.kind() != ScopeKind.UI_TREE && target.kind() != ScopeKind.COMPONENT
                && target.kind() != ScopeKind.MODULE && target.kind() != ScopeKind.CLIENT_SESSION) {
            throw new IllegalArgumentException("Spatial scene requires a module, session, tree or component scope");
        }
        var scene = new Scene(host::assertOwner, this::release);
        var theme = switch (target.kind()) {
            case UI_TREE, COMPONENT -> module.engine().require(UIApis.THEME).scope(module, target);
            default -> null;
        };
        var binding = new Binding(target, theme);
        binding.update(scene, ClientUiSettings.current().spatial());
        host.onScopeClose(target, scene::close);
        scenes.put(scene, binding);
        return scene;
    }

    Scene forRender(SpatialScene scene, ScopeHandle scope) {
        if (!(scene instanceof Scene nativeScene) || scenes.get(nativeScene) == null
                || scenes.get(nativeScene).scope != scope) {
            throw new IllegalArgumentException("Spatial scene must belong to the active render scope");
        }
        return nativeScene;
    }

    @Override public Support capability(Capability capability) {
        var settings = ClientUiSettings.current().spatial();
        return switch (capability) {
            case SDF_SHADOWS, INNER_SHADOWS -> !settings.enabled() || !settings.shadows() ? Support.DISABLED
                    : SpatialRenderer.ready() ? Support.SUPPORTED : Support.DEGRADED;
            case DYNAMIC_LIGHTING -> settings.enabled() && settings.lighting() ? Support.SUPPORTED : Support.DISABLED;
            case SPATIAL_ANIMATION -> settings.enabled() ? Support.SUPPORTED : Support.DISABLED;
            case BACKGROUND_BLUR -> !settings.enabled() || !settings.blur()
                    || !ClientUiSettings.current().widgetBackdropBlur() ? Support.DISABLED
                    : SpatialRenderer.backdropReady() ? Support.SUPPORTED : Support.DEGRADED;
            case ARBITRARY_PATH_SHADOWS -> !settings.enabled() || !settings.shadows() ? Support.DISABLED
                    : SpatialRenderer.arbitraryPathReady() ? Support.SUPPORTED : Support.DEGRADED;
            case GPU_TIMING -> settings.enabled() && settings.profiling()
                    ? SpatialRenderer.gpuTimingSupport() : Support.DISABLED;
        };
    }

    void resolveStyles() {
        if (scenes.isEmpty()) return;
        var settings = ClientUiSettings.current().spatial();
        scenes.forEach((scene, binding) -> binding.update(scene, settings));
    }

    @Override public Motion motion(SpatialScene scene, SpatialScene.Node node, UiId property, float initial,
                                   SpringSpec spring, DoubleConsumer apply) {
        host.assertOwner();
        if (!(scene instanceof Scene nativeScene) || !scenes.containsKey(nativeScene)) {
            throw new IllegalArgumentException("Spatial scene is not mounted");
        }
        if (node != null && scene.find(node.id()).orElse(null) != node) {
            throw new IllegalArgumentException("Spatial animation node belongs to another scene");
        }
        validateValue(initial);
        var key = new MotionKey(nativeScene, node, Objects.requireNonNull(property));
        var track = new Track(key, initial, Objects.requireNonNull(spring), Objects.requireNonNull(apply));
        if (tracks.putIfAbsent(key, track) != null) throw new IllegalStateException("Spatial property is already bound");
        if (node != null) track.unlisten = nativeScene.onNodeClose(node, track::close);
        return track;
    }

    void update() {
        if (active.isEmpty()) return;
        long time = now();
        var settings = ClientUiSettings.current();
        boolean reduce = settings.reducedMotion() || !settings.spatial().enabled();
        for (var track : List.copyOf(active)) {
            if (track.closed || track.paused) continue;
            try {
                track.sample(time, reduce);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                host.report("Spatial animation " + track.key.property, failure);
                track.close();
            }
        }
    }

    private void release(Scene scene) {
        scenes.remove(scene);
        for (var track : List.copyOf(tracks.values())) {
            if (track.key.scene == scene) track.close();
        }
    }

    private long now() { return host.nowNanos() / 1_000_000L; }

    private static void validateValue(float value) {
        if (!Float.isFinite(value) || Math.abs(value) > 1_000_000) {
            throw new IllegalArgumentException("Spatial animation value must be in [-1000000, 1000000]");
        }
    }

    private record MotionKey(Scene scene, SpatialScene.Node node, UiId property) {}

    private final class Binding {
        private final ScopeHandle scope;
        private final ThemeService.Scope theme;
        private Settings settings;
        private int surface, shadow;

        Binding(ScopeHandle scope, ThemeService.Scope theme) {
            this.scope = scope;
            this.theme = theme;
        }

        void update(Scene scene, Settings next) {
            int surface = (theme == null ? themes.global().resolve(ThemeTokens.SURFACE)
                    : theme.resolve(ThemeTokens.SURFACE)).argb();
            int shadow = (theme == null ? themes.global().resolve(ThemeTokens.SHADOW)
                    : theme.resolve(ThemeTokens.SHADOW)).color().argb();
            if (settings == null || this.surface != surface || this.shadow != shadow) {
                // Existing UI shadow tokens include alpha. Preserve their hue and keep useful contrast
                // after applying the per-layer elevation opacities.
                int color = shadow & 0xffffff | Math.min(255, (shadow >>> 24) * 3) << 24;
                scene.baseTheme(Theme.palette(surface, color));
                this.surface = surface;
                this.shadow = shadow;
            }
            if (!next.equals(settings)) {
                scene.globalSettings(next);
                settings = next;
            }
        }
    }

    private final class Track implements Motion {
        private final MotionKey key;
        private final InterruptibleAnimation animation;
        private DoubleConsumer apply;
        private float value;
        private long pausedAt, pausedMs;
        private boolean paused, closed;
        private Runnable unlisten = () -> {};

        Track(MotionKey key, float initial, SpringSpec spring, DoubleConsumer apply) {
            this.key = key;
            this.apply = apply;
            this.value = initial;
            animation = new InterruptibleAnimation(new Spring.Preset(spring.response(), spring.damping(),
                    .0015f, .02f), initial);
        }

        @Override public long play(float target, MotionPolicy policy, int priority) {
            check();
            Objects.requireNonNull(policy);
            validateValue(target);
            if (policy == MotionPolicy.REPLACE) return tween(target, 90, true, priority);
            long time = effective(now());
            // Retarget preserves the current velocity. A short blend replaces the velocity without jumping.
            long revision = animation.transitionTo(target, time, switch (policy) {
                case REPLACE, BLEND -> InterruptibleAnimation.Policy.BLEND;
                case RETARGET -> InterruptibleAnimation.Policy.RETARGET;
                case QUEUE -> InterruptibleAnimation.Policy.QUEUE;
                case IGNORE -> InterruptibleAnimation.Policy.IGNORE;
            }, priority);
            if (revision != 0 && !paused) active.add(this);
            return revision;
        }

        @Override public long tween(float target, long durationMs, boolean eased, int priority) {
            check();
            validateValue(target);
            if (durationMs < 1 || durationMs > 60000) {
                throw new IllegalArgumentException("Invalid spatial tween");
            }
            long revision = animation.blendTo(target, effective(now()), priority, durationMs, eased
                    ? InterruptibleAnimation.BlendCurve.SMOOTHSTEP : InterruptibleAnimation.BlendCurve.LINEAR);
            if (revision != 0 && !paused) active.add(this);
            return revision;
        }

        @Override public void pause() {
            check();
            if (paused) return;
            pausedAt = now();
            paused = true;
            active.remove(this);
        }

        @Override public void resume() {
            check();
            if (!paused) return;
            pausedMs += now() - pausedAt;
            paused = false;
            active.add(this);
        }

        @Override public void cancel() {
            check();
            animation.stop(effective(now()));
            if (!paused) active.add(this);
        }

        @Override public boolean takeCompletion(long revision) { check(); return animation.takeCompletion(revision); }
        @Override public float value() { check(); return value; }

        private long effective(long time) { return (paused ? pausedAt : time) - pausedMs; }

        private void sample(long time, boolean reduce) {
            var next = animation.sample(effective(time), reduce);
            if (!animation.running()) active.remove(this);
            if (Float.compare(value, next) != 0) {
                value = next;
                apply.accept(next);
            }
        }

        private void check() {
            host.assertOwner();
            if (closed) throw new IllegalStateException("Spatial animation is closed");
        }

        @Override public void close() {
            host.assertOwner();
            if (closed) return;
            closed = true;
            active.remove(this);
            tracks.remove(key, this);
            unlisten.run();
            unlisten = () -> {};
            animation.close();
            apply = null;
        }
    }
}
