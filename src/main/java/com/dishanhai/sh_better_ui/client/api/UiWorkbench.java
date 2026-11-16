// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiResourcePicker;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Cross-version entry points for native UUK workbenches. Client thread only. */
public final class UiWorkbench {
    private UiWorkbench() {}

    public static UiScreenBuilder builder(Component title, Screen parent) {
        return new UiScreenBuilder(title).parent(parent).backButton(false).dynamicTheme();
    }

    public static UiButton button(UiScreen owner, int x, int y, int width, int height, Component label, Runnable action) {
        return owner.button(x, y, width, height, label, action);
    }

    public static UiPreferences preferences(UiScreen owner) { return owner.context().preferences(); }

    public static int textColor() { return com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme.current().text(); }
    public static int secondaryTextColor() {
        var theme = com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme.current();
        return UiPreferences.configured().highContrast() ? theme.text() : theme.secondaryText(0xFFB0B8C8, 0.3f);
    }
    public static int accentColor() {
        var theme = com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme.current();
        return UiPreferences.configured().highContrast() ? theme.text() : theme.accent();
    }

    /** Registry ID to an independent default stack; empty items are excluded. */
    public static Map<String, ItemStack> itemIcons() {
        Map<String, ItemStack> result = new LinkedHashMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            var stack = item.getDefaultInstance();
            if (!stack.isEmpty()) result.put(BuiltInRegistries.ITEM.getKey(item).toString(), stack);
        }
        return result;
    }

    public static boolean selectItem(UiScreen owner, Component title, Supplier<? extends GuiEventListener> focus,
                                     Consumer<ItemStack> onSelect) {
        return selectItem(owner, title, focus, ItemStack.EMPTY, false, null, onSelect);
    }

    /** Stages a replacement using the GTSH-style resource browser; only Confirm invokes onSelect. */
    public static boolean selectItem(UiScreen owner, Component title, Supplier<? extends GuiEventListener> focus,
                                     ItemStack initial, boolean quantities, Runnable manualEntry, Consumer<ItemStack> onSelect) {
        return UiResourcePicker.open(owner, title, focus, initial, quantities, manualEntry, onSelect);
    }
}
