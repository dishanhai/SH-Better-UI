// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.selection.IngredientCatalog;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientValues;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration;
import com.dishanhai.sh_better_ui.kernel.api.controls.ResourcePickerState.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/** Platform data adapter. No GTSH classes or mod-specific types cross the UUK API. */
public final class UiResourcePickerSources {
    private UiResourcePickerSources() {}

    public static List<Entry<ItemStack>> items() {
        var result = new LinkedHashMap<String, Entry<ItemStack>>();
        for (var ingredient : IngredientCatalog.automatic().ingredients()) {
            if (!ingredient.visible() || ingredient.kind() != JeiIntegration.Kind.ITEM) continue;
            ingredient.copy(ItemStack.class).filter(stack -> !stack.isEmpty()).ifPresent(stack -> {
                var value = entry(ingredient.id().toString(), stack, 1);
                result.putIfAbsent(value.id(), value);
            });
        }
        UiWorkbench.itemIcons().forEach((id, stack) -> result.computeIfAbsent(id, ignored -> entry(id, stack, 1)));
        return List.copyOf(result.values());
    }
    public static List<Entry<ItemStack>> inventory() {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();
        var result = new ArrayList<Entry<ItemStack>>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (!stack.isEmpty()) result.add(entry("inventory:" + i, stack, stack.getCount()));
        }
        return List.copyOf(result);
    }
    public static List<Entry<ItemStack>> bookmarks() {
        var result = new ArrayList<Entry<ItemStack>>();
        for (var stack : JeiIntegration.bookmarkedItems()) {
            if (!stack.isEmpty()) result.add(entry("bookmark:" + result.size(), stack, stack.getCount()));
        }
        return List.copyOf(result);
    }
    public static boolean bookmarksAvailable() { return JeiIntegration.isAvailable(); }

    public static Entry<ItemStack> entry(String key, ItemStack original, int amount) {
        var stack = original.copy();
        stack.setCount(1);
        var ingredient = IngredientValues.item(stack);
        final String[] tooltip = {null};
        return new Entry<>(key.isEmpty() ? ingredient.id().toString() : key, ingredient.id().toString(), ingredient.name(), ingredient.modName(),
                ingredient.tags().stream().map(Object::toString).toList(), () -> {
                    if (tooltip[0] == null) {
                        try { tooltip[0] = ingredient.tooltip().stream().map(Component::getString).collect(Collectors.joining(" ")); }
                        catch (RuntimeException failure) { tooltip[0] = ingredient.name() + " " + ingredient.id(); }
                    }
                    return tooltip[0];
                }, Math.max(1, amount), stack);
    }
}
