package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDisclosure;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStepper;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStepperState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiUnderlinedTabs;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** 山海：原生控件工厂。创建的控件由宿主注册和更新。 */
public final class ControlsApi {
    ControlsApi() {}

    public UiButton button(Bounds area, Component label, Button.OnPress onPress) {
        return new UiButton(area.x(), area.y(), area.width(), area.height(), label, onPress);
    }

    public UiButton button(Bounds area, Component label, Button.OnPress onPress, UiPreferences preferences,
                           boolean primary, UiStyle style) {
        return new UiButton(area.x(), area.y(), area.width(), area.height(), label, onPress, preferences, primary, style);
    }

    /** A draggable, keyboard-accessible scrollbar backed by a shared UUK scroll model. */
    public UiVerticalScrollBar verticalScrollBar(Bounds area, Component name, VerticalScroll scroll,
                                                  double step, Runnable onChange) {
        return new UiVerticalScrollBar(area, name, scroll, step, onChange);
    }

    public UiSlider slider(Bounds area, int min, int max, int step, int value, UiPreferences preferences,
                           IntFunction<Component> label, IntConsumer onChange) {
        return new UiSlider(area, min, max, step, value, preferences, label, onChange);
    }

    public UiToggleButton toggle(Bounds area, boolean value, UiPreferences preferences, UiStyle style,
                                 Function<Boolean, Component> label, Consumer<Boolean> onChange) {
        return new UiToggleButton(area, value, preferences, style, label, onChange);
    }

    public UiSectionBar sectionBar(Bounds area, UiPreferences preferences, List<UiSectionBar.Section> sections,
                                   int selectedIndex, IntConsumer onSelect) {
        return new UiSectionBar(area.x(), area.y(), area.width(), area.height(), preferences, sections, selectedIndex, onSelect);
    }

    /** 横向滚动标签组，作为单个原生控件注册；默认读取客户端标签栏设置。 */
    public UiScrollableSectionBar scrollableSectionBar(Bounds area, UiPreferences preferences,
            List<UiSectionBar.Section> sections, int selectedIndex, IntConsumer onSelect) {
        return new UiScrollableSectionBar(area, preferences, sections, selectedIndex, onSelect);
    }

    public UiScrollableSectionBar scrollableSectionBar(Bounds area, UiPreferences preferences,
            List<UiSectionBar.Section> sections, int selectedIndex, IntConsumer onSelect,
            TabScrollSettings settings, int tabWidth) {
        return new UiScrollableSectionBar(area, preferences, sections, selectedIndex, onSelect, settings, tabWidth);
    }

    public UiUnderlinedTabs underlinedTabs(Bounds area, IntConsumer onSelect) {
        return underlinedTabs(area, List.of(defaultTab("overview"), defaultTab("billing"), defaultTab("settings")),
                0, onSelect);
    }

    public UiUnderlinedTabs underlinedTabs(Bounds area, List<UiUnderlinedTabs.Tab> tabs,
                                          int selectedIndex, IntConsumer onSelect) {
        return new UiUnderlinedTabs(area, tabs, selectedIndex, onSelect);
    }

    private static UiUnderlinedTabs.Tab defaultTab(String key) {
        String prefix = "sh_better_ui.control.underlined_tabs." + key;
        return new UiUnderlinedTabs.Tab(Component.translatable(prefix), Component.translatable(prefix + ".panel"));
    }

    public UiStepper stepper(Bounds area, int currentIndex, IntConsumer onSelect) {
        String prefix = "sh_better_ui.control.stepper.";
        return stepper(area, List.of(Component.translatable(prefix + "cart"), Component.translatable(prefix + "address"),
                Component.translatable(prefix + "payment"), Component.translatable(prefix + "review")),
                new UiStepperState(4, currentIndex), onSelect);
    }

    public UiStepper stepper(Bounds area, List<Component> labels, UiStepperState progress, IntConsumer onSelect) {
        return new UiStepper(area, labels, progress, onSelect);
    }

    public UiDisclosure disclosure(boolean expanded) {
        return new UiDisclosure(expanded);
    }
}
