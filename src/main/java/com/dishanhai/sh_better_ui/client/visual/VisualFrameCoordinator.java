// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.PixelRect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Node;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.client.gui.scaled.RevealProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.client.visual.VisualEffectResult.*;

/** Internal native event bridge. All transient work belongs to the current Screen render. */
public final class VisualFrameCoordinator {
    private static long nextFrame;
    private static Frame current;

    private VisualFrameCoordinator() {}

    public static VisualLayoutBinding bind(Screen owner, long mountGeneration, ScopeHandle scope, AlignmentLayout layout,
                                           Consumer<VisualLayoutBinding.FrameTime> sample) {
        var binding = new Binding(owner, mountGeneration, scope, layout, sample);
        scope.track(binding);
        return binding;
    }

    public static void begin(Screen owner, GuiGraphics graphics, float partialTick) {
        if (current == null && UiFrameScheduler.canCommitFor(owner)) {
            current = new Frame(++nextFrame, UiFrameScheduler.nowMs(), owner, graphics, partialTick);
        }
    }

    public static Frame screenFrame(Screen owner) {
        var frame = current;
        return frame != null && owner != null && frame.owner == owner && frame.active() ? frame : null;
    }

    public static void cancel(Screen owner) {
        if (current != null && current.owner == owner) end();
    }

    public static void end() {
        var frame = current;
        current = null;
        if (frame != null) {
            if (frame.binding != null && !frame.finished) frame.discard(frame.binding);
            frame.graphics = null;
            frame.owner = null;
            frame.blur = null;
            frame.foreground.clear();
        }
    }

    /** Seal before GaussBlur captures and clears its legacy slots. */
    public static void prepareBlur(Screen owner, GuiGraphics graphics) {
        var frame = matching(owner, graphics);
        if (frame == null || frame.sealed) return;
        frame.sealed = true;
        var blur = frame.blur;
        frame.blur = null;
        if (blur != null && frame.active() && (blur.binding() == null || blur.binding().isActive())
                && blur.target().equals(VisualCanvas.target())) {
            // The legacy primary keeps priority even when it was submitted after the managed request.
            GaussBlur.installManagedPrimary(blur.pixels(), blur.strength(), blur.radius(), blur.style());
        }
    }

    public static void drawForeground(Screen owner, GuiGraphics graphics) {
        var frame = matching(owner, graphics);
        if (frame == null || !frame.sealed || frame.drawingForeground || frame.finished) return;
        frame.drawingForeground = true;
        boolean published = false;
        try {
            if (!frame.active()) return;
            frame.foreground.sort(Comparator.comparingInt(Foreground::z));
            var draws = new ArrayList<>(frame.foreground);
            frame.foreground.clear();
            for (var item : draws) {
                if (!frame.active()) break;
                try {
                    item.draw().accept(frame);
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    if (item.node() != null) frame.report(item.node(), "foreground", failure);
                    else ShBetterUi.LOGGER.error("Screen foreground failed screen={} frame={}",
                            frame.screenName, frame.id, failure);
                }
            }
            published = frame.binding != null && frame.publish();
        } finally {
            frame.foreground.clear();
            frame.drawingForeground = false;
            frame.finished = true;
            if (!published && frame.binding != null) frame.discard(frame.binding);
        }
    }

    private static void drawNodeForeground(Frame frame, Node layoutNode, Consumer<VisualNodePass.DrawContext> draw,
                                           Consumer<GuiGraphics> afterClip) {
        drawNodeForeground(frame, frame.snapshot, layoutNode, draw, afterClip);
    }

    static void drawNodeForeground(Frame frame, FrameViewSnapshot snapshot, Node layoutNode,
                                   Consumer<VisualNodePass.DrawContext> draw, Consumer<GuiGraphics> afterClip) {
        if (!frame.hasSnapshot(snapshot) || !frame.target.equals(VisualCanvas.target())) return;
        var node = snapshot.entry(layoutNode);
        var graphics = frame.graphics;
        VisualCanvas.draw(graphics, frame.target, snapshot.clip(), node.view(), canvas -> {
            var context = new VisualNodePass.DrawContext(snapshot, node, frame.preferences, canvas);
            try {
                ThemeEffects.preview(frame.settings, () -> UiTheme.preview(frame.palette, () -> draw.accept(context)));
            } finally {
                context.expire();
            }
        });
        if (afterClip != null && frame.hasSnapshot(snapshot)) {
            GuiStateGuard.draw(() -> {
                try {
                    afterClip.accept(graphics);
                } finally {
                    graphics.flush();
                }
            });
        }
    }

    /** Internal implementation of the low-level BlurApi entry. */
    public static VisualEffectResult requestPixels(GuiGraphics graphics, PixelRect pixels, float strength,
                                                   float radiusPixels, GlassStyle style) {
        RenderSystem.assertOnRenderThread();
        validateBlur(pixels, strength, radiusPixels, style);
        var frame = current;
        if (frame == null || frame.sealed || frame.graphics != graphics || !frame.active()) return OUTSIDE_SCREEN_PHASE;
        return frame.request(null, VisualCanvas.target(), pixels, strength, radiusPixels, style);
    }

