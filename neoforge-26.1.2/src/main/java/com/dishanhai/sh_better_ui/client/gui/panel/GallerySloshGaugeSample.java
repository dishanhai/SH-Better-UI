// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSloshGauge;
import net.minecraft.network.chat.Component;

final class GallerySloshGaugeSample extends GallerySampleScreen {
    private int level = 60;

    GallerySloshGaugeSample(DeveloperGalleryScreen parent, String id) {
        super(parent, id);
    }

    @Override
    protected void initSample() {
        initialFocus = addRenderableWidget(ShBetterUiApi.controls().sloshGauge(
                SloshGaugeDemo.centered(body), Component.translatable(SloshGaugeDemo.NAME),
                level, UiSloshGauge.Style.DEFAULT, preferences, next -> level = next));
    }

    @Override
    protected int minimumBodyHeight() {
        return SloshGaugeDemo.MIN_HEIGHT;
    }

}
