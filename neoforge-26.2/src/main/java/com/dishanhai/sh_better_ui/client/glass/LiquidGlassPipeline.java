// Author: dishanhai
package com.dishanhai.sh_better_ui.client.glass;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class LiquidGlassPipeline {
    private static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
            .addAttribute("LineWidth", GpuFormat.R32_FLOAT)
            .build();

    private LiquidGlassPipeline() {}

    static RenderPipeline create() {
        var shader = Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "core/liquid_glass");
        return RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "pipeline/liquid_glass"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withVertexBinding(0, FORMAT)
                .withCull(false)
                .build();
    }
}
