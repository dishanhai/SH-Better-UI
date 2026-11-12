// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.*;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.*;

import java.util.*;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.spatial.internal.SpatialMesh.*;

/** UI-thread implementation. Platform adapters consume only ordered, clipped commands. */
public final class Scene implements SpatialScene {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_LIGHTS = 64;
    private static final Comparator<Element> DRAW_ORDER = Comparator
            .comparingInt((Element e) -> e.spec.z()).thenComparingLong(e -> e.order);
    private static final Comparator<Element> HIT_ORDER = Comparator
            .comparingInt((Element e) -> e.spec.hitPriority()).thenComparing(DRAW_ORDER);

    public record Paint(Shape shape, ShadowType type, double x, double y, double blur, double spread,
                        int color, boolean soft, boolean clipNode, float highlight, float border, float depth,
                        float roughness, float lightX, float lightY, ShadowPath shadowPath) {
        public Paint(Shape shape, ShadowType type, double x, double y, double blur, double spread,
                     int color, boolean soft, boolean clipNode, float highlight, float border, float depth,
                     float roughness, float lightX, float lightY) {
            this(shape, type, x, y, blur, spread, color, soft, clipNode, highlight, border, depth,
                    roughness, lightX, lightY, null);
        }
    }

    @FunctionalInterface
    public interface Sink {
        void emit(Paint paint, List<Vertex> mesh, Backdrop backdrop);
        default void endNode(Node node) {}
    }

    public record Backdrop(float x, float y, float width, float height, float corner, float radius) {}

    private record Draw(Paint paint, List<Vertex> mesh, Backdrop backdrop) {}

    private final Runnable assertOwner;
    private final Consumer<Scene> release;
    private final Map<UiId, Element> nodes = new LinkedHashMap<>();
    private final Map<UiId, Light> lights = new LinkedHashMap<>();
    private SpatialLighting.Index lightIndex;
    private final List<Element> ordered = new ArrayList<>();
    private Theme baseTheme = Theme.DEFAULT;
    private Theme overrideTheme;
    private Settings settings = Settings.DEFAULT;
    private Settings requested = Settings.DEFAULT;
    private Settings global = Settings.DEFAULT;
    private Element modal;
    private boolean orderDirty;
    private boolean closed;
    private long order;
    private double width, height, guiScale;
    private Clip viewport;
    private boolean sdf = true;
    private boolean pathSdf = true;
    private Profile profile = new Profile(0, 0, 0, 0, 0, 0, 0, Quality.HIGH);
    private Quality automatic = Quality.HIGH;
    private double smoothedCpu;
    private int samples;
    private int pressure;
    private long gpuNanos = -1;

    public Scene(Runnable assertOwner) { this(assertOwner, ignored -> {}); }

    public Scene(Runnable assertOwner, Consumer<Scene> release) {
        this.assertOwner = Objects.requireNonNull(assertOwner);
        this.release = Objects.requireNonNull(release);
    }

    public Runnable onNodeClose(Node node, Runnable action) {
        check();
        var element = require(node.id());
        if (element != node) throw new IllegalArgumentException("Spatial node belongs to another scene");
        element.cleanup.add(Objects.requireNonNull(action));
        return () -> element.cleanup.remove(action);
    }

    @Override public Node create(Spec spec) {
        check();
        Objects.requireNonNull(spec);
        if (nodes.containsKey(spec.id())) throw new IllegalArgumentException("Duplicate spatial node " + spec.id());
        Element parent = spec.parent() == null ? null : require(spec.parent());
        if (parent != null && parent.depth == MAX_DEPTH) throw new IllegalArgumentException("Spatial tree exceeds maximum depth");
        var element = new Element(spec, parent, ++order);
        nodes.put(spec.id(), element);
        ordered.add(element);
        if (parent != null) parent.children.add(element);
        orderDirty = true;
        return element;
    }

    @Override public Optional<Node> find(UiId id) { check(); return Optional.ofNullable(nodes.get(id)); }

    @Override public Optional<Node> hitTest(double x, double y) {
        check();
        if (!settings.enabled() || !Double.isFinite(x) || !Double.isFinite(y)
                || viewport != null && !viewport.contains(x, y)) return Optional.empty();
        Element best = null;
        for (var element : ordered) {
            if (!element.spec.interactive() || !element.interactive() || !element.inModal()) continue;
            if (best != null && HIT_ORDER.compare(element, best) <= 0) continue;
            if (element.contains(x, y)) best = element;
        }
        return Optional.ofNullable(best);
    }

