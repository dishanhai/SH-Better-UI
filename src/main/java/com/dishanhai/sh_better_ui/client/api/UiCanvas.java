// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Portable drawing contract for a screen's render callback. All coordinates are logical
 * GUI pixels; neither the immediate renderer nor the GUI extractor appears in this API.
 * Use only during the callback that supplied the frame.
 */
public interface UiCanvas {
    Bounds viewport();
    UiPreferences preferences();
    long nowMs();
    float partialTick();

    void fill(Bounds bounds, int argb);

    void drawScaledText(Font font, Component text, int x, int y, int argb, float scale);

    /** Draws with the current frame's preferences and the platform's normal SVG/texture selection. */
    void icon(GraphicIcon icon, int x, int y, int scale, int argb);

    /** Intersects with the host clip, restores it on return, and skips an empty region. */
    void clipped(Bounds bounds, Runnable content);

    /** Draw after the backdrop and before foreground text. Returns the presentation path used. */
    default UiGlassPanel.Paint glass(UiGlassPanel panel) {
        if (panel.empty()) return UiGlassPanel.Paint.EMPTY;
        fill(panel.bounds(), panel.fallbackFill(preferences()));
        return UiGlassPanel.Paint.SOLID;
    }

    /** Paints in a resolved layout node's local coordinates, with its screen-space clip. */
    void placed(AlignmentLayout.View view, Runnable content);

    /** Creates a layout for this viewport. Retain it per screen, not per frame. */
    default AlignmentLayout createLayout() {
        return new AlignmentLayout(new Rect(viewport()));
    }
}
