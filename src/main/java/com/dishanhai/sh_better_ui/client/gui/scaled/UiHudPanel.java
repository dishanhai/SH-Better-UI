// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.glass.GlassSurface;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Compact themed rows with the same size and placement in the HUD and in a preview. */
public final class UiHudPanel {
    private static final int MARGIN = 4;
    private static final int PADDING = 6;
    private static final int ICON_SIZE = GraphicIcon.PIXELS / 2;
    private static final int TEXT_X = PADDING + ICON_SIZE + 4;

    private final List<Row> rows;
    private final int width;
    private final int height;
    private final TextReadabilityPalette palette = new TextReadabilityPalette();

    public UiHudPanel(Font font, List<Row> rows) {
        this.rows = List.copyOf(rows);
        int textWidth = this.rows.stream().mapToInt(row -> font.width(row.text())).max().orElse(0);
        width = TEXT_X + textWidth + PADDING;
        height = this.rows.size() * rowHeight(font) + 2 * PADDING;
    }

    public boolean isEmpty() { return rows.isEmpty(); }

    public Placement fit(int screenWidth, int screenHeight) {
        return fit(screenWidth, screenHeight, 1);
    }

    /** Fits a requested positive scale inside the viewport while keeping the screen margins. */
    public Placement fit(int screenWidth, int screenHeight, float requestedScale) {
        if (!Float.isFinite(requestedScale) || requestedScale <= 0) {
            throw new IllegalArgumentException("Invalid HUD scale");
        }
        int availableWidth = Math.max(1, screenWidth - 2 * MARGIN);
        int availableHeight = Math.max(1, screenHeight - 2 * MARGIN);
        float scale = Math.min(requestedScale, Math.min((float) availableWidth / width, (float) availableHeight / height));
        int drawnWidth = Math.min(availableWidth, (int) Math.ceil(width * scale));
        int drawnHeight = Math.min(availableHeight, (int) Math.ceil(height * scale));
        return new Placement(drawnWidth, drawnHeight, availableWidth - drawnWidth, availableHeight - drawnHeight, scale);
    }

    public void draw(GuiGraphics graphics, Font font, UiPreferences preferences, int x, int y, float scale) {
        draw(graphics, font, preferences, x, y, scale, null);
    }

    /** Use a stable, distinct sample ID for each HUD panel and its editor preview. */
    public void draw(GuiGraphics graphics, Font font, UiPreferences preferences, int x, int y, float scale, UiId sampleId) {
        if (isEmpty()) return;
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1);
            int textColor = LiquidGlassTheme.textColor();
            boolean useGlass = preferences.useBackdropBlur();
            boolean glass = useGlass && paintGlass(graphics, textColor);
            int darkest;
            int lightest;
            if (!glass) {
                int backdrop = DarkModeTheme.surfaceColor();
                LiquidGlassTheme.plate(graphics, 0, 0, width, height,
                        preferences.highContrast() ? textColor : DarkModeTheme.borderColor(),
                        backdrop, preferences);
                darkest = GuiMotion.lerpColor(0xff000000, backdrop | 0xff000000, (backdrop >>> 24) / 255f);
                lightest = GuiMotion.lerpColor(0xffffffff, backdrop | 0xff000000, (backdrop >>> 24) / 255f);
            } else {
                int luma = ((textColor >> 16 & 255) * 54 + (textColor >> 8 & 255) * 183 + (textColor & 255) * 19) >> 8;
                // Snell caps/floors the protected interior at 0.18 linear luminance.
                // Round the sRGB bounds outward to include quantized framebuffer colors.
                darkest = luma >= 128 ? 0xff000000 : 0xff757575;
                lightest = luma >= 128 ? 0xff767676 : 0xffffffff;
            }
            var sample = glass ? ClientUiKernel.sampleHudText(graphics, sampleId,
                    new Rect(TEXT_X, PADDING, width - TEXT_X - PADDING, height - 2 * PADDING)) : null;
            var sharedPalette = ClientUiKernel.hudTextPalette(sampleId);
            var framePalette = sharedPalette == null ? palette : sharedPalette;
            framePalette.update(sample, darkest, lightest);
            textColor = framePalette.baseColor(textColor);
            int rowHeight = rowHeight(font);
            int iconY = PADDING + (rowHeight - ICON_SIZE) / 2;
            pose.pushPose();
            try {
                pose.scale(0.5f, 0.5f, 1);
                for (var row : rows) {
                    row.icon().draw(graphics, PADDING * 2, iconY * 2, 1,
                            preferences.highContrast() ? textColor : row.iconColor());
                    iconY += rowHeight;
                }
            } finally {
                pose.popPose();
            }
            int lineY = PADDING + (rowHeight - font.lineHeight) / 2;
            if (glass && !preferences.highContrast() && supportsSoftText(scale)) {
                graphics.flush();
                int haloY = lineY;
                for (var row : rows) {
                    GlassSurface.drawTextHaloInBatch(graphics, font, row.text().getVisualOrderText(), TEXT_X, haloY, textColor);
                    haloY += rowHeight;
                }
                graphics.flush();
            }
            for (var row : rows) {
                UiHudRichText.draw(graphics, font, row.text().getVisualOrderText(), TEXT_X, lineY,
                        textColor, preferences, framePalette);
                lineY += rowHeight;
            }
        } finally {
            pose.popPose();
        }
    }

    private static boolean supportsSoftText(float scale) {
        var window = Minecraft.getInstance().getWindow();
        double pixelsPerUnit = window.getGuiScale() * scale;
        // The outline can obscure thin bitmap strokes in small or fractionally scaled HUDs.
        return window.getWidth() >= 800 && window.getHeight() >= 600 && pixelsPerUnit >= 2
                && Math.abs(pixelsPerUnit - Math.rint(pixelsPerUnit)) < 0.000001;
    }

    private boolean paintGlass(GuiGraphics graphics, int textColor) {
        var region = GlassRegion.rounded(0, 0, width, height, 6);
        var foreground = new GlassDraw.ForegroundBounds(PADDING - 1, PADDING - 1,
                width - 2 * PADDING + 2, height - 2 * PADDING + 2);
        return LiquidGlassTheme.clearSurface(graphics, region, foreground, textColor);
    }

    private static int rowHeight(Font font) {
        return Math.max(ICON_SIZE, font.lineHeight) + 3;
    }

    public record Row(GraphicIcon icon, int iconColor, Component text) {
        public Row {
            text = UiHudRichText.prepare(text);
        }
    }

    public record Placement(int width, int height, int travelX, int travelY, float scale) {
        public int x(double fraction) { return MARGIN + (int) Math.round(travelX * fraction); }
        public int y(double fraction) { return MARGIN + (int) Math.round(travelY * fraction); }

        public double moveX(double x, double current) { return fraction(x, travelX, current); }
        public double moveY(double y, double current) { return fraction(y, travelY, current); }

        private static double fraction(double coordinate, int travel, double current) {
            return travel == 0 ? current : Math.max(0, Math.min(1, (coordinate - MARGIN) / travel));
        }
    }
}
