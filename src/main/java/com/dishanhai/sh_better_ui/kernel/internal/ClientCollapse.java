// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation.BlendCurve;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation.Policy;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.AnimationService;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseHandle;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseListener;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseService;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseSpec;
import com.dishanhai.sh_better_ui.kernel.api.dock.CollapseState;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.dishanhai.sh_better_ui.kernel.api.dock.CollapseState.*;

final class ClientCollapse implements CollapseService {
    private final KernelHost host;
    private final AnimationService animation;
    private final Set<Controller> moving = new LinkedHashSet<>();
    private long nextController;

    ClientCollapse(KernelHost host, AnimationService animation) {
        this.host = host;
        this.animation = animation;
    }

    @Override
    public CollapseHandle create(ModuleContext module, ScopeHandle scope, CollapseSpec spec) {
        Objects.requireNonNull(spec, "spec");
        var target = host.ownedNode(module, scope);
        var access = host.moduleAccess(module);
        access.requireClaim(PROGRESS_CLAIM);
        var id = new UiId(target.owner().namespace(), "collapse/track/" + ++nextController);
        var lifetime = target.openChild(ScopeKind.COMPONENT, id);
        try {
            var track = animation.bind(module, lifetime, PROGRESS_CLAIM.id(), Spring.STAGGER,
                    spec.initiallyExpanded() ? 1f : 0f, () -> ClientUiSettings.current().reducedMotion(), ignored -> {});
            var controller = new Controller(lifetime, access, spec, track);
            lifetime.track(controller::release);
            return controller;
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            lifetime.close();
            throw failure;
        }
    }

    /** Consumes current track values and completions after the kernel animation phase samples them. */
    void update() {
        if (moving.isEmpty()) return;
        for (var controller : List.copyOf(moving)) {
            controller.sample();
        }
    }

    private final class Controller implements CollapseHandle {
        private final ScopeHandle lifetime;
        private final ModuleAccess access;
        private final CollapseSpec spec;
        private final AnimationService.Track track;
        private final Set<Listener> listeners = new LinkedHashSet<>();
        private final ArrayDeque<CollapseState> changes = new ArrayDeque<>();
        private CollapseState state;
        private float progress;
        private boolean expanded;
        private boolean locked;
        private boolean notifying;
        private long revision;

        Controller(ScopeHandle lifetime, ModuleAccess access, CollapseSpec spec, AnimationService.Track track) {
            this.lifetime = lifetime;
            this.access = access;
            this.spec = spec;
            this.track = track;
            expanded = spec.initiallyExpanded();
            progress = expanded ? 1f : 0f;
            state = expanded ? EXPANDED : COLLAPSED;
        }

        @Override public void expand() { setExpanded(true); }
        @Override public void collapse() { setExpanded(false); }
        @Override public void toggle() { setExpanded(!expanded); }
        @Override public CollapseState state() { return state; }
        @Override public float progress() { return progress; }
        @Override public boolean targetExpanded() { return expanded; }

        @Override
        public void setExpanded(boolean value) {
            checkActive();
            if (locked || value == expanded) return;
            expanded = value;
            if (spec.controlled()) {
                updateControlledState();
                return;
            }

            float endpoint = expanded ? 1f : 0f;
            long duration = expanded ? spec.expandMillis() : spec.collapseMillis();
            if (duration == 0 || ClientUiSettings.current().reducedMotion()) {
                track.play(endpoint, Policy.IMMEDIATE, 0);
                finish(endpoint);
                return;
            }
            boolean interrupted = revision != 0 && !track.takeCompletion(revision);
            if (interrupted || spec.curve() == CollapseSpec.Curve.SPRING) {
                revision = track.play(endpoint, Policy.RETARGET, 0);
            } else {
                var curve = spec.curve() == CollapseSpec.Curve.LINEAR ? BlendCurve.LINEAR : BlendCurve.SMOOTHSTEP;
                revision = track.blend(endpoint, 0, duration, curve);
            }
            if (track.takeCompletion(revision)) {
                finish(endpoint);
                return;
            }
            moving.add(this);
            setState(expanded ? EXPANDING : COLLAPSING);
        }

        @Override
        public void setProgress(float value) {
            checkActive();
            if (locked) return;
            if (!spec.controlled()) throw new IllegalStateException("Collapse progress is managed by the animation service");
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Collapse progress must be finite");
            progress = clamp(value);
            updateControlledState();
        }

        @Override
        public void setLocked(boolean value) {
            checkActive();
            locked = value;
        }

        @Override
        public Subscription onStateChanged(CollapseListener listener) {
            checkActive();
            var subscription = new Listener(Objects.requireNonNull(listener, "listener"));
            listeners.add(subscription);
            return subscription;
        }

        @Override public void close() { lifetime.close(); }

        private void sample() {
            if (!lifetime.isOpen() || revision == 0) return;
            if (!track.isActive()) {
                close();
                return;
            }
            if (track.takeCompletion(revision)) {
                finish(expanded ? 1f : 0f);
            } else {
                progress = clamp(track.value());
            }
        }

        private void finish(float endpoint) {
            moving.remove(this);
            revision = 0;
            progress = endpoint;
            setState(expanded ? EXPANDED : COLLAPSED);
        }

        private void updateControlledState() {
            setState(expanded
                    ? (progress == 1f ? EXPANDED : EXPANDING)
                    : (progress == 0f ? COLLAPSED : COLLAPSING));
        }

        private void setState(CollapseState next) {
            if (next == state) return;
            state = next;
            if (listeners.isEmpty()) return;
            changes.addLast(next);
            if (notifying) return;
            notifying = true;
            try {
                while (!changes.isEmpty()) {
                    var change = changes.removeFirst();
                    for (var listener : List.copyOf(listeners)) {
                        if (!listener.isActive()) continue;
                        try {
                            listener.callback.onStateChanged(change);
                        } catch (RuntimeException | LinkageError | AssertionError failure) {
                            host.report("Collapse state listener at " + lifetime.id(), failure);
                        }
                    }
                }
            } finally {
                notifying = false;
            }
        }

        private void checkActive() {
            host.assertOwner();
            access.assertSideEffectAllowed();
            if (!lifetime.isOpen() || !track.isActive()) throw new IllegalStateException("Collapse controller was released");
        }

        private void release() {
            moving.remove(this);
            changes.clear();
            listeners.forEach(listener -> listener.callback = null);
            listeners.clear();
        }

        private final class Listener implements Subscription {
            private volatile CollapseListener callback;

            Listener(CollapseListener callback) {
                this.callback = callback;
            }

            @Override public boolean isActive() { return callback != null && lifetime.isOpen(); }

            @Override
            public void close() {
                host.assertOwner();
                access.assertSideEffectAllowed();
                callback = null;
                listeners.remove(this);
            }
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
