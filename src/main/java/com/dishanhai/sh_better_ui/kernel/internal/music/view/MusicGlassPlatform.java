// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import java.util.List;

final class MusicGlassPlatform {
    private MusicGlassPlatform() {}

    static boolean draw(UiFrame ui, List<Bounds> areas, boolean lightSurface) {
        return UiTheme.current().backdrop() && ClientUiSettings.current().backdrop().enabled()
                && ClientUiKernel.paintHudGlassBatch(ui.graphics(), areas.stream().map(box ->
                new GlassDraw(GlassRegion.rounded(box.x(), box.y(), box.width(), box.height(),
                        MusicGlass.radius(box)), lightSurface ? MusicGlass.LIGHT_SURFACE : MusicGlass.MATERIAL, GlassDraw.Profile.SNELL, null)).toList());
    }
}
