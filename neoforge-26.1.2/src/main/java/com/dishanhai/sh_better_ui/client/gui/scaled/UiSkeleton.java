// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Objects;

/** Extractor backend for the UUK skeleton renderer. Animation math is shared with the legacy backend. */
public final class UiSkeleton {
    private UiSkeleton() {}

    public record Block(Bounds bounds, float cornerRadius) {
        public Block {
            Objects.requireNonNull(bounds, "bounds");
            if (!Float.isFinite(cornerRadius) || cornerRadius < 0f) throw new IllegalArgumentException();
        }

        public Block(Bounds bounds) { this(bounds, 3f); }
    }

    public static void draw(GuiGraphicsExtractor graphics, Bounds viewport, List<Block> blocks, long nowMs,
                            UiPreferences preferences) {
        draw(graphics, viewport, blocks, nowMs, preferences, 1f);
    }

    public static void draw(GuiGraphicsExtractor graphics, Bounds viewport, List<Block> blocks, long nowMs,
                            UiPreferences preferences, float opacity) {
        if (!(opacity > 0f) || viewport.width() <= 0 || viewport.height() <= 0) return;
        var theme = UiTheme.current();
        boolean contrast = preferences.highContrast();
        int base = GuiMotion.lerpColor(theme.border(), theme.text(), contrast ? .48f : .10f);
        int peak = GuiMotion.lerpColor(theme.border(), theme.text(), contrast ? .75f : .30f);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            for (var block : blocks) {
                var bounds = block.bounds();
                if (!UiSkeletonMotion.intersects(viewport, bounds)) continue;
                int fill = UiSkeletonMotion.fill(viewport, bounds, nowMs, preferences.reducedMotion(), base, peak, opacity);
                RoundedCorners.draw(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                        block.cornerRadius(), 0f, 0, fill);
            }
        } finally {
            graphics.disableScissor();
        }
    }
}
