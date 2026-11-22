// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.display.SvgIconTexture;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.List;

/** Native HUD rows and a single-surface capsule reveal for the 26 GUI extractor. */
public final class UiHudPanel {
    private static final int MARGIN = 4, PADDING = 6, ICON_SIZE = 8, TEXT_X = 18;
    private static final int TEXT = 0xFFF7F7FA;
    private final List<Row> rows;
    private final int width, height, iconSize;
    private final TextReadabilityPalette palette = new TextReadabilityPalette();

    public UiHudPanel(Font font, List<Row> rows) {
        this(font, rows, 0);
    }

    /** Reserves additional text space in unscaled GUI pixels for changing HUD values. */
    public UiHudPanel(Font font, List<Row> rows, int minimumTextWidth) {
        this(font, rows, minimumTextWidth, ICON_SIZE);
    }

    /** Resizes row icons within the existing icon lane without changing the text layout. */
    public UiHudPanel(Font font, List<Row> rows, int minimumTextWidth, int iconSize) {
        this.rows = List.copyOf(rows);
        this.iconSize = UiMath.clamp(iconSize, 1, Math.min(font.lineHeight + 3, ICON_SIZE + 2));
        int textWidth = this.rows.stream().mapToInt(row -> font.width(row.text())).max().orElse(0);
        width = TEXT_X + PADDING + Math.max(textWidth, minimumTextWidth);
        height = rows.size() * (font.lineHeight + 3) + 2 * PADDING;
    }

    public boolean isEmpty() { return rows.isEmpty(); }

    public Placement fit(int viewportWidth, int viewportHeight) {
        return fit(viewportWidth, viewportHeight, 1);
    }

    public Placement fit(int viewportWidth, int viewportHeight, float requested) {
        if (!Float.isFinite(requested) || requested <= 0) throw new IllegalArgumentException("Invalid HUD scale");
        int availableWidth = Math.max(1, viewportWidth - 2 * MARGIN);
        int availableHeight = Math.max(1, viewportHeight - 2 * MARGIN);
        float scale = Math.min(requested, Math.min((float) availableWidth / width, (float) availableHeight / height));
        int drawnWidth = Math.min(availableWidth, (int) Math.ceil(width * scale));
        int drawnHeight = Math.min(availableHeight, (int) Math.ceil(height * scale));
        return new Placement(drawnWidth, drawnHeight, availableWidth - drawnWidth, availableHeight - drawnHeight, scale);
    }

    public void drawSelected(GuiGraphicsExtractor graphics, Font font, UiPreferences preferences,
                             int x, int y, float scale, int selected) {
        if (isEmpty()) return;
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).scale(scale);
            surface(graphics, 0, 0, width, height, 6, preferences, false);
            foreground(graphics, font, preferences, selected, scale);
        } finally { pose.popMatrix(); }
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, UiPreferences preferences,
                     int x, int y, float scale) {
        drawSelected(graphics, font, preferences, x, y, scale, -1);
    }

    /** Draws the animated HUD surface once and clips the foreground to its current bounds. */
    public void drawReveal(GuiGraphicsExtractor graphics, Font font, UiPreferences preferences,
                           int x, int y, float scale, int selected,
                           int frameX, int frameY, int frameWidth, int frameHeight, float progress) {
        if (isEmpty() || frameWidth <= 0 || frameHeight <= 0 || progress <= 0) return;
        surface(graphics, frameX, frameY, frameWidth, frameHeight, 6 * scale, preferences, false);
        graphics.enableScissor(frameX, frameY, frameX + frameWidth, frameY + frameHeight);
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).scale(scale);
            foreground(graphics, font, preferences, selected, scale);
        } finally {
            pose.popMatrix();
            graphics.disableScissor();
        }
    }

    private void foreground(GuiGraphicsExtractor graphics, Font font, UiPreferences preferences, int selected, float scale) {
        if (selected >= 0 && selected < rows.size()) {
            int rowY = PADDING + selected * (font.lineHeight + 3);
            surface(graphics, PADDING - 2, rowY, width - 2 * PADDING + 4, font.lineHeight + 3, 6, preferences, true);
        }
        palette.update(null, 0xff000000, preferences.highContrast() ? 0xff000000 : 0xff343b46);
        int color = palette.color(TEXT);
        int lineY = PADDING + 1;
        for (var row : rows) {
            if ((row.color() >>> 24) != 0) {
                // Keep the original icon centre and preserve the text/keycap geometry.
                int offset = Math.floorDiv(ICON_SIZE - iconSize, 2);
                var svg = SvgIconTexture.find(row.icon(), iconSize * scale);
                int texels = svg == null ? GraphicIcon.PIXELS : svg.side();
                int tint = preferences.highContrast() ? (color & 0x00ffffff) | (row.color() & 0xff000000) : row.color();
                graphics.blit(RenderPipelines.GUI_TEXTURED, svg == null ? row.icon() : svg.id(),
                        PADDING + offset, lineY + offset, 0f, 0f,
                        iconSize, iconSize, texels, texels, texels, texels, tint);
            }
            UiHudRichText.draw(graphics, font, row.text().getVisualOrderText(), TEXT_X, lineY, color, preferences, palette);
            lineY += font.lineHeight + 3;
        }
    }

    private static void surface(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float radius,
                                UiPreferences preferences, boolean selected) {
        int border = preferences.highContrast() ? TEXT : 0xCC8393A7;
        var base = GlassMaterial.preset(GlassVariant.REGULAR).optics();
        var optics = selected ? new GlassOptics(base.transmission(), base.refraction(), base.lensing(), 0.9f,
                base.edgeHighlight(), base.thickness(), 20f) : base;
        int tint = selected ? 0xD018202C : 0xB018202C;
        if (preferences.useBackdropBlur() && ShBetterUiApi.blur().prepareBackdrop(graphics)
                && LiquidGlassSurface.drawPrepared(graphics, x, y, width, height, radius, tint, border, optics, preferences, 1)) return;
        RoundedCorners.draw(graphics, x, y, width, height, radius, 1, border,
                preferences.highContrast() ? 0xFF000000 : 0xFF18202C);
    }

    public static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("sh_better_ui", "textures/gui/display/" + name + ".png");
    }

    public record Row(Identifier icon, int color, Component text) {
        public Row { text = UiHudRichText.prepare(text); }

        /** HUD rows use the icon's static texture and tint; its miniature remains unanimated. */
        public Row(GraphicIcon icon, int iconColor, Component text) { this(icon.texture(), iconColor, text); }

        public int iconColor() { return color; }
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
