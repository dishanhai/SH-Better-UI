package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** One bounded, texture-free pass. Shader lifetime is owned by Minecraft's reload listener. */
final class BlackHoleBackdropRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType TYPE = create("sh_better_ui_black_hole_backdrop", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(WaveRenderState.ALPHA_BLEND).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private BlackHoleBackdropRenderer() {
        super("sh_better_ui_black_hole_backdrop", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }
    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        shader = null;
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "black_hole_backdrop");
        try {
            register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
        } catch (IOException exception) {
            ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception);
        }
    }
    static boolean draw(GuiGraphics g, Bounds area, BlackHoleBackdropProfile p, float phase) {
        if (shader == null) return false;
        if (area.width() <= 0 || area.height() <= 0 || p.opacity() <= 0) return true;
        if (!Float.isFinite(phase)) return false;
        WaveRenderState state = WaveRenderState.capture();
        try {
            g.flush();
            shader.safeGetUniform("Surface").set((float) area.width() / area.height(), p.size(), phase, p.tilt());
            shader.safeGetUniform("Disk").set(p.diskWidth(), p.lensing(), p.brightness(), p.glow());
            shader.safeGetUniform("Opacity").set(p.opacity());
            color("InkColor", p.color());
            color("BackgroundColor", p.backgroundColor());
            VisualRevealRenderer.quad(g, TYPE, area.x(), area.y(), area.width(), area.height(), g.pose().last().pose());
            return true;
        } finally {
            state.restore();
        }
    }
    private static void color(String name, int argb) {
        shader.safeGetUniform(name).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
