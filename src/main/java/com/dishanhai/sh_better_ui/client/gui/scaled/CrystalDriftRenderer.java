package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Original SHBUI crystal sprites, one texture-free batch and no screen-owned GPU resources. */
final class CrystalDriftRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final int MAX_QUADS = 320;
    private static final RenderType TYPE = create("sh_better_ui_crystal_drift", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 48 * 1024, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(WaveRenderState.ALPHA_BLEND).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private CrystalDriftRenderer() {
        super("sh_better_ui_crystal_drift", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                48 * 1024, false, false, () -> {}, () -> {});
    }

    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        shader = null;
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "crystal_drift");
        try {
            register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> shader = loaded);
        } catch (IOException exception) {
            ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception);
        }
    }

    static boolean draw(GuiGraphics graphics, Bounds area, CrystalDriftProfile p, CrystalDriftField f) {
        if (shader == null) return false;
        if (p.opacity() == 0 || area.width() <= 0 || area.height() <= 0) return true;
        Matrix4f pose = graphics.pose().last().pose();
        float determinant = pose.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) < 1e-8f) return false;
        WaveRenderState state = WaveRenderState.capture();
        try {
            graphics.flush();
            shader.safeGetUniform("InversePose").set(new Matrix4f(pose).invert());
            shader.safeGetUniform("ClipRect").set((float) area.x(), (float) area.y(), (float) area.right(), (float) area.bottom());
            shader.safeGetUniform("Chrome").set(p.material() == CrystalDriftProfile.Material.CHROME ? 1f : 0f);
            shader.safeGetUniform("Glow").set(p.glow());
            var vertices = graphics.bufferSource().getBuffer(TYPE);
            float unit = Math.max(1f, Math.min(area.width(), area.height()) / 42f) * p.size();
            int count = Math.min(MAX_QUADS, Math.min(p.count(), f.count()));
            for (int i = 0; i < count; i++) {
                float depth = f.depth[i];
                float flash = f.flash[i];
                float halfLong = unit * (.65f + f.size[i] * .9f) * (.55f + depth * .8f);
                float halfShort = halfLong * (.27f + .12f * f.size[i]);
                float x = area.x() + f.x[i] * area.width();
                float y = area.y() + f.y[i] * area.height();
                float cosine = (float) Math.cos(f.angle[i]);
                float sine = (float) Math.sin(f.angle[i]);
                float tint = Math.min(1f, depth * .55f + flash * .45f);
                int color = mix(p.color(), p.accentColor(), tint, p.opacity() * (.4f + depth * .6f));
                if ((color >>> 24) == 0) continue;
                // 20% margin contains feathered edges and a bounded local halo.
                vertex(vertices, pose, x, y, cosine, sine, halfLong, halfShort, -1.2f, -1.2f, color);
                vertex(vertices, pose, x, y, cosine, sine, halfLong, halfShort, -1.2f, 1.2f, color);
                vertex(vertices, pose, x, y, cosine, sine, halfLong, halfShort, 1.2f, 1.2f, color);
                vertex(vertices, pose, x, y, cosine, sine, halfLong, halfShort, 1.2f, -1.2f, color);
            }
            graphics.bufferSource().endBatch(TYPE);
            return true;
        } finally {
            state.restore();
        }
    }

    private static int mix(int a, int b, float t, float opacity) {
        int alpha = Math.round(((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t) * opacity);
        int red = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int green = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int blue = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, float x, float y, float cosine, float sine,
                               float halfLong, float halfShort, float u, float v, int color) {
        VisualRevealRenderer.particleVertex(vertices, pose,
                x + cosine * halfLong * u - sine * halfShort * v,
                y + sine * halfLong * u + cosine * halfShort * v, u, v, color);
    }
}
