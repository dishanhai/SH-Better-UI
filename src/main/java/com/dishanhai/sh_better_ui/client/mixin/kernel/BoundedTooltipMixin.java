// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.kernel;

import com.dishanhai.sh_better_ui.client.gui.layout.BoundedTooltipGraphics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Supplies the viewport positioner while retaining native gather, render and theme hooks. */
@Mixin(GuiGraphics.class)
abstract class BoundedTooltipMixin {
    @ModifyVariable(method = {"renderTooltipInternal", "m_280497_"}, at = @At("HEAD"),
            argsOnly = true, remap = false)
    private ClientTooltipPositioner shbui$fitBoundedTooltip(ClientTooltipPositioner original) {
        if ((Object) this instanceof BoundedTooltipGraphics bounded) return bounded.positioner();
        return original;
    }
}
