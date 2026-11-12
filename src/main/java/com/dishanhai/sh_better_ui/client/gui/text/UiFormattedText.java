package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.lyrics.common.style.ColorMath;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntToDoubleFunction;

/** 山海：在 GUI 中绘制带有 {@link UiTextEffects} 标记的原版文字。 */
public final class UiFormattedText {
    private UiFormattedText() {}

    /** Fits the prefix and an omission marker before converting the line to visual order. */
    public static FormattedText ellipsize(StringSplitter splitter, FormattedText line, int width) {
        var marker = splitter.headByWidth(CommonComponents.ELLIPSIS, width, Style.EMPTY);
        int available = width - (int) Math.ceil(splitter.stringWidth(marker));
        var prefix = splitter.headByWidth(line, available, Style.EMPTY);
        return FormattedText.composite(prefix, marker);
    }

    /** 输入可以是翻译组件，也可以是已按视觉顺序排列的一行文字。 */
    public static int draw(GuiGraphics graphics, Font font, Component text, float x, float y,
                           int argb, boolean shadow, UiPreferences preferences) {
        return draw(graphics, font, UiTextEffects.normalize(text).getVisualOrderText(), x, y, argb, shadow, preferences);
    }

    /** 逐字位移和描边只影响绘制。返回值仍按原版字宽计算。 */
    public static int draw(GuiGraphics graphics, Font font, FormattedCharSequence text, float x, float y,
                           int argb, boolean shadow, UiPreferences preferences) {
        return draw(graphics, font, text, x, y, argb, shadow, preferences, PageTransition.nowMs(),
                UiTextEffects.Timing.DEFAULT);
    }

    public static int draw(GuiGraphics graphics, Font font, FormattedCharSequence text, float x, float y,
                           int argb, boolean shadow, UiPreferences preferences, long timeMillis,
                           UiTextEffects.Timing timing) {
        List<Glyph> glyphs = sample(text, argb, timeMillis, preferences, timing);
        if (glyphs == null) return graphics.drawString(font, text, x, y, argb, shadow);
        if (glyphs.stream().noneMatch(Glyph::positioned)) {
            return graphics.drawString(font, sequence(glyphs), x, y, argb, shadow);
        }
        int end = drawGlyphs(font, glyphs, x, y, argb, shadow, graphics.pose().last().pose(),
                graphics.bufferSource(), Font.DisplayMode.NORMAL, LightTexture.FULL_BRIGHT);
        graphics.flush();
        return end;
    }

    /** 在现有字体批次内绘制，返回 null 表示由原版处理。 */
    public static Integer drawBatch(Font font, FormattedCharSequence text, float x, float y, int argb,
                                    boolean shadow, Matrix4f pose, MultiBufferSource buffers,
                                    Font.DisplayMode mode, int background, int light, boolean legacy) {
        if (!hasEffect(text, legacy)) return null;
        var glyphs = collectGlyphs(text);
        sample(glyphs, argb, PageTransition.nowMs(), UiPreferences.configured(), UiTextEffects.Timing.DEFAULT, legacy);
        if (background != 0 || glyphs.stream().noneMatch(Glyph::positioned)) {
            return font.drawInBatch(sequence(glyphs), x, y, argb, shadow, pose, buffers, mode, background, light);
        }
        return drawGlyphs(font, glyphs, x, y, argb, shadow, pose, buffers, mode, light);
    }

    private static int drawGlyphs(Font font, List<Glyph> glyphs, float x, float y, int argb, boolean shadow,
                                  Matrix4f pose, MultiBufferSource buffers, Font.DisplayMode mode, int light) {
        float[] positions = new float[glyphs.size()];
        float cursor = x;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            positions[i] = cursor + glyph.x();
            cursor += font.getSplitter().stringWidth(glyph.sequence());
            if (glyph.outline()) {
                FormattedCharSequence outline = FormattedCharSequence.codepoint(glyph.codePoint(),
                        glyph.style().withColor(0x000000));
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx != 0 || dy != 0) {
                            font.drawInBatch(outline, positions[i] + dx, y + glyph.y() + dy, argb, false,
                                    pose, buffers, mode, 0, light);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            font.drawInBatch(glyph.sequence(), positions[i], y + glyph.y(), argb, shadow,
                    pose, buffers, mode, 0, light);
        }
        return (int) cursor + (shadow ? 1 : 0);
    }

    /** 生成当前颜色帧，可传给原版 tooltip 或其他只接受 FormattedCharSequence 的接口。 */
    public static FormattedCharSequence colorize(FormattedCharSequence text, int argb,
                                                  long timeMillis, UiPreferences preferences) {
        return colorize(text, argb, timeMillis, preferences, UiTextEffects.Timing.DEFAULT);
    }

