// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.api.BlurApi;
import com.dishanhai.sh_better_ui.client.api.GraphicsApi;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Submits native GUI work for a single render phase with a lifetime-bound drawing context. */
public interface RenderService extends UIService {
    GraphicsApi graphics();
    BlurApi blur();

    /** SCREEN requires a mounted node. HUD uses a module scope or a session mounted through ScreenService. */
    TaskHandle submit(ModuleContext module, ScopeHandle scope, Surface surface, Layer layer, int zOffset,
                      Consumer<Context> draw);

    /** Opens the opt-in inspector for a detached frame. The snapshot remains explicitly frozen while inspected. */
    default void openInspector(ModuleContext module, ScopeHandle scope) {
        throw new UnsupportedOperationException("This render backend does not provide the correctness inspector");
    }

    enum Surface { SCREEN, HUD }
    enum Layer { CONTENT, OVERLAY, TOOLTIP }

    record Frame(Surface surface, int width, int height, double guiScale, float partialTick) {
        public Frame {
            Objects.requireNonNull(surface, "surface");
            if (width < 0 || height < 0 || !Double.isFinite(guiScale) || guiScale <= 0) {
                throw new IllegalArgumentException("Invalid GUI viewport");
            }
        }
    }

    /** All operations require the current render thread and the active pass. Edges use GUI units. */
    interface Context {
        Frame frame();
        /** Shared client music pulse (0..1) for this frame. Zero when unavailable or disabled. */
        default float musicPulse() { return 0f; }

        void fill(int x, int y, int right, int bottom, int argb);
        void clip(int x, int y, int right, int bottom, Runnable draw);
        void transform(double x, double y, float scale, Runnable draw);

        /** Draws the material surfaces in order, sampling one backdrop shared by the native pass. */
        default void glass(List<GlassDraw> surfaces) {
            throw new UnsupportedOperationException("Liquid Glass requires a native GUI context");
        }

        /** Draws foreground text in GUI coordinates. */
        default void text(Component label, int x, int y, int argb) {
            throw new UnsupportedOperationException("Text requires a native GUI context");
        }

        /** Draws a scene mounted on the scope of this render task. */
        default void spatial(SpatialScene scene) {
            throw new UnsupportedOperationException("Spatial rendering requires a native UI context");
        }

        /**
         * Draws node content immediately after its surface and inner shadows. The callback uses scene GUI coordinates.
         * Apply native clipping to its content as needed. Use the single-argument overload to batch surface-only nodes.
         */
        default void spatial(SpatialScene scene, BiConsumer<SpatialScene.Node, Context> content) {
            throw new UnsupportedOperationException("Spatial rendering requires a native UI context");
        }
    }
}
