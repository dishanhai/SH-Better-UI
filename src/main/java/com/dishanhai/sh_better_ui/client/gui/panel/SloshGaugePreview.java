// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSloshGauge;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

final class SloshGaugePreview implements PreviewScene {
    private int level;
    private UiSloshGauge gauge;

    SloshGaugePreview() {
        this(60);
    }

    private SloshGaugePreview(int level) {
        this.level = level;
    }

    @Override
    public void layout(PreviewContext host) {
        suspend();
        gauge = host.add(ShBetterUiApi.controls().sloshGauge(
                SloshGaugeDemo.centered(host.viewport()), Component.translatable(SloshGaugeDemo.NAME),
                level, UiSloshGauge.Style.DEFAULT, host.preferences(), next -> level = next));
    }

    @Override
    public void suspend() {
        if (gauge != null) gauge.cancelDrag();
        gauge = null;
    }

    @Override
    public void close() {
        suspend();
    }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        if (gauge != null) gauge.cancelDrag();
        int saved = level;
        return () -> new SloshGaugePreview(saved);
    }
}
