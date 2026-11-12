// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.spatial.SpatialRenderer;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.geometry.FilledGeometry;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Local;
import com.dishanhai.sh_better_ui.kernel.api.geometry.Geometry2.Point;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichExtensionNode;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichLayoutSnapshot.Rect;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextDataSource;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Scoped registrations for trusted native rich text renderers and their data sources. */
public final class RichTextExtensions implements Subscription {
    private static final int MAX_REGISTRATIONS = 64;
    private static final int MAX_COMMANDS = 128;
    private static final int MAX_FRAGMENT_TRIANGLES = 1_024;
    private static final int MAX_VIEW_TRIANGLES = 8_192;

    private final ScopeHandle owner;
    private final Map<UiId, Registration<Renderer>> renderers = new LinkedHashMap<>();
    private final Map<UiId, Registration<RichTextDataSource>> sources = new LinkedHashMap<>();
    private boolean closed;

    private RichTextExtensions(ScopeHandle owner) {
        this.owner = owner;
        owner.track(this);
    }

    /** Attach on the render thread to the registering module's scope or mounted UI tree. */
    public static RichTextExtensions attach(ScopeHandle owner) {
        RenderSystem.assertOnRenderThread();
        return new RichTextExtensions(Objects.requireNonNull(owner, "owner"));
    }

    @FunctionalInterface
    public interface Renderer {
        /** Record the inline foreground in the host's already committed fragment geometry. */
        void render(Context context);
    }

    /** The source snapshot and node rectangle stay fixed throughout one presentation. */
    public record Context(Canvas graphics, Font font, RichExtensionNode node, Rect bounds, long nowMs,
                          boolean reducedMotion, Frame frame) {
        public Optional<RichTextDataSource.Snapshot> data(UiId id) {
            return Optional.ofNullable(frame.samples.get(Objects.requireNonNull(id, "id")));
        }
    }

    /** Immutable sampled values. A frame is valid only for the registry that produced it. */
    public static final class Frame {
        private final RichTextExtensions owner;
        private final Map<UiId, RichTextDataSource.Snapshot> samples;

        private Frame(RichTextExtensions owner, Map<UiId, RichTextDataSource.Snapshot> samples) {
            this.owner = owner;
            this.samples = Map.copyOf(samples);
        }
    }

    /** Bounded native drawing commands. Coordinates are in the document's local space. */
    public static final class Canvas {
        private final Rect bounds;
        private final Font font;
        private final GeometryBudget viewBudget;
        private final List<Consumer<GuiGraphics>> commands = new ArrayList<>();
        private final List<FilledGeometry<Local>> geometry = new ArrayList<>();
        private int remainingTriangles = MAX_FRAGMENT_TRIANGLES;
        private boolean preciseHit;
        private boolean sealed;
        private boolean rendered;

        private Canvas(Rect bounds, Font font, GeometryBudget viewBudget) {
            this.bounds = bounds;
            this.font = font;
            this.viewBudget = viewBudget;
        }

        /** Intersect the fill with the fragment before recording it. */
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            requireOpen();
            int left = Math.max(bounds.x(), x0);
            int top = Math.max(bounds.y(), y0);
            int right = Math.min(bounds.x() + bounds.width(), x1);
            int bottom = Math.min(bounds.y() + bounds.height(), y1);
            if (left < right && top < bottom) record(graphics -> graphics.fill(left, top, right, bottom, argb));
        }

        /**
         * Experimental. Draw a filled mesh in document-local coordinates. The fragment clips the
         * mesh before drawing. When called, the union of visible mesh fills defines this fragment's
         * hit shape. Transparent fills and decorative text or rectangular fills contribute no area.
         */
        public void fillGeometry(FilledGeometry<Local> mesh, int argb) {
            requireOpen();
            Objects.requireNonNull(mesh, "mesh");
            preciseHit = true;
            int size = mesh.triangles().size();
            if (size == 0 || (argb >>> 24) == 0) return;
            reserveTriangles(size);
            var box = new Bounds<Local>(bounds.x(), bounds.y(),
                    bounds.x() + (double) bounds.width(), bounds.y() + (double) bounds.height());
            // Clipping each triangle against four edges visits at most 3 + 4 + 5 + 6 vertices.
            var clipped = mesh.clip(box, size * 18);
            int extra = clipped.triangles().size() - size;
            if (extra > 0) reserveTriangles(extra);
            if (clipped.triangles().isEmpty()) return;
            record(graphics -> SpatialRenderer.drawFlatGeometry(graphics, clipped, argb));
            geometry.add(clipped);
        }

        private void reserveTriangles(int size) {
            if (size > remainingTriangles) throw new IllegalStateException("Inline mesh exceeds the fragment budget");
            viewBudget.reserve(size);
            remainingTriangles -= size;
        }

        /** Draw only if the measured string and shadow fit the fragment. */
        public void drawText(Component text, int x, int y, int argb, boolean shadow) {
            requireOpen();
            var sequence = Objects.requireNonNull(text, "text").getVisualOrderText();
            int inset = shadow ? 1 : 0;
            if (x >= bounds.x() && y >= bounds.y()
                    && (long) x + font.width(sequence) + inset <= (long) bounds.x() + bounds.width()
                    && (long) y + font.lineHeight + inset <= (long) bounds.y() + bounds.height()) {
                record(graphics -> graphics.drawString(font, sequence, x, y, argb, shadow));
            }
        }

        private void record(Consumer<GuiGraphics> command) {
            if (commands.size() >= MAX_COMMANDS) throw new IllegalStateException("Inline renderer command limit exceeded");
            commands.add(command);
        }

