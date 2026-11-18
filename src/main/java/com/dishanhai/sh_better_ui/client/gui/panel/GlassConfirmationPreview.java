// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

final class GlassConfirmationPreview implements PreviewScene {
    private static Component text(String key, Object... args) {
        return Component.translatable("sh_better_ui.glass_confirmation." + key, args);
    }
    @Override public void layout(PreviewContext host) {
        var area = host.viewport();
        if (area.width() < 120 || area.height() < 24) return;
        var title = text("launch");
        var bounds = new Bounds(area.x() + 4, area.y() + 4, area.width() - 8, 22);
        host.add(ShBetterUiApi.controls().button(bounds, title, ignored -> {
            if (!host.interactive()) return;
            var details = Component.empty();
            for (int i = 0; i < 8; i++) details.append(text("details")).append("\n\n");
            ShBetterUiApi.interactions().confirmGlass(host.screen(),
                    new UiConfirmation.Prompt(text("title"), details, CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL),
                    host.preferences(), graphics -> {
                        for (int x = 0; x < host.screen().width; x += 48)
                            graphics.fill(x, 0, x + 24, host.screen().height, 0xff245276);
                    }, () -> host.screen().children().stream()
                            .filter(child -> child instanceof AbstractWidget widget && widget.getMessage().equals(title))
                            .findFirst().orElse(null), () -> DeveloperPanelScreen.showToast(host.preferences()));
        }));
    }
}
