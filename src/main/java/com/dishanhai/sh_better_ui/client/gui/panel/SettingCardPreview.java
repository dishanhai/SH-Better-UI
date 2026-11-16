// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.*;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Supplier;

/** Isolated primary toggle and expandable detail control; never mutates live client settings. */
final class SettingCardPreview implements PreviewScene {
    private UiSettingCard card;
    private boolean selected, expanded, detailSelected;
    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec("sh_better_ui:setting_card", PreviewSpec.Category.CONTROLS, 45,
                "sh_better_ui.setting_card.demo.title", 180, 0, "", SettingCardPreview::new));
    }
    @Override public void layout(PreviewContext host) {
        suspend(); var area = host.viewport();
        if (area.width() < 180 || area.height() < 150) return;
        var primary = new UiButton(0, 0, 100, 22, label("toggle"), b -> {
            if (host.interactive()) { selected = !selected; ((UiButton) b).setSelected(selected); }
        }, host.preferences(), false);
        primary.setSelected(selected);
        var detail = new UiButton(0, 0, 100, 22, label("detail"), b -> {
            if (host.interactive()) { detailSelected = !detailSelected; ((UiButton) b).setSelected(detailSelected); }
        }, host.preferences(), false);
        detail.setSelected(detailSelected);
        card = ShBetterUiApi.controls().settingCard(host.font(), new Bounds(area.x(), area.y(), area.width(), 1),
                label("title"), label("description"), primary, label("details"), host.preferences(), () -> {
                    if (host.interactive()) { expanded = !expanded; card.setExpanded(expanded, List.of(detail)); }
                });
        card.setExpanded(expanded, List.of(detail));
        card.widgets().forEach(host::add);
    }
    @Override public void render(UiFrame frame) {
        if (card != null) card.render(frame.graphics(), -1, -1, frame.partialTick());
    }
    @Override public void suspend() { if (card != null) card.close(); card = null; }
    @Override public Supplier<? extends PreviewScene> saveForRemount() {
        boolean savedSelected = selected, savedExpanded = expanded, savedDetail = detailSelected;
        return () -> { var result = new SettingCardPreview(); result.selected = savedSelected; result.expanded = savedExpanded; result.detailSelected = savedDetail; return result; };
    }
    private static Component label(String key) { return Component.translatable("sh_better_ui.setting_card.demo." + key); }
}
