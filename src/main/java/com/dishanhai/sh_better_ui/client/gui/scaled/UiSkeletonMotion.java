package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** Shared skeleton sweep and compositing, independent of the native graphics backend. */
final class UiSkeletonMotion {
    private static final long PERIOD_MS = 1800L;
    private static final float HIGHLIGHT_SPREAD = 0.4f;

    private UiSkeletonMotion() {}

    static boolean intersects(Bounds viewport, Bounds block) {
        return block.width() > 0 && block.height() > 0 && block.right() > viewport.x()
                && block.bottom() > viewport.y() && block.x() < viewport.right() && block.y() < viewport.bottom();
    }

    static int fill(Bounds viewport, Bounds block, long nowMs, boolean reducedMotion, int base, int peak, float opacity) {
        int color = GuiMotion.lerpColor(base, peak, highlight(viewport, block, nowMs, reducedMotion));
        return GuiMotion.lerpColor(color & 0xFFFFFF, color, opacity);
    }

    static float highlight(Bounds viewport, Bounds bounds, long nowMs, boolean reducedMotion) {
        if (reducedMotion || viewport.width() == 0) return 0f;
        float phase = (float) Math.floorMod(nowMs, PERIOD_MS) / PERIOD_MS;
        float relativeCenter = GuiMotion.clamp01(
                ((bounds.x() - (float) viewport.x()) + bounds.width() * 0.5f) / viewport.width());
        float sweep = GuiMotion.lerp(-HIGHLIGHT_SPREAD, 1f + HIGHLIGHT_SPREAD, phase);
        float brightness = 1f - GuiMotion.clamp01(Math.abs(sweep - relativeCenter) / HIGHLIGHT_SPREAD);
        return brightness * brightness * (3f - 2f * brightness);
    }
}