    @Override public void modal(UiId root) { check(); modal = root == null ? null : require(root); }

    @Override public Occlusion occlusion(UiId id) {
        check();
        var node = require(id);
        var bounds = quad(node.worldTransform(), 0, 0, node.shape.width(), node.shape.height());
        var region = clipped(node, bounds, ShadowClip.PARENT);
        boolean visible = settings.enabled() && node.surfaceAlpha() > 0 && region.size() >= 3;
        boolean covered = false;
        if (visible) {
            for (var other : ordered) {
                if (DRAW_ORDER.compare(other, node) <= 0 || other.surfaceAlpha() < 1) continue;
                if (other.covers(region)) {
                    covered = true;
                    break;
                }
            }
        }
        return new Occlusion(visible, !region.equals(bounds), covered, !node.inModal(),
                region.stream().map(v -> new Point(v.x(), v.y())).toList());
    }

    @Override public void theme(Theme theme) {
        check();
        if (Objects.equals(overrideTheme, theme)) return;
        overrideTheme = theme;
        invalidatePaint();
    }

    public void baseTheme(Theme theme) {
        check();
        baseTheme = Objects.requireNonNull(theme);
        if (overrideTheme == null) invalidatePaint();
    }

    private Theme theme() { return overrideTheme == null ? baseTheme : overrideTheme; }

    @Override public void settings(Settings settings) {
        check();
        requested = Objects.requireNonNull(settings);
        applySettings();
    }

    public void globalSettings(Settings settings) {
        check();
        global = Objects.requireNonNull(settings);
        applySettings();
    }

    private void applySettings() {
        var next = new Settings(global.enabled() && requested.enabled(), global.shadows() && requested.shadows(),
                global.lighting() && requested.lighting(), global.blur() && requested.blur(),
                global.quality() == Quality.AUTO ? requested.quality() : global.quality(),
                global.profiling() || requested.profiling());
        if (settings.equals(next)) return;
        settings = next;
        if (!settings.profiling()) gpuNanos = -1;
        smoothedCpu = 0;
        samples = pressure = 0;
        automatic = Quality.HIGH;
        invalidatePaint();
    }

    @Override public Settings settings() { check(); return settings; }

    @Override public void light(UiId id, Light light) {
        check();
        Objects.requireNonNull(id);
        Objects.requireNonNull(light);
        if (lights.size() == MAX_LIGHTS && !lights.containsKey(id)) {
            throw new IllegalStateException("Spatial scene light limit reached");
        }
        var previous = lights.put(id, light);
        if (!light.equals(previous)) invalidateLight(previous, light);
    }

    @Override public boolean removeLight(UiId id) {
        check();
        var previous = lights.remove(id);
        if (previous == null) return false;
        invalidateLight(previous, null);
        return true;
    }

    @Override public Profile profile() { check(); return profile; }

    public void draw(double width, double height, double guiScale, Sink sink) {
        draw(width, height, guiScale, true, sink);
    }

    public void draw(double width, double height, double guiScale, boolean sdf, Sink sink) {
        draw(width, height, guiScale, sdf, sink, false);
    }

    public void draw(double width, double height, double guiScale, boolean sdf, Sink sink, boolean content) {
        draw(width, height, guiScale, sdf, sdf, sink, content);
    }

