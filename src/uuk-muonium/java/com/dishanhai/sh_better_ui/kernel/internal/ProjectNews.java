// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiSettingsDetailPage.Field;
import com.dishanhai.sh_better_ui.client.api.UiSettingsDetailPage.Section;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ProjectNews {
    private static final String REPOSITORY = "https://github.com/dishanhai/SH-Better-UI";
    private UiScreen screen;

    private ProjectNews() {}

    public static Component text(String key) {
        return Component.translatable("sh_better_ui.project_news." + key);
    }

    public static void open(Screen parent) {
        var news = new ProjectNews();
        var page = ShBetterUiApi.controls().settingsDetailPage(text("title"), List.of(
                new Section(text("updates"), List.of(
                        news.link("skija", "install", "/blob/main/docs/MUONIUM_PLAYER.md"),
                        news.link("releases", "view", "/releases"))),
                new Section(text("help"), List.of(
                        news.link("docs", "view", "/tree/main/docs"),
                        news.link("feedback", "view", "/issues")))));
        news.screen = page.builder().parent(parent).build();
        news.screen.open();
    }

    private Field link(String label, String action, String path) {
        var focus = new AtomicReference<AbstractWidget>();
        return new Field(text(label), (bounds, context) -> {
            var widget = ShBetterUiApi.controls().button(bounds, text(action), clicked -> {
                if (!ShBetterUiApi.links().open(screen, REPOSITORY + path, focus::get)) {
                    clicked.setMessage(text("link_unavailable"));
                }
            });
            focus.set(widget);
            return widget;
        });
    }
}
