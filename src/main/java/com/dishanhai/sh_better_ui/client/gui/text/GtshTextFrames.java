// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Isolated GTSH snapshot compatibility, not a global renderer clock.
 * Uses Unicode code points rather than upstream UTF-16 halves. Multiline length
 * is measured after dash normalization. Invalid palettes fail explicitly. Long speeds use quotient/remainder arithmetic
 * without overflowing a speed-times-palette-length product.
 */
final class GtshTextFrames {
    private final LongSupplier clock;
    private long lockedTime;
    private boolean locked;
    private long speed = 80;
    private boolean reversed;

    GtshTextFrames(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    void setSpeed(long millis) {
        speed = Math.max(1, millis);
    }

    void setDirection(int direction) { reversed = direction < 0; }
    void lockTime() { lockedTime = clock.getAsLong(); locked = true; }
    void unlockTime() { locked = false; }
    void reset() { speed = 80; reversed = false; locked = false; }
    private long time() { return locked ? lockedTime : clock.getAsLong(); }


    /** Ordinary named modes are construction-time snapshots; rebuild to animate. */
    Component styled(String text, String name) { return styled(text, name, UiPreferences.DEFAULT); }

    Component styled(String text, String name, UiPreferences preferences) {
        Objects.requireNonNull(preferences, "preferences");
        if (text == null || text.isEmpty() || name == null) return Component.literal(text == null ? "" : text);
        if (preferences.highContrast()) return Component.literal(text);
        var format = UiTextEffects.find(name);
        if (format == null || format.palette() == null) format = UiTextEffects.find("ultimate");
        var palette = format.palette();
        if (!preferences.reducedMotion() && (palette.mode() == TextPalettes.Mode.ALL || palette.mode() == TextPalettes.Mode.RANDOM)) {
            return UiTextEffects.applyNamed(Component.literal(text), name, "");
        }
        // WOBBLE's source global render context is intentionally not recreated here.
        if (!preferences.reducedMotion() && palette.mode() == TextPalettes.Mode.WOBBLE) {
            return UiTextEffects.applyNamed(Component.literal(text), name, "");
        }
        int[] points = points(text);
        long frame = preferences.reducedMotion() ? 0 : time();
        var result = Component.empty();
        for (int i = 0; i < points.length; i++) {
            int rgb = palette.obfuscated()
                    ? palette.rgb()[(int) Math.floorMod(Math.floorDiv(frame, speed), palette.rgb().length)]
                    : sample(palette, i, points.length, frame);
            Style style = Style.EMPTY.withColor(rgb);
            if (!preferences.reducedMotion() && (palette.obfuscated() || palette.mode() == TextPalettes.Mode.DISTORT
                    && Math.floorMod(Math.floorMod(frame, 400) + (long) i * 131, 400) < 120)) style = style.withObfuscated(true);
            result.append(Component.literal(character(points[i])).setStyle(style));
        }
        return result;
    }

    /** Discrete ChatFormatting snapshot; intentionally ignores the compatibility lock/speed. */
    Component custom(String text, ChatFormatting[] colors, int intervalMillis, int mask) {
        Objects.requireNonNull(colors, "colors");
        if (colors.length == 0) throw new IllegalArgumentException("Palette must contain a color or format");
        for (var color : colors) Objects.requireNonNull(color, "palette entry");
        long step = Math.floorDiv(clock.getAsLong() & (long) mask, Math.max(1, intervalMillis));
        var result = Component.empty();
        int[] points = points(text);
        for (int i = 0; i < points.length; i++) {
            int index = (int) Math.floorMod(Math.floorMod(step, colors.length) + i, colors.length);
            result.append(Component.literal(character(points[i])).withStyle(colors[index]));
        }
        return result;
    }

    /** Discrete RGB snapshot; dir is a palette stride, including zero and values other than +/-1. */
    Component customRGB(String text, int[] colors, int intervalMillis, int dir) {
        Objects.requireNonNull(colors, "colors");
        if (colors.length == 0) throw new IllegalArgumentException("Palette must contain a color");
        long step = Math.floorDiv(clock.getAsLong(), Math.max(1, intervalMillis));
        var result = Component.empty();
        int[] points = points(text);
        for (int i = 0; i < points.length; i++) {
            int index = (int) Math.floorMod(Math.floorMod(step, colors.length) + (long) dir * i, colors.length);
            result.append(Component.literal(character(points[i])).withStyle(Style.EMPTY.withColor(colors[index] & 0xffffff)));
        }
        return result;
    }

    /** Preserves upstream midpoint for a single glyph and truncating channel interpolation. */
    Component gradientFiller(String text, int start, int end) {
        int[] points = points(text);
        if (points.length == 0) return Component.literal(" ");
        var result = Component.empty();
        for (int i = 0; i < points.length; i++) {
            float amount = points.length == 1 ? .5f : (float) i / (points.length - 1);
            int color = 0;
            for (int shift : new int[]{16, 8, 0}) {
                int channel = (int) (((start >>> shift) & 255) * (1 - amount) + ((end >>> shift) & 255) * amount);
                color |= channel << shift;
            }
            result.append(Component.literal(character(points[i])).withStyle(Style.EMPTY.withColor(color)));
        }
        return result;
    }

    Component[] multiLineText(String[] lines, String name) {
        Objects.requireNonNull(lines, "lines");
        var palette = palette(name);
        Component[] result = new Component[lines.length];
        if (palette == null) {
            for (int i = 0; i < lines.length; i++) result[i] = Component.literal(Objects.requireNonNull(lines[i], "line"));
            return result;
        }
        int[][] normalized = new int[lines.length][];
        int total = 0;
        for (int i = 0; i < lines.length; i++) {
            normalized[i] = Objects.requireNonNull(lines[i], "line").replace("——", "----").codePoints().toArray();
            total = Math.addExact(total, normalized[i].length);
        }
        long frame = time();
        int position = 0;
        for (int i = 0; i < lines.length; i++) {
            var line = Component.empty();
            if (normalized[i].length == 0) line.append(" ");
            for (int cp : normalized[i]) {
                line.append(Component.literal(character(cp)).withStyle(Style.EMPTY.withColor(
                        sample(palette, position++, Math.max(1, total), frame))));
            }
            result[i] = line;
        }
        return result;
    }

    int getStartColor(String name) { return color(name, 0, 1, time()); }

    int getEndColor(String name, String text) {
        int count = Math.max(1, points(text == null ? null : text.replace("——", "----")).length);
        return color(name, count - 1, count, time());
    }

    /** Reveal count is not clamped before tail arithmetic; tail uses wall time, color uses frame time. */
    Component reveal(String text, int revealed, String name, int obfuscatedTail, UiPreferences preferences) {
        Objects.requireNonNull(preferences, "preferences");
        int[] points = points(text);
        long frame = preferences.reducedMotion() ? 0 : time();
        long flip = Math.floorDiv(clock.getAsLong(), 200);
        var result = Component.empty();
        for (int i = 0; i < points.length; i++) {
            if (i >= revealed) { result.append(Component.literal("　")); continue; }
            Style style = Style.EMPTY;
            if (!preferences.highContrast()) {
                style = style.withColor(color(name, i, points.length, frame));
                if (!preferences.reducedMotion() && obfuscatedTail > 0
                        && i >= (long) revealed - obfuscatedTail && Math.floorMod(i + flip, 3) == 0) {
                    style = style.withObfuscated(true);
                }
            }
            result.append(Component.literal(character(points[i])).setStyle(style));
        }
        return result;
    }

    private int color(String name, int index, int length, long frame) {
        var palette = palette(name);
        return palette == null ? 0xffffff : sample(palette, index, length, frame);
    }

    /** Upstream snapshot helpers use raw RGB, not the named animation or body speed. */
    private static TextPalettes.Palette palette(String name) {
        if (name == null) return null;
        var format = UiTextEffects.find(name);
        if (format == null || format.palette() == null) return null;
        var palette = format.palette();
        var mode = switch (palette.mode()) {
            case ALL, RANDOM, FULL_RANDOM -> palette.mode();
            default -> TextPalettes.Mode.CYCLE;
        };
        return new TextPalettes.Palette(palette.rgb(), false, mode, false);
    }

    private long interval(boolean body, int length) {
        if (speed != 80) return speed;
        if (body) return 200;
        return length <= 5 ? 80 : length <= 8 ? 60 : length <= 10 ? 50 : 65;
    }

    private int sample(TextPalettes.Palette palette, int index, int length, long frame) {
        var mode = palette.mode();
        if (mode == TextPalettes.Mode.ALL || mode == TextPalettes.Mode.RANDOM || mode == TextPalettes.Mode.FULL_RANDOM) {
            // Shared SHBUI meta-mode engine, not source PRNG/global-registry identity parity.
            long scaled = speed == 80 ? frame : (long) (frame * (80.0 / speed));
            return TextPalettes.color(palette, index, length, scaled, new UiTextEffects.Timing(0, reversed));
        }
        int[] rgb = palette.rgb();
        long step = interval(palette.body(), length);
        long whole = Math.floorDiv(frame, step);
        double fraction = Math.floorMod(frame, step) / (double) step;
        int base = (int) Math.floorMod(whole, rgb.length);
        return switch (mode) {
            case STATIC -> rgb[rgb.length / 2];
            case BREATHE -> rgb[base];
            case SCAN -> {
                int light = (int) (Math.floorMod(frame, 1200) * (long) length / 1200);
                int boost = Math.max(0, 100 - 50 * Math.abs(light - index));
                int color = rgb[rgb.length / 4];
                yield (Math.min(255, (color >>> 16 & 255) + boost) << 16)
                        | (Math.min(255, (color >>> 8 & 255) + boost) << 8)
                        | Math.min(255, (color & 255) + boost);
            }
            case TWINKLE -> {
                double unit = (frame + index * 3000.0) / (step * (palette.body() ? 3.0 : 4.0));
                double phase = unit - Math.floor(unit / 4) * 4;
                yield rgb[(int) (Math.abs(Math.sin(phase * .5)) * (rgb.length - 1))];
            }
            case DISTORT -> rgb[(int) Math.floorMod(Math.floorDiv(frame, 50)
                    + Math.floorDiv(Math.floorMod(frame, 50) + (long) index * 137, 50), rgb.length)];
            default -> {
                int from = Math.floorMod(base + (reversed ? -index : index), rgb.length);
                yield blend(rgb[from], rgb[(from + 1) % rgb.length], fraction);
            }
        };
    }

    private static int blend(int from, int to, double fraction) {
        if (fraction == 0) return from;
        int result = 0;
        for (int shift : new int[]{16, 8, 0}) {
            double a = linear(from >>> shift & 255), b = linear(to >>> shift & 255);
            double value = a * (1 - fraction) + b * fraction;
            double srgb = value <= .0031308 ? value * 12.92 : 1.055 * Math.pow(value, 1 / 2.4) - .055;
            result |= ((int) Math.round(Math.max(0, Math.min(1, srgb)) * 255)) << shift;
        }
        return result;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private static int[] points(String text) { return text == null ? new int[0] : text.codePoints().toArray(); }
    private static String character(int codePoint) { return new String(Character.toChars(codePoint)); }
}
