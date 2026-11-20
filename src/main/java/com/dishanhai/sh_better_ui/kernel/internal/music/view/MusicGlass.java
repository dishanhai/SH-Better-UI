// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import java.util.ArrayList;

/** Paints only the control chrome so artwork, lyrics and row labels stay in the foreground. */
final class MusicGlass {
    static final int DARK = 0xff171920;
    static final int CANVAS_TOP = 0xff262b39;
    static final int RIM = 0x66c6d8ef;
    static final GlassMaterial MATERIAL = new GlassMaterial(GlassVariant.CLEAR,
            GlassMaterial.preset(GlassVariant.CLEAR).optics(), GlassQuality.PERFORMANCE,
            GlassMaterial.Foreground.LIGHT);
    static final GlassOptics EXTRACTOR_OPTICS = new GlassOptics(.93f, 4.6f, .42f, .12f, .78f, 4f, 0f);

    private MusicGlass() {}

    static boolean active() {
        return MusicAppearance.liquidGlass(ClientUiSettings.current().theme().mode(), UiTheme.current().liquidGlass());
    }

    static void canvas(UiFrame ui, Bounds frame) {
        ui.graphics().fillGradient(frame.x(), frame.y(), frame.right(), frame.bottom(),
                CANVAS_TOP, DARK);
    }

    static void chrome(UiFrame ui, Bounds first, Bounds second) {
        // Always submit related plates together, before artwork or widgets can enter the snapshot.
        var areas = new ArrayList<Bounds>(2);
        if (nonEmpty(first)) areas.add(first);
        if (nonEmpty(second)) areas.add(second);
        if (areas.isEmpty()) return;
        boolean optical = ui.preferences().useBackdropBlur() && MusicGlassPlatform.draw(ui, areas);
        if (!optical) for (var area : areas) fallback(ui, area);
    }

    static float radius(Bounds box) {
        return Math.min(13f, Math.min(box.width(), box.height()) / 2f);
    }

    private static boolean nonEmpty(Bounds box) {
        return box != null && box.width() > 0 && box.height() > 0;
    }

    static void fallback(UiFrame ui, Bounds box) {
        boolean contrast = ui.preferences().highContrast();
        RoundedCorners.draw(ui.graphics(), box.x(), box.y(), box.width(), box.height(), radius(box),
                contrast ? 1.5f : .75f, contrast ? UiTheme.current().text() : RIM,
                contrast ? UiTheme.current().background() | 0xff000000 : 0xff262b36);
    }
}
