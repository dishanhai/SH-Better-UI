// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.selection.IngredientCatalog;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientValues;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration;
import com.dishanhai.sh_better_ui.kernel.api.controls.ResourcePickerState.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/** Platform data adapter. No GTSH classes or mod-specific types cross the UUK API. */
public final class UiResourcePickerSources {
    private UiResourcePickerSources() {}

    /** Changes when the active catalog, its language or registry tags change. */
    public static long itemsRevision() { return IngredientCatalog.automatic().revision(); }

    public static List<Entry<ItemStack>> items() {
        var result = new LinkedHashMap<String, Entry<ItemStack>>();
        for (var ingredient : JeiIntegration.ingredients()) {
            if (!ingredient.visible() || ingredient.kind() != JeiIntegration.Kind.ITEM) continue;
            var id = ingredient.id();
            String key = id.toString();
            if (result.containsKey(key)) continue;
            ingredient.copy(ItemStack.class).filter(stack -> !stack.isEmpty()).ifPresent(stack -> {
                result.put(key, ownedEntry(key, id, stack, 1));
            });
        }
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            String key = id.toString();
            if (result.containsKey(key)) continue;
            var stack = item.getDefaultInstance();
            if (!stack.isEmpty()) result.put(key, ownedEntry(key, id, stack, 1));
        }
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
            if (!stack.isEmpty()) result.add(ownedEntry("bookmark:" + result.size(),
                    BuiltInRegistries.ITEM.getKey(stack.getItem()), stack, stack.getCount()));
        }
        return List.copyOf(result);
    }
    public static boolean bookmarksAvailable() { return JeiIntegration.isAvailable(); }

    public static Entry<ItemStack> entry(String key, ItemStack original, int amount) {
        return ownedEntry(key, BuiltInRegistries.ITEM.getKey(original.getItem()), original.copy(), amount);
    }

    private static Entry<ItemStack> ownedEntry(String key, ResourceLocation id, ItemStack stack, int amount) {
        stack.setCount(1);
        String name = stack.getHoverName().getString();
        return new Entry<>(key.isEmpty() ? id.toString() : key, id.toString(), name, id.getNamespace(),
                stack.getTags().map(tag -> tag.location().toString()).sorted().toList(), () -> {
                    try {
                        return IngredientValues.itemTooltip(stack).stream().map(Component::getString)
                                .collect(Collectors.joining(" "));
                    } catch (RuntimeException failure) {
                        return name + " " + id;
                    }
                }, Math.max(1, amount), stack);
    }
}
