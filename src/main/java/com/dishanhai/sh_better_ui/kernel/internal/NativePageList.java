// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.TabScrollState;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** Draws only visible rows; selection and search are local and never become page fields. */
final class NativePageList extends AbstractWidget {
    private static final int RAIL = 5;
    private final TabScrollState scroll;
    private final int rowHeight;
    private List<String> source = List.of();
    private String query = "";
    private List<Row> sourceRows = List.of();
    private List<Row> rows = List.of();
    private NativeTextReadability.Label readableLabel;
    private long sourceRevision;
    private boolean draggingThumb;
    private boolean initialized;

    NativePageList(int width, int height, int rowHeight, TabScrollState scroll) {
        super(0, 0, width, height, Component.translatable("sh_better_ui.pages.list"));
        this.rowHeight = rowHeight;
        this.scroll = scroll;
    }

    void bindTextReadability(NativeTextReadability.Label label) {
        readableLabel = label;
    }

    void update(List<String> items, String filter) {
        if (initialized && source.equals(items) && query.equals(filter)) return;
        if (!initialized || !source.equals(items)) {
            retireTextRows();
            sourceRevision++;
            sourceRows = IntStream.range(0, items.size()).mapToObj(index -> new Row(items.get(index),
                    readableLabel == null ? null : new UiId(readableLabel.id().namespace(),
                            readableLabel.id().path() + "/row/" + sourceRevision + "/" + index))).toList();
        }
        initialized = true;
        source = items;
        query = filter;
        String needle = filter.toLowerCase(Locale.ROOT).strip();
        rows = needle.isEmpty() ? sourceRows
                : sourceRows.stream().filter(row -> row.text().toLowerCase(Locale.ROOT).contains(needle)).toList();
        scroll.resize(getHeight(), (double) rows.size() * rowHeight);
    }

    void retireTextRows() {
        if (readableLabel != null) {
            for (var row : sourceRows) readableLabel.retire(row.id());
        }
    }

    public boolean mouseScrolled(double x, double y, double vertical) {
        return isMouseOver(x, y) && scroll.scrollBy(-vertical * rowHeight);
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return mouseScrolled(x, y, vertical);
    }

    @Override
    public void onClick(double x, double y) {
        draggingThumb = scroll.maxOffset() > 0 && x >= getX() + getWidth() - RAIL;
        if (draggingThumb) dragTo(y);
    }

    @Override
    protected void onDrag(double x, double y, double dx, double dy) {
        if (draggingThumb) dragTo(y);
    }

    @Override
    public void onRelease(double x, double y) {
        draggingThumb = false;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) draggingThumb = false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!isFocused() || !active) return false;
        return switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll.scrollBy(-rowHeight);
            case GLFW.GLFW_KEY_DOWN -> scroll.scrollBy(rowHeight);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll.scrollBy(-getHeight());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll.scrollBy(getHeight());
            case GLFW.GLFW_KEY_HOME -> scroll.scrollTo(0);
            case GLFW.GLFW_KEY_END -> scroll.scrollTo(scroll.maxOffset());
            default -> false;
        };
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            graphics.fill(x, y, x + width, y + height, DarkModeTheme.surfaceColor());
            var font = Minecraft.getInstance().font;
            int offset = (int) Math.round(scroll.offset());
            int first = offset / rowHeight;
            int end = Math.min(rows.size(), (offset + height + rowHeight - 1) / rowHeight);
            for (int index = first; index < end; index++) {
                var row = rows.get(index);
                String line = font.plainSubstrByWidth(row.text(), Math.max(0, width - RAIL - 6));
                int top = y + index * rowHeight - offset;
                int lineY = top + Math.max(0, (rowHeight - font.lineHeight) / 2);
                int color = DarkModeTheme.textColor();
                if (readableLabel == null || !readableLabel.draw(graphics, font, Component.literal(line).getVisualOrderText(),
                        x + 3, lineY, color, true, 0, isHovered && mouseY >= top && mouseY < top + rowHeight,
                        isFocused() && index == first, row.id(), new Rect(x, top, Math.max(0, width - RAIL), rowHeight))) {
                    graphics.drawString(font, line, x + 3, lineY, color);
                }
            }
            if (scroll.maxOffset() > 0) {
                int thumb = thumbHeight();
                int top = y + (int) Math.round((height - thumb) * scroll.offset() / scroll.maxOffset());
                graphics.fill(x + width - RAIL, top, x + width, top + thumb, DarkModeTheme.textColor());
            }
            if (isFocused()) graphics.renderOutline(x, y, width, height, DarkModeTheme.textColor());
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        int first = (int) scroll.offset() / rowHeight;
        if (first < rows.size()) output.add(NarratedElementType.HINT, Component.literal(rows.get(first).text()));
    }

    private record Row(String text, UiId id) {}

    private int thumbHeight() {
        return Math.min(getHeight(), Math.max(8, (int) (getHeight() * (double) getHeight()
                / Math.max(1, rows.size() * rowHeight))));
    }

    private void dragTo(double y) {
        int thumb = thumbHeight();
        scroll.scrollTo(scroll.maxOffset() * (y - getY() - thumb / 2.0) / Math.max(1, getHeight() - thumb));
    }
}
