// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native extraction adapter; no browser, private framebuffer or input ownership. */
final class ConfirmationGlassPaint {
    private final ConfirmationGlassStyle style = new ConfirmationGlassStyle();

    ConfirmationGlassStyle.Palette draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                        UiPreferences preferences) {
        var theme = UiTheme.current();
        int fill = theme.surface(), title = theme.text();
        int details = GuiMotion.lerpColor(title, fill, .22f);
        var palette = style.palette(fill, title, details, false);
        var settings = com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings.current();
        boolean optical = false;
        if (width > 0 && height > 0 && preferences.useBackdropBlur() && settings.glassEnabled()
                && com.dishanhai.sh_better_ui.client.blur.BackdropCapture.request(graphics) != null) {
            var surface = ConfirmationGlassStyle.surface(x, y, width, height, palette.title());
            optical = com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface.drawPrepared(graphics,
                    x, y, width, height, 9,
                    (surface.material().foreground().fallbackArgb() & 0xffffff) | 0x40000000, 0,
                    settings.optics(surface.material().optics()), preferences, 1);
        }
        palette = style.palette(fill, title, details, optical);
        if (width > 0 && height > 0) RoundedCorners.draw(graphics, x, y, width, height, 9, 1,
                preferences.highContrast() ? palette.title() : theme.border(), palette.fill());
        return palette;
    }
}
