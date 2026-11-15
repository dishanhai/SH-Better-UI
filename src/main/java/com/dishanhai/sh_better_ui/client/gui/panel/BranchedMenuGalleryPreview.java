// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBranchedMenu;

final class BranchedMenuGalleryPreview implements PreviewScene {
    private final BranchedMenuDemo demo = new BranchedMenuDemo();
    private UiBranchedMenu menu;

    @Override
    public void layout(PreviewContext host) {
        var area = host.viewport();
        menu = null;
        if (area.width() < 90 || area.height() < BranchedMenuDemo.MIN_HEIGHT) return;
        menu = demo.layout(area, host.preferences());
        menu.buttons().forEach(host::add);
    }

    @Override
    public void render(UiFrame frame) {
        if (menu == null) return;
        boolean contrast = frame.preferences().highContrast();
        int rail = contrast ? GuiMotion.lerpColor(DarkModeTheme.surfaceColor(), LiquidGlassTheme.textColor(), 0.55f)
                : DarkModeTheme.borderColor();
        menu.draw(frame.graphics()::fill, rail,
                contrast ? LiquidGlassTheme.textColor() : LiquidGlassTheme.accentColor());
    }
}
