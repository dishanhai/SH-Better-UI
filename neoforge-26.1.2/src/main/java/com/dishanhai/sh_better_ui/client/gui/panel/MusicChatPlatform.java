// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.internal.NativePort;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Native 26.x control center and supported global theme actions for the music assistant. */
public final class MusicChatPlatform {
    private MusicChatPlatform() {}

    public static void enqueue(Runnable action) {
        Minecraft.getInstance().schedule(action);
    }

    public static void openControlCenter() {
        Minecraft client = Minecraft.getInstance();
        NativePort.show(client, new ControlPanelScreen(NativePort.screen(client)));
    }

    public static String chooseGlobalTheme(String id) {
        // The native 26.x palette resolver supports the built-in registry.
        var theme = BuiltinThemes.registry().find(id);
        if (theme.isEmpty()) return text("theme_unavailable");
        var settings = ControlPanelSettings.current();
        try {
            if (!ControlPanelSettings.save(settings.withTheme(settings.theme().withRegisteredTheme(id)))) {
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
        for (var theme : BuiltinThemes.registry().themes().values()) {
            if (!Component.translatable(theme.nameKey()).getString().equalsIgnoreCase(name.strip())) continue;
            if (found != null) return "";
            found = theme.id();
        }
        return found;
    }

    private static String text(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.music_chat.extra." + key, arguments).getString();
    }
}
