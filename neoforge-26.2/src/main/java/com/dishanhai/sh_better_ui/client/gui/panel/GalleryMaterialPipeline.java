// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class GalleryMaterialPipeline {
    private static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .build();

    private GalleryMaterialPipeline() {}

    static RenderPipeline create() {
        var shader = Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "core/gallery_material");
        return RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "pipeline/gallery_material"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withVertexBinding(0, FORMAT)
                .withCull(false)
                .build();
    }
}