    public void draw(double width, double height, double guiScale, boolean sdf, boolean pathSdf,
                     Sink sink, boolean content) {
        check();
        if (!Double.isFinite(width) || width < 0 || !Double.isFinite(height) || height < 0
                || !Double.isFinite(guiScale) || guiScale <= 0) throw new IllegalArgumentException("Invalid spatial viewport");
        if (viewport == null || this.width != width || this.height != height || this.guiScale != guiScale
                || this.sdf != sdf || this.pathSdf != pathSdf) {
            this.width = width;
            this.height = height;
            this.guiScale = guiScale;
            this.sdf = sdf;
            this.pathSdf = pathSdf;
            viewport = new Clip(Matrix.IDENTITY, width, height);
            invalidatePaint();
        }
        if (orderDirty) {
            ordered.sort(DRAW_ORDER);
            orderDirty = false;
        }
        long start = System.nanoTime();
        var quality = settings.quality() == Quality.AUTO ? automatic : settings.quality();
        int visible = 0, shadows = 0, commands = 0, hits = 0, misses = 0;
        if (settings.enabled() && width > 0 && height > 0) {
            if (settings.lighting() && lightIndex == null && !lights.isEmpty()) {
                lightIndex = new SpatialLighting.Index(lights.values());
            }
            var paintOrder = content ? List.copyOf(ordered) : ordered;
            for (var element : paintOrder) {
                if (closed) break;
                if (!element.open) continue;
                if (element.alpha() <= 0) continue;
                if (element.draws == null) {
                    misses++;
                    element.draws = compile(element, quality);
                } else {
                    hits++;
                }
                if (element.draws.isEmpty()) {
                    if (content && contentVisible(element)) sink.endNode(element);
                    continue;
                }
                visible++;
                for (var draw : element.draws) {
                    if (draw.paint.type != ShadowType.SURFACE) shadows++;
                    commands++;
                    sink.emit(draw.paint, draw.mesh, draw.backdrop);
                }
                if (content && contentVisible(element)) sink.endNode(element);
            }
        }
        long elapsed = System.nanoTime() - start;
        profile = new Profile(elapsed, visible, shadows, commands, settings.lighting() ? lights.size() : 0,
                hits, misses, quality);
        if (settings.enabled() && settings.quality() == Quality.AUTO && commands != 0) sampleQuality(elapsed);
    }

    public void recordSubmission(int batches, int backdropSamples, long nanos) {
        var p = profile;
        profile = new Profile(p.cpuNanos(), p.visibleNodes(), p.shadowCommands(), p.paintCommands(),
                p.activeLights(), p.cacheHits(), p.cacheMisses(), p.quality(), batches, backdropSamples, nanos, gpuNanos);
    }

    public void recordGpuTime(long nanos) {
        if (!closed && settings.profiling()) gpuNanos = nanos;
    }

    private boolean contentVisible(Element node) {
        return clipped(node, quad(node.worldTransform(), 0, 0, node.shape.width(), node.shape.height()),
                ShadowClip.PARENT).size() >= 3;
    }

    private void sampleQuality(long elapsed) {
        smoothedCpu += (elapsed - smoothedCpu) * .06;
        if (++samples < 90) return;
        samples = 0;
        pressure = smoothedCpu > 3_000_000 ? Math.max(0, pressure) + 1
                : smoothedCpu < 950_000 ? Math.min(0, pressure) - 1 : 0;
        Quality next = automatic;
        if (pressure >= 2) next = automatic == Quality.HIGH ? Quality.MEDIUM : Quality.LOW;
        if (pressure <= -3) next = automatic == Quality.LOW ? Quality.MEDIUM : Quality.HIGH;
        if (next != automatic) {
            automatic = next;
            pressure = 0;
            invalidatePaint();
        }
    }

    private List<Draw> compile(Element node, Quality quality) {
        var matrix = node.worldTransform();
        var shape = node.shape;
        double alpha = node.alpha();
        var material = node.material == null ? theme().material() : node.material;
        var layers = settings.shadows() && sdf && (node.shadowPath == null || pathSdf)
                ? (node.shadows == null ? theme().shadows(node.elevation()) : node.shadows) : List.<Shadow>of();
        var list = new ArrayList<Draw>(Math.min(layers.size(), quality.shadowLimit()) + 1);
        var center = matrix.point(shape.width() / 2, shape.height() / 2);
        var light = SpatialLighting.sample(lightIndex, center.x(), center.y(), node.elevation(), node.inverse,
                material, settings.lighting() ? quality.lightLimit() : 0);
        for (int i = 0; i < Math.min(layers.size(), quality.shadowLimit()); i++) {
            var layer = layers.get(i);
            if (layer.type() != ShadowType.INNER) shadow(list, node, layer, quality, light);
        }
        var surface = new Paint(shape, ShadowType.SURFACE, 0, 0, 0, 0,
                light.tint(alpha(material.color(), alpha * material.opacity())), false, false,
                (float) material.highlight(), (float) material.border(), (float) material.depth(),
                (float) material.roughness(), light.directionX(), light.directionY());
        double aa = 1.5 * Math.max(Math.hypot(node.inverse.a(), node.inverse.c()),
                Math.hypot(node.inverse.b(), node.inverse.d())) / guiScale;
        aa = Math.min(8, aa);
        var mesh = sdf ? quad(matrix, -aa, -aa, shape.width() + aa, shape.height() + aa)
                : outline(matrix, shape);
        mesh = clipped(node, mesh, ShadowClip.PARENT);
        if (mesh.size() >= 3 && surface.color() >>> 24 > 0) {
            Backdrop backdrop = settings.blur() && material.blur() > 0 && quality != Quality.LOW && alpha == 1
                    && material.opacity() == 1 && node.backdropSafe()
                    ? new Backdrop((float) matrix.x(), (float) matrix.y(), (float) (shape.width() * matrix.a()),
                            (float) (shape.height() * matrix.d()), (float) (shape.topLeft() * matrix.a()),
                            (float) Math.min(32, material.blur() * matrix.a())) : null;
            list.add(new Draw(surface, mesh, backdrop));
        }
        for (int i = 0; i < Math.min(layers.size(), quality.shadowLimit()); i++) {
            var layer = layers.get(i);
            if (layer.type() == ShadowType.INNER) shadow(list, node, layer, quality, light);
        }
        return list;
    }

