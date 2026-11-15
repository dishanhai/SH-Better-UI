// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.motion.PhotonKnot;

/** Draws a transparent UUK ribbon on the current GUI layer. */
public final class UiPhotonKnot {
    private UiPhotonKnot() {}

    public static void draw(UiFrame frame, Bounds area, PhotonKnot knot) {
        float scale = Math.min(area.width(), area.height());
        if (scale <= 0) {
            knot.suspend();
            return;
        }
        var theme = UiTheme.current();
        boolean highContrast = frame.preferences().highContrast();
        var mesh = new GalleryFieldMesh();
        float centerX = area.width() / 2f, centerY = area.height() / 2f;
        knot.sample(frame.nowMs(), highContrast || frame.preferences().reducedMotion(),
                highContrast ? theme.text() : theme.accent(), theme.text(),
                (x, y, argb) -> mesh.vertex(centerX + x * scale, centerY + y * scale, argb));
        mesh.submit(frame.graphics(), area);
    }
}
