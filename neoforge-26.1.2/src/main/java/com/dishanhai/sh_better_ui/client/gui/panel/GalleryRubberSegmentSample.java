// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import net.minecraft.network.chat.Component;

import java.util.List;

final class GalleryRubberSegmentSample extends GallerySampleScreen {
    private static final String KEY = "sh_better_ui.rubber_segment.";
    private static final List<UiSectionBar.Section> PERIODS = List.of(
            option("day", GraphicIcon.LIGHT_MODE), option("week", GraphicIcon.ORGANIZE),
            option("month", GraphicIcon.DOCUMENT), option("year", GraphicIcon.EXPAND));
    private int selected = 1;
    private com.dishanhai.sh_better_ui.client.gui.scaled.GlassControls glass;
    private com.dishanhai.sh_better_ui.client.gui.scaled.UiRubberSegment control;

    @Override protected void beforeSampleInit() {
        if (glass != null) glass.close();
        glass = ShBetterUiApi.controls().glass(preferences);
        control = null;
    }

    @Override public void removed() {
        super.removed();
        if (glass != null) glass.close();
    }

    @Override protected void extractSample(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                           int mouseX, int mouseY, float partialTick) {
        if (control == null) return;
        int x = control.getX(), y = control.getY(), w = control.getWidth(), h = control.getHeight();
        graphics.fill(x, y, x + w, y + h, 0xff153f79);
        graphics.fill(x + w / 5, y, x + w * 2 / 5, y + h, 0xff85c9d7);
        graphics.fill(x + w * 3 / 5, y, x + w * 4 / 5, y + h, 0xffd79d66);
    }

    private static UiSectionBar.Section option(String period, GraphicIcon icon) {
        return new UiSectionBar.Section(Component.translatable(KEY + period), icon);
    }

    GalleryRubberSegmentSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        int width = Math.min(224, body.width());
        if (width < 76 || body.height() < 28) return;
        control = addRenderableWidget(glass.segmented(
                new Bounds(body.x() + (body.width() - width) / 2, body.y() + (body.height() - 28) / 2, width, 28),
                Component.translatable(KEY + "period"), PERIODS, selected, index -> selected = index));
        initialFocus = control;
    }
}
