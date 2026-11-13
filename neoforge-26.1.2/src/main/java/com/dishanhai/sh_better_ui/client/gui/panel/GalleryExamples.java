// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import org.jspecify.annotations.Nullable;

/** Examples only for APIs that the 26.x artifact actually exposes. */
final class GalleryExamples {
    private GalleryExamples() {}

    static @Nullable String code(String id) {
        return switch (id) {
            case "button" -> """
                    import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
                    import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
                    import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
                    import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
                    import net.minecraft.network.chat.Component;

                    // In a Screen subclass:
                    private void addExample(int x, int y) {
                        var api = ShBetterUiApi.controls();
                        var button = api.button(new Bounds(x, y, 140, 22), Component.translatable("gui.done"),
                                pressed -> {
                                    var control = (UiButton) pressed;
                                    control.setSelected(!control.isSelected());
                                }, ShBetterUiApi.settings().preferences(), false, UiStyle.DEFAULT);
                        addRenderableWidget(button);
                    }
                    """;
            case "toggle", "capsule_toggle" -> """
                    import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
                    import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
                    import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
                    import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
                    import net.minecraft.network.chat.CommonComponents;

                    private boolean enabled;
                    private UiToggleButton toggle;

                    private void addExample(int x, int y) {
                        toggle = ShBetterUiApi.controls().toggle(new Bounds(x, y, 140, 22), enabled,
                                ShBetterUiApi.settings().preferences(), UiStyle.%s,
                                CommonComponents::optionStatus, value -> {
                                    enabled = value;
                                    toggle.setValue(value);
                                });
                        addRenderableWidget(toggle.button());
                    }
                    """.formatted(id.equals("capsule_toggle") ? "LIQUID_GLASS" : "DEFAULT");
            case "slider" -> """
                    import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
                    import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
                    import net.minecraft.network.chat.Component;

                    private int value = 50;

                    private void addExample(int x, int y) {
                        var slider = ShBetterUiApi.controls().slider(new Bounds(x, y, 164, 22),
                                0, 100, 5, value, ShBetterUiApi.settings().preferences(),
                                current -> Component.translatable("sh_better_ui.panel.developer.gallery.slider_value", current),
                                current -> value = current);
                        addRenderableWidget(slider);
                    }
                    """;
            case "disclosure" -> """
                    import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
                    import com.dishanhai.sh_better_ui.client.gui.scaled.UiDisclosure;
                    import net.minecraft.network.chat.Component;

                    private final UiDisclosure disclosure = ShBetterUiApi.controls().disclosure(false);

                    private void addExample(int x, int y) {
                        addRenderableWidget(disclosure.button(x, y, 140, 22,
                                Component.translatable("sh_better_ui.panel.developer.gallery.expand"),
                                Component.translatable("sh_better_ui.panel.developer.gallery.collapse"),
                                ShBetterUiApi.settings().preferences(), expanded -> {}));
                    }
                    """;
            case "underlined_tabs" -> """
                    import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
                    import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

                    private void addExample(int x, int y) {
                        var tabs = ShBetterUiApi.controls().underlinedTabs(new Bounds(x, y, 220, 60),
                                index -> selectedTab = index);
                        addRenderableWidget(tabs);
                    }

                    private int selectedTab;
                    """;
            case "stepper" -> """
                    import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
                    import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

                    private void addExample(int x, int y) {
                        var steps = ShBetterUiApi.controls().stepper(new Bounds(x, y, 220, 48), 0,
                                index -> selectedStep = index);
                        addRenderableWidget(steps);
                        // After validating the current step: steps.progress().advance();
                    }

                    private int selectedStep;
                    """;
            case "dialog" -> """
                    import net.minecraft.client.gui.screens.ConfirmScreen;
                    import net.minecraft.client.gui.screens.Screen;
                    import net.minecraft.network.chat.CommonComponents;
                    import net.minecraft.network.chat.Component;
                    import java.util.function.Consumer;

                    private Screen confirmation(Consumer<Boolean> onResult) {
                        return new ConfirmScreen(confirmed -> onResult.accept(confirmed),
                                Component.translatable("sh_better_ui.panel.developer.demo.dialog"),
                                Component.translatable("sh_better_ui.panel.developer.demo.dialog_details"),
                                CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL);
                    }
                    """;
            case "toast" -> """
                    import net.minecraft.client.gui.components.toasts.SystemToast;
                    import net.minecraft.client.gui.components.toasts.ToastManager;
                    import net.minecraft.network.chat.Component;

                    private static final SystemToast.SystemToastId NOTICE = new SystemToast.SystemToastId();

                    private void notify(ToastManager manager) {
                        SystemToast.add(manager, NOTICE,
                                Component.translatable("sh_better_ui.panel.developer.demo.toast"),
                                Component.translatable("sh_better_ui.panel.developer.demo.toast_details"));
                    }
                    """;
            case "sections" -> """
                    import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
                    import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
                    import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
                    import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
                    import net.minecraft.network.chat.Component;
                    import java.util.List;

                    private int selectedSection;

                    private void addExample(int x, int y) {
                        var sections = List.of(
                                new UiSectionBar.Section(Component.translatable("sh_better_ui.panel.section.overview"), GraphicIcon.INFO),
                                new UiSectionBar.Section(Component.translatable("sh_better_ui.panel.section.display"), GraphicIcon.MONITOR));
                        var bar = ShBetterUiApi.controls().sectionBar(new Bounds(x, y, 220, 22),
                                ShBetterUiApi.settings().preferences(), sections, selectedSection,
                                index -> selectedSection = index);
                        bar.buttons().forEach(this::addRenderableWidget);
                    }
                    """;
            case "scrollable_sections" -> """
                    import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
                    import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
                    import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
                    import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
                    import net.minecraft.network.chat.Component;
                    import java.util.List;

                    private int selectedSection;

                    private void addExample(int x, int y) {
                        var sections = List.of(
                                new UiSectionBar.Section(Component.translatable("sh_better_ui.panel.section.overview"), GraphicIcon.INFO),
                                new UiSectionBar.Section(Component.translatable("sh_better_ui.panel.section.display"), GraphicIcon.MONITOR));
                        var bar = ShBetterUiApi.controls().scrollableSectionBar(new Bounds(x, y, 220, 22),
                                ShBetterUiApi.settings().preferences(), sections, selectedSection,
                                index -> selectedSection = index);
                        addRenderableWidget(bar);
                    }
                    """;
            default -> null;
        };
    }
}
