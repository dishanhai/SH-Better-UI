// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiRubberSegment;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import net.minecraft.network.chat.Component;

import java.util.List;

import java.util.function.Supplier;

final class RubberSegmentPreview implements PreviewScene {
    private static final String KEY = "sh_better_ui.rubber_segment.";
    private static final List<UiSectionBar.Section> PERIODS = List.of(
            option("day", GraphicIcon.LIGHT_MODE), option("week", GraphicIcon.ORGANIZE),
            option("month", GraphicIcon.DOCUMENT), option("year", GraphicIcon.EXPAND));
    private int selected = 1;

    private static UiSectionBar.Section option(String period, GraphicIcon icon) {
        return new UiSectionBar.Section(Component.translatable(KEY + period), icon);
    }

    private UiRubberSegment control;

    @Override
    public void layout(PreviewContext host) {
        suspend();
        var area = host.viewport();
        int width = Math.min(224, area.width());
        if (width < 76 || area.height() < 28) return;
        control = host.add(ShBetterUiApi.controls().rubberSegment(
                new Bounds(area.x() + (area.width() - width) / 2, area.y() + (area.height() - 28) / 2, width, 28),
                Component.translatable(KEY + "period"), PERIODS, selected, host.preferences(), index -> {
                    if (host.interactive()) selected = index;
                    else control.select(selected);
                }));
    }

    @Override
    public void suspend() {
        if (control != null) control.cancelDrag();
        control = null;
    }

    @Override
    public void close() {
        suspend();
    }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        int value = selected;
        return () -> {
            var preview = new RubberSegmentPreview();
            preview.selected = value;
            return preview;
        };
    }
}
