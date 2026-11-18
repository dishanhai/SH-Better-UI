// Author: dishanhai
package com.dishanhai.sh_better_ui.client.visual;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.kernel.api.richtext.RichMarkdown;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Width-dependent, immutable Markdown layout. Build on source/font/width changes, never every frame.
 * Drawing and hit testing use the same wrapped lines; the host owns scrolling, clipping and link confirmation.
 * No HTML, remote resources, scripts or inline game commands are executed.
 */
public final class MarkdownView {
    private record Line(FormattedCharSequence text, int x, int y, int width) {}
    private record Box(int x, int y, int width, int height, int color) {}
    private final List<Line> lines = new ArrayList<>();
    private final List<Box> boxes = new ArrayList<>();
    private final Font font;
    private final int width;
    private int height;

    public MarkdownView(Font font, RichMarkdown.Document document, int width) {
        this.font = java.util.Objects.requireNonNull(font);
        this.width = Math.max(32, Math.min(4096, width));
        for (var block : document.blocks()) {
            if (block instanceof RichMarkdown.Table table) table(table);
            else if (block instanceof RichMarkdown.ThematicBreak) {
                boxes.add(new Box(0, height + 4, this.width, 1, 0xff657489)); height += 10;
            } else if (block instanceof RichMarkdown.CodeBlock code) {
                int start = height;
                text(Component.literal(code.text()).withStyle(net.minecraft.ChatFormatting.GRAY), 5, this.width - 10);
                boxes.add(new Box(0, start - 2, this.width, height - start + 4, 0x70404a58));
            } else if (block instanceof RichMarkdown.Heading heading) {
                text(inline(heading.text()).copy().withStyle(net.minecraft.ChatFormatting.BOLD), 0, this.width);
            } else if (block instanceof RichMarkdown.Quote quote) {
                int start = height;
                text(inline(quote.text()), 8, this.width - 8);
                boxes.add(new Box(1, start, 2, height - start, 0xff75869e));
            } else if (block instanceof RichMarkdown.ListItem item) {
                int indent = Math.min(this.width / 3, item.indent() * 4);
                String marker = item.checked() == null ? item.marker() : item.checked() ? "[x]" : "[ ]";
                int offset = Math.min(this.width / 2, indent + font.width(marker + " "));
                lines.add(new Line(Component.literal(marker).getVisualOrderText(), indent, height, offset - indent));
                text(inline(item.text()), offset, this.width - offset);
            } else if (block instanceof RichMarkdown.Paragraph paragraph) {
                text(inline(paragraph.text()), 0, this.width);
            }
            height += 6;
        }
    }

    private static Component inline(String source) { return ShBetterUiApi.markdown().parseInline(source); }

    private void text(Component text, int x, int available) {
        var wrapped = font.split(text, Math.max(1, available));
        for (var line : wrapped) {
            lines.add(new Line(line, x, height, font.width(line)));
            height += font.lineHeight + 3;
        }
        if (wrapped.isEmpty()) height += font.lineHeight + 3;
    }

    private void table(RichMarkdown.Table table) {
        // Very narrow panels use labelled rows, rather than overlapping unreadable columns.
        if (width / table.header().size() < 48) {
            for (var row : table.rows()) {
                for (int i = 0; i < table.header().size(); i++) {
                    text(Component.empty().append(inline(table.header().get(i)).copy()
                            .withStyle(net.minecraft.ChatFormatting.BOLD))
                            .append(Component.literal(": ")).append(inline(row.get(i))), 4, width - 8);
                }
                height += 4;
            }
            if (table.rows().isEmpty()) for (var header : table.header()) text(inline(header), 4, width - 8);
            return;
        }
        row(table.header(), table.alignments(), true);
        for (var row : table.rows()) row(row, table.alignments(), false);
    }

    private void row(List<String> cells, List<RichMarkdown.Alignment> alignments, boolean header) {
        int top = height, bottom = top;
        for (int i = 0; i < cells.size(); i++) {
            int left = width * i / cells.size(), right = width * (i + 1) / cells.size();
            int available = right - left - 8;
            Component value = inline(cells.get(i));
            if (header) value = value.copy().withStyle(net.minecraft.ChatFormatting.BOLD);
            int y = top + 4;
            for (var line : font.split(value, available)) {
                int measured = font.width(line);
                int x = switch (alignments.get(i)) {
                    case LEFT -> left + 4;
                    case CENTER -> left + 4 + (available - measured) / 2;
                    case RIGHT -> right - 4 - measured;
                };
                lines.add(new Line(line, x, y, measured)); y += font.lineHeight + 3;
            }
            bottom = Math.max(bottom, Math.max(y, top + font.lineHeight + 7));
        }
        height = bottom + 4;
        boxes.add(new Box(0, top, width, height - top, header ? 0x80505e74 : 0x402e3745));
        boxes.add(new Box(0, height - 1, width, 1, 0xff657489));
    }

    /** Distinct links in reading order for host keyboard/narration controls. */
    public List<String> links() {
        var links = new java.util.LinkedHashSet<String>();
        for (var line : lines) line.text.accept((index, style, codePoint) -> {
            if (style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.OpenUrl link) links.add(link.uri().toString());
            return true;
        });
        return List.copyOf(links);
    }

    public int height() { return height; }

    /** Host must apply its viewport scissor before rendering. */
    public void render(GuiGraphicsExtractor graphics, int x, int y, int clipTop, int clipBottom, int color) {
        for (var box : boxes) if (y + box.y + box.height > clipTop && y + box.y < clipBottom)
            graphics.fill(x + box.x, y + box.y, x + box.x + box.width, y + box.y + box.height, box.color);
        for (var line : lines) if (y + line.y + font.lineHeight > clipTop && y + line.y < clipBottom)
            graphics.text(font, line.text, x + line.x, y + line.y, color, false);
    }

    /** Local coordinates only; caller must reject pointer events outside its scissor. */
    public Style styleAt(double x, double y) {
        for (var line : lines) if (y >= line.y && y < line.y + font.lineHeight
                && x >= line.x && x < line.x + line.width) {
            double[] remaining = {x - line.x};
            Style[] hit = {null};
            line.text.accept((index, style, codepoint) -> {
                remaining[0] -= font.getSplitter().stringWidth(FormattedCharSequence.codepoint(codepoint, style));
                if (remaining[0] < 0) { hit[0] = style; return false; }
                return true;
            });
            return hit[0];
        }
        return null;
    }
}