    private void shadow(List<Draw> out, Element node, Shadow layer, Quality quality, SpatialLighting.Sample light) {
        int color = alpha(layer.color(), node.alpha() * layer.opacity());
        if (color >>> 24 == 0) return;
        var shape = node.shape;
        var matrix = node.worldTransform();
        boolean inner = layer.type() == ShadowType.INNER;
        boolean soft = layer.soft() && (quality == Quality.HIGH || quality == Quality.ULTRA);
        boolean clipNode = inner || node.shadowClip == ShadowClip.NODE;
        double dx = layer.x(), dy = layer.y();
        if (layer.type() == ShadowType.DIRECTIONAL && light.directional()) {
            double distance = Math.hypot(dx, dy);
            dx = -light.directionX() * distance;
            dy = -light.directionY() * distance;
        }
        double pad = (soft ? 3 : 1) * layer.blur() + Math.max(0, layer.spread()) + 2;
        var path = node.shadowPath;
        var bounds = path == null ? null : path.bounds();
        double left = bounds == null ? 0 : bounds.left();
        double top = bounds == null ? 0 : bounds.top();
        double right = bounds == null ? shape.width() : bounds.right();
        double bottom = bounds == null ? shape.height() : bounds.bottom();
        boolean ellipse = path == null && shape.kind() == Kind.ELLIPSE;
        double padX = ellipse ? pad * shape.width() / Math.min(shape.width(), shape.height()) : pad;
        double padY = ellipse ? pad * shape.height() / Math.min(shape.width(), shape.height()) : pad;
        var mesh = clipNode ? quad(matrix, left - 2, top - 2, right + 2, bottom + 2)
                : quad(matrix, dx + left - padX, dy + top - padY, dx + right + padX, dy + bottom + padY);
        mesh = clipped(node, mesh, inner ? ShadowClip.PARENT : node.shadowClip);
        if (mesh.size() >= 3) out.add(new Draw(new Paint(shape, layer.type(), dx, dy,
                layer.blur(), layer.spread(), color, soft, clipNode, 0, 0, 0, 1, 0, 0, path), mesh, null));
    }

    private List<Vertex> clipped(Element node, List<Vertex> mesh, ShadowClip policy) {
        if (viewport != null) mesh = clip(mesh, viewport);
        if (policy == ShadowClip.OVERFLOW || policy == ShadowClip.WINDOW) return mesh;
        for (Element parent = node.parent; parent != null && !mesh.isEmpty(); parent = parent.parent) {
            if (parent.spec.clipChildren()) {
                parent.worldTransform();
                mesh = clip(mesh, parent.clip);
            }
        }
        return mesh;
    }

    private static int alpha(int color, double opacity) {
        return color & 0xffffff | (int) Math.round((color >>> 24) * opacity) << 24;
    }

    private void invalidatePaint() {
        for (var node : ordered) node.draws = null;
    }

    private void invalidateLight(Light previous, Light next) {
        lightIndex = null;
        if (!settings.lighting()) return;
        if (SpatialLighting.global(previous) || SpatialLighting.global(next)) {
            invalidatePaint();
            return;
        }
        for (var node : ordered) {
            if (node.draws == null) continue;
            var matrix = node.world;
            double u = node.shape.width() / 2, v = node.shape.height() / 2;
            double x = matrix.a() * u + matrix.c() * v + matrix.x();
            double y = matrix.b() * u + matrix.d() * v + matrix.y();
            if (SpatialLighting.affects(previous, x, y) || SpatialLighting.affects(next, x, y)) node.draws = null;
        }
    }

