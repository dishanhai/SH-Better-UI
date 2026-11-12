// Author: dishanhai
package com.dishanhai.sh_better_ui.client.glass;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class LiquidGlassPipeline {
    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .add("LineWidth", VertexFormatElement.LINE_WIDTH)
            .build();

    private LiquidGlassPipeline() {}

    static RenderPipeline create() {
        var shader = Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "core/liquid_glass");
        return RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "pipeline/liquid_glass"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withVertexFormat(FORMAT, VertexFormat.Mode.QUADS)
                .withCull(false)
                .build();
    }
}
