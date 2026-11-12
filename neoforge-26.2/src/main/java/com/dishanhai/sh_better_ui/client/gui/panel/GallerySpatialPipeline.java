// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.GpuFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class GallerySpatialPipeline {
    private static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .build();

    private GallerySpatialPipeline() {}

    static RenderPipeline create(boolean reveal) {
        String name = reveal ? "gallery_spatial_reveal" : "gallery_spatial";
        var builder = RenderPipeline.builder(reveal ? RenderPipelines.GUI_TEXTURED_SNIPPET : RenderPipelines.GUI_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "core/gallery_spatial"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "core/" + name))
                .withVertexBinding(0, FORMAT)
                .withCull(false);
        if (reveal) builder.withShaderDefine("GALLERY_REVEAL");
        return builder.build();
    }
}
