// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeletonMotion;
import com.muoniumplayer.core.rendering.RGBA;
import com.muoniumplayer.core.screens.ncm.NCMScreen;
import com.muoniumplayer.core.screens.ncm.NCMScreen.ColorType;
import com.muoniumplayer.core.screens.ncm.effects.EffectsLayout.Box;
import com.muoniumplayer.render.skija.SkijaGfx;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.types.RRect;

/** Layout and placeholder painting shared by catalogue navigation and detail pages. */
public final class PlayingCatalogPresentation {
    static final int INSET = 18;

    private PlayingCatalogPresentation() {}

    public static double coverSize(double width, double height) {
        return Math.max(38, Math.min(104, Math.min(width * .22, height * .34)));
    }

    static double tabsY(double cover, boolean credits) {
        return Math.max(credits ? 94 : 82, cover + 20);
    }

    public static void skeleton(Box frame, boolean artist, boolean credits, boolean featured,
                                long nowMs, UiPreferences preferences, float alpha) {
        if (frame.width() < 100 || frame.height() < 68) return;
        var blocks = painter(frame, nowMs, preferences, alpha);
        if (blocks == null) return;
        double x = frame.x() + INSET;
        double y = frame.y();
        double cover = coverSize(frame.width(), frame.height());
        double textX = x + cover + 12;
        double textWidth = Math.max(0, frame.right() - INSET - textX);
        double tabsY = y + tabsY(cover, credits);
        blocks.paint(x, y + 12, cover, cover, artist ? cover / 2 : 6);
        blocks.paint(textX, y + 18, textWidth * .65, cover < 68 ? 17 : 23, 4);
        blocks.paint(textX, y + (cover < 68 ? 41 : 49), textWidth * .43, 8, 4);
        if (credits) blocks.paint(textX, y + (cover < 68 ? 54 : 64), textWidth * .31, 7, 4);
        blocks.paint(textX, y + Math.max(58 + (credits ? 11 : 0), cover - 10), Math.min(86, textWidth), 19, 5);
        double available = frame.width() - 2 * INSET;
        for (int i = 0; i < (artist ? 3 : 2); i++) {
            double step = Math.min(90, available / (artist ? 3 : 2));
            blocks.paint(x + i * step + 6, tabsY + 6, step * .65, 8, 4);
        }
        double heading = tabsY + 25;
        if (featured && frame.bottom() - heading >= 78) {
            double width = (available - 8) / 2;
            for (int i = 0; i < 2; i++) {
                double cardX = x + i * (width + 8);
                blocks.paint(cardX + 4, heading + 4, 26, 26, 4);
                blocks.paint(cardX + 36, heading + 9, Math.max(0, (width - 38) * .74), 8, 4);
            }
            heading += 44;
        }
        blocks.paint(x, heading + 6, Math.min(85, available * .3), 9, 4);
        for (int i = 0; i < 8; i++) {
            double row = heading + 22 + i * 30;
            if (row + 26 > frame.bottom()) break;
            blocks.paint(x + 3, row + 3, 24, 24, 4);
            blocks.paint(x + 36, row + 5, (available - 45) * (.46 - i % 3 * .055), 7, 4);
            blocks.paint(x + 36, row + 17, (available - 45) * .24, 5, 3);
        }
    }

    /** Initial recommendations, aligned with the home page's live grid geometry. */
    public static void playlistGridSkeleton(Box frame, double cardWidth, double coverSize, double gap,
                                            long nowMs, UiPreferences preferences, float alpha) {
        var blocks = painter(frame, nowMs, preferences, alpha);
        if (blocks == null || coverSize < 16) return;
        double inset = (cardWidth - coverSize) * .5;
        double step = cardWidth + gap;
        for (double y = frame.y() + inset; y + 12 <= frame.bottom(); y += step + 24) {
            for (double x = frame.x() + inset; x + coverSize <= frame.right(); x += step) {
                blocks.paint(x, y, coverSize, Math.min(coverSize, frame.bottom() - y), 6);
                blocks.paint(x, y + coverSize + 7, coverSize * .74, 7, 4);
                blocks.paint(x, y + coverSize + 20, coverSize * .48, 6, 3);
            }
        }
    }

    /** Initial song rows, aligned with 30-unit rows and their 24-unit covers. */
    public static void trackListSkeleton(Box frame, long nowMs, UiPreferences preferences, float alpha) {
        var blocks = painter(frame, nowMs, preferences, alpha);
        if (blocks == null || frame.width() < 62) return;
        double textWidth = frame.width() - 66;
        for (double y = frame.y(); y + 27 <= frame.bottom(); y += 30) {
            blocks.paint(frame.x() + 12, y + 12, 8, 6, 3);
            blocks.paint(frame.x() + 30, y + 3, 24, 24, 2);
            blocks.paint(frame.x() + 58, y + 6, textWidth * .58, 7, 4);
            blocks.paint(frame.x() + 58, y + 19, textWidth * .36, 5, 3);
        }
    }

    private static Skeleton painter(Box frame, long now, UiPreferences preferences, float alpha) {
        var canvas = SkijaGfx.canvas();
        if (canvas == null || alpha <= .01f || frame.width() < 1 || frame.height() < 1) return null;
        int ink = NCMScreen.getColor(ColorType.PRIMARY_TEXT);
        return new Skeleton(canvas, area(frame.x(), frame.y(), frame.width(), frame.height()), now,
                preferences.reducedMotion(), RGBA.color(ink, preferences.highContrast() ? .47f : .12f),
                RGBA.color(ink, preferences.highContrast() ? .67f : .27f), alpha);
    }

    private static Bounds area(double x, double y, double width, double height) {
        return new Bounds((int) x, (int) y, (int) width, (int) height);
    }

    private record Skeleton(Canvas canvas, Bounds viewport, long now, boolean reduced,
                            int base, int peak, float alpha) {
        void paint(double x, double y, double width, double height, double radius) {
            if (width < 1 || height < 1 || y + height > viewport.bottom()) return;
            int fill = UiSkeletonMotion.fill(viewport, area(x, y, width, height), now, reduced, base, peak, alpha);
            canvas.drawRRect(RRect.makeXYWH((float) x, (float) y, (float) width, (float) height, (float) radius),
                    SkijaGfx.fill(fill));
        }
    }
}
