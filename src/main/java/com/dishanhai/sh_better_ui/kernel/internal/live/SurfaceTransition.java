// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceGeometry;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.Rect;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Client-thread controller for a single painted surface, advanced by the kernel frame clock. */
public final class SurfaceTransition<T> {
    public enum Motion { FULL, REDUCED, NONE }

    public enum Interruption {
        /** Start again from the sampled visual state. */
        RETARGET,
        /** Retain geometric velocity wherever the viewport allows it. */
        PRESERVE_VELOCITY,
        /** Apply the entire target immediately. */
        SNAP,
        /** Finish the active destination and then start the most recent requested target. */
        COMPLETE_THEN_START,
        /** Freeze the visual sample, drop the requested target and release input. */
        CANCEL
    }

    /** A stable content key lets payload refreshes keep their current blend weights. */
    public record Target<T>(SurfacePresentation presentation, SurfaceGeometry geometry, T content, Object contentKey) {
        public Target {
            Objects.requireNonNull(presentation);
            Objects.requireNonNull(geometry);
        }

        public Target(SurfacePresentation presentation, SurfaceGeometry geometry, T content) {
            this(presentation, geometry, content, content);
        }
    }

    public record Layer<T>(SurfacePresentation presentation, T content, Object contentKey, double weight) {}

    /** Current is the last settled endpoint. Animated and input describe this frame. */
    public record State<T>(Target<T> target, Target<T> current, SurfaceGeometry animated, Target<T> input,
                           List<Layer<T>> contentMix, boolean running) {}

    private static final long FULL_MIX_MS = 140;
    private static final long REDUCED_MIX_MS = 90;
    private static final long PAUSE_MS = 750;
    private static final double MIN_LAYER_WEIGHT = 1d / 4096;

    private record Weighted<T>(Target<T> target, double weight) {}

    private final Target<T> hidden = new Target<>(SurfacePresentation.HIDDEN, SurfaceGeometry.HIDDEN, null);
    private final GeometryTracks tracks;
    private final InterruptibleAnimation blend = new InterruptibleAnimation(Spring.FLUID_MORPH, 1);
    private List<Weighted<T>> outgoing = List.of();
    private Target<T> active = hidden;
    private Target<T> current = hidden;
    private long lastMs = Long.MIN_VALUE;
    private Motion lastMotion = Motion.FULL;
    private boolean cancelled;
    private State<T> state = new State<>(hidden, hidden, SurfaceGeometry.HIDDEN, hidden, List.of(), false);

    public SurfaceTransition() {
        this(Spring.SCROLL);
    }

    /** Uses the preset's response and damping with pixel-scale settling tolerances. */
    public SurfaceTransition(Spring.Preset geometryPreset) {
        double response = geometryPreset.response();
        double damping = geometryPreset.damping();
        // This range stays stable with the shared spring's maximum 4 ms integration step.
        if (!(response >= 0.08 && response <= 2 && damping >= 0.3 && damping <= 2)) {
            throw new IllegalArgumentException("Invalid surface spring response or damping");
        }
        tracks = new GeometryTracks(new Spring.Preset(response, damping,
                Spring.SCROLL.settleEps(), Spring.SCROLL.settleVelocity()));
    }

    /** Takes one coherent geometry, content and hit-test sample at the given frame time. */
    public State<T> update(Target<T> desired, Rect viewport, long nowMs, Motion motion, Interruption interruption) {
        Target<T> requested = fit(desired, viewport);
        nowMs = Math.max(nowMs, lastMs);
        boolean paused = lastMs != Long.MIN_VALUE && (double) nowMs - lastMs >= PAUSE_MS;
        lastMs = nowMs;

        if (!cancelled && !state.running() && motion == lastMotion && active.equals(requested)
                && interruption != Interruption.CANCEL) return state;

        tracks.advance(nowMs, paused);
        if (paused) blend.finish();
        else blend.sample(nowMs);
        settle();

        if (interruption == Interruption.CANCEL) {
            if (!cancelled) {
                outgoing = mix();
                tracks.snap(drawn(viewport));
                blend.snapTo(1);
                cancelled = true;
            }
            return publish(current, viewport);
        }

        boolean motionChanged = lastMotion != motion;
        lastMotion = motion;
        if (motion == Motion.NONE || interruption == Interruption.SNAP) {
            tracks.snap(requested.geometry);
            blend.snapTo(1);
            outgoing = List.of();
            cancelled = false;
            active = requested;
        } else if (!cancelled && !motionChanged && sameDestination(active, requested)) {
            active = requested;
        } else if (cancelled || motionChanged || !running() || interruption != Interruption.COMPLETE_THEN_START) {
            start(requested, viewport, nowMs, motion, motionChanged, interruption == Interruption.PRESERVE_VELOCITY);
        }
        settle();
        return publish(requested, viewport);
    }

