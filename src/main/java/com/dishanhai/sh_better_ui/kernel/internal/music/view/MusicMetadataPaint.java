// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Uniform text-run scaling preserves shaping, glyph proportions and the final ellipsis. */
final class MusicMetadataPaint {
    static void line(UiFrame ui, Font font, Component text, int x, int y, int width,
                     int color, float baseScale, MusicMetadataReveal.Frame frame) {
        if (width <= 0) return;
        var line = MusicTextLayout.ellipsize(font, MusicTypography.text(text),
                Math.max(1, (int) (width / baseScale)));
        float scale = baseScale * frame.scale();
        int ink = (Math.round((color >>> 24) * frame.opacity()) << 24) | (color & 0x00ffffff);
        var pose = ui.graphics().pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1);
            ui.graphics().drawString(font, line, 0, 0, ink, false);
        } finally { pose.popPose(); }
    }
}
