// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.gui.selection.IngredientValues;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class RichInlinePlatform {
    private RichInlinePlatform() {}

    static Ingredient fluid(Fluid fluid) {
        return IngredientValues.fluid(new FluidStack(fluid, FluidType.BUCKET_VOLUME));
    }

    static void entity(GuiGraphics graphics, LivingEntity entity, int x, int y, int scale) {
        InventoryScreen.renderEntityInInventory(graphics, x, y, scale, new Vector3f(),
                new Quaternionf().rotateZ((float) Math.PI), null, entity);
    }
}