        private void requireOpen() {
            RenderSystem.assertOnRenderThread();
            if (sealed) throw new IllegalStateException("Inline rendering context has expired");
        }
    }

    static final class GeometryBudget {
        private int remaining = MAX_VIEW_TRIANGLES;

        private void reserve(int size) {
            if (size > remaining) throw new IllegalStateException("Inline meshes exceed the view budget");
            remaining -= size;
        }
    }

    record Hit(boolean precise, List<FilledGeometry<Local>> geometry, BooleanSupplier active) {
        static final Hit RECTANGLE = new Hit(false, List.of(), () -> true);
        static final Hit NONE = new Hit(false, List.of(), () -> false);

        Hit { geometry = List.copyOf(geometry); }

        boolean contains(double x, double y) {
            if (!active.getAsBoolean()) return false;
            if (!precise) return true;
            if (!(Math.abs(x) <= Geometry2.MAX_COORDINATE && Math.abs(y) <= Geometry2.MAX_COORDINATE)) return false;
            var point = new Point<Local>(x, y);
            for (var mesh : geometry) {
                if (mesh.contains(point)) return true;
            }
            return false;
        }
    }

    record PaintResult(boolean placeholder, Hit hit) {
        static final PaintResult UNAVAILABLE = new PaintResult(true, Hit.RECTANGLE);
        static final PaintResult FAILED = new PaintResult(true, Hit.NONE);
        static final PaintResult CLIPPED = new PaintResult(false, Hit.NONE);
    }

    /** Register one renderer version for an ID owned by this module. Duplicate IDs are rejected. */
    public Subscription register(UiId type, int version, Renderer renderer) {
        if (version < 1 || version > 65_535) throw new IllegalArgumentException("Invalid inline renderer version");
        return register(renderers, type, version, renderer);
    }

    /** Register a pure owner-thread source that is sampled once per view presentation. */
    public Subscription registerData(UiId id, RichTextDataSource source) {
        return register(sources, id, 0, source);
    }

    private <T> Subscription register(Map<UiId, Registration<T>> entries, UiId id, int version, T value) {
        RenderSystem.assertOnRenderThread();
        if (!isActive()) throw new IllegalStateException("Rich text extension scope is closed");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        if (!id.namespace().equals(owner.owner().namespace())) {
            throw new SecurityException("Rich text extension ID must be in the scope owner's namespace");
        }
        if (entries.containsKey(id)) throw new IllegalArgumentException("Duplicate rich text extension " + id);
        if (entries.size() >= MAX_REGISTRATIONS) throw new IllegalStateException("Rich text registration limit exceeded");
        var registration = new Registration<>(entries, id, version, value);
        entries.put(id, registration);
        return registration;
    }

    /** Capture source values before painting. A failed or null source is absent for this presentation. */
    public Frame sample() {
        RenderSystem.assertOnRenderThread();
        var values = new LinkedHashMap<UiId, RichTextDataSource.Snapshot>();
        if (!isActive()) return new Frame(this, values);
        for (var registration : List.copyOf(sources.values())) {
            if (!registration.isActive()) continue;
            try {
                var snapshot = Objects.requireNonNull(registration.value.sample(), "data snapshot");
                if (registration.isActive()) values.put(registration.id, snapshot);
                registration.failed = false;
            } catch (RuntimeException | LinkageError error) {
                registration.failed("sample", error);
            }
        }
        return new Frame(this, values);
    }

    PaintResult paint(GuiGraphics graphics, Font font, RichExtensionNode node, Rect box, long nowMs,
                      boolean reducedMotion, Frame frame, GeometryBudget budget) {
        if (!isActive()) return PaintResult.FAILED;
        if (!node.renderable() || frame == null || frame.owner != this) return PaintResult.UNAVAILABLE;
        var registration = renderers.get(node.type());
        if (registration == null || registration.version != node.version()) return PaintResult.UNAVAILABLE;
        var canvas = new Canvas(box, font, budget);
        try {
            VisualCanvas.drawInline(graphics, box, local -> {
                registration.value.render(new Context(canvas, font, node, box, nowMs, reducedMotion, frame));
                canvas.sealed = true;
                if (registration.isActive()) {
                    canvas.commands.forEach(command -> command.accept(local));
                    canvas.rendered = true;
                }
            });
            registration.failed = false;
            if (!registration.isActive()) return PaintResult.FAILED;
            if (!canvas.rendered) return PaintResult.CLIPPED;
            return new PaintResult(false, new Hit(canvas.preciseHit, canvas.geometry, registration::isActive));
        } catch (RuntimeException | LinkageError error) {
            registration.failed("render", error);
            return PaintResult.FAILED;
        } finally {
            canvas.sealed = true;
            canvas.commands.clear();
            canvas.geometry.clear();
        }
    }

    @Override public boolean isActive() { return !closed && owner.isOpen(); }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        for (var renderer : List.copyOf(renderers.values())) renderer.close();
        for (var source : List.copyOf(sources.values())) source.close();
    }

    private final class Registration<T> implements Subscription {
        private final Map<UiId, Registration<T>> entries;
        private final UiId id;
        private final int version;
        private T value;
        private boolean failed;

        private Registration(Map<UiId, Registration<T>> entries, UiId id, int version, T value) {
            this.entries = entries;
            this.id = id;
            this.version = version;
            this.value = value;
        }

        @Override public boolean isActive() { return value != null && RichTextExtensions.this.isActive(); }

        @Override public void close() {
            RenderSystem.assertOnRenderThread();
            entries.remove(id, this);
            value = null;
        }

        private void failed(String operation, Throwable error) {
            if (!failed) ShBetterUi.LOGGER.warn("Could not {} rich text extension {}", operation, id, error);
            failed = true;
        }
    }
}
