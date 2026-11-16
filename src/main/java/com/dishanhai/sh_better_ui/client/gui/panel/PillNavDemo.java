// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPillNav;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.kernel.api.controls.PillNavState.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Shared gallery configuration; all navigation rendering and behavior stay in the UUK control. */
final class PillNavDemo {
    static final String ID = "pill_nav";
    static final int ORDER = 36, HEIGHT = 310;
    private static final String KEY = "sh_better_ui.pill_nav.";
    static final String CODE = """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPillNav;
            import com.dishanhai.sh_better_ui.kernel.api.controls.PillNavState;
            import net.minecraft.network.chat.Component;
            import java.util.List;

            private void addNavigation(int x, int y) {
                var entries = List.of(
                        new UiPillNav.Item("home", Component.translatable("sh_better_ui.pill_nav.home")),
                        new UiPillNav.Item("work", Component.translatable("sh_better_ui.pill_nav.work")));
                var nav = ShBetterUiApi.controls().pillNav(font, new Bounds(x, y, 300, 210),
                        Component.translatable("sh_better_ui.pill_nav.name"), entries, "home", null,
                        PillNavState.Options.DEFAULT, UiPillNav.Palette.DARK,
                        ShBetterUiApi.settings().preferences(), (value, index) -> {}, open -> {});
                addRenderableWidget(nav);
                // nav.setActiveValue(value) enables controlled selection; nav.select(value) restores local selection.
                // Bounds include dropdown space. On unmount call nav.cancelInteraction().
            }
            """;

    private String selected = "home";
    private Options options = Options.DEFAULT;
    private boolean light, narrow;
    private UiPillNav control;

    void mount(Font font, Bounds viewport, UiPreferences preferences, Consumer<AbstractWidget> add) {
        suspend();
        int columns = viewport.width() >= 260 ? 3 : 2;
        int rows = (12 + columns - 1) / columns;
        int toolbarHeight = rows * 22;
        int top = viewport.y() + toolbarHeight + 8;
        var area = new Bounds(viewport.x(), top, viewport.width(), Math.max(0, viewport.bottom() - top));
        control = ShBetterUiApi.controls().pillNav(font, area, text("name"), List.of(
                new UiPillNav.Item("home", text("home")), new UiPillNav.Item("work", text("work")),
                new UiPillNav.Item("about", text("about")), new UiPillNav.Item("contact", text("contact"))), selected,
                new UiPillNav.Logo(GraphicIcon.HOME, text("logo"), () -> { selected = "home"; control.select(selected); }),
                options, light ? UiPillNav.Palette.LIGHT : UiPillNav.Palette.DARK, preferences,
                (value, index) -> selected = value, ignored -> {});
        applyWidth(area);
        for (int index = 0; index < 12; index++) {
            int setting = index;
            int w = (viewport.width() - (columns - 1) * 4) / columns;
            var button = new UiButton(viewport.x() + index % columns * (w + 4),
                    viewport.y() + index / columns * 22, w, 19, settingLabel(index), pressed -> {
                        change(setting); control.setOptions(options);
                        control.setPalette(light ? UiPillNav.Palette.LIGHT : UiPillNav.Palette.DARK);
                        applyWidth(area); pressed.setMessage(settingLabel(setting));
                    }, preferences, false, UiStyle.DEFAULT);
            add.accept(button);
        }
        add.accept(control);
    }
    private void applyWidth(Bounds area) {
        int width = narrow ? Math.min(180, area.width()) : area.width();
        control.setWidth(width); control.setX(area.x() + (area.width() - width) / 2);
    }
    private void change(int index) {
        var o = options;
        if (index == 0) light = !light;
        if (index == 8) narrow = !narrow;
        options = new Options(index == 1 ? next(o.layout()) : o.layout(), index == 4 ? next(o.size()) : o.size(),
                index == 5 ? next(o.surface()) : o.surface(), index == 7 ? next(o.itemStyle()) : o.itemStyle(),
                index == 2 ? next(o.hoverEffect()) : o.hoverEffect(), index == 3 ? next(o.indicator()) : o.indicator(),
                index == 6 ? next(o.collapse()) : o.collapse(), index == 9 ? cycle(o.bounce()) : o.bounce(),
                index == 10 ? cycle(o.stretch()) : o.stretch(), index == 11 ? !o.intro() : o.intro());
    }
    private Component settingLabel(int index) {
        String key = switch (index) {
            case 0 -> "theme"; case 1 -> "layout"; case 2 -> "hover"; case 3 -> "indicator";
            case 4 -> "size"; case 5 -> "surface"; case 6 -> "collapse"; case 7 -> "items";
            case 8 -> "width"; case 9 -> "bounce"; case 10 -> "stretch"; default -> "intro";
        };
        Object value = switch (index) {
            case 0 -> text(light ? "light" : "dark");
            case 1 -> value(options.layout()); case 2 -> value(options.hoverEffect());
            case 3 -> value(options.indicator()); case 4 -> value(options.size());
            case 5 -> value(options.surface()); case 6 -> value(options.collapse()); case 7 -> value(options.itemStyle());
            case 8 -> text(narrow ? "narrow" : "wide"); case 9 -> Math.round(options.bounce() * 100) + "%";
            case 10 -> Math.round(options.stretch() * 100) + "%";
            default -> Component.translatable(options.intro() ? "options.on" : "options.off");
        };
        return Component.translatable(KEY + "setting." + key, value);
    }
    private static Component value(Enum<?> value) { return text(value.name().toLowerCase(Locale.ROOT)); }
    private static Component text(String key) { return Component.translatable(KEY + key); }
    private static float cycle(float value) { return value >= .99f ? 0 : Math.min(1, value + .25f); }
    private static <E extends Enum<E>> E next(E current) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        return values[(current.ordinal() + 1) % values.length];
    }
    void cancelInteraction() { if (control != null) control.cancelInteraction(); }
    void suspend() { cancelInteraction(); control = null; }
}
