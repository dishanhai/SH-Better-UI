// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiJellyRadio;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;

final class JellyRadioDemo {
    static final String ID = "jelly_radio";
    static final int ORDER = 35;
    static final int HEIGHT = 64;
    static final String DEFAULT_VALUE = "medium";
    static final String CODE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiJellyRadio;
            import net.minecraft.network.chat.Component;
            import java.util.List;
            import java.util.function.BiConsumer;

            private void addRadio(int x, int y, BiConsumer<String, Integer> onChange) {
                var options = List.of(
                        new UiJellyRadio.Option("off", Component.translatable("sh_better_ui.jelly_radio.off")),
                        new UiJellyRadio.Option("low", Component.translatable("sh_better_ui.jelly_radio.low")),
                        new UiJellyRadio.Option("high", Component.translatable("sh_better_ui.jelly_radio.high"), null, true));
                var radio = ShBetterUiApi.controls().jellyRadio(new Bounds(x, y, 220, 36),
                        Component.translatable("sh_better_ui.jelly_radio.label"), options, "low",
                        ShBetterUiApi.settings().preferences(), onChange);
                addRenderableWidget(radio);
            }
            """;

    private static final String KEY = "sh_better_ui.jelly_radio.";
    private static final List<UiJellyRadio.Option> OPTIONS = List.of(
            new UiJellyRadio.Option("off", Component.translatable(KEY + "off")),
            new UiJellyRadio.Option("low", Component.translatable(KEY + "low"), GraphicIcon.ORGANIZE, false),
            new UiJellyRadio.Option("medium", Component.translatable(KEY + "medium")),
            new UiJellyRadio.Option("high", Component.translatable(KEY + "high")),
            new UiJellyRadio.Option("max", Component.translatable(KEY + "max"), null, true));

    private JellyRadioDemo() {}

    static UiJellyRadio layout(Bounds viewport, UiPreferences preferences, String selected,
                               BiConsumer<String, Integer> onChange) {
        int width = Math.min(284, viewport.width());
        int height = Math.min(36, viewport.height());
        if (width < 100 || height < 22) return null;
        var area = new Bounds(viewport.x() + (viewport.width() - width) / 2,
                viewport.y() + (viewport.height() - height) / 2, width, height);
        return ShBetterUiApi.controls().jellyRadio(area, Component.translatable(KEY + "label"), OPTIONS,
                selected, preferences, onChange);
    }
}
