// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;

/** Shared UUK hold-button palette and rounded liquid mask for both GUI renderers. */
final class HoldButtonPaint {
    private HoldButtonPaint() {}

    record Palette(int background, int fill, int text, int fillText, int border) {}

    static Palette palette(UiHoldButton.Appearance appearance, UiPreferences preferences,
                           boolean active, boolean hovered, boolean focused) {
        var theme = UiTheme.current();
        int background = opaque(appearance.backgroundColor(), theme.surface());
        int fill = opaque(appearance.fillColor(), theme.accent());
        int normalInk = opaque(appearance.textColor(), theme.text());
        int fillInk = opaque(appearance.fillTextColor(), theme.text());
        int border = theme.border() | 0xFF000000;
        if (!active) {
            int muted = theme.background() | 0xFF000000;
            background = GuiMotion.lerpColor(background, muted, 0.55f);
            fill = GuiMotion.lerpColor(fill, muted, 0.65f);
            normalInk = GuiMotion.lerpColor(normalInk, background, 0.32f);
            fillInk = GuiMotion.lerpColor(fillInk, fill, 0.32f);
        } else if (hovered) {
            background = GuiMotion.lerpColor(background, normalInk, 0.08f);
        }
        double ratio = preferences.highContrast() ? ContrastPolicy.HIGH_CONTRAST_RATIO : ContrastPolicy.TEXT_RATIO;
        if (focused || preferences.highContrast()) {
            border = ContrastPolicy.readable(focused ? fill : normalInk, background, background, 3);
        }
        return new Palette(background, fill, ContrastPolicy.readable(normalInk, background, background, ratio),
                ContrastPolicy.readable(fillInk, fill, fill, ratio), border);
    }

    private static int opaque(Integer override, int fallback) {
        return (override == null ? fallback : override) | 0xFF000000;
    }

    @FunctionalInterface
    interface Span {
        void draw(int left, int top, int right, int bottom);
    }

    /** Coalesces adjacent scan lines with identical fill bounds. */
    static void spans(int width, int height, float radius, float progress, boolean up, float wave, Span span) {
        if (width <= 0 || height <= 0 || progress <= 0) return;
        int across = up ? width : height;
        int depth = up ? height : width;
        float amplitude = Math.min(wave, depth * 0.25f) * Math.min(1, 8 * Math.min(progress, 1 - progress));
        double phase = progress * Math.PI * 4;
        int from = 0, previousLow = 0, previousHigh = 0;
        for (int line = 0; line <= across; line++) {
            int low = 0, high = 0;
            if (line < across) {
                float distance = radius - Math.min(line + 0.5f, across - line - 0.5f);
                int inset = distance <= 0 ? 0 : (int) Math.floor(radius - Math.sqrt(radius * radius - distance * distance) + 0.5);
                double offset = amplitude == 0 ? 0 : amplitude * Math.sin(line * (2 * Math.PI / 20) - phase);
                int edge = (int) Math.round(progress * depth + offset);
                low = up ? Math.max(inset, depth - edge) : inset;
                high = up ? depth - inset : Math.min(depth - inset, edge);
            }
            if (low == previousLow && high == previousHigh) continue;
            if (previousHigh > previousLow) {
                if (up) span.draw(from, previousLow, line, previousHigh);
                else span.draw(previousLow, from, previousHigh, line);
            }
            from = line;
            previousLow = low;
            previousHigh = high;
        }
    }
}
