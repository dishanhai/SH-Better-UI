// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;

/** Paints the same slot geometry and motion through each loader's GUI renderer. */
final class CodeSlotsPaint {
    interface Canvas {
        void box(int x, int y, int width, int height, int radius, int stroke, int edge, int fill);
        void text(String value, int centerX, int centerY, int color);
        void line(int x, int y, int height, int color);
    }

    private CodeSlotsPaint() {}

    static int gap(int width, int length) {
        return width >= length * 16L ? 4 : width >= length * 8L ? 2 : 0;
    }

    static int slotAt(double x, int width, int length) {
        int gap = gap(width, length);
        int usable = width - gap * (length - 1);
        int slot = 0;
        while (slot + 1 < length && x >= slotLeft(slot + 1, usable, gap, length) - gap / 2.0) slot++;
        return slot;
    }

    static void draw(Canvas canvas, CodeSlotsState state, int x, int y, int width, int height,
                     UiPreferences preferences, boolean focused, int hovered, boolean masked,
                     boolean caret, boolean enabled, float opacity, long now) {
        int length = state.length();
        int gap = gap(width, length);
        int usable = width - gap * (length - 1);
        if (usable < length || height < 10) return;
        UiTheme theme = UiTheme.current();
        int surface = theme.controlFill(0xFF27272A, 0f);
        int text = theme.text(0xFFF5F5F5) | 0xFF000000;
        int accent = theme.accent(0xFFF5F5F5) | 0xFF000000;
        int border = theme.border(0xFF55555C);
        int danger = 0xFFFF5349;
        int radius = Math.min(6, usable / length / 2);
        float alpha = enabled ? opacity : opacity * 0.45f;
        boolean reduced = preferences.reducedMotion();
        boolean error = state.status() == UiCodeSlots.Status.ERROR;
        boolean success = state.status() == UiCodeSlots.Status.SUCCESS;
        boolean rejecting = error && !state.accepting();

        for (int i = 0; i < length; i++) {
            int left = x + slotLeft(i, usable, gap, length);
            int size = x + slotLeft(i + 1, usable, gap, length) - left - gap;
            boolean selected = focused && i == state.selected() && !success;
            int edge = (error ? danger : selected || preferences.highContrast() ? text : border);
            int background = selected || i == hovered ? GuiMotion.lerpColor(surface, text, 0.13f) : surface;
            canvas.box(left, y, size, height, radius, selected ? 2 : 1, fade(edge, alpha), fade(background, alpha));
            char digit = state.displayed(i);
            if (digit != 0) {
                boolean dropping = rejecting || state.digit(i) == 0;
                float age = progress(state.transitionAge(i, now), CodeSlotsState.SETTLE_MS);
                float visible = reduced ? (state.digit(i) != 0 ? 1 : 0) : dropping ? 1 - age : ease(age);
                if (success) visible *= reduced ? 0 : 1 - ease(progress(state.statusAge(now) - 60 - i * 30L, 300));
                if (visible > 0) {
                    int lift = reduced ? 0 : Math.round((1 - visible) * 6);
                    int fill = rejecting ? danger : accent;
                    int ink = ContrastPolicy.readable(surface, fill, fill, preferences.highContrast() ? 7 : 4.5);
                    int top = y + 2 + lift;
                    int digitHeight = height - 4 - lift;
                    canvas.box(left + 2, top, size - 4, digitHeight, Math.max(0, radius - 1),
                            0, 0, fade(fill, alpha * visible));
                    if (size >= 8) canvas.text(masked ? "•" : String.valueOf(digit), left + size / 2,
                            y + height / 2 + lift / 2, fade(ink, alpha * visible));
                }
            }
            if (selected && enabled && caret && digit == 0 && (reduced || now % 1000 < 650)) {
                int caretHeight = Math.min(10, height - 4);
                canvas.line(left + size / 2, y + (height - caretHeight) / 2, caretHeight, fade(text, alpha));
            }
        }

        if (success) {
            int wash = Math.round(width * (reduced ? 1 : ease(progress(state.statusAge(now), 300))));
            if (wash > 0) canvas.box(x + (width - wash) / 2, y, wash, height, radius,
                    focused ? 2 : 0, fade(text, alpha), fade(accent, alpha));
            float show = reduced ? 1 : ease(progress(state.statusAge(now) - 280, 200));
            if (show > 0) {
                int ink = ContrastPolicy.readable(surface, accent, accent, ContrastPolicy.TEXT_RATIO);
                canvas.text("✓", x + width / 2, y + height / 2 + Math.round((1 - show) * 6), fade(ink, alpha * show));
            }
        }
    }

    private static int slotLeft(int slot, int usable, int gap, int length) {
        return slot * gap + (int) ((long) slot * usable / length);
    }

    private static float progress(long age, long duration) {
        return GuiMotion.clamp01(age / (float) duration);
    }

    private static float ease(float progress) {
        float remaining = 1 - progress;
        return 1 - remaining * remaining * remaining;
    }

    private static int fade(int color, float alpha) {
        return (color & 0xFFFFFF) | (Math.round((color >>> 24) * alpha) << 24);
    }
}
