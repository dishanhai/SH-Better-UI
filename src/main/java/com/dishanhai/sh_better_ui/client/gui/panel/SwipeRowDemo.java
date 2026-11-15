// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeRow;
import com.dishanhai.sh_better_ui.kernel.api.controls.SwipeRowState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;

final class SwipeRowDemo {
    static final String ID = "swipe_row";
    static final int HEIGHT = 111;
    static final int MIN_WIDTH = 120;
    static final String EXAMPLE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeRow;
            import com.dishanhai.sh_better_ui.kernel.api.controls.SwipeRowState;
            import net.minecraft.network.chat.Component;
            import java.util.List;

            private UiSwipeRow row;

            private void addSwipeRow(int x, int y) {
                row = ShBetterUiApi.controls().swipeRow(new Bounds(x, y, 240, 32),
                        Component.translatable("sh_better_ui.swipe_row.demo_left"),
                        List.of(new UiSwipeRow.Action("archive",
                                Component.translatable("sh_better_ui.swipe_row.archive"), GraphicIcon.ARCHIVE, true)),
                        SwipeRowState.Options.DEFAULT, UiSwipeRow.Style.DEFAULT,
                        UiPreferences.configured(), UiSwipeRow.Callbacks.NONE);
                addRenderableWidget(row);
            }

            @Override
            public void tick() {
                super.tick();
                if (row != null) row.tick();
            }

            // Forward primary-button releases and Escape to the focused row before screen navigation.
            // Call row.reset() to restore a collapsed row for Undo.
            """;

    private final UiSwipeRow[] rows = new UiSwipeRow[2];
    private final ArrayDeque<Integer> archived = new ArrayDeque<>();
    private UiButton undo;
    private int top;
    private Component status = label("demo_hint");

    void layout(Bounds area, UiPreferences preferences, Consumer<AbstractWidget> add) {
        int width = Math.min(232, area.width() - 8);
        if (width < MIN_WIDTH || area.height() < HEIGHT) return;
        int x = area.x() + (area.width() - width) / 2;
        top = top(area);
        int y = top;
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == null) {
                final int slot = i;
                var direction = i == 0 ? SwipeRowState.Direction.LEFT : SwipeRowState.Direction.RIGHT;
                var options = new SwipeRowState.Options(direction, 50, .55, .6, .2, 200, true, true);
                var actions = List.of(
                        new UiSwipeRow.Action("archive", label("archive"), GraphicIcon.ARCHIVE, true),
                        new UiSwipeRow.Action("mark", label("mark"), null, null, false,
                                () -> status = label("marked")));
                rows[i] = ShBetterUiApi.controls().swipeRow(new Bounds(x, y, width, 25),
                        label(i == 0 ? "demo_left" : "demo_right"), actions, options, new UiSwipeRow.Style(8, null,
                                null, null, null), preferences, new UiSwipeRow.Callbacks(open -> {
                                    if (open && rows[1 - slot] != null) rows[1 - slot].setOpen(false);
                                }, null, action -> {
                                    archived.push(slot);
                                    undo.active = true;
                                    status = label("archived");
                                }));
            } else {
                rows[i].cancelPress();
                rows[i].setX(x);
                rows[i].setY(y);
                rows[i].setWidth(width);
                rows[i].setPreferences(preferences);
            }
            add.accept(rows[i]);
            y += 30;
        }
        undo = ShBetterUiApi.controls().button(new Bounds(x + (width - 96) / 2, y, 96, 20),
                label("undo"), button -> {
                    Integer slot = archived.poll();
                    if (slot == null) return;
                    rows[slot].reset();
                    reflow();
                    status = label("restored");
                    undo.active = !archived.isEmpty();
                });
        undo.active = !archived.isEmpty();
        add.accept(undo);
        reflow();
    }

    void tick() {
        for (var row : rows) if (row != null) row.tick();
        reflow();
    }

    private void reflow() {
        if (undo == null) return;
        int y = top;
        for (var row : rows) {
            row.setY(y);
            int height = row.layoutHeight();
            y += height + (height > 0 ? 5 : 0);
        }
        undo.setY(y);
    }

    void cancelPress() {
        for (var row : rows) if (row != null) row.cancelPress();
    }

    int statusY(Bounds area) { return undo == null ? top(area) + 84 : undo.getY() + 24; }
    Component status() { return status; }
    private static int top(Bounds area) { return area.y() + (area.height() - HEIGHT) / 2; }
    private static Component label(String key) { return Component.translatable("sh_better_ui.swipe_row." + key); }
}
