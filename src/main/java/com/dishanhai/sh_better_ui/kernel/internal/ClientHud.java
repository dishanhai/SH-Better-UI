// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.kernel.api.HudService;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class ClientHud implements HudService {
    private final KernelHost host;
    private final ClientGlassRender rendering;
    private final List<Layer> layers = new ArrayList<>();
    private long nextLayer;
    private Pass pass;

    ClientHud(KernelHost host, ClientGlassRender rendering) {
        this.host = host;
        this.rendering = rendering;
    }

    @Override
    public AutoCloseable mount(ModuleContext module, Consumer<Frame> draw) {
        host.assertOwner();
        Objects.requireNonNull(draw);
        var access = host.moduleAccess(module);
        var scope = access.write(module.scope());
        access.require(UIPermission.REGISTER_RENDER_PASS, scope);
        var layer = new Layer(++nextLayer, module, scope, draw);
        layer.lifetime = host.onScopeClose(scope, layer::close);
        layers.add(layer);
        return layer;
    }

    @Override
    public TextReadabilityPalette textPalette(ModuleContext module, Frame frame, UiId panelId, Rect local,
                                               int darkestArgb, int lightestArgb) {
        var access = host.moduleAccess(module);
        var active = pass;
        if (active == null || active.frame != frame || active.layer.draw == null || !active.layer.scope.isOpen()) {
            throw new IllegalStateException("HUD text readability requires its active mounted callback");
        }
        if (active.layer.module != module) throw new SecurityException("The HUD callback belongs to another module");
        access.require(UIPermission.REGISTER_RENDER_PASS, active.layer.scope);
        return rendering.prepareHudText(frame.graphics(), active.layer.id, Objects.requireNonNull(panelId, "panelId"),
                Objects.requireNonNull(local, "local"), darkestArgb, lightestArgb, null);
    }

    void render(GuiGraphics graphics, float partialTick) {
        if (layers.isEmpty()) return;
        var preferences = UiPreferences.configured();
        for (var layer : List.copyOf(layers)) {
            if (layer.draw == null || !layer.scope.isOpen() || !host.moduleAccess(layer.module)
                    .decision(UIPermission.REGISTER_RENDER_PASS, layer.scope).allowed()) continue;
            try {
                graphics.flush();
                GuiStateGuard.draw(() -> {
                    var canvas = new GuiGraphics(Minecraft.getInstance(), graphics.bufferSource());
                    canvas.pose().last().pose().set(graphics.pose().last().pose());
                    canvas.pose().last().normal().set(graphics.pose().last().normal());
                    var frame = new Frame(canvas, preferences, partialTick);
                    pass = new Pass(layer, frame);
                    try {
                        layer.draw.accept(frame);
                    } finally {
                        pass = null;
                        canvas.flush();
                    }
                });
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                layer.close();
                host.report("HUD layer for " + layer.module.descriptor().id(), failure);
            }
        }
    }

    private record Pass(Layer layer, Frame frame) {}

    private final class Layer implements AutoCloseable {
        private final long id;
        private final ModuleContext module;
        private final ScopeLifecycle.Scope scope;
        private Consumer<Frame> draw;
        private Subscription lifetime;

        Layer(long id, ModuleContext module, ScopeLifecycle.Scope scope, Consumer<Frame> draw) {
            this.id = id;
            this.module = module;
            this.scope = scope;
            this.draw = draw;
        }

        @Override
        public void close() {
            host.assertOwner();
            if (draw == null) return;
            draw = null;
            layers.remove(this);
            rendering.forgetHudText(id);
            lifetime.close();
            lifetime = null;
        }
    }
}