    private Element require(UiId id) {
        var node = nodes.get(id);
        if (node == null) throw new IllegalArgumentException("Unknown spatial node " + id);
        return node;
    }

    private void check() {
        assertOwner.run();
        if (closed) throw new IllegalStateException("Spatial scene is closed");
    }

    @Override public void close() {
        assertOwner.run();
        if (closed) return;
        closed = true;
        for (var node : ordered) {
            node.open = false;
            node.draws = null;
            node.children.clear();
            node.cleanup.clear();
        }
        nodes.clear();
        ordered.clear();
        lights.clear();
        lightIndex = null;
        modal = null;
        release.accept(this);
    }

    private final class Element implements Node {
        private final Spec spec;
        private final Element parent;
        private final int depth;
        private final long order;
        private final List<Element> children = new ArrayList<>();
        private final List<Runnable> cleanup = new ArrayList<>();
        private Transform transform;
        private Transform hitTransform;
        private Shape shape;
        private Matrix world, inverse, hitInverse, hitWorld;
        private Clip clip;
        private double height, opacity = 1;
        private Elevation token = Elevation.SURFACE;
        private Material material;
        private List<Shadow> shadows;
        private ShadowPath shadowPath;
        private List<Draw> draws;
        private ShadowClip shadowClip = ShadowClip.PARENT;
        private HitTransparency hitTransparency = HitTransparency.SHAPE;
        private boolean visible = true, enabled = true, open = true;

        Element(Spec spec, Element parent, long order) {
            this.spec = spec;
            this.parent = parent;
            this.order = order;
            depth = parent == null ? 0 : parent.depth + 1;
            transform = spec.transform();
            shape = spec.shape();
        }

        @Override public UiId id() { return spec.id(); }
        @Override public Transform transform() { checkNode(); return transform; }
        @Override public Shape shape() { checkNode(); return shape; }
        @Override public boolean isOpen() { assertOwner.run(); return open; }

        @Override public void transform(Transform next) {
            checkNode();
            if (transform.equals(next)) return;
            transform = Objects.requireNonNull(next);
            invalidateTransform();
        }

        @Override public void visualTransform(Transform next) {
            checkNode();
            Objects.requireNonNull(next);
            if (hitTransform == null) hitTransform(transform);
            transform(next);
        }

        @Override public void hitTransform(Transform next) {
            checkNode();
            if (Objects.equals(hitTransform, next)) return;
            hitTransform = next;
            invalidateHit();
        }

        @Override public void hitTransparency(HitTransparency next) {
            checkNode();
            hitTransparency = Objects.requireNonNull(next);
        }

        @Override public Matrix worldTransform() {
            checkNode();
            if (world == null) {
                var local = transform.matrix();
                if (parent == null) {
                    world = local;
                    inverse = local.inverse();
                } else {
                    world = parent.worldTransform().compose(local);
                    inverse = local.inverse().compose(parent.inverse);
                }
            }
            if (clip == null) clip = new Clip(inverse, shape.width(), shape.height());
            return world;
        }

        private Matrix hitWorld() {
            if (hitWorld == null) {
                var local = (hitTransform == null ? transform : hitTransform).matrix();
                if (parent == null) {
                    hitWorld = local;
                    hitInverse = local.inverse();
                } else {
                    hitWorld = parent.hitWorld().compose(local);
                    hitInverse = local.inverse().compose(parent.hitInverse);
                }
            }
            return hitWorld;
        }

        @Override public void shape(Shape next) {
            checkNode();
            if (shape.equals(next)) return;
            shape = Objects.requireNonNull(next);
            clip = null;
            invalidateSubtreePaint();
        }

        @Override public double elevation() { checkNode(); return token == null ? height : theme().elevation(token); }

        @Override public void elevation(double next) {
            checkNode();
            if (!Double.isFinite(next) || next < 0 || next > 64) throw new IllegalArgumentException("Invalid elevation");
            if (token == null && height == next) return;
            token = null;
            height = next;
            draws = null;
        }

