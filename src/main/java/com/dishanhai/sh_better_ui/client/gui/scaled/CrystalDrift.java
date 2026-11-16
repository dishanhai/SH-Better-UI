package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.BackgroundSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Screen-owned original floating crystal background. Input is observed, never consumed. */
public final class CrystalDrift implements BackgroundSpec.Renderer {
    public static final String ID = "sh_better_ui:crystal_drift";
    private final CrystalDriftField field = new CrystalDriftField();
    private final UiPointerTracker pointer = new UiPointerTracker();
    private boolean canPulse;

    @Override
    public void draw(GuiGraphics g, Font font, Bounds area, UiMouseState mouse, double x, double y,
                     long now, UiPreferences prefs) {
        var p = ThemeEffects.profile(ID, CrystalDriftProfile.DEFAULT);
        boolean enabled = area.width() > 0 && area.height() > 0 && p.opacity() > 0 &&
                p.mouseInteraction() && !prefs.reducedMotion() && !prefs.highContrast();
        var cursor = pointer.update(area, g.pose().last().pose(), mouse, enabled, now);
        canPulse = enabled && cursor.inside() && p.pulseOnClick();
        if (area.width() <= 0 || area.height() <= 0 || prefs.highContrast() || p.opacity() <= 0) {
            field.suspend();
            return;
        }
        field.update(now, p, prefs.reducedMotion(), cursor.x() / area.width(), cursor.y() / area.height(),
                enabled && cursor.inside(), cursor.down());
        int alpha = Math.round((p.backgroundColor() >>> 24) * p.opacity());
        if (alpha > 0) g.fill(area.x(), area.y(), area.right(), area.bottom(),
                (alpha << 24) | (p.backgroundColor() & 0xFFFFFF));
        // Missing shader or unsupported pose leaves the selected flat background, not a crash.
        CrystalDriftRenderer.draw(g, area, p, field);
    }

    /** Host forwards unconsumed background clicks in this layer's local GUI coordinates. */
    @Override
    public void click(Bounds area, double x, double y, long now) {
        if (canPulse && area.width() > 0 && area.height() > 0 && area.contains(x, y)) {
            field.pulse((float) ((x - area.x()) / area.width()), (float) ((y - area.y()) / area.height()));
        }
    }

    @Override
    public void close() {
        canPulse = false;
        pointer.reset();
        field.suspend();
    }
}
