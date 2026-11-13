package com.dishanhai.sh_better_ui.client.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 山海：NeoForge 1.21.1 的 JEI 提示文本适配。 */
final class JeiPlatformTooltip {
    private JeiPlatformTooltip() {}

    static <T> List<Component> get(IIngredientRenderer<T> renderer, T ingredient) {
        Minecraft client = Minecraft.getInstance();
        var flag = client.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        return renderer.getTooltip(ingredient, Item.TooltipContext.of(client.level), client.player, flag);
    }
}