    private static Frame matching(Screen owner, GuiGraphics graphics) {
        var frame = current;
        return frame != null && frame.owner == owner && frame.graphics == graphics ? frame : null;
    }

    static void validateBlur(PixelRect pixels, float strength, float radius, GlassStyle style) {
        Objects.requireNonNull(pixels);
        Objects.requireNonNull(style);
        if (pixels.getW() < 0 || pixels.getH() < 0 || !Float.isFinite(strength) || strength < 0 || strength > 1
                || !Float.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException("Invalid managed blur area, strength or pixel radius");
        }
    }

    static final class Binding implements VisualLayoutBinding {
        private final WeakReference<Screen> owner;
        final long mountGeneration;
        final ScopeHandle scope;
        private AlignmentLayout layout;
        private Consumer<FrameTime> sample;
        FrameViewSnapshot snapshot;
        long lastReportFrame;
        int suppressedFailures;

        Binding(Screen owner, long mountGeneration, ScopeHandle scope, AlignmentLayout layout, Consumer<FrameTime> sample) {
            this.owner = new WeakReference<>(Objects.requireNonNull(owner));
            this.mountGeneration = mountGeneration;
            this.scope = scope;
            this.layout = Objects.requireNonNull(layout);
            this.sample = Objects.requireNonNull(sample);
        }

        @Override public boolean isActive() {
            var screen = owner.get();
            var client = Minecraft.getInstance();
            return layout != null && scope.isOpen() && screen != null && client != null && client.screen == screen;
        }

        @Override public void close() {
            RenderSystem.assertOnRenderThread();
            layout = null;
            sample = null;
            snapshot = null;
            var frame = current;
            if (frame != null && frame.binding == this) frame.discard(this);
        }

        @Override public Optional<FrameViewSnapshot> currentSnapshot() {
            RenderSystem.assertOnRenderThread();
            return isActive() ? Optional.ofNullable(snapshot) : Optional.empty();
        }

        Frame drawing(FrameViewSnapshot candidate) {
            var frame = current;
            return frame != null && frame.binding == this && frame.hasSnapshot(candidate) ? frame : null;
        }

        @Override
        public void renderScreen(UiFrame input, Consumer<LayoutPass> draw) {
            RenderSystem.assertOnRenderThread();
            Objects.requireNonNull(draw);
            if (!isActive()) throw new IllegalStateException("Visual layout scope is no longer mounted");
            var frame = matching(owner.get(), input.graphics());
            if (frame == null || frame.sealed || !frame.active()) {
                throw new IllegalStateException("Visual layout requires active native Screen drawing before Post");
            }
            if (frame.binding != null) throw new IllegalStateException("A visual layout has already entered this Screen frame");
            frame.binding = this;
            var target = VisualCanvas.target();
            if (target == null) {
                frame.discard(this);
                throw new IllegalStateException("Visual layout requires the native main GUI target");
            }
            frame.target = target;
            frame.preferences = input.preferences();
            frame.settings = ThemeEffects.settings();
            frame.palette = UiTheme.current();
            frame.glass = ThemeEffects.glass();
            frame.reveal = ThemeEffects.profile("reveal", RevealProfile.DEFAULT);
            var viewport = new Rect(input.viewport());
            var clip = VisualCanvas.screenClip(target).intersect(viewport);
            var source = layout;
            try {
                source.screen(viewport);
                sample.accept(new FrameTime(frame.id, frame.nowMs, frame.preferences.reducedMotion()));
                if (!isActive() || !frame.active()) return;
                source.resolve(frame.nowMs, frame.preferences.reducedMotion());
                frame.snapshot = new FrameViewSnapshot(this, frame.id, frame.nowMs, clip, source.snapshot(target.grid()));
                var pass = new Pass(frame);
                try {
                    draw.accept(pass);
                } finally {
                    pass.live = false;
                }
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                frame.discard(this);
                frame.report(null, "composition", failure);
            }
        }
    }

    private static final class Pass implements VisualLayoutBinding.LayoutPass {
        private final Frame frame;
        private boolean live = true;

        Pass(Frame frame) { this.frame = frame; }

        private void requireLive() {
            RenderSystem.assertOnRenderThread();
            if (!live || !frame.acceptsNodes()) throw new IllegalStateException("Visual layout pass has expired");
        }

        @Override public FrameViewSnapshot snapshot() {
            requireLive();
            return frame.snapshot;
        }

        @Override public void at(Node node, Consumer<VisualNodePass> draw) {
            requireLive();
            Objects.requireNonNull(draw);
            var pass = new VisualNodePass(frame, node, frame.snapshot.entry(node));
            int checkpoint = frame.foreground.size();
            var previousBlur = frame.blur;
            try {
                draw.accept(pass);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                if (frame.foreground.size() > checkpoint) {
                    frame.foreground.subList(checkpoint, frame.foreground.size()).clear();
                }
                if (frame.blur != null && frame.blur.binding() == frame.binding) frame.blur = previousBlur;
                frame.report(node, "node", failure);
            } finally {
                pass.expire();
            }
        }
    }