    public State<T> state() {
        return state;
    }

    private Target<T> fit(Target<T> desired, Rect viewport) {
        if (!desired.presentation.visible()) {
            return desired.geometry.equals(SurfaceGeometry.HIDDEN) && desired.content == null ? desired
                    : new Target<>(desired.presentation, SurfaceGeometry.HIDDEN, null);
        }
        SurfaceGeometry geometry = SurfacePlacement.fit(desired.geometry, viewport);
        if (!geometry.visible()) return hidden;
        return geometry == desired.geometry ? desired
                : new Target<>(desired.presentation, geometry, desired.content, desired.contentKey);
    }

    private void start(Target<T> requested, Rect viewport, long nowMs, Motion motion,
                       boolean motionChanged, boolean preserveVelocity) {
        SurfaceGeometry from = drawn(viewport);
        SurfaceGeometry to = requested.geometry;
        if (!to.visible()) {
            to = motion == Motion.REDUCED ? withOpacity(from, 0) : collapsed(from);
        } else if (!from.visible()) {
            from = motion == Motion.REDUCED ? withOpacity(to, 0) : collapsed(to);
        }
        if (!preserveVelocity || cancelled || !from.equals(tracks.geometry())) tracks.snap(from);
        tracks.retarget(to, nowMs, motion);

        if (!sameLayer(active, requested) || motionChanged || cancelled) {
            outgoing = mix();
            blend.snapTo(1);
            if (requested.presentation.visible() && from.visible() && !outgoing.isEmpty()) {
                blend.snapTo(0);
                blend.blendTo(1, nowMs, 0, mixDuration(motion), InterruptibleAnimation.BlendCurve.LINEAR);
            }
        }
        active = requested;
        cancelled = false;
    }

    private boolean running() {
        return tracks.running() || blend.running();
    }

    private void settle() {
        if (!cancelled && !running()) {
            current = active;
            outgoing = List.of();
        }
    }

    private SurfaceGeometry drawn(Rect viewport) {
        if (!cancelled && !running() && !active.presentation.visible()) return SurfaceGeometry.HIDDEN;
        return SurfacePlacement.fit(tracks.geometry(), viewport);
    }

    private State<T> publish(Target<T> requested, Rect viewport) {
        SurfaceGeometry drawn = drawn(viewport);
        List<Weighted<T>> weights = mix();
        Target<T> input = hidden;
        if (!cancelled && sameDestination(active, requested) && drawn.opacity() >= 0.5 && drawn.visible()) {
            for (Weighted<T> entry : weights) {
                if (entry.weight >= 0.5 && sameLayer(entry.target, active)) {
                    input = new Target<>(active.presentation, drawn, active.content, active.contentKey);
                    break;
                }
            }
        }
        var layers = weights.stream().map(entry ->
                new Layer<>(entry.target.presentation, entry.target.content, entry.target.contentKey, entry.weight)).toList();
        state = new State<>(requested, current, drawn, input, layers, running());
        return state;
    }

