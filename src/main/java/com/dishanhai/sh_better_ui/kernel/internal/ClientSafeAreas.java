// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.ValidationReport.Evidence;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import java.util.ArrayList;
import java.util.List;

/** Shared observation adapter; estimates and previous-frame HUD observations keep their provenance. */
final class ClientSafeAreas {
    private ClientSafeAreas() {}

    static List<UISnapshot.ReservedArea> collect(Minecraft minecraft, int width, int height,
            List<SurfacePlacement.Rect> registered, List<SurfacePlacement.Rect> bosses, boolean scoreboard, long frame) {
        var areas = new ArrayList<UISnapshot.ReservedArea>();
        int index = 0;
        for (var bounds : registered) add(areas, "sh_better_ui:reservation/" + index++, rect(bounds), Evidence.MODEL, frame);
        index = 0;
        for (var bounds : bosses) add(areas, "minecraft:boss/" + index++, rect(bounds), Evidence.OBSERVED, Math.max(0, frame - 1));
        NativeMusicOverlay.occupiedIslandBounds(width, height).ifPresent(bounds ->
                add(areas, "sh_better_ui:music_island", new Rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Space.GUI), Evidence.MODEL, frame));
        if (minecraft.level != null) {
            add(areas, "minecraft:hotbar_and_status", new Rect(width / 2d - 97, height - 46, 194, 46, Space.GUI), Evidence.MODEL, frame);
            var chat = minecraft.gui.getChat();
            double chatHeight = minecraft.screen instanceof ChatScreen ? chat.getHeight() + 16 : chat.getHeight();
            add(areas, "minecraft:chat", new Rect(0, Math.max(0, height - chatHeight - 42), chat.getWidth() + 4, chatHeight + 42, Space.GUI), Evidence.MODEL, frame);
            if (scoreboard) add(areas, "minecraft:scoreboard", new Rect(width - Math.min(160, width / 3d), Math.max(24, height / 2d - 97),
                    Math.min(160, width / 3d), 152, Space.GUI), Evidence.MODEL, frame);
            if (minecraft.options.showSubtitles().get()) {
                add(areas, "minecraft:subtitles_estimate", new Rect(Math.max(0, width - 180), Math.max(0, height - 120),
                        Math.min(180, width), Math.min(120, height), Space.GUI), Evidence.MODEL, frame);
            }
        }
        if (minecraft.screen != null) {
            if (minecraft.screen instanceof AbstractContainerScreen<?> container) {
                add(areas, "minecraft:container", new Rect(container.getGuiLeft() - 3, container.getGuiTop() - 3,
                        container.getXSize() + 6, container.getYSize() + 6, Space.GUI), Evidence.OBSERVED, frame);
            }
            int count = 0;
            for (var listener : minecraft.screen.children()) {
                if (listener instanceof AbstractWidget widget && widget.visible && widget.getWidth() > 0 && widget.getHeight() > 0) {
                    add(areas, "minecraft:widget/" + count, new Rect(widget.getX() - 2, widget.getY() - 2,
                            widget.getWidth() + 4, widget.getHeight() + 4, Space.GUI), Evidence.OBSERVED, frame);
                    if (++count >= 64) break;
                }
            }
        }
        if (areas.size() > 128) {
            var limited = new ArrayList<>(areas.subList(0, 127));
            add(limited, "sh_better_ui:observation_capacity_exceeded", new Rect(0, 0, width, height, Space.GUI), Evidence.LIMITATION, frame);
            return List.copyOf(limited);
        }
        return List.copyOf(areas);
    }

    private static Rect rect(SurfacePlacement.Rect bounds) { return new Rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Space.GUI); }
    private static void add(List<UISnapshot.ReservedArea> areas, String id, Rect rect, Evidence source, long frame) {
        if (!rect.empty()) areas.add(new UISnapshot.ReservedArea(UiId.parse(id), rect, source, frame));
    }
}
