// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.*;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A scope-owned, UI-thread scene. Closing a node closes its descendants. */
public interface SpatialScene extends AutoCloseable {
    Node create(Spec spec);
    Optional<Node> find(UiId id);
    Optional<Node> hitTest(double x, double y);
    Occlusion occlusion(UiId id);
    void modal(UiId root);
    /** Sets a scene theme. Null restores the current scoped kernel theme. */
    void theme(Theme theme);
    void settings(Settings settings);
    Settings settings();

    /** Replaces the light with this ID. Remove it to disable it without changing other lights. */
    void light(UiId id, Light light);
    boolean removeLight(UiId id);
    Profile profile();

    @Override void close();

    /** Z and hit priority are independent. The parent ID must already exist in this scene. */
    record Spec(UiId id, UiId parent, Shape shape, Transform transform, int z, int hitPriority,
                boolean interactive, boolean clipChildren) {
        public Spec {
            Objects.requireNonNull(id);
            Objects.requireNonNull(shape);
            Objects.requireNonNull(transform);
        }
    }

    record Profile(long cpuNanos, int visibleNodes, int shadowCommands, int paintCommands, int activeLights,
                   int cacheHits, int cacheMisses, Quality quality, int batches, int backdropSamples,
                   long submissionNanos, long gpuNanos) {
        public Profile(long cpuNanos, int visibleNodes, int shadowCommands, int paintCommands, int activeLights,
                       int cacheHits, int cacheMisses, Quality quality) {
            this(cpuNanos, visibleNodes, shadowCommands, paintCommands, activeLights, cacheHits, cacheMisses,
                    quality, 0, 0, 0, -1);
        }
    }

    /** Region describes the clipped rectangular bounds in scene GUI coordinates. */
    record Occlusion(boolean visible, boolean clipped, boolean covered, boolean modalBlocked, List<Point> region) {
        public Occlusion { region = List.copyOf(region); }
    }

    enum HitTransparency { SHAPE, VISIBLE_SURFACE }

    interface Node extends AutoCloseable {
        UiId id();
        Transform transform();
        void transform(Transform transform);
        /** Changes presentation while keeping the current local hit transform stable. */
        void visualTransform(Transform transform);
        Matrix worldTransform();

        /** Optional stable hit geometry, independent of animated visual geometry. Null follows visuals. */
        void hitTransform(Transform transform);
        void hitTransparency(HitTransparency transparency);
        Shape shape();
        void shape(Shape shape);
        double elevation();
        void elevation(double height);
        void elevation(Elevation token);
        void opacity(double opacity);
        void visible(boolean visible);
        void enabled(boolean enabled);
        void material(Material material);
        void shadows(List<Shadow> shadows);
        /**
         * Replaces the silhouette of this node's shadow layers. Null follows the node shape.
         * The path is independent of the surface geometry, hit testing and child clipping.
         */
        default void shadowPath(ShadowPath path) {
            if (path != null) throw new UnsupportedOperationException("Spatial shadow paths are unavailable");
        }
        void shadowClip(ShadowClip clip);
        boolean isOpen();
        @Override void close();
    }
}
