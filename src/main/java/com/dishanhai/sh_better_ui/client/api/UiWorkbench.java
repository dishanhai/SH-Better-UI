// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiResourcePicker;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.text.UiRichText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
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

    /** Paints a centered or anchored workbench panel over a dimmed background. */
    public static void paintPopup(UiScreen owner, UiFrame frame, Bounds panel) {
        var theme = UiTheme.current();
        frame.graphics().fill(0, 0, owner.width, owner.height, 0xB0000000);
        int border = frame.preferences().highContrast() ? theme.text() : theme.border();
        RoundedCorners.draw(frame.graphics(), panel.x(), panel.y(), panel.width(), panel.height(), 9, 1,
                border, theme.surface() | 0xFF000000);
    }

    /** Content bounds with room for a title, controls and visible margins on small windows. */
    public static Bounds popupBounds(UiScreen screen, int width, int height) {
        int w = Math.max(0, Math.min(width, screen.width - 16));
        int h = Math.max(0, Math.min(height, screen.height - 12));
        return new Bounds((screen.width - w) / 2, (screen.height - h) / 2, w, h);
    }

    /** Creates a native child dialog with a themed, centered panel. */
    public static UiScreenBuilder popup(Component title, Screen parent, int width, int height) {
        return builder(title, parent).onRender((screen, frame) -> {
            var area = popupBounds(screen, width, height);
            paintPopup(screen, frame, area);
            var font = Minecraft.getInstance().font;
            frame.drawScaledText(font, UiRichText.fit(font, title, Math.max(0, area.width() - 24)),
                    area.x() + 12, area.y() + 9, textColor(), 1);
        });
    }

    public record Choice(Component label, Runnable action) {}

    public static void showChoices(UiScreen owner, Component title, Component details, List<Choice> choices, Component cancel) {
        owner.workbenchOverlays().showChoices(title, details, choices.stream()
                .map(choice -> new UiOverlayHost.Action(choice.label(), choice.action())).toList(), cancel);
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

    public static boolean selectItemSequence(UiScreen owner, Component title, Supplier<? extends GuiEventListener> focus,
                                             int limit, Consumer<java.util.List<ItemStack>> onSelect) {
        return UiResourcePicker.openSequence(owner, title, focus, limit, onSelect);
    }
}
