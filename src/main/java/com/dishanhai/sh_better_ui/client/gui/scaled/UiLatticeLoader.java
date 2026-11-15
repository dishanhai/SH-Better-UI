// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader;
import com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader.Pattern;
import com.dishanhai.sh_better_ui.kernel.api.motion.LatticeLoader.Status;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Draws a UUK lattice status row in the current GUI pass. The caller owns its operation state. */
public final class UiLatticeLoader {
    private static final String KEY = "sh_better_ui.control.lattice_loader.";
    private static final Status[] LAYERS = Status.values();

    public enum Shape { ROUND, SQUARE }

    public record Style(Shape shape, int cellSize, int gap, int fontSize, double stepMs, float idleOpacity,
                        Integer color, int doneColor, int errorColor, boolean glow, Integer glowColor,
                        boolean showTimer) {
        public static final Style DEFAULT = new Style(Shape.ROUND, 6, 2, 14, 90, .15f,
                null, 0xFF22C55E, 0xFFEF4444, false, null, true);

        public Style {
            Objects.requireNonNull(shape, "shape");
            if (cellSize <= 0 || gap < 0 || fontSize <= 0 || !Double.isFinite(stepMs) || stepMs <= 0
                    || !Float.isFinite(idleOpacity) || idleOpacity < 0 || idleOpacity > 1) {
                throw new IllegalArgumentException("Invalid lattice loader style");
            }
        }
    }

    public record Labels(Component working, Component done, Component error) {
        public static final Labels DEFAULT = new Labels(Component.translatable(KEY + "working"),
                Component.translatable(KEY + "done"), Component.translatable(KEY + "error"));

        public Component label(Status status) {
            return switch (status) {
                case WORKING -> working;
                case DONE -> done;
                case ERROR -> error;
            };
        }
    }

    private UiLatticeLoader() {}

    public static int gridSize(Pattern pattern, Style style) {
        return pattern.grid() * style.cellSize() + (pattern.grid() - 1) * style.gap();
    }

    /** Draws a row at its top left. An elapsed override replaces only the stopwatch (in seconds). */
    public static void draw(UiFrame frame, Font font, int x, int y, LatticeLoader loader,
                            Pattern pattern, Style style, Labels labels, Double elapsedSeconds) {
        boolean contrast = frame.preferences().highContrast();
        var sample = loader.frame(frame.nowMs(), frame.preferences().reducedMotion());
        int color = style.color() == null || contrast ? UiTheme.current().text() : style.color();
        int size = gridSize(pattern, style);

        for (Status layer : LAYERS) {
            if (sample.opacity(layer) > 0) drawGrid(frame, x, y, size, pattern, style, sample, layer, color);
        }

        float scale = style.fontSize() / 9f;
        int padding = Math.round(style.fontSize() * .625f);
        int textX = x + size + padding;
        int textY = y + Math.round((size - font.lineHeight * scale) / 2);
        int labelWidth = 0;
        for (Status layer : LAYERS) {
            int textColor = alpha(color, sample.opacity(layer));
            if (textColor >>> 24 < 4) continue;
            Component label = labels.label(layer);
            frame.drawScaledText(font, label, textX, textY, textColor, scale);
            labelWidth = Math.max(labelWidth, font.width(label));
        }
        if (style.showTimer()) {
            float timerScale = scale * .875f;
            int timerX = textX + (int) Math.ceil(labelWidth * scale) + padding;
            int timerY = y + Math.round((size - font.lineHeight * timerScale) / 2);
            Component timer = time(elapsedTenths(loader, frame.nowMs(), elapsedSeconds), false);
            frame.drawScaledText(font, timer, timerX, timerY, alpha(color, contrast ? 1 : .6), timerScale);
        }
    }

    /** Stable narration text. Announce on operation state changes to avoid reading the live stopwatch. */
    public static Component narration(LatticeLoader loader, long nowMs, Style style, Labels labels,
                                      Double elapsedSeconds) {
        Component label = labels.label(loader.status());
        if (loader.status() == Status.WORKING || !style.showTimer()) return label;
        return Component.translatable(KEY + "result", label, time(elapsedTenths(loader, nowMs, elapsedSeconds), true));
    }

    private static long elapsedTenths(LatticeLoader loader, long nowMs, Double elapsedSeconds) {
        if (elapsedSeconds == null) return loader.elapsedMs(nowMs) / 100;
        return Double.isFinite(elapsedSeconds) ? Math.max(0, Math.round(elapsedSeconds * 10)) : 0;
    }

    private static Component time(long tenths, boolean spoken) {
        String seconds = ((tenths % 600) / 10) + "." + (tenths % 10);
        String key = KEY + (spoken ? "spoken_" : "");
        return tenths < 600 ? Component.translatable(key + "seconds", seconds)
                : Component.translatable(key + "minutes", tenths / 600, seconds);
    }

    private static void drawGrid(UiFrame frame, int x, int y, int size, Pattern pattern, Style style,
                                 LatticeLoader.Frame sample, Status layer, int color) {
        int grid = pattern.grid();
        int pitch = style.cellSize() + style.gap();
        float scale = layer == Status.WORKING ? 1 : (float) sample.markScale(layer);
        float side = style.cellSize() * scale;
        float inset = size * (1 - scale) / 2;
        double layerOpacity = sample.opacity(layer);
        float radius = style.shape() == Shape.ROUND ? side / 2 : Math.max(1, side / 4);
        int markColor = layer == Status.ERROR ? style.errorColor() : style.doneColor();
        boolean contrast = frame.preferences().highContrast();
        double idle = contrast ? Math.max(.35, style.idleOpacity()) : style.idleOpacity();
        for (int cell = 0; cell < grid * grid; cell++) {
            boolean mark = sample.marked(layer, grid, cell);
            boolean hole = layer == Status.WORKING && pattern.delay(cell) < 0;
            double ink = mark ? 1 : layer == Status.WORKING ? sample.wave(pattern, cell, style.stepMs()) : 0;
            double opacity = hole ? idle * .47 : idle + (1 - idle) * ink;
            int cellColor = mark && !contrast ? markColor : color;
            float cellX = x + inset + cell % grid * pitch * scale;
            float cellY = y + inset + cell / grid * pitch * scale;
            if (style.glow() && !contrast && ink > 0) {
                int glow = style.glowColor() == null ? cellColor : style.glowColor();
                float spread = side * .23f;
                RoundedCorners.draw(frame.graphics(), cellX - spread, cellY - spread, side + spread * 2,
                        side + spread * 2, radius + spread, 0, 0, alpha(glow, ink * layerOpacity * .22));
            }
            RoundedCorners.draw(frame.graphics(), cellX, cellY, side, side, radius, 0, 0,
                    alpha(cellColor, opacity * layerOpacity));
        }
    }

    private static int alpha(int color, double opacity) {
        return (color & 0xFFFFFF) | (int) Math.round((color >>> 24) * opacity) << 24;
    }
}
