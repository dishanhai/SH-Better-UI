// Author: dishanhai
package com.dishanhai.sh_better_ui.client;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.stream.Collectors;

final class NeoForgePlatformBridge {
    private NeoForgePlatformBridge() {}

    static void start() {
        var files = ModList.get().getModFiles().stream().map(file -> new ClientUiKernel.ModFile(
                file.getFile().findResource("META-INF", NativeClientBridge.MODULE_DESCRIPTOR), file.moduleName(),
                file.getMods().stream().map(mod -> mod.getModId()).collect(Collectors.toUnmodifiableSet()))).toList();
        ClientUiKernel.start(ModuleDescriptor.Platform.NEOFORGE_1_21_1, files);
    }

    static boolean scroll(ScreenEvent.MouseScrolled.Pre event) {
        return NativeClientBridge.input(event.getScreen(), new NativeInput.Pointer(NativeInput.PointerAction.SCROLL,
                event.getMouseX(), event.getMouseY(), -1, event.getScrollDeltaX(), event.getScrollDeltaY()));
    }

    static void hud(RenderGuiEvent.Post event) {
        NativeClientBridge.render(null, event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    static boolean hasSidebar(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) return false;
        var scoreboard = minecraft.level.getScoreboard();
        var team = minecraft.player.getTeam();
        var slot = team == null ? null : DisplaySlot.teamColorToSlot(team.getColor());
        return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) != null
                || slot != null && scoreboard.getDisplayObjective(slot) != null;
    }
}