    public static FormattedCharSequence colorize(FormattedCharSequence text, int argb, long timeMillis,
                                                  UiPreferences preferences, UiTextEffects.Timing timing) {
        List<Glyph> glyphs = sample(text, argb, timeMillis, preferences, timing);
        return glyphs == null ? text : sequence(glyphs);
    }

    /** 相邻行边界的同一效果段共享长度、时钟和码点索引。 */
    public static List<FormattedCharSequence> colorizeLines(List<FormattedCharSequence> lines, int argb,
            long timeMillis, UiPreferences preferences, UiTextEffects.Timing timing) {
        var glyphs = new ArrayList<Glyph>();
        int[] ends = new int[lines.size() + 1];
        for (int i = 0; i < lines.size(); i++) {
            glyphs.addAll(collectGlyphs(lines.get(i)));
            ends[i + 1] = glyphs.size();
        }
        sample(glyphs, argb, timeMillis, preferences, timing, true);
        var output = new ArrayList<FormattedCharSequence>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            output.add(sequence(glyphs.subList(ends[i], ends[i + 1])));
        }
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
                glyphs.set(i, new Glyph(glyph.index(), '　', glyph.style().withInsertion(null)
                        .withObfuscated(false).withUnderlined(false).withStrikethrough(false), 0, 0, false));
            } else if (!preferences.highContrast() && !preferences.reducedMotion()
                    && Math.floorMod(phase + i, 3) == 0) {
                glyphs.set(i, new Glyph(glyph.index(), glyph.codePoint(), glyph.style().withObfuscated(true),
                        0, 0, false));
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
            glyphs.set(i, new Glyph(glyph.index(), glyph.codePoint(), glyph.style().withColor(rgb), 0, 0, false));
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
            glyphs.add(new Glyph(index, codePoint, style, 0, 0, false));
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
            glyphs.set(i, new Glyph(glyph.index(), glyph.codePoint(), glyph.style().withColor(rgb), 0, 0, false));
        }
        return sequence(glyphs);
    }

    private static double unitProgress(double progress) {
        return progress > 0 ? Math.min(1, progress) : 0;
    }

    private static List<Glyph> sample(FormattedCharSequence text, int argb, long timeMillis,
                                      UiPreferences preferences, UiTextEffects.Timing timing) {
        if (!hasEffect(text, true)) return null;
        var glyphs = collectGlyphs(text);
        sample(glyphs, argb, timeMillis, preferences, timing, true);
        return glyphs;
    }

    private static boolean hasEffect(FormattedCharSequence text, boolean legacy) {
        boolean[] hasEffect = {false};
        text.accept((index, style, codePoint) -> {
            if (UiTextEffects.format(style, legacy) == null) return true;
            hasEffect[0] = true;
            return false;
        });
        return hasEffect[0];
    }

    private static void sample(List<Glyph> glyphs, int argb, long timeMillis, UiPreferences preferences,
                               UiTextEffects.Timing timing, boolean legacy) {
        for (int from = 0; from < glyphs.size();) {
            String marker = glyphs.get(from).style().getInsertion();
            int to = from + 1;
            while (to < glyphs.size() && Objects.equals(marker, glyphs.get(to).style().getInsertion())) to++;
            UiTextEffects.Format format = UiTextEffects.format(glyphs.get(from).style(), legacy);
            if (format != null) {
                for (int i = from; i < to; i++) {
                    Glyph glyph = glyphs.get(i);
                    Style style = glyph.style().withInsertion(null);
                    if (preferences.highContrast()) {
                        glyphs.set(i, new Glyph(glyph.index(), glyph.codePoint(), style, 0, 0, false));
                        continue;
                    }
                    int rgb = style.getColor() == null ? argb & 0xffffff : style.getColor().getValue();
                    UiTextEffects.Glyph sample = UiTextEffects.sample(format, rgb, i - from, to - from,
                            glyph.codePoint(), timeMillis, preferences.reducedMotion(), timing, style.getColor() != null);
                    if (sample.rgb() != rgb) style = style.withColor(sample.rgb());
                    if (sample.obfuscated()) style = style.withObfuscated(true);
                    glyphs.set(i, new Glyph(glyph.index(), glyph.codePoint(), style,
                            sample.x(), sample.y(), sample.outline()));
                }
            }
            from = to;
        }
    }

    private static FormattedCharSequence sequence(List<Glyph> glyphs) {
        return sink -> {
            for (Glyph glyph : glyphs) {
                if (!sink.accept(glyph.index(), glyph.style(), glyph.codePoint())) return false;
            }
            return true;
        };
    }

    private record Glyph(int index, int codePoint, Style style, float x, float y, boolean outline) {
        boolean positioned() {
            return x != 0 || y != 0 || outline;
        }

        FormattedCharSequence sequence() {
            return FormattedCharSequence.codepoint(codePoint, style);
        }
    }
}
