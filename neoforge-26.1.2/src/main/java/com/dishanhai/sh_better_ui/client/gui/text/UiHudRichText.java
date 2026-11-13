// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.text;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.text.TextReadabilityPalette;
import com.dishanhai.sh_better_ui.kernel.api.richtext.LocalizedText;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichDocument;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichStyle;
import com.dishanhai.sh_better_ui.kernel.api.richtext.TextRun;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Objects;

/** HUD adapter for styled components and SHBUI text effects on the extractor backend. */
public final class UiHudRichText {
    private UiHudRichText() {}

    public static Component prepare(Component text) { return UiTextEffects.normalize(text); }

    /** Project a text-only rich document into one HUD row. */
    public static Component prepare(RichDocument document) {
        var result = Component.empty();
        for (var node : document.nodes()) {
            if (node instanceof TextRun run) {
                result.append(Component.literal(run.text()).setStyle(nativeStyle(run.style())));
            } else if (node instanceof LocalizedText localized) {
                result.append(Component.translatableWithFallback(localized.key(), localized.fallback(),
                        localized.arguments().toArray()).setStyle(nativeStyle(localized.style())));
            } else {
                throw new IllegalArgumentException("HUD rows support text nodes only");
            }
        }
        return prepare(result);
    }

    private static Style nativeStyle(RichStyle style) {
        var nativeStyle = Style.EMPTY.withBold(style.bold()).withItalic(style.italic())
                .withUnderlined(style.underlined()).withStrikethrough(style.strikethrough())
                .withObfuscated(style.obfuscated());
        if (style.color() != null) nativeStyle = nativeStyle.withColor(style.color());
        if (style.font() != null) nativeStyle = nativeStyle.withFont(new FontDescription.Resource(Identifier.tryParse(style.font().toString())));
        return nativeStyle;
    }

    /** Draw one visual line using the caller's already sampled readability palette. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y,
                            int color, UiPreferences preferences, TextReadabilityPalette palette) {
        draw(graphics, font, text, x, y, color, color, preferences, palette);
    }

    /** Accepts the authored base independently of the currently animated HUD base fill. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y,
                            int color, int authoredColor, UiPreferences preferences, TextReadabilityPalette palette) {
        boolean[] marked = {false};
        text.accept((index, style, codePoint) -> {
            if (UiTextEffects.format(style) == null) return true;
            marked[0] = true;
            return false;
        });
        if (!marked[0]) {
            var adapted = palette.glyphs(text);
            drawStatic(graphics, font, adapted, x, y, color, palette);
            return;
        }
        var glyphs = new ArrayList<Glyph>();
        text.accept((index, style, codePoint) -> {
            glyphs.add(new Glyph(index, style, codePoint, 0, 0, false));
            return true;
        });
        long time = System.currentTimeMillis();
        boolean positioned = false;
        for (int from = 0; from < glyphs.size();) {
            String marker = glyphs.get(from).style().getInsertion();
            int to = from + 1;
            while (to < glyphs.size() && Objects.equals(marker, glyphs.get(to).style().getInsertion())) to++;
            var format = UiTextEffects.format(glyphs.get(from).style());
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
                        var sample = UiTextEffects.sample(format, rgb, i - from, to - from, glyph.codePoint(),
                                time, preferences.reducedMotion(), UiTextEffects.Timing.DEFAULT, style.getColor() != null);
                        style = style.withColor(sample.rgb()).withObfuscated(sample.obfuscated() || style.isObfuscated());
                        dx = sample.x();
                        dy = sample.y();
                        outline = sample.outline();
                    }
                }
                if (style.getColor() != null) {
                    int rgb = style.getColor().getValue();
                    style = style.withColor((animatedColor ? palette.animatedColor(rgb) : palette.color(rgb)) & 0xffffff);
                }
                glyphs.set(i, new Glyph(glyph.index(), style, glyph.codePoint(), dx, dy, outline));
                positioned |= dx != 0 || dy != 0 || outline;
            }
            from = to;
        }
        if (!positioned || font.isBidirectional()) {
            drawStatic(graphics, font, sequence(glyphs), x, y, color, palette);
            return;
        }
        float cursor = x;
        for (var glyph : glyphs) {
            var sequence = FormattedCharSequence.codepoint(glyph.codePoint(), glyph.style());
            int gx = Math.round(cursor + glyph.x()), gy = Math.round(y + glyph.y());
            int fill = glyph.style().getColor() == null ? color : glyph.style().getColor().getValue();
            int protection = palette.outlineColor(fill);
            if (glyph.outline() || protection != 0) {
                var outline = FormattedCharSequence.codepoint(glyph.codePoint(), glyph.style().withColor(protection & 0xFFFFFF));
                for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
                    if (ox != 0 || oy != 0) graphics.text(font, outline, gx + ox, gy + oy, color, false);
                }
            }
            graphics.text(font, sequence, gx, gy, color, false);
            cursor += font.getSplitter().stringWidth(sequence);
        }
    }

    private static boolean hasColorEffect(UiTextEffects.Format format, Style style, boolean reducedMotion) {
        var palette = format.palette();
        if (palette != null && palette.mode() == TextPalettes.Mode.ALL && style.getColor() != null) return false;
        if (reducedMotion) return false;
        return format.effect() != null || palette != null && palette.mode() != TextPalettes.Mode.STATIC
                || format.symbols().chars().anyMatch(symbol -> "^?+>".indexOf(symbol) >= 0);
    }

    private static void drawStatic(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text,
                                   int x, int y, int base, TextReadabilityPalette palette) {
        int baseOutline = palette.outlineColor(base);
        boolean outlined = !text.accept((index, style, glyph) -> {
            int fill = style.getColor() == null ? base : style.getColor().getValue();
            return palette.outlineColor(fill) == 0;
        });
        if (outlined) {
            // Mixed widths need a positioned glyph pass; the uniform case can keep native shaping.
            if (baseOutline != 0 && text.accept((index, style, glyph) -> style.getColor() == null)) {
                for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
                    if (ox != 0 || oy != 0) graphics.text(font, text, x + ox, y + oy, baseOutline, false);
                }
            } else {
                float[] cursor = {x};
                text.accept((index, style, glyph) -> {
                    var original = FormattedCharSequence.codepoint(glyph, style);
                    int fill = style.getColor() == null ? base : style.getColor().getValue();
                    int edge = palette.outlineColor(fill);
                    if (edge != 0) {
                        var outline = FormattedCharSequence.codepoint(glyph, style.withColor(edge & 0xFFFFFF));
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
        graphics.text(font, text, x, y, base, false);
    }

    private static FormattedCharSequence sequence(ArrayList<Glyph> glyphs) {
        return sink -> {
            for (var glyph : glyphs) {
                if (!sink.accept(glyph.index(), glyph.style(), glyph.codePoint())) return false;
            }
            return true;
        };
    }

    private record Glyph(int index, Style style, int codePoint, float x, float y, boolean outline) {}
}
