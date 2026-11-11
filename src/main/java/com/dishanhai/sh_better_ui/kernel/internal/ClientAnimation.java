// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.MotionApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

final class ClientAnimation implements AnimationService {
    private final KernelHost host;
    private final Set<SpringTrack> active = new LinkedHashSet<>();

    ClientAnimation(KernelHost host) {
        this.host = host;
    }

    @Override public MotionApi motion() { return ShBetterUiApi.motion(); }

    @Override
    public Track bind(ModuleContext module, ScopeHandle node, UiId property, Spring.Preset spring, float initial,
                      BooleanSupplier reducedMotion, DoubleConsumer apply) {
        var access = host.moduleAccess(module);
        var target = access.write(node);
        target.assertOwner();
        if (target.kind() != ScopeKind.UI_TREE && target.kind() != ScopeKind.COMPONENT) {
            throw new IllegalArgumentException("Animation requires a tree or component scope");
        }
        var track = new SpringTrack(motion().animation(Objects.requireNonNull(spring), initial),
                Objects.requireNonNull(reducedMotion), Objects.requireNonNull(apply));
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.ANIMATION_PROPERTY, property);
        track.lease = host.acquire(access, new ResourceRequest(claim, target, 0, 0, ConflictPolicy.REJECT), track::release);
        return track;
    }

    void update() {
        if (active.isEmpty()) return;
        long nowMs = nowMs();
        for (var track : List.copyOf(active)) {
            if (track.isActive() && !track.paused) {
                try (var sample = host.metrics().begin(RuntimeMetrics.Category.ANIMATION)) {
                    track.sample(nowMs);
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    host.metrics().failure(RuntimeMetrics.Category.ANIMATION);
                    host.report("UI animation " + track.lease.resource().id(), failure);
                    track.close();
                }
            }
        }
    }

    private long nowMs() {
        return host.nowNanos() / 1_000_000L;
    }

    private final class SpringTrack implements Track {
        private final InterruptibleAnimation animation;
        private BooleanSupplier reducedMotion;
        private DoubleConsumer apply;
        private OwnershipLease lease;
        private boolean paused;
        private long pausedAt;
        private long pausedMs;
        private volatile float value;

        SpringTrack(InterruptibleAnimation animation, BooleanSupplier reducedMotion, DoubleConsumer apply) {
            this.animation = animation;
            this.reducedMotion = reducedMotion;
            this.apply = apply;
            value = animation.value();
        }

        @Override public long play(float target, InterruptibleAnimation.Policy policy, int priority) {
            checkActive();
            long revision = animation.transitionTo(target, effectiveTime(nowMs()), policy, priority);
            if (revision != 0 && !paused) active.add(this);
            return revision;
        }

        @Override public long blend(float target, int priority, long durationMs, InterruptibleAnimation.BlendCurve curve) {
            checkActive();
            long revision = animation.blendTo(target, effectiveTime(nowMs()), priority, durationMs, curve);
            if (revision != 0 && !paused) active.add(this);
            return revision;
        }

        @Override public void pause() {
            checkActive();
            if (paused) return;
            pausedAt = nowMs();
            paused = true;
            active.remove(this);
        }

        @Override public void resume() {
            checkActive();
            if (!paused) return;
            pausedMs += nowMs() - pausedAt;
            paused = false;
            active.add(this);
        }

        @Override public void cancel() {
            checkActive();
            animation.stop(effectiveTime(nowMs()));
            if (!paused) active.add(this);
        }

        @Override public boolean takeCompletion(long revision) {
            checkActive();
            return animation.takeCompletion(revision);
        }

        @Override public boolean isActive() { return lease.isActive(); }
        @Override public float value() { return value; }
        @Override public void close() { lease.close(); }

        private long effectiveTime(long now) { return (paused ? pausedAt : now) - pausedMs; }

        private void sample(long now) {
            boolean reduce = reducedMotion.getAsBoolean();
            if (!isActive()) return;
            var next = animation.sample(effectiveTime(now), reduce);
            if (!animation.running()) active.remove(this);
            if (Float.compare(value, next) != 0) {
                value = next;
                apply.accept(next);
            }
        }

        private void checkActive() {
            host.assertOwner();
            if (!isActive()) throw new IllegalStateException("UI animation property was released");
        }

        private void release() {
            active.remove(this);
            animation.close();
            reducedMotion = null;
            apply = null;
        }
    }
}
