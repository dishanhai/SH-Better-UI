// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.UiClient;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Platform-specific control center and global theme actions for the music assistant. */
public final class MusicChatPlatform {
    private MusicChatPlatform() {}

    public static void enqueue(Runnable action) {
        Minecraft.getInstance().tell(action);
    }

    public static void openControlCenter() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(UiClient.createControlPanel(client.screen));
    }

    public static String chooseGlobalTheme(String id) {
        var theme = MinecraftThemes.manager().registry().find(id);
        if (theme.isEmpty() || MinecraftThemes.isConfiguredPalette(id)) return text("theme_unavailable");
        var settings = ClientUiSettings.current();
        try {
            if (!ShBetterUi.saveClientUiSettings(settings.withTheme(settings.theme().withRegisteredTheme(id)))) {
                return text("theme_save_failed");
            }
        } catch (IllegalArgumentException fullArchive) {
            return text("theme_save_failed");
        }
        return text("global_theme_selected", Component.translatable(theme.get().nameKey()).getString());
    }

    /** Returns an ID, null for no match, or an empty string for an ambiguous displayed name. */
    public static String findGlobalTheme(String name) {
        if (name == null || name.isBlank()) return null;
        String found = null;
        for (var theme : MinecraftThemes.manager().registry().themes().values()) {
            if (MinecraftThemes.isConfiguredPalette(theme.id())
                    || !Component.translatable(theme.nameKey()).getString().equalsIgnoreCase(name.strip())) continue;
            if (found != null) return "";
            found = theme.id();
        }
        return found;
    }

    private static String text(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.music_chat.extra." + key, arguments).getString();
    }
}
