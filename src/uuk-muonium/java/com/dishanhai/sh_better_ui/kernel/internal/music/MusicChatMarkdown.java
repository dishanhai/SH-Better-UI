// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichMarkdown;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichTextGraphemes;
import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.rendering.font.CFontRenderer;
import com.muoniumplayer.core.screens.ncm.NcmExpressive;
import com.muoniumplayer.render.skija.SkijaGfx;
import io.github.humbleui.types.Rect;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Projects the existing Markdown APIs onto the player's scaled fonts and scrollable cards. */
final class MusicChatMarkdown {
    private static final UlkInline ULK = UlkInline.find();

    record Line(List<Span> spans, double width) {
        void draw(double x, double y, int color, int muted) {
            draw(x, y, color, muted, Integer.MAX_VALUE);
        }

        int length() { return spans.stream().mapToInt(span -> span.ends.length).sum(); }

        void draw(double x, double y, int color, int muted, int remaining) {
            for (var span : spans) {
                if (remaining <= 0) break;
                var font = font(span.style);
                String visible = remaining >= span.ends.length ? span.text
                        : span.text.substring(0, span.ends[remaining - 1]);
                int ink = span.style.getColor() == null ? color : muted;
                if (span.style.isItalic()) {
                    SkijaGfx.pushMatrix();
                    try {
                        SkijaGfx.translate(x + 2, y);
                        SkijaGfx.skew(-.22, 0);
                        font.drawString(visible, 0, 0, ink);
                    } finally {
                        SkijaGfx.popMatrix();
                    }
                } else font.drawString(visible, x, y, ink);
                if (span.style.isStrikethrough() && SkijaGfx.canvas() != null) {
                    double center = y + font.getFontHeight() * .45;
                    SkijaGfx.canvas().drawRect(Rect.makeXYWH((float) x, (float) center,
                                    (float) (visible == span.text ? span.width : font.getStringWidthD(visible)), 1),
                            SkijaGfx.fill(ink));
                }
                x += span.width;
                remaining -= span.ends.length;
            }
        }
    }

    private record Span(String text, Style style, double width, int[] ends) {
        Span(String text, Style style, double width) { this(text, style, width, RichTextGraphemes.ends(text)); }
    }

    private MusicChatMarkdown() {}

    static List<Line> plain(String value, double width) {
        var lines = new ArrayList<Line>();
        for (String line : NcmExpressive.wrap(FontManager.pf14, safe(value), width)) {
            double measured = FontManager.pf14.getStringWidthD(line);
            lines.add(new Line(List.of(new Span(line, Style.EMPTY, measured)), measured));
        }
        return lines;
    }

    static List<Line> layout(String value, double width) {
        List<Component> blocks;
        try { blocks = blocks(safe(value)); }
        catch (IllegalArgumentException oversized) { return plain(value, width); }
        var splitter = new StringSplitter((cp, style) -> {
            String glyph = Character.toString(cp);
            return (float) font(style).advanceInRun(glyph, 0, glyph.length());
        });
        var lines = new ArrayList<Line>();
        for (var block : blocks) {
            for (FormattedText line : splitter.splitLines(block, Math.max(1, (int) width - 2), Style.EMPTY)) {
                var spans = new ArrayList<Span>();
                line.visit((style, part) -> {
                    spans.add(new Span(part, style, font(style).getStringWidthD(part)));
                    return Optional.empty();
                }, Style.EMPTY);
                lines.add(new Line(List.copyOf(spans), spans.stream().mapToDouble(Span::width).sum()));
            }
        }
        return lines.isEmpty() ? plain(value, width) : lines;
    }

    static List<Component> blocks(String value) {
        var lines = new ArrayList<Component>();
        for (var block : ShBetterUiApi.markdown().document(value).blocks()) {
            if (block instanceof RichMarkdown.Heading heading) {
                lines.add(inline(heading.text()).withStyle(Style.EMPTY.withBold(true)));
            } else if (block instanceof RichMarkdown.Paragraph paragraph) {
                for (String line : paragraph.text().split("\n", -1)) lines.add(inline(line));
            } else if (block instanceof RichMarkdown.CodeBlock code) {
                lines.add(Component.literal(code.text()).withStyle(net.minecraft.ChatFormatting.GRAY));
            } else if (block instanceof RichMarkdown.ListItem item) {
                String marker = item.checked() == null ? (item.marker().length() == 1 ? "•" : item.marker())
                        : item.checked() ? "[x]" : "[ ]";
                lines.add(Component.literal(" ".repeat(Math.min(16, item.indent())) + marker + " ")
                        .append(inline(item.text())));
            } else if (block instanceof RichMarkdown.Quote quote) {
                lines.add(Component.literal("│ ").append(inline(quote.text())));
            } else if (block instanceof RichMarkdown.ThematicBreak) {
                lines.add(Component.literal("────────"));
            } else if (block instanceof RichMarkdown.Table table) {
                lines.add(tableRow(table.header()).withStyle(Style.EMPTY.withBold(true)));
                for (var row : table.rows()) lines.add(tableRow(row));
            }
        }
        return lines;
    }

    private static MutableComponent tableRow(List<String> cells) {
        var row = Component.empty();
        for (String cell : cells) {
            if (!row.getSiblings().isEmpty()) row.append(" │ ");
            row.append(inline(cell));
        }
        return row;
    }

    private static MutableComponent inline(String source) {
        if (ULK != null) {
            try { return ULK.component(source); }
            catch (ReflectiveOperationException | LinkageError unavailable) { /* Fall through to the standalone parser. */ }
        }
        return ShBetterUiApi.markdown().parseInline(source);
    }

    private static CFontRenderer font(Style style) { return style.isBold() ? FontManager.pf14bold : FontManager.pf14; }

    private static String safe(String value) { return value.replace("§", "\\u00a7"); }

    /** Avoids a mandatory SHCLIB dependency and uses its existing passive inline model when installed. */
    private record UlkInline(Method parse, Method spans, Method text, Method formats) {
        static UlkInline find() {
            try {
                var parser = Class.forName("com.dishanhai.sh_common_lib.api.ulk.markdown.Markdown");
                var parse = parser.getMethod("parseInline", String.class);
                var span = Class.forName(parse.getReturnType().getDeclaringClass().getName() + "$Span", false,
                        parser.getClassLoader());
                return new UlkInline(parse, parse.getReturnType().getMethod("spans"),
                        span.getMethod("text"), span.getMethod("formats"));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }

        MutableComponent component(String source) throws ReflectiveOperationException {
            var result = Component.empty();
            for (Object span : (List<?>) spans.invoke(parse.invoke(null, source))) {
                Style style = Style.EMPTY;
                for (Object format : (Set<?>) formats.invoke(span)) {
                    style = switch (((Enum<?>) format).name()) {
                        case "BOLD" -> style.withBold(true);
                        case "ITALIC" -> style.withItalic(true);
                        case "STRIKETHROUGH" -> style.withStrikethrough(true);
                        case "CODE" -> style.withColor(net.minecraft.ChatFormatting.GRAY);
                        default -> style;
                    };
                }
                result.append(Component.literal((String) text.invoke(span)).setStyle(style));
            }
            return result;
        }
    }
}
