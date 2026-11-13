package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

import java.io.IOException;

/** 山海：NeoForge 图标 shader 注册与四边形提交。 */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class IconShaderRenderer {
    private IconShaderRenderer() {}

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        for (var effect : GraphicIconShader.BUILT_INS) register(event, effect.id());
        register(event, ElectricLogo.SHADER_ID);
    }

    private static void register(RegisterShadersEvent event, ResourceLocation id) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.POSITION_TEX),
                    shader -> {});
        } catch (IOException exception) {
            ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception);
        }
    }

    static void drawQuad(Matrix4f pose, int x, int y, int width, int height) {
        var vertices = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float right = (float) x + width;
        float bottom = (float) y + height;
        vertices.addVertex(pose, x, y, 0f).setUv(0f, 0f);
        vertices.addVertex(pose, x, bottom, 0f).setUv(0f, 1f);
        vertices.addVertex(pose, right, bottom, 0f).setUv(1f, 1f);
        vertices.addVertex(pose, right, y, 0f).setUv(1f, 0f);
        BufferUploader.draw(vertices.buildOrThrow());
    }
}