    private List<Weighted<T>> mix() {
        if (cancelled || !active.presentation.visible()) return outgoing;
        if (!blend.running()) return List.of(new Weighted<>(active, 1));
        double fraction = blend.value();
        if (fraction <= 0) return outgoing;

        var weights = new ArrayList<Weighted<T>>(outgoing.size() + 1);
        double discarded = 0;
        boolean hasActive = false;
        for (Weighted<T> entry : outgoing) {
            double weight = entry.weight * (1 - fraction);
            if (sameLayer(entry.target, active)) {
                weights.add(new Weighted<>(active, weight + fraction));
                hasActive = true;
            } else if (weight >= MIN_LAYER_WEIGHT) {
                weights.add(new Weighted<>(entry.target, weight));
            } else {
                discarded += weight;
            }
        }
        if (!hasActive) weights.add(new Weighted<>(active, fraction));
        if (discarded > 0) {
            int strongest = 0;
            for (int i = 1; i < weights.size(); i++) {
                if (weights.get(i).weight > weights.get(strongest).weight) strongest = i;
            }
            var entry = weights.get(strongest);
            weights.set(strongest, new Weighted<>(entry.target, entry.weight + discarded));
        }
        return weights;
    }

    private static boolean sameDestination(Target<?> left, Target<?> right) {
        return sameLayer(left, right) && left.geometry.equals(right.geometry);
    }

    private static boolean sameLayer(Target<?> left, Target<?> right) {
        return left.presentation == right.presentation && Objects.equals(left.contentKey, right.contentKey);
    }

    private static SurfaceGeometry collapsed(SurfaceGeometry geometry) {
        double width = Math.min(16, geometry.width());
        double height = Math.min(4, geometry.height());
        return new SurfaceGeometry(geometry.x() + (geometry.width() - width) / 2,
                geometry.y() + (geometry.height() - height) / 2, width, height, geometry.radius(), 0);
    }

    private static SurfaceGeometry withOpacity(SurfaceGeometry geometry, double opacity) {
        return new SurfaceGeometry(geometry.x(), geometry.y(), geometry.width(), geometry.height(), geometry.radius(), opacity);
    }

    private static long mixDuration(Motion motion) {
        return motion == Motion.REDUCED ? REDUCED_MIX_MS : FULL_MIX_MS;
    }

    private static final class GeometryTracks {
        private final List<InterruptibleAnimation> bounds;
        private final InterruptibleAnimation opacity = new InterruptibleAnimation(Spring.FLUID_MORPH);
        private SurfaceGeometry destination = SurfaceGeometry.HIDDEN;

        private GeometryTracks(Spring.Preset preset) {
            bounds = List.of(new InterruptibleAnimation(preset), new InterruptibleAnimation(preset),
                    new InterruptibleAnimation(preset), new InterruptibleAnimation(preset), new InterruptibleAnimation(preset));
        }

        private void snap(SurfaceGeometry geometry) {
            destination = geometry;
            double[] values = coordinates(geometry);
            for (int i = 0; i < bounds.size(); i++) bounds.get(i).snapTo((float) values[i]);
            opacity.snapTo((float) geometry.opacity());
        }

        private void retarget(SurfaceGeometry geometry, long nowMs, Motion motion) {
            destination = geometry;
            double[] values = coordinates(geometry);
            for (int i = 0; i < bounds.size(); i++) {
                var track = bounds.get(i);
                if (motion == Motion.REDUCED) track.snapTo((float) values[i]);
                else track.animateTo((float) values[i], nowMs);
            }
            if (motion == Motion.REDUCED) {
                opacity.blendTo((float) geometry.opacity(), nowMs, 0, REDUCED_MIX_MS,
                        InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
            } else {
                opacity.animateTo((float) geometry.opacity(), nowMs);
            }
        }

        private void advance(long nowMs, boolean paused) {
            if (paused) {
                snap(destination);
            } else {
                for (var track : bounds) track.sample(nowMs);
                opacity.sample(nowMs);
            }
        }

        private boolean running() {
            if (opacity.running()) return true;
            for (var track : bounds) if (track.running()) return true;
            return false;
        }

        private SurfaceGeometry geometry() {
            if (!running()) return destination;
            return new SurfaceGeometry(bounds.get(0).value(), bounds.get(1).value(), bounds.get(2).value(),
                    bounds.get(3).value(), bounds.get(4).value(), opacity.value());
        }

        private static double[] coordinates(SurfaceGeometry geometry) {
            return new double[]{geometry.x(), geometry.y(), geometry.width(), geometry.height(), geometry.radius()};
        }
    }
}
