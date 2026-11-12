// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Native glyph extraction for the Gallery's formatted text and color gradient. */
final class GallerySpatialText {
    private static final String DAWN_WAVE = "shbui:fcs:sh_better_ui:shanhai_dawn|~";
    private static final int[] DAWN = {0xFF668B, 0xFF985F, 0xFFF1B0, 0xFFC781, 0xF984A0};
    private static final int TEXT = 0xFFF6F6FF;

    private GallerySpatialText() {}

    static void draw(GuiGraphicsExtractor graphics, Font font, Bounds area, Component example, Component gradient,
                     long elapsed, boolean animate, boolean contrast) {
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), 0xFF111A2A);
        int lineHeight = font.lineHeight + 4;
        int width = Math.max(1, area.width() - 12);
        var waveLines = font.split(example.copy().withStyle(style -> style.withInsertion(DAWN_WAVE)), width);
        var gradientLines = font.split(gradient, width);
        int linesPerGroup = Math.max(1, (area.height() - 3) / (2 * lineHeight));
        int waveCount = Math.min(linesPerGroup, waveLines.size());
        int gradientCount = Math.min(linesPerGroup, gradientLines.size());
        int top = area.y() + Math.max(2, (area.height() - (waveCount + gradientCount) * lineHeight) / 2);
        drawGroup(graphics, font, waveLines.subList(0, waveCount), area.x() + 6, top, lineHeight,
                true, elapsed, animate, contrast);
        drawGroup(graphics, font, gradientLines.subList(0, gradientCount), area.x() + 6, top + waveCount * lineHeight,
                lineHeight, false, elapsed, false, contrast);
    }

    private static void drawGroup(GuiGraphicsExtractor graphics, Font font, List<FormattedCharSequence> lines,
                                  int x, int y, int lineHeight, boolean wave, long elapsed,
                                  boolean animate, boolean contrast) {
        var glyphs = new ArrayList<Glyph>();
        int[] ends = new int[lines.size() + 1];
        for (int line = 0; line < lines.size(); line++) {
            lines.get(line).accept((index, style, codePoint) -> glyphs.add(new Glyph(index, style, codePoint)));
            ends[line + 1] = glyphs.size();
        }
        for (int line = 0; line < lines.size(); line++) {
            int from = ends[line], to = ends[line + 1], baseline = y + line * lineHeight;
            if (contrast) {
                graphics.text(font, lines.get(line), x, baseline, TEXT, false);
            } else if (wave && animate && !font.isBidirectional()) {
                float cursor = x;
                int dy = 0;
                for (int i = from; i < to; i++) {
                    var glyph = glyphs.get(i);
                    var sequence = FormattedCharSequence.codepoint(glyph.codePoint(),
                            styled(glyph, i, glyphs.size(), true, elapsed));
                    if (Character.getType(glyph.codePoint()) != Character.NON_SPACING_MARK) {
                        dy = (int) Math.round(Math.sin(elapsed / 175.0 - i * .41) * 2.0);
                    }
                    graphics.text(font, sequence, Math.round(cursor), baseline + dy, TEXT, false);
                    cursor += font.getSplitter().stringWidth(sequence);
                }
            } else {
                FormattedCharSequence styledLine = sink -> {
                    for (int i = from; i < to; i++) {
                        var glyph = glyphs.get(i);
                        if (!sink.accept(glyph.index(), styled(glyph, i, glyphs.size(), wave, animate ? elapsed : 0),
                                glyph.codePoint())) return false;
                    }
                    return true;
                };
                graphics.text(font, styledLine, x, baseline, TEXT, false);
            }
        }
    }

    private static Style styled(Glyph glyph, int index, int count, boolean wave, long elapsed) {
        var style = glyph.style();
        if (wave && DAWN_WAVE.equals(style.getInsertion())) {
            double step = (index * .34 + elapsed / 150.0) % DAWN.length;
            int a = (int) step;
            return style.withInsertion(null).withColor(lerp(DAWN[a], DAWN[(a + 1) % DAWN.length], step - a));
        }
        return wave ? style : style.withColor(lerp(0x74E1CE, 0xC3ADFF, index / (double) Math.max(1, count - 1)));
    }

    private static int lerp(int a, int b, double progress) {
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int start = a >> shift & 255, end = b >> shift & 255;
            result |= (int) Math.round(start + (end - start) * progress) << shift;
        }
        return result;
    }

    private record Glyph(int index, Style style, int codePoint) {}
}
