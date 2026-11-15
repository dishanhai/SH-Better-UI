package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeRow;
import com.dishanhai.sh_better_ui.kernel.api.controls.SwipeRowState;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.FolderFloatControl;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFolderFloat;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCallChip;
import com.dishanhai.sh_better_ui.kernel.api.controls.CallChipState;
import com.dishanhai.sh_better_ui.client.gui.scaled.FuseButtonStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHoldButton;
import com.dishanhai.sh_better_ui.kernel.api.input.HoldGesture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFuseButton;
import com.dishanhai.sh_better_ui.kernel.api.controls.FuseButtonState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCodeSlots;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPromptBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiJellyRadio;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiVerticalScrollBar;
import com.dishanhai.sh_better_ui.kernel.api.layout.VerticalScroll;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDisclosure;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDynamicStatus;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLoading;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSloshGauge;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiRubberSegment;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStepper;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStepperState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextDetails;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextField;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiUnderlinedTabs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** 山海：原生控件工厂、实时状态与加载提示。创建的控件由宿主注册和更新。 */
public final class ControlsApi {
    /** Mounts as a native widget. Call tick() while mounted and route captured releases to the focused row. */
    public UiSwipeRow swipeRow(Bounds area, Component content, List<UiSwipeRow.Action> actions,
                              SwipeRowState.Options options, UiSwipeRow.Style style, UiPreferences preferences,
                              UiSwipeRow.Callbacks callbacks) {
        return new UiSwipeRow(area, content, actions, options, style, preferences, callbacks);
    }

    /** Creates a native floating folder. The bounds include the space above the folder for its labels. */
    public <T> UiFolderFloat<T> folderFloat(Font font, Bounds area, Component label, Component sublabel,
                                           List<FolderFloatControl.Item<T>> items, FolderFloatControl.Options options,
                                           UiPreferences preferences, BiConsumer<T, Integer> onSelect,
                                           Consumer<Boolean> onOpenChange) {
        return new UiFolderFloat<>(font, area, label, sublabel, items, options, preferences, onSelect, onOpenChange);
    }

    /** Creates a native call indicator. Pass a null callback to disable retry. */
    public UiCallChip callChip(Bounds area, Component name, Component argument, GraphicIcon icon,
                               CallChipState state, UiPreferences preferences, boolean showTimer, Runnable onRetry) {
        return new UiCallChip(area, name, argument, icon, state, preferences, showTimer, onRetry);
    }

    ControlsApi() {}

    /** Native undoable button. Register it as a widget and call its tick() in the screen tick. */
    public UiFuseButton fuseButton(Bounds area, Component label, Component undo, Component done,
                                   UiPreferences preferences, FuseButtonStyle style, FuseButtonState.Options options,
                                   FuseButtonState.Callbacks callbacks) {
        return new UiFuseButton(area, label, undo, done, preferences, style, options, callbacks);
    }

    /** Mount as a native widget. A null tap callback leaves short presses without an action. */
    public UiHoldButton holdButton(Bounds area, Component label, Component doneLabel, HoldGesture.Timing timing,
                                   UiHoldButton.Appearance appearance, UiPreferences preferences,
                                   Runnable onHold, Runnable onTap) {
        return new UiHoldButton(area, label, doneLabel, timing, appearance, preferences, onHold, onTap);
    }

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

    /** A focusable digit row. Register it as a widget and use its completion callback to validate input. */
    public UiCodeSlots codeSlots(Bounds area, int length, Component label, UiPreferences preferences) {
        return new UiCodeSlots(area, length, label, preferences);
    }

    /** Register the composer as a single widget. Attachment and dictation callbacks are optional. */
    public UiPromptBar promptBar(Font font, Bounds area, Component placeholder, List<UiPromptBar.Source> sources,
                                 List<UiPromptBar.Option> commands, List<UiPromptBar.Option> models,
                                 List<Component> efforts, UiPreferences preferences, Consumer<UiPromptBar.Submission> onSend,
                                 Runnable onStop, Runnable onAttach, Runnable onDictate) {
        return new UiPromptBar(font, area, placeholder, sources, commands, models, efforts, preferences,
                onSend, onStop, onAttach, onDictate);
    }

    /** UUK single-line editor with clipboard, selection, and undo. Register it as a widget. */
    public UiTextField textField(Font font, Bounds area, Component label, int maxLength, UiPreferences preferences) {
        return new UiTextField(font, area, label, maxLength, preferences);
    }

    /** A radio group with independent elastic chips. The listener receives the selected value and index. */
    public UiJellyRadio jellyRadio(Bounds area, Component name, List<UiJellyRadio.Option> options,
                                   String initialValue, UiPreferences preferences, BiConsumer<String, Integer> onChange) {
        return new UiJellyRadio(area, name, options, initialValue, preferences, onChange);
    }

    public UiRubberSegment rubberSegment(Bounds area, Component name, List<UiSectionBar.Section> options,
                                         int selected, UiPreferences preferences, IntConsumer onChange) {
        return new UiRubberSegment(area, name, options, selected, preferences, onChange);
    }

    /** Creates a vertical liquid meter. Pass a listener to enable editing, or null for read-only. */
    public UiSloshGauge sloshGauge(Bounds area, Component name, int value, UiSloshGauge.Style style,
                                  UiPreferences preferences, IntConsumer onChange) {
        return new UiSloshGauge(area, name, value, style, preferences, onChange);
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

    public UiTextDetails textDetails(Component title, Component details, int collapsedLines) {
        return new UiTextDetails(title, details, collapsedLines);
    }

    public UiDynamicStatus dynamicStatus(Supplier<UiDynamicStatus.Snapshot> source) {
        return new UiDynamicStatus(source);
    }

    public void drawStatus(GuiGraphics graphics, Font font, Bounds area, UiDynamicStatus.Snapshot snapshot,
                           UiPreferences preferences) {
        UiDynamicStatus.draw(graphics, font, area, snapshot, preferences);
    }

    public void skeletonCard(GuiGraphics graphics, Bounds area, long nowMs, UiPreferences preferences) {
        UiSkeleton.card(graphics, area, nowMs, preferences);
    }

    public void skeleton(GuiGraphics graphics, Bounds viewport, List<UiSkeleton.Block> blocks, long nowMs,
                         UiPreferences preferences, float opacity) {
        UiSkeleton.draw(graphics, viewport, blocks, nowMs, preferences, opacity);
    }

    public void spinner(GuiGraphics graphics, Bounds area, long nowMs, UiPreferences preferences) {
        UiLoading.spinner(graphics, area, nowMs, preferences);
    }

    public void progressRing(GuiGraphics graphics, Bounds area, float progress, UiPreferences preferences) {
        UiLoading.spinner(graphics, area, progress, preferences);
    }

    public void edgeSpinner(GuiGraphics graphics, Bounds viewport, UiLoading.Edge edge, long nowMs,
                            UiPreferences preferences) {
        UiLoading.edgeSpinner(graphics, viewport, edge, nowMs, preferences);
    }

    public void progressBar(GuiGraphics graphics, Bounds area, float progress, UiPreferences preferences) {
        UiLoading.progressBar(graphics, area, progress, preferences);
    }

    public void topLoadingBar(GuiGraphics graphics, Bounds viewport, long nowMs, UiPreferences preferences) {
        UiLoading.topBar(graphics, viewport, nowMs, preferences);
    }

    public void topProgressBar(GuiGraphics graphics, Bounds viewport, float progress, UiPreferences preferences) {
        UiLoading.topBar(graphics, viewport, progress, preferences);
    }
}
