// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.blur.BackdropCapture;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelSettings;
import java.util.List;

final class MusicGlassPlatform {
    private MusicGlassPlatform() {}

    static boolean draw(UiFrame ui, List<Bounds> areas, boolean lightSurface) {
        var settings = ControlPanelSettings.current();
        if (!settings.glassEnabled() || BackdropCapture.request(ui.graphics()) == null)
            return false;
        var optics = settings.optics(MusicGlass.EXTRACTOR_OPTICS);
        for (var box : areas) {
            if (!LiquidGlassSurface.drawPrepared(ui.graphics(), box.x(), box.y(), box.width(), box.height(),
                    MusicGlass.radius(box), lightSurface ? 0x32eff4ff : 0x32263040,
                    lightSurface ? 0x66617491 : MusicGlass.RIM, optics, ui.preferences(), 1f))
                MusicGlass.fallback(ui, box, lightSurface);
        }
        return true;
    }
}
