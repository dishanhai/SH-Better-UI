// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.lyrics.common.style.ColorMath;

/** Native extractor text effects. Measurement remains Minecraft's untransformed advance. */
public final class UiFormattedText {
    private UiFormattedText() {}

    /** Only explicit FCS markup is interpreted at the native String entry point. */
    public static Component parseLegacy(String text) {
        return text == null ? null : LegacyTextParser.parse(text, Style.EMPTY, true);
    }

    /** Returns false without changing unmarked sequences. Sampled output has no effect markers. */
    public static boolean drawNative(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text,
                                     int x, int y, int color, boolean shadow) {
        if (text.accept((index, style, cp) -> UiTextEffects.format(style) == null)) return false;
        draw(graphics, font, text, x, y, color, color, shadow, UiPreferences.configured(), null,
                System.currentTimeMillis());
        return true;
    }

    static void draw(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y,
                     int color, int authoredColor, boolean shadow, UiPreferences preferences,
                     TextReadabilityPalette palette, long time) {
        boolean[] marked = {false};
        text.accept((index, style, codePoint) -> {
            if (UiTextEffects.format(style) == null) return true;
            marked[0] = true;
            return false;
        });
        if (!marked[0]) {
            var adapted = palette == null ? text : palette.glyphs(text);
            drawStatic(graphics, font, adapted, x, y, color, shadow, palette);
            return;
        }
        var glyphs = sample(text, authoredColor, preferences, palette, time);
        boolean positioned = glyphs.stream().anyMatch(glyph -> glyph.x() != 0 || glyph.y() != 0 || glyph.outline());
        if (!positioned) {
            drawStatic(graphics, font, sequence(glyphs), x, y, color, shadow, palette);
            return;
        }
        float cursor = x;
        for (int from = 0; from < glyphs.size();) {
            var first = glyphs.get(from);
            int to = drawingGroupEnd(glyphs, from);
            var group = new ArrayList<>(glyphs.subList(from, to));
            var sequence = sequence(group);
            // Keep the whole visual cluster together; the matrix preserves fractional advance.
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(cursor + first.x(), y + first.y());
                if (first.outline() && !UiRichText.isObject(first.style(), first.codePoint())) {
                    var outline = sequence(group, true);
                    for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
                        if (ox != 0 || oy != 0) graphics.text(font, outline, ox, oy, color & 0xFF000000, false);
                    }
                }
                drawStatic(graphics, font, sequence, 0, 0, color, shadow, palette);
            } finally {
                graphics.pose().popMatrix();
            }
            cursor += font.getSplitter().stringWidth(sequence);
            from = to;
        }
    }

    /** Custom effects may intentionally assign different offsets to members of a cluster. */
    static int drawingGroupEnd(List<Glyph> glyphs, int from) {
        var first = glyphs.get(from);
        int to = from + 1;
        while (to < glyphs.size()) {
            var next = glyphs.get(to);
            if (next.cluster() != first.cluster() || next.x() != first.x() || next.y() != first.y()
                    || next.outline() != first.outline()) break;
            to++;
        }
        return to;
    }

    /** Freeze one color frame without changing source indices, styles or visual order. */
    public static FormattedCharSequence colorize(FormattedCharSequence text, int argb,
                                                long timeMillis, UiPreferences preferences) {
        return colorize(text, argb, timeMillis, preferences, UiTextEffects.Timing.DEFAULT);
    }

    public static FormattedCharSequence colorize(FormattedCharSequence text, int argb, long timeMillis,
                                                UiPreferences preferences, UiTextEffects.Timing timing) {
        if (text.accept((index, style, cp) -> UiTextEffects.format(style) == null)) return text;
        return sequence(sample(text, argb, preferences, null, timeMillis, timing));
    }

    /** Adjacent same-effect lines share one clock and cluster phase, then retain their line boundaries. */
    public static List<FormattedCharSequence> colorizeLines(List<FormattedCharSequence> lines, int argb,
            long timeMillis, UiPreferences preferences, UiTextEffects.Timing timing) {
        var glyphs = new ArrayList<Glyph>();
        int[] ends = new int[lines.size() + 1];
        var originalIndices = new ArrayList<Integer>();
        int lineOffset = 0;
        for (int i = 0; i < lines.size(); i++) {
            int nextOffset = lineOffset;
            for (var glyph : collectGlyphs(lines.get(i))) {
                originalIndices.add(glyph.index());
                int index = lineOffset + glyph.index();
                glyphs.add(new Glyph(index, glyph.style(), glyph.codePoint(), 0, 0, false, 0));
                nextOffset = Math.max(nextOffset, index + Character.charCount(glyph.codePoint()));
            }
            lineOffset = nextOffset;
            ends[i + 1] = glyphs.size();
        }
        var frame = sample(sequence(glyphs), argb, preferences, null, timeMillis, timing, ends);
        for (int i = 0; i < frame.size(); i++) {
            var glyph = frame.get(i);
            frame.set(i, new Glyph(originalIndices.get(i), glyph.style(), glyph.codePoint(),
                    glyph.x(), glyph.y(), glyph.outline(), glyph.cluster()));
        }
        var output = new ArrayList<FormattedCharSequence>(lines.size());
        for (int i = 0; i < lines.size(); i++) output.add(sequence(frame.subList(ends[i], ends[i + 1])));
        return List.copyOf(output);
    }

    /** 把未揭示的码点显示为空位，末端可附带按时钟推进的混淆。 */
    public static FormattedCharSequence reveal(FormattedCharSequence text, int revealed, int obfuscatedTail,
                                                long timeMillis, UiPreferences preferences) {
        var glyphs = collectGlyphs(text);
        int visible = UiMath.clamp(revealed, 0, glyphs.size());
        int tail = Math.min(visible, Math.max(0, obfuscatedTail));
        long phase = Math.floorDiv(timeMillis, 200);
        for (int i = visible - tail; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            if (i >= visible) {
                glyphs.set(i, new Glyph(glyph.index(), glyph.style().withInsertion(null)
                        .withObfuscated(false).withUnderlined(false).withStrikethrough(false), '　', 0, 0, false, glyph.cluster()));
            } else if (!preferences.highContrast() && !preferences.reducedMotion()
                    && Math.floorMod(phase + i, 3) == 0) {
                glyphs.set(i, new Glyph(glyph.index(), glyph.style().withObfuscated(true), glyph.codePoint(),
                        0, 0, false, glyph.cluster()));
            }
        }
        return sequence(glyphs);
    }

    /** 沿视觉字序插值，保留字体与其余原版样式。 */
    public static FormattedCharSequence gradientFiller(FormattedCharSequence text, int startRgb, int endRgb) {
        var glyphs = collectGlyphs(text);
        int span = Math.max(1, glyphs.size() - 1);
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            int rgb = ColorMath.lerp(0xff000000 | startRgb, 0xff000000 | endRgb, (float) i / span) & 0xffffff;
            glyphs.set(i, new Glyph(glyph.index(), glyph.style().withColor(rgb), glyph.codePoint(), 0, 0, false, glyph.cluster()));
        }
        return sequence(glyphs);
    }

    /** 按视觉顺序推进整行着色，进度 0 到 1。 */
    public static FormattedCharSequence colorizeLyric(FormattedCharSequence text, float progress,
                                                      int sungRgb, int unsungRgb) {
        List<Glyph> glyphs = collectGlyphs(text);
        double front = unitProgress(progress) * glyphs.size();
        return colorizeLyric(glyphs, ordinal -> front - ordinal, sungRgb, unsungRgb);
    }

    /** 由宿主按视觉字序提供各码点进度，可映射自己的逐字时间轴。 */
    public static FormattedCharSequence colorizeLyric(FormattedCharSequence text,
                                                      IntToDoubleFunction glyphProgress,
                                                      int sungRgb, int unsungRgb) {
        return colorizeLyric(collectGlyphs(text), glyphProgress, sungRgb, unsungRgb);
    }

    private static List<Glyph> collectGlyphs(FormattedCharSequence text) {
        List<Glyph> glyphs = new ArrayList<>();
        text.accept((index, style, codePoint) -> {
            glyphs.add(new Glyph(index, style, codePoint, 0, 0, false, glyphs.size()));
            return true;
        });
        return glyphs;
    }

    private static FormattedCharSequence colorizeLyric(List<Glyph> glyphs, IntToDoubleFunction progress,
                                                       int sungRgb, int unsungRgb) {
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            int rgb = ColorMath.lerp(0xff000000 | unsungRgb, 0xff000000 | sungRgb,
                    (float) unitProgress(progress.applyAsDouble(i))) & 0xffffff;
            glyphs.set(i, new Glyph(glyph.index(), glyph.style().withColor(rgb), glyph.codePoint(), 0, 0, false, glyph.cluster()));
        }
        return sequence(glyphs);
    }

    private static double unitProgress(double progress) {
        return progress > 0 ? Math.min(1, progress) : 0;
    }

    /** Pure frame sampling shared by the native extractor and HUD renderer. */
    static ArrayList<Glyph> sample(FormattedCharSequence text, int authoredColor, UiPreferences preferences,
                                   TextReadabilityPalette palette, long time) {
        return sample(text, authoredColor, preferences, palette, time, UiTextEffects.Timing.DEFAULT);
    }

    private static ArrayList<Glyph> sample(FormattedCharSequence text, int authoredColor, UiPreferences preferences,
                                   TextReadabilityPalette palette, long time, UiTextEffects.Timing timing) {
        return sample(text, authoredColor, preferences, palette, time, timing, new int[0]);
    }

    private static ArrayList<Glyph> sample(FormattedCharSequence text, int authoredColor, UiPreferences preferences,
                                   TextReadabilityPalette palette, long time, UiTextEffects.Timing timing,
                                   int[] lineEnds) {
        var glyphs = new ArrayList<Glyph>();
        text.accept((index, style, codePoint) -> {
            glyphs.add(new Glyph(index, style, codePoint, 0, 0, false, 0));
            return true;
        });
        for (int from = 0; from < glyphs.size();) {
            String marker = glyphs.get(from).style().getInsertion();
            int to = from + 1;
            while (to < glyphs.size() && Objects.equals(marker, glyphs.get(to).style().getInsertion())) to++;
            var format = UiTextEffects.format(glyphs.get(from).style());
            int[] points = new int[to - from], indices = new int[to - from];
            for (int i = from; i < to; i++) {
                points[i - from] = glyphs.get(i).codePoint();
                indices[i - from] = glyphs.get(i).index();
            }
            int start = from, end = to;
            int[] boundaries = java.util.Arrays.stream(lineEnds).filter(boundary -> boundary > start && boundary < end)
                    .map(boundary -> boundary - start).distinct().toArray();
            int[] clusters = TextEffectClusters.ordinals(points, indices, boundaries);
            int count = clusters[clusters.length - 1] + 1;
            for (int i = from; i < to; i++) {
                var glyph = glyphs.get(i);
                var style = glyph.style();
                boolean animatedColor = false;
                float dx = 0, dy = 0;
                boolean outline = false;
                if (format != null) {
                    style = style.withInsertion(null);
                    if (!preferences.highContrast()) {
                        int rgb = style.getColor() == null ? authoredColor & 0xffffff : style.getColor().getValue();
                        animatedColor = hasColorEffect(format, style, preferences.reducedMotion());
                        var sample = UiTextEffects.sample(format, rgb, clusters[i - from], count, glyph.codePoint(),
                                time, preferences.reducedMotion(), timing, style.getColor() != null);
                        if (sample.rgb() != rgb || animatedColor) style = style.withColor(sample.rgb());
                        if (sample.obfuscated()) style = style.withObfuscated(true);
                        dx = sample.x();
                        dy = sample.y();
                        outline = sample.outline();
                    }
                }
                if (palette != null && style.getColor() != null) {
                    int rgb = style.getColor().getValue();
                    style = style.withColor((animatedColor ? palette.animatedColor(rgb) : palette.color(rgb)) & 0xffffff);
                }
                glyphs.set(i, new Glyph(glyph.index(), style, glyph.codePoint(), dx, dy, outline, from + clusters[i - from]));
            }
            from = to;
        }
        return glyphs;
    }

    private static boolean hasColorEffect(UiTextEffects.Format format, Style style, boolean reducedMotion) {
        var palette = format.palette();
        if (palette != null && palette.mode() == TextPalettes.Mode.ALL && style.getColor() != null) return false;
        if (reducedMotion) return false;
        return format.effect() != null || palette != null && palette.mode() != TextPalettes.Mode.STATIC
                || format.symbols().chars().anyMatch(symbol -> "^?+>".indexOf(symbol) >= 0);
    }

    private static void drawStatic(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text,
                                   int x, int y, int base, boolean shadow, TextReadabilityPalette palette) {
        if (palette == null) {
            graphics.text(font, text, x, y, base, shadow);
            return;
        }
        int baseOutline = palette.outlineColor(base);
        boolean outlined = !text.accept((index, style, glyph) -> {
            int fill = style.getColor() == null ? base : style.getColor().getValue();
            return palette.outlineColor(fill) == 0;
        });
        if (outlined) {
            // Mixed widths need a positioned glyph pass; the uniform case can keep native shaping.
            if (baseOutline != 0 && text.accept((index, style, glyph) -> style.getColor() == null)) {
                for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
                    if (ox != 0 || oy != 0) graphics.text(font, UiRichText.withoutObjects(text), x + ox, y + oy, baseOutline, false);
                }
            } else {
                float[] cursor = {x};
                text.accept((index, style, glyph) -> {
                    var original = FormattedCharSequence.codepoint(glyph, style);
                    int fill = style.getColor() == null ? base : style.getColor().getValue();
                    int edge = palette.outlineColor(fill);
                    if (edge != 0) {
                        var outline = FormattedCharSequence.codepoint(glyph, style.withColor(edge & 0xFFFFFF).withInsertion(null));
                        int left = Math.round(cursor[0]);
                        for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
                            if (ox != 0 || oy != 0) graphics.text(font, outline, left + ox, y + oy, edge, false);
                        }
                    }
                    cursor[0] += font.getSplitter().stringWidth(original);
                    return true;
                });
            }
        }
        graphics.text(font, text, x, y, base, shadow);
    }

    private static FormattedCharSequence sequence(List<Glyph> glyphs) {
        return sequence(glyphs, false);
    }

    private static FormattedCharSequence sequence(List<Glyph> glyphs, boolean outline) {
        return sink -> {
            for (var glyph : glyphs) {
                if (!sink.accept(glyph.index(), outline ? glyph.style().withColor(0) : glyph.style(), glyph.codePoint())) return false;
            }
            return true;
        };
    }

    record Glyph(int index, Style style, int codePoint, float x, float y, boolean outline, int cluster) {}
}
