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
final class AmbientBackdropRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType TYPE = create("sh_better_ui_ambient_backdrop", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(WaveRenderState.ALPHA_BLEND).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    private AmbientBackdropRenderer() {
        super("sh_better_ui_ambient_backdrop", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }
    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        shader = null;
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "ambient_backdrop");
        try {
            register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
        } catch (IOException exception) {
            ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception);
        }
    }
    static boolean draw(GuiGraphics g, Bounds area, AmbientBackdrop.Style style, AmbientBackdropProfile p, float phase) {
        if (shader == null) return false;
        WaveRenderState state = WaveRenderState.capture();
        try {
            g.flush();
            shader.safeGetUniform("Surface").set((float) area.width() / area.height(), p.scale(), phase, p.intensity());
            shader.safeGetUniform("Opacity").set(p.opacity());
            shader.safeGetUniform("Style").set(style.ordinal());
            color("PrimaryColor", p.primaryColor());
            color("SecondaryColor", p.secondaryColor());
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
