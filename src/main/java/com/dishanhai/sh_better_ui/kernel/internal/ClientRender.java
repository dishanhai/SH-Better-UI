// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.BlurApi;
import com.dishanhai.sh_better_ui.client.api.GraphicsApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.internal.Scene;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class ClientRender implements RenderService {
    interface Backend {
        void push();
        void pop();
        void translate(double x, double y, double z);
        void scale(float factor);
        void clip(int x, int y, int right, int bottom);
        void unclip();
        void fill(int x, int y, int right, int bottom, int color);
        default void glass(ClientGlassRender glass, List<GlassDraw> surfaces) {
            throw new UnsupportedOperationException("Liquid Glass requires a native GUI backend");
        }
        default void text(Component label, int x, int y, int color) {
            throw new UnsupportedOperationException("Text requires a native GUI backend");
        }
        default void spatial(Scene scene, Frame frame, Consumer<SpatialScene.Node> content) {
            throw new UnsupportedOperationException("Spatial rendering requires a native GUI backend");
        }
    }

    private final KernelHost host;
    private final ClientScreens screens;
    private final ClientSpatial spatial;
    private final ClientGlassRender glass;
    private Frame frame;
    private Backend backend;

    ClientRender(KernelHost host, ClientScreens screens, ClientSpatial spatial, ClientGlassRender glass) {
        this.host = host;
        this.screens = screens;
        this.spatial = spatial;
        this.glass = glass;
    }

    @Override public GraphicsApi graphics() { return ShBetterUiApi.graphics(); }
    @Override public BlurApi blur() { return ShBetterUiApi.blur(); }

    @Override public void openInspector(ModuleContext module, ScopeHandle scope) {
        host.ownedScope(module, scope);
        var correctness = module.engine().correctness();
        var snapshot = correctness.snapshot(module, scope);
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        minecraft.setScreen(new CorrectnessInspectorScreen(minecraft.screen, snapshot, correctness.exportJson(module, scope)));
    }

    @Override
    public TaskHandle submit(ModuleContext module, ScopeHandle scope, Surface surface, Layer layer, int zOffset,
                             Consumer<Context> draw) {
        var access = host.moduleAccess(module);
        var target = access.write(scope);
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(draw, "draw");
        if (zOffset < -99 || zOffset > 99) throw new IllegalArgumentException("Invalid render Z offset");
        switch (surface) {
            case SCREEN -> {
                if (screens.ownerOf(host.ownedNode(module, target)) == null) {
                    throw new IllegalStateException("Render pass requires a mounted Screen node");
                }
            }
            case HUD -> {
                if ((target.kind() != ScopeKind.MODULE && !screens.ownsSession(target))
                        || !module.scope().owner().equals(target.owner())) {
                    throw new SecurityException("HUD render pass requires an owned module or mounted native session");
                }
            }
        }
        int z = switch (layer) {
            case CONTENT -> zOffset;
            case OVERLAY -> 200 + zOffset;
            case TOOLTIP -> 400 + zOffset;
        };
        return module.engine().scheduler().submit(UITask.ui(target, UIPhase.RENDER, UIPriority.NORMAL,
                () -> paint(target, surface, z, draw)));
    }

    void render(Frame viewport, Backend canvas, Runnable runPhase) {
        host.assertOwner();
        if (backend != null) throw new IllegalStateException("Nested native render phase");
        glass.begin();
        frame = viewport;
        backend = canvas;
        try {
            runPhase.run();
        } finally {
            backend = null;
            frame = null;
            glass.end();
        }
    }

    void reload() { glass.reload(); }

    void renderMaterialPreview(Screen owner, ScopeHandle scope) {
        var draw = DeveloperPanelScreen.takeMaterialDraw(owner);
        if (draw != null) paint(scope, Surface.SCREEN, 0, draw);
    }

    private void paint(ScopeHandle scope, Surface surface, int z, Consumer<Context> draw) {
        if (backend == null || frame.surface() != surface) {
            throw new IllegalStateException("No active render context for " + surface);
        }
        var context = new PassContext(scope);
        backend.push();
        try (var sample = host.metrics().begin(RuntimeMetrics.Category.RENDER)) {
            backend.translate(0, 0, z);
            draw.accept(context);
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            host.metrics().failure(RuntimeMetrics.Category.RENDER);
            throw failure;
        } finally {
            context.live = false;
            backend.pop();
        }
    }

    private final class PassContext implements Context {
        private final ScopeHandle scope;
        private boolean live = true;
        private boolean drawingSpatial;

        PassContext(ScopeHandle scope) { this.scope = scope; }

        @Override public Frame frame() {
            requireLive();
            return frame;
        }

        @Override public void fill(int x, int y, int right, int bottom, int argb) {
            requireLive();
            backend.fill(x, y, right, bottom, argb);
        }

        @Override public void glass(List<GlassDraw> surfaces) {
            requireLive();
            backend.glass(glass, List.copyOf(surfaces));
        }

        @Override public void text(Component label, int x, int y, int argb) {
            requireLive();
            backend.text(Objects.requireNonNull(label, "label"), x, y, argb);
        }

        @Override public void spatial(SpatialScene scene) {
            drawSpatial(scene, null);
        }

        @Override public void spatial(SpatialScene scene, BiConsumer<SpatialScene.Node, Context> content) {
            Objects.requireNonNull(content);
            drawSpatial(scene, node -> content.accept(node, this));
        }

        private void drawSpatial(SpatialScene scene, Consumer<SpatialScene.Node> content) {
            requireLive();
            if (drawingSpatial) throw new IllegalStateException("Nested spatial rendering");
            drawingSpatial = true;
            try {
                backend.spatial(spatial.forRender(scene, scope), frame, content);
            } finally {
                drawingSpatial = false;
            }
        }

        @Override public void clip(int x, int y, int right, int bottom, Runnable draw) {
            requireLive();
            backend.clip(x, y, right, bottom);
            try {
                draw.run();
            } finally {
                backend.unclip();
            }
        }

        @Override public void transform(double x, double y, float scale, Runnable draw) {
            requireLive();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Float.isFinite(scale) || scale <= 0) {
                throw new IllegalArgumentException("Invalid render transform");
            }
            backend.push();
            try {
                backend.translate(x, y, 0);
                backend.scale(scale);
                draw.run();
            } finally {
                backend.pop();
            }
        }

        private void requireLive() {
            host.assertOwner();
            if (!live) throw new IllegalStateException("UI render context has expired");
        }
    }
}