    public static final class Frame {
        final long id;
        final long nowMs;
        final String screenName;
        private final float partialTick;
        Screen owner;
        GuiGraphics graphics;
        final ArrayList<Foreground> foreground = new ArrayList<>();
        boolean sealed;
        boolean drawingForeground;
        boolean finished;
        Binding binding;
        FrameViewSnapshot snapshot;
        UiPreferences preferences;
        ClientUiSettings settings;
        UiTheme palette;
        GlassBackdropSettings glass;
        RevealProfile reveal;
        VisualProjection.Target target;
        private BlurRequest blur;
        private IdentityHashMap<AbstractWidget, WidgetPosition> widgetPositions;

        Frame(long id, long nowMs, Screen owner, GuiGraphics graphics, float partialTick) {
            this.id = id;
            this.nowMs = nowMs;
            this.owner = owner;
            this.screenName = owner.getClass().getName();
            this.graphics = graphics;
            this.partialTick = partialTick;
        }

        boolean active() { return current == this && UiFrameScheduler.canCommitFor(owner); }

        public long id() { return id; }
        public float partialTick() { return partialTick; }
        public GuiGraphics graphics() { return graphics; }
        public boolean isCurrent() { return active(); }
        public boolean acceptsForeground() { return active() && !sealed; }
        public boolean overlays() { return active() && finished; }

        public void deferForeground(int z, Consumer<GuiGraphics> draw) {
            if (!acceptsForeground()) throw new IllegalStateException("Screen foreground has been sealed");
            Objects.requireNonNull(draw, "draw");
            foreground.add(new Foreground(null, z, frame -> draw.accept(frame.graphics)));
        }

        boolean acceptsNodes() {
            return !sealed && hasSnapshot(snapshot);
        }

        boolean hasSnapshot(FrameViewSnapshot candidate) {
            return candidate != null && snapshot == candidate && active() && binding.isActive();
        }

        void rememberWidgetPosition(AbstractWidget widget) {
            if (widgetPositions == null) widgetPositions = new IdentityHashMap<>();
            widgetPositions.computeIfAbsent(widget, value -> new WidgetPosition(value.getX(), value.getY()));
        }

        boolean publish() {
            if (!hasSnapshot(snapshot) || !target.equals(VisualCanvas.target())) return false;
            binding.snapshot = snapshot;
            widgetPositions = null;
            return true;
        }

        VisualEffectResult request(Binding binding, VisualProjection.Target target, PixelRect pixels,
                                   float strength, float radius, GlassStyle style) {
            validateBlur(pixels, strength, radius, style);
            if (pixels.isEmpty()) return EMPTY_REGION;
            if (blur != null || GaussBlur.hasPrimaryRequest()) return CONFLICT;
            var settings = binding == null ? ThemeEffects.glass() : glass;
            if ((binding != null && !preferences.useBackdropBlur()) || !settings.enabled()
                    || settings.strength() <= 0.01f || settings.blurRadiusScale() <= 0f || strength <= 0.01f) return DISABLED;
            if (target == null || !GaussBlur.isAllowed(owner) || !GaussBlur.isReady()) return UNAVAILABLE;
            var clipped = target.clamp(pixels);
            if (clipped.isEmpty()) return FULLY_CLIPPED;
            blur = new BlurRequest(binding, target, clipped, Math.min(1f, strength * settings.strength()),
                    radius * settings.blurRadiusScale(), style);
            return ACCEPTED;
        }

        void discard(Binding owner) {
            snapshot = null;
            owner.snapshot = null;
            if (blur != null && blur.binding() == owner) blur = null;
            foreground.removeIf(item -> item.node() != null);
            var positions = widgetPositions;
            widgetPositions = null;
            if (positions != null) positions.forEach((widget, position) -> {
                widget.setX(position.x());
                widget.setY(position.y());
            });
        }

        void report(Node node, String effect, Throwable failure) {
            if (binding.lastReportFrame != 0 && id - binding.lastReportFrame < 120) {
                binding.suppressedFailures++;
                return;
            }
            ShBetterUi.LOGGER.error("Visual composition {} screen={} scope={} mount={} frame={} node={} suppressed={}",
                    effect, screenName, binding.scope.id(), binding.mountGeneration, id,
                    node == null ? "-" : node.name(), binding.suppressedFailures, failure);
            binding.lastReportFrame = id;
            binding.suppressedFailures = 0;
        }
    }

    record Foreground(Node node, int z, Consumer<Frame> draw) {
        Foreground(Node node, int z, Consumer<VisualNodePass.DrawContext> draw, Consumer<GuiGraphics> afterClip) {
            this(node, z, frame -> drawNodeForeground(frame, node, draw, afterClip));
        }
    }
    private record BlurRequest(Binding binding, VisualProjection.Target target, PixelRect pixels,
                               float strength, float radius, GlassStyle style) {}
    private record WidgetPosition(int x, int y) {}
}
