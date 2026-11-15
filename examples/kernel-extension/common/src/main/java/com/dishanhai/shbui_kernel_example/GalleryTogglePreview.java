// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import net.minecraft.network.chat.Component;

final class GalleryTogglePreview implements PreviewScene {
    private boolean enabled;
    private UiToggleButton toggle;

    @Override
    public void layout(PreviewContext host) {
        Bounds viewport = host.viewport();
        int width = Math.min(170, viewport.width() - 8);
        int height = 22;
        if (width < 1 || viewport.height() < height) return;

        var area = new Bounds(viewport.x() + (viewport.width() - width) / 2,
                viewport.y() + (viewport.height() - height) / 2, width, height);
        toggle = new UiToggleButton(area, enabled, host.preferences(), UiStyle.LIQUID_GLASS,
                value -> ExampleUiModule.label("preview.toggle.state",
                        Component.translatable(value ? "options.on" : "options.off")),
                value -> {
                    enabled = value;
                    toggle.setValue(value);
                });
        host.add(toggle.button());
    }
}
