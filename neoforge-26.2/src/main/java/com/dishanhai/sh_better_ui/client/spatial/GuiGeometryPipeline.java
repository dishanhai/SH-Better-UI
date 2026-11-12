// Author: dishanhai
package com.dishanhai.sh_better_ui.client.spatial;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class GuiGeometryPipeline {
    private GuiGeometryPipeline() {}

    static RenderPipeline create() {
        return RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(ShBetterUi.MOD_ID, "pipeline/gui_geometry"))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }
}
