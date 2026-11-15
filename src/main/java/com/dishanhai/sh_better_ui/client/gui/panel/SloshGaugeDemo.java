// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

final class SloshGaugeDemo {
    static final String ID = "slosh_gauge";
    static final String NAME = "sh_better_ui.slosh_gauge.level";
    static final int ORDER = 34;
    static final int HEIGHT = 180;
    static final int MIN_HEIGHT = 48;
    static final String CODE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSloshGauge;
            import net.minecraft.network.chat.Component;
            import java.util.function.IntConsumer;

            class Example {
                UiSloshGauge gauge(Bounds area, Component name, int level,
                                   UiPreferences preferences, IntConsumer onChange) {
                    return ShBetterUiApi.controls().sloshGauge(
                            area, name, level, UiSloshGauge.Style.DEFAULT, preferences, onChange);
                }
            }
            """;

    private SloshGaugeDemo() {}

    static Bounds centered(Bounds area) {
        int height = Math.min(HEIGHT, area.height());
        int width = Math.min(88, Math.min(area.width(), Math.max(32, height * 88 / HEIGHT)));
        return new Bounds(area.x() + (area.width() - width) / 2, area.y() + (area.height() - height) / 2, width, height);
    }
}
