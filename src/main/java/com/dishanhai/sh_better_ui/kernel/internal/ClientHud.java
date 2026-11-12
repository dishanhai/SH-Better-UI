// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiStateGuard;
import com.dishanhai.sh_better_ui.kernel.api.HudService;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.Subscription;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class ClientHud implements HudService {
    private final KernelHost host;
    private final List<Layer> layers = new ArrayList<>();

    ClientHud(KernelHost host) {
        this.host = host;
    }

    @Override
    public AutoCloseable mount(ModuleContext module, Consumer<Frame> draw) {
        host.assertOwner();
        Objects.requireNonNull(draw);
        var access = host.moduleAccess(module);
        var scope = access.write(module.scope());
        access.require(UIPermission.REGISTER_RENDER_PASS, scope);
        var layer = new Layer(module, scope, draw);
        layer.lifetime = host.onScopeClose(scope, layer::close);
        layers.add(layer);
        return layer;
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
                    try {
                        layer.draw.accept(new Frame(canvas, preferences, partialTick));
                    } finally {
                        canvas.flush();
                    }
                });
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                layer.close();
                host.report("HUD layer for " + layer.module.descriptor().id(), failure);
            }
        }
    }

    private final class Layer implements AutoCloseable {
        private final ModuleContext module;
        private final ScopeLifecycle.Scope scope;
        private Consumer<Frame> draw;
        private Subscription lifetime;

        Layer(ModuleContext module, ScopeLifecycle.Scope scope, Consumer<Frame> draw) {
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
            lifetime.close();
            lifetime = null;
        }
    }
}
