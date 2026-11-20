// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.kernel.api.controls.ResourcePickerState.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** 26.x data adapter. JEI bookmarks remain unavailable until a compatible JEI backend exists. */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class UiResourcePickerSources {
    private static final AtomicLong TAG_REVISION = new AtomicLong();

    private UiResourcePickerSources() {}

    public static long itemsRevision() { return TAG_REVISION.get(); }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) { TAG_REVISION.incrementAndGet(); }

    public static List<Entry<ItemStack>> items() {
        return UiWorkbench.itemIcons().entrySet().stream().map(entry -> entry(entry.getKey(), entry.getValue(), 1)).toList();
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
    public static List<Entry<ItemStack>> bookmarks() { return List.of(); }
    public static boolean bookmarksAvailable() { return false; }
    public static Entry<ItemStack> entry(String key, ItemStack original, int amount) {
        var stack = original.copy(); stack.setCount(1);
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String name = stack.getHoverName().getString();
        return new Entry<>(key.isEmpty() ? id : key, id, name, id.substring(0, id.indexOf(':')),
                stack.getItem().builtInRegistryHolder().tags().map(tag -> tag.location().toString()).toList(),
                () -> name + " " + id, Math.max(1, amount), stack);
    }
}
