// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.GuiGraphics;

/** Draw after the inert context and before modal text/widgets. This does not route input. */
final class ConfirmationGlassPaint {
    private final ConfirmationGlassStyle style = new ConfirmationGlassStyle();

    ConfirmationGlassStyle.Palette draw(GuiGraphics graphics, int x, int y, int width, int height,
                                        UiPreferences preferences) {
        int fill = DarkModeTheme.surfaceColor(), title = DarkModeTheme.textColor();
        int details = DarkModeTheme.secondaryTextColor();
        var palette = style.palette(fill, title, details, false);
        boolean optical = width > 0 && height > 0 && LiquidGlassTheme.useBackdrop(preferences)
                && com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.paintHudGlass(graphics,
                        ConfirmationGlassStyle.surface(x, y, width, height, palette.title()));
        palette = style.palette(fill, title, details, optical);
        if (width > 0 && height > 0) RoundedCorners.draw(graphics, x, y, width, height, 9, 1,
                preferences.highContrast() ? palette.title() : DarkModeTheme.borderColor(), palette.fill());
        return palette;
    }
}
