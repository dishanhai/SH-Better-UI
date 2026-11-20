// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/** Keeps a fixed scroll track and mounts only the controls in the current row window. */
public final class UiScrollingRows {
    public static final int TRACK_SPACE = 8;
    private static final int TRACK_WIDTH = 5;
    private final UiScreen owner;
    private final Bounds area;
    private final VerticalScroll scroll = new VerticalScroll();
    private final UiVerticalScrollBar bar;
    private final int rowHeight;
    private final IntConsumer mount;
    private List<AbstractWidget> mounted = List.of();
    private int first = -1;

    /** The mount callback adds regular widgets for rows starting at the supplied index. */
    public UiScrollingRows(UiScreen owner, Bounds area, Component label, int rowHeight, int visibleRows,
                           int totalRows, int initialRow, IntConsumer mount) {
        this.owner = owner;
        this.area = area;
        this.rowHeight = rowHeight;
        this.mount = mount;
        scroll.resize((double) visibleRows * rowHeight, (double) totalRows * rowHeight);
        scroll.scrollTo((double) initialRow * rowHeight);
        bar = owner.add(new UiVerticalScrollBar(new Bounds(area.right() - TRACK_WIDTH, area.y(),
                TRACK_WIDTH, area.height()), label, scroll, rowHeight, this::update));
        bar.active = bar.visible = scroll.maxOffset() > 0;
        update();
    }

    public boolean scroll(double x, double y, double vertical) {
        return area.contains(x, y) && bar.scrollByWheel(vertical);
    }

    private void update() {
        int next = (int) Math.round(scroll.offset() / rowHeight);
        if (next == first) return;
        first = next;
        mounted.forEach(owner::remove);
        int from = owner.children().size();
        mount.accept(first);
        mounted = owner.children().subList(from, owner.children().size()).stream()
                .map(AbstractWidget.class::cast).toList();
    }
}
