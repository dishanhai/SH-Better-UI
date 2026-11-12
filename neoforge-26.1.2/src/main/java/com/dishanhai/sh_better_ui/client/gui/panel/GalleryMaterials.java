// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.BackdropCapture;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The 26.x renderer's glass materials over the gallery's sample backdrop. */
final class GalleryMaterials {
    private static final int HORIZONTAL_INSET = 8;
    private static final int VERTICAL_INSET = 5;

    private GalleryMaterials() {}

    static void draw(GuiGraphicsExtractor graphics, Bounds canvas, Preset preset, UiPreferences preferences) {
        int width = canvas.width() - 2 * HORIZONTAL_INSET;
        int height = canvas.height() - 2 * VERTICAL_INSET;
        if (width <= 0 || height <= 0) return;

        int x = canvas.x() + HORIZONTAL_INSET;
        int y = canvas.y() + VERTICAL_INSET;
        LiquidGlassSurface.draw(graphics, x, y, width, height, 5f, preset.tint, 0xB8D0E9FF,
                preset.optics, preferences);
    }

    /** UiButton reads a snapshot prepared by the screen after it draws the sample backdrop. */
    static void prepareButtonBackdrop(GuiGraphicsExtractor graphics, UiPreferences preferences) {
        if (preferences.useBackdropBlur()) BackdropCapture.request(graphics);
    }

    enum Preset {
        CLEAR(GlassVariant.CLEAR, 0x24213B56),
        FROSTED(GlassVariant.REGULAR, 0x54252B34);

        private final GlassOptics optics;
        private final int tint;

        Preset(GlassVariant variant, int tint) {
            this.optics = GlassMaterial.preset(variant).optics();
            this.tint = tint;
        }
    }
}
