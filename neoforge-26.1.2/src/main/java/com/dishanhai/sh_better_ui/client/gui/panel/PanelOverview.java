// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.ShBetterUi;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.List;

/** Read-only client diagnostics. The caller refreshes them while the overview is visible. */
final class PanelOverview {
    private static final long MIB = 1024 * 1024;

    private PanelOverview() {}

    static List<Component> capture(Minecraft client) {
        var mods = ModList.get();
        var mod = mods.getModContainerById(ShBetterUi.MOD_ID).map(container -> container.getModInfo());
        var loader = mods.getModContainerById("neoforge").map(container -> container.getModInfo());
        var graphics = ControlPanelPlatform.graphics();
        var runtime = Runtime.getRuntime();
        var window = client.getWindow();
        String session = client.level == null ? "menu" : client.getSingleplayerServer() != null ? "integrated" : "remote";
        return List.of(
                label("details.name", mod.map(IModInfo::getDisplayName).orElse(ShBetterUi.MOD_ID)),
                label("device.backend", graphics.backend()),
                label("details.version", mod.map(info -> info.getVersion().toString()).orElse("")),
                label("details.minecraft", SharedConstants.getCurrentVersion().name()),
                label("details.loader", loader.map(IModInfo::getDisplayName).orElse("NeoForge"),
                        loader.map(info -> info.getVersion().toString()).orElse("")),
                label("device.gpu", graphics.renderer()),
                label("device.vendor", graphics.vendor()),
                label("device.driver", graphics.driver()),
                label("runtime.session", label("state." + session)),
                label("runtime.fps", client.getFps()),
                label("runtime.memory", (runtime.totalMemory() - runtime.freeMemory()) / MIB, runtime.maxMemory() / MIB),
                label("status.loaded_mods", mods.getMods().size()),
                label("system.os", System.getProperty("os.name"), System.getProperty("os.version")),
                label("system.java", System.getProperty("java.version"), System.getProperty("java.vm.name")),
                label("device.display", window.getWidth(), window.getHeight()),
                label("device.gui", window.getGuiScaledWidth(), window.getGuiScaledHeight(), window.getGuiScale()));
    }

    private static Component label(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.panel.overview." + key, arguments);
    }

    record Graphics(String backend, String renderer, String vendor, String driver) {}
}
