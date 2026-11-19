package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Objects;

/** 山海：卡片和其他布局的加载占位。宿主在数据可用后用真实内容替换占位。 */
public final class UiSkeleton {
    private UiSkeleton() {}

    public record Block(Bounds bounds, float cornerRadius) {
        public Block {
            Objects.requireNonNull(bounds, "bounds");
            if (!Float.isFinite(cornerRadius) || cornerRadius < 0f) throw new IllegalArgumentException();
        }

        public Block(Bounds bounds) {
            this(bounds, 3f);
        }
    }

    /** 用真实卡片的区域绘制圆角底板、缩略图和两行占位。时间可用 {@link PageTransition#nowMs()}。 */
    public static void card(GuiGraphics graphics, Bounds area, long nowMs, UiPreferences preferences) {
        render(graphics, area, cardBlocks(area), nowMs, preferences, true, 1f);
    }

    /** 按宿主布局绘制自定义占位块。块和裁切区使用同一组原生 Screen 坐标。 */
    public static void draw(GuiGraphics graphics, Bounds viewport, List<Block> blocks, long nowMs,
                            UiPreferences preferences) {
        draw(graphics, viewport, blocks, nowMs, preferences, 1f);
    }

    /** 在内容入场时用相同的布局淡出占位块。 */
    public static void draw(GuiGraphics graphics, Bounds viewport, List<Block> blocks, long nowMs,
                            UiPreferences preferences, float opacity) {
        if (opacity > 0f) render(graphics, viewport, blocks, nowMs, preferences, false, opacity);
    }

    private static void render(GuiGraphics graphics, Bounds viewport, List<Block> blocks, long nowMs,
                               UiPreferences preferences, boolean card, float opacity) {
        if (viewport.width() == 0 || viewport.height() == 0) return;
        boolean highContrast = preferences.highContrast();
        int base = highContrast ? DarkModeTheme.hoverBorderColor() : GuiMotion.lerpColor(DarkModeTheme.borderColor(), DarkModeTheme.hoverBorderColor(), 0.2f);
        int peak = highContrast ? DarkModeTheme.mutedTextColor() : GuiMotion.lerpColor(DarkModeTheme.borderColor(), DarkModeTheme.hoverBorderColor(), 0.65f);
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            if (card) {
                RoundedCorners.draw(graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height(), 6f, 1f,
                        highContrast ? DarkModeTheme.secondaryTextColor() : DarkModeTheme.borderColor(),
                        highContrast ? DarkModeTheme.backgroundColor() : DarkModeTheme.surfaceColor());
            }
            for (Block block : blocks) {
                Bounds bounds = block.bounds();
                if (!UiSkeletonMotion.intersects(viewport, bounds)) continue;
                int fill = UiSkeletonMotion.fill(viewport, bounds, nowMs, preferences.reducedMotion(), base, peak, opacity);
                RoundedCorners.draw(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                        block.cornerRadius(), 0f, 0, fill);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    static List<Block> cardBlocks(Bounds area) {
        if (area.width() == 0 || area.height() == 0) return List.of();
        int padding = Math.min(8, Math.min(area.width(), area.height()) / 4);
        int x = area.x() + padding;
        int y = area.y() + padding;
        int width = area.width() - 2 * padding;
        int height = area.height() - 2 * padding;
        int thumbnail = Math.min(36, Math.min(width / 3, height));
        int gap = Math.min(6, Math.max(1, width / 12));
        int textWidth = width - thumbnail - gap;
        if (thumbnail < 6 || textWidth < 8 || height < 9) {
            return List.of(new Block(new Bounds(x, y, width, height)));
        }

        int line = Math.min(5, height / 3);
        int lineGap = Math.min(4, height - 2 * line);
        int textX = x + thumbnail + gap;
        int textY = y + (height - 2 * line - lineGap) / 2;
        return List.of(new Block(new Bounds(x, y + (height - thumbnail) / 2, thumbnail, thumbnail), 5f),
                new Block(new Bounds(textX, textY, Math.round(textWidth * 0.78f), line)),
                new Block(new Bounds(textX, textY + line + lineGap, Math.round(textWidth * 0.52f), line)));
    }

    static float highlight(Bounds viewport, Bounds bounds, long nowMs, boolean reducedMotion) {
        return UiSkeletonMotion.highlight(viewport, bounds, nowMs, reducedMotion);
    }
}