        @Override public void elevation(Elevation next) {
            checkNode();
            if (token == next && next != null) return;
            token = Objects.requireNonNull(next);
            draws = null;
        }

        @Override public void opacity(double next) {
            checkNode();
            if (!Double.isFinite(next) || next < 0 || next > 1) throw new IllegalArgumentException("Invalid opacity");
            if (opacity == next) return;
            opacity = next;
            invalidateSubtreePaint();
        }

        @Override public void visible(boolean next) {
            checkNode();
            if (visible == next) return;
            visible = next;
            invalidateSubtreePaint();
        }

        @Override public void enabled(boolean next) { checkNode(); enabled = next; }
        @Override public void material(Material next) { checkNode(); material = next; draws = null; }
        @Override public void shadows(List<Shadow> next) {
            checkNode();
            if (next != null && next.size() > 8) throw new IllegalArgumentException("Too many shadow layers");
            shadows = next == null ? null : List.copyOf(next);
            draws = null;
        }
        @Override public void shadowPath(ShadowPath next) {
            checkNode();
            if (Objects.equals(shadowPath, next)) return;
            shadowPath = next;
            draws = null;
        }
        @Override public void shadowClip(ShadowClip next) { checkNode(); shadowClip = Objects.requireNonNull(next); draws = null; }

        private double alpha() {
            return !visible ? 0 : parent == null ? opacity : opacity * parent.alpha();
        }

        private boolean interactive() {
            return enabled && visible && opacity > 0 && (parent == null || parent.interactive());
        }

        private double surfaceAlpha() {
            var surface = material == null ? theme().material() : material;
            return alpha() * surface.opacity() * (surface.color() >>> 24) / 255.0;
        }

        private boolean covers(List<Vertex> region) {
            worldTransform();
            for (var vertex : region) {
                var point = inverse.point(vertex.x(), vertex.y());
                if (shape.distance(point.x(), point.y()) > 0) return false;
                for (Element node = parent; node != null; node = node.parent) {
                    if (node.spec.clipChildren()) {
                        node.worldTransform();
                        if (!node.clip.contains(vertex.x(), vertex.y())) return false;
                    }
                }
            }
            return true;
        }

        private boolean inModal() {
            if (modal == null) return true;
            for (Element node = this; node != null; node = node.parent) {
                if (node == modal) return true;
            }
            return false;
        }

        private boolean contains(double x, double y) {
            if (hitTransparency == HitTransparency.VISIBLE_SURFACE && surfaceAlpha() <= 0) return false;
            hitWorld();
            var local = hitInverse.point(x, y);
            if (shape.distance(local.x(), local.y()) > 0) return false;
            for (Element node = parent; node != null; node = node.parent) {
                if (node.spec.clipChildren()) {
                    node.hitWorld();
                    var point = node.hitInverse.point(x, y);
                    if (point.x() < 0 || point.y() < 0 || point.x() > node.shape.width() || point.y() > node.shape.height()) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean backdropSafe() {
            if (shape.kind() != Kind.BOX || world.b() != 0 || world.c() != 0 || world.a() <= 0 || world.d() <= 0
                    || world.a() != world.d() || shape.topLeft() != shape.topRight()
                    || shape.topLeft() != shape.bottomRight() || shape.topLeft() != shape.bottomLeft()) return false;
            for (Element node = parent; node != null; node = node.parent) {
                if (node.spec.clipChildren()) return false;
            }
            return true;
        }

        private void invalidateTransform() {
            world = inverse = hitWorld = hitInverse = null;
            clip = null;
            draws = null;
            for (var child : children) child.invalidateTransform();
        }

        private void invalidateHit() {
            hitWorld = hitInverse = null;
            for (var child : children) child.invalidateHit();
        }

        private void invalidateSubtreePaint() {
            draws = null;
            for (var child : children) child.invalidateSubtreePaint();
        }

        private void checkNode() {
            check();
            if (!open) throw new IllegalStateException("Spatial node is closed: " + id());
        }

        @Override public void close() {
            assertOwner.run();
            if (!open) return;
            for (var child : List.copyOf(children)) child.close();
            if (parent != null) parent.children.remove(this);
            if (modal == this) modal = null;
            nodes.remove(id());
            ordered.remove(this);
            draws = null;
            open = false;
            for (var action : List.copyOf(cleanup)) action.run();
            cleanup.clear();
        }
    }
}
