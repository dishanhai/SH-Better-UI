// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIconMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDisclosure;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStepperState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Interactive 26.x samples. Preview edits live only in this screen. */
public final class DeveloperGalleryScreen extends Screen {
    private static final int INSET = 12;
    private static final int ROW_HEIGHT = 22;
    private static final int GAP = 5;
    private static final int ROW_STEP = ROW_HEIGHT + GAP;
    private static final int STRIPE_WIDTH = 4;
    private static final int MODE_COUNT = 3;
    private static final SystemToast.SystemToastId DEMO_TOAST = new SystemToast.SystemToastId();
    private static final GlassOptics GLASS = GlassMaterial.preset(GlassVariant.REGULAR).optics();
    private static final List<Sample> SAMPLES = List.of(Sample.values());
    private static final List<UiSectionBar.Section> SECTIONS = List.of(
            new UiSectionBar.Section(panel("section.overview"), GraphicIcon.INFO),
            new UiSectionBar.Section(panel("section.display"), GraphicIcon.MONITOR),
            new UiSectionBar.Section(panel("section.appearance"), GraphicIcon.CUSTOM_THEME),
            new UiSectionBar.Section(panel("section.glass"), GraphicIcon.BLUR),
            new UiSectionBar.Section(panel("section.tools"), GraphicIcon.TOOL),
            new UiSectionBar.Section(panel("section.links"), GraphicIcon.GITHUB));
    private static final List<Component> STEPS = List.of(step("cart"), step("address"), step("payment"), step("review"));
    private static final List<GraphicIconMotion> ICON_MOTIONS = List.of(GraphicIconMotion.values());
    private static final List<GraphicIcon> WAITING_ICONS = ICON_MOTIONS.stream().map(GraphicIcon.STATUS_PENDING::withMotion).toList();
    private static final List<Component> MOTION_LABELS = ICON_MOTIONS.stream()
            .map(motion -> label("icon_motion." + motion.name().toLowerCase(Locale.ROOT))).toList();

    private final Screen parent;
    private final Runnable afterReturn;
    private final UiStepperState steps = new UiStepperState(STEPS.size(), 0);
    private final UiDisclosure disclosure = ShBetterUiApi.controls().disclosure(false);
    private final UiDisclosure textDetails = ShBetterUiApi.controls().disclosure(false);
    private final EnumSet<Sample> selectedStyles = EnumSet.noneOf(Sample.class);
    private final GallerySearch search = new GallerySearch();
    private final GalleryFrameTimeline timeline = new GalleryFrameTimeline();
    private @Nullable Sample sample;
    private @Nullable AbstractWidget preferredFocus;
    private @Nullable UiSlider slider;
    private @Nullable UiScrollableSectionBar scrollable;
    private @Nullable UiToggleButton toggle;
    private @Nullable UiButton detailsPage;
    private @Nullable Component dialogResult;
    private UiButton back;
    private UiPreferences preferences;
    private UiPreferences effectPreferences;
    private UiTheme theme;
    private int revision;
    private int directoryIndex;
    private int directoryCapacity;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int footerY;
    private boolean compact;

    private int presses;
    private boolean toggled;
    private int sliderValue = 50;
    private int selectedSection;
    private int selectedScrollable;
    private int selectedTab;
    private double scrollOffset;
    private boolean blur = true;
    private boolean opticalGlass = true;
    private boolean materialEnabled = true;
    private int refraction = 100;
    private boolean capsuleEnabled;
    private int mode;

    public DeveloperGalleryScreen(Screen parent, Runnable afterReturn) {
        super(label("title"));
        this.parent = Objects.requireNonNull(parent);
        this.afterReturn = Objects.requireNonNull(afterReturn);
    }

    @Override
    protected void init() {
        cancelGesture();
        if (scrollable != null) scrollOffset = scrollable.scrollOffset();
        scrollable = null;
        slider = null;
        toggle = null;
        detailsPage = null;
        preferredFocus = null;
        revision++;

        preferences = UiPreferences.configured();
        effectPreferences = new UiPreferences(preferences.highContrast(), preferences.reducedMotion(), true);
        theme = UiTheme.current();
        if (sample == Sample.DARK_BUTTON) {
            theme = UiTheme.resolve(ControlPanelSettings.current().theme().withMode(ThemeSettings.Mode.DARK));
        }
        panelWidth = Math.max(1, Math.min(500, width - 16));
        panelHeight = Math.max(1, Math.min(306, height - 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        contentX = left + INSET;
        contentY = top + 35;
        contentWidth = panelWidth - 2 * INSET;
        footerY = top + panelHeight - INSET - ROW_HEIGHT;
        compact = panelWidth < 156 || panelHeight < 126;
        if (compact) {
            int buttonHeight = Math.max(1, Math.min(ROW_HEIGHT, height - 2));
            back = button(new Bounds(left, Math.max(0, top + panelHeight - buttonHeight), panelWidth, buttonHeight),
                    CommonComponents.GUI_BACK, ignored -> onClose(), false);
            return;
        }

        if (sample == null) initDirectory();
        else initPreview(sample);
        back = button(new Bounds(contentX, footerY, Math.min(96, contentWidth / 2), ROW_HEIGHT),
                CommonComponents.GUI_BACK, ignored -> onClose(), false);
    }

    private void initDirectory() {
        int columns = contentWidth >= 260 ? 2 : 1;
        int rows = (footerY - contentY) / ROW_STEP;
        directoryCapacity = columns * rows;
        int start = directoryIndex / directoryCapacity * directoryCapacity;
        int end = Math.min(start + directoryCapacity, SAMPLES.size());
        int columnWidth = (contentWidth - GAP * (columns - 1)) / columns;
        for (int index = start; index < end; index++) {
            var entry = SAMPLES.get(index);
            int cell = index - start;
            int x = contentX + (cell % columns) * (columnWidth + GAP);
            int y = contentY + (cell / columns) * ROW_STEP;
            var button = button(new Bounds(x, y, columnWidth, ROW_HEIGHT), entry.title(), ignored -> open(entry), false);
            button.setLeadingIcon(entry.icon);
            if (index == directoryIndex) preferredFocus = button;
        }

        if (directoryCapacity < SAMPLES.size()) {
            int right = contentX + contentWidth;
            var previous = button(new Bounds(right - 2 * ROW_HEIGHT - GAP, footerY, ROW_HEIGHT, ROW_HEIGHT),
                    label("directory_previous"), ignored -> turnPage(-1), false);
            var next = button(new Bounds(right - ROW_HEIGHT, footerY, ROW_HEIGHT, ROW_HEIGHT),
                    label("directory_next"), ignored -> turnPage(1), false);
            previous.setIcon(GraphicIcon.PREVIOUS);
            previous.active = start > 0;
            next.setIcon(GraphicIcon.NEXT);
            next.active = end < SAMPLES.size();
        }
    }

    private void initPreview(Sample preview) {
        var controls = ShBetterUiApi.controls();
        var row = new Bounds(contentX, contentY, contentWidth, ROW_HEIGHT);
        switch (preview) {
            case BUTTON -> {
                var control = button(row, label("preview.press_count", presses),
                        pressed -> pressed.setMessage(label("preview.press_count", ++presses)), true);
                control.setLeadingIcon(GraphicIcon.RUN);
                preferredFocus = control;
            }
            case TOGGLE -> preferredFocus = demoToggle(row, toggled,
                    value -> CommonComponents.optionStatus(label("try_toggle"), value), value -> toggled = value);
            case SLIDER -> preferredFocus = demoSlider(row, 100, sliderValue, value -> label("slider_value", value),
                    value -> sliderValue = value);
            case DISCLOSURE -> preferredFocus = addRenderableWidget(disclosure.button(contentX, contentY,
                    contentWidth, ROW_HEIGHT, label("expand"), label("collapse"), preferences, expanded ->
                            triggerImmediateNarration(true)));
            case SECTIONS -> {
                var bar = controls.sectionBar(row, preferences, SECTIONS.subList(0, 3), selectedSection,
                        index -> selectedSection = index);
                bar.buttons().forEach(this::addRenderableWidget);
                preferredFocus = bar.buttons().get(selectedSection);
            }
            case SCROLLABLE_SECTIONS -> {
                scrollable = addRenderableWidget(controls.scrollableSectionBar(row, preferences, SECTIONS,
                        selectedScrollable, index -> selectedScrollable = index, TabScrollSettings.DEFAULT,
                        UiScrollableSectionBar.DEFAULT_TAB_WIDTH));
                scrollable.scrollTo(scrollOffset);
                preferredFocus = scrollable;
            }
            case UNDERLINED_TABS -> {
                var tabs = addRenderableWidget(controls.underlinedTabs(new Bounds(contentX, contentY, contentWidth,
                        footerY - contentY - GAP), index -> selectedTab = index));
                tabs.selection().select(selectedTab);
                preferredFocus = tabs;
            }
            case STEPPER -> {
                int height = Math.min(48, footerY - contentY - ROW_STEP);
                var continueButton = controls.button(
                        new Bounds(contentX, contentY + height + GAP, contentWidth, ROW_HEIGHT),
                        CommonComponents.GUI_CONTINUE, pressed -> {
                            steps.advance();
                            pressed.active = steps.currentIndex() < steps.count() - 1;
                        }, preferences, true, UiStyle.DEFAULT);
                continueButton.active = steps.currentIndex() < steps.count() - 1;
                preferredFocus = addRenderableWidget(controls.stepper(new Bounds(contentX, contentY, contentWidth, height),
                        STEPS, steps, index -> continueButton.active = index < steps.count() - 1));
                addRenderableWidget(continueButton);
            }
            case BACKDROP_BLUR -> preferredFocus = demoToggle(
                    new Bounds(contentX, effectControlsY(), contentWidth, ROW_HEIGHT), blur,
                    value -> panel("enabled", CommonComponents.optionStatus(value)), value -> blur = value);
            case LIQUID_GLASS -> {
                int half = (contentWidth - GAP) / 2;
                preferredFocus = demoToggle(new Bounds(contentX, effectControlsY(), half, ROW_HEIGHT), opticalGlass,
                        value -> panel("optical_glass", CommonComponents.optionStatus(value)), enabled -> {
                            opticalGlass = enabled;
                            if (slider != null) slider.active = opticalGlass && !preferences.highContrast();
                        });
                demoSlider(new Bounds(contentX + half + GAP, effectControlsY(), contentWidth - half - GAP, ROW_HEIGHT),
                        200, refraction, value -> panel("scale", panel("refraction"), value), value -> refraction = value)
                        .active = opticalGlass && !preferences.highContrast();
            }
            case CAPSULE_TOGGLE -> {
                preferredFocus = demoToggle(row, capsuleEnabled, CommonComponents::optionStatus,
                        value -> capsuleEnabled = value, UiStyle.LIQUID_GLASS);
                toggle.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);
                toggle.setModeIndicatorsVisible(true);
            }
            case MODE_INDICATORS -> {
                var control = controls.button(row, label("mode", mode + 1), pressed -> {
                    mode = (mode + 1) % MODE_COUNT;
                    var button = (UiButton) pressed;
                    button.setMessage(label("mode", mode + 1));
                    button.setModeIndicators(MODE_COUNT, mode);
                    triggerImmediateNarration(true);
                }, preferences, false, UiStyle.LIQUID_GLASS);
                control.setModeIndicators(MODE_COUNT, mode);
                control.setPreview(label("mode_tip"));
                preferredFocus = addRenderableWidget(control);
            }
            case TEXT_DETAILS -> initTextDetails(row);
            case SEARCH -> preferredFocus = search.addWidgets(
                    new Bounds(contentX, contentY, contentWidth, footerY - contentY - GAP), font,
                    preferences, this::addRenderableWidget);
            case DIALOG -> preferredFocus = button(row, developer("demo.dialog"), ignored -> openDialog(), true);
            case FRAME_TIMELINE -> {
                slider = timeline.layout(new Bounds(contentX, contentY, contentWidth, footerY - contentY),
                        preferences, this::addRenderableWidget);
                preferredFocus = slider;
            }
            case LIQUID_BUTTON, DARK_BUTTON, NETWORK_TAB -> initStyledButton(preview, row);
            case CLEAR_GLASS, FROSTED_GLASS -> preferredFocus = demoToggle(
                    new Bounds(contentX, effectControlsY(), contentWidth, ROW_HEIGHT), materialEnabled,
                    value -> panel("optical_glass", CommonComponents.optionStatus(value)), value -> materialEnabled = value);
            case RESET_ICON, SETTINGS_ICON, COPY_ICON, STATUS_ICON -> { /* Drawn in the preview area. */ }
            default -> throw new IllegalStateException("Standalone gallery sample: " + preview.key);
        }
    }

    private void initTextDetails(Bounds row) {
        int half = (row.width() - GAP) / 2;
        preferredFocus = addRenderableWidget(textDetails.button(row.x(), row.y(), half, row.height(),
                label("expand"), label("collapse"), preferences, expanded -> triggerImmediateNarration(true)));
        var page = new Bounds(row.x() + half + GAP, row.y(), row.width() - half - GAP, row.height());
        detailsPage = button(page, Component.translatable("sh_better_ui.details.open_page"), ignored -> openDetails(), false);
    }

    private void openDetails() {
        Runnable backToDetails = () -> {
            ControlPanelPlatform.show(minecraft, this);
            if (detailsPage != null) setInitialFocus(detailsPage);
        };
        ControlPanelPlatform.show(minecraft, new AlertScreen(backToDetails, Sample.TEXT_DETAILS.title(), label("long_text")) {
            @Override
            public void onClose() { backToDetails.run(); }
        });
    }

    private void openDialog() {
        ControlPanelPlatform.show(minecraft, new ConfirmScreen(confirmed -> {
            dialogResult = Component.translatable(confirmed ? "sh_better_ui.action.succeeded" : "sh_better_ui.action.cancelled");
            ControlPanelPlatform.show(minecraft, this);
            if (preferredFocus != null) setInitialFocus(preferredFocus);
            if (confirmed) SystemToast.add(ControlPanelPlatform.toasts(minecraft), DEMO_TOAST,
                    developer("demo.toast"), developer("demo.toast_details"));
        }, developer("demo.dialog"), developer("demo.dialog_details"), CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL));
    }

    private void initStyledButton(Sample preview, Bounds row) {
        UiStyle style = switch (preview) {
            case LIQUID_BUTTON -> UiStyle.LIQUID_GLASS;
            case NETWORK_TAB -> UiStyle.NETWORK;
            default -> UiStyle.DEFAULT;
        };
        boolean liquid = preview == Sample.LIQUID_BUTTON;
        int width = Math.min(164, row.width());
        int y = liquid ? row.y() + (canvasBottom() - row.y() - row.height()) / 2 : row.y();
        var area = new Bounds(row.x() + (row.width() - width) / 2, y, width, row.height());
        var control = ShBetterUiApi.controls().button(area, CommonComponents.GUI_DONE, pressed -> {
            if (!selectedStyles.add(preview)) selectedStyles.remove(preview);
            ((UiButton) pressed).setSelected(selectedStyles.contains(preview));
            triggerImmediateNarration(true);
        }, liquid ? effectPreferences : preferences, false, style);
        control.setLeadingIcon(GraphicIcon.RESET);
        control.setSelected(selectedStyles.contains(preview));
        control.setSampleBackground(liquid);
        preferredFocus = addRenderableWidget(control);
    }

    private UiButton demoToggle(Bounds area, boolean value, Function<Boolean, Component> caption,
                                Consumer<Boolean> changed) {
        return demoToggle(area, value, caption, changed, UiStyle.DEFAULT);
    }

    private UiButton demoToggle(Bounds area, boolean value, Function<Boolean, Component> caption,
                                Consumer<Boolean> changed, UiStyle style) {
        var control = ShBetterUiApi.controls().toggle(area, value, preferences, style, caption, enabled -> {
            changed.accept(enabled);
            if (toggle != null) toggle.setValue(enabled);
            triggerImmediateNarration(true);
        });
        toggle = control;
        return addRenderableWidget(control.button());
    }

    private UiSlider demoSlider(Bounds area, int maximum, int value, IntFunction<Component> caption,
                                IntConsumer changed) {
        var control = ShBetterUiApi.controls().slider(area, 0, maximum, 5, value, preferences, caption, changed);
        control.setOnCommit(ignored -> {}, changed);
        slider = addRenderableWidget(control);
        return control;
    }

    private UiButton button(Bounds area, Component label, Button.OnPress onPress, boolean primary) {
        return addRenderableWidget(ShBetterUiApi.controls().button(area, label, onPress, preferences, primary, UiStyle.DEFAULT));
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(preferredFocus == null ? back : preferredFocus);
    }

    @Override
    protected void repositionElements() {
        var focused = getFocused();
        boolean focusBack = !compact && focused == back;
        int slot = compact || focused == null ? -1 : children().indexOf(focused);
        if (sample == null && slot >= 0) {
            int first = directoryIndex / directoryCapacity * directoryCapacity;
            if (slot < directoryCapacity && first + slot < SAMPLES.size()) directoryIndex = first + slot;
        }
        super.repositionElements();
        if (focusBack) {
            setInitialFocus(back);
        } else if (sample != null && !compact && slot >= 0 && slot < children().size()
                && children().get(slot) instanceof AbstractWidget widget && widget.visible && widget.active) {
            setInitialFocus(widget);
        }
    }

    private void open(Sample selected) {
        directoryIndex = selected.ordinal();
        if (selected.factory != null) {
            ControlPanelPlatform.show(minecraft, selected.factory.apply(this, selected.key));
            return;
        }
        sample = selected;
        rebuildWidgets();
    }

    void returnFromStandalone() {
        ControlPanelPlatform.show(minecraft, this);
        rebuildWidgets();
        setInitialFocus();
    }

    private boolean turnPage(int direction) {
        if (compact || sample != null) return false;
        int page = directoryIndex / directoryCapacity;
        int next = (page + direction) * directoryCapacity;
        if (next < 0 || next >= SAMPLES.size()) return false;
        directoryIndex = next;
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int before = revision;
        boolean handled = super.mouseClicked(event, doubleClick);
        if (sample == Sample.SEARCH && !compact) {
            var editor = search.takeFocusRequest();
            if (editor != null) {
                setDragging(false);
                setInitialFocus(editor);
            }
        }
        if (revision != before) {
            // Native dispatch assigns focus to the clicked widget after its callback returns.
            setDragging(false);
            setInitialFocus();
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= contentX && mouseX < contentX + contentWidth && mouseY >= contentY && mouseY < footerY
                && deltaY != 0 && turnPage(deltaY < 0 ? 1 : -1)) return true;
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (sample == Sample.SEARCH && !compact && search.keyPressed(event, getFocused(), this::setInitialFocus)) return true;
        if (event.isEscape()) {
            if ((getFocused() instanceof UiSlider focusedSlider && focusedSlider.keyPressed(event))
                    || (scrollable != null && scrollable.cancelDrag())) {
                setDragging(false);
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN && turnPage(1)) return true;
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP && turnPage(-1)) return true;
        return super.keyPressed(event);
    }

    private void cancelGesture() {
        if (slider != null) slider.cancelEdit();
        if (scrollable != null) scrollable.cancelDrag();
        setDragging(false);
    }

    @Override
    public void tick() {
        if (!minecraft.isWindowActive()) {
            cancelGesture();
            if (sample == Sample.FRAME_TIMELINE) timeline.pause();
        }
    }

    @Override
    public void removed() {
        cancelGesture();
        if (sample == Sample.FRAME_TIMELINE) timeline.pause();
    }

    @Override
    public void onClose() {
        cancelGesture();
        if (sample != null) {
            if (sample == Sample.FRAME_TIMELINE) timeline.pause();
            sample = null;
            rebuildWidgets();
            return;
        }
        ControlPanelPlatform.show(minecraft, parent);
        afterReturn.run();
    }

    @Override
    public Component getNarrationMessage() {
        if (sample == null) return hasDirectoryPages() ? CommonComponents.joinForNarration(getTitle(), directoryPage())
                : super.getNarrationMessage();
        var note = switch (sample) {
            case DISCLOSURE -> disclosure.expanded() ? label("details") : null;
            case TEXT_DETAILS -> textDetails.expanded() ? label("long_text") : null;
            case DIALOG -> dialogResult;
            case RESET_ICON, SETTINGS_ICON, COPY_ICON -> iconPath();
            case STATUS_ICON -> CommonComponents.joinForNarration(MOTION_LABELS.toArray(Component[]::new));
            default -> effectStatus();
        };
        return note == null ? sample.title() : CommonComponents.joinForNarration(sample.title(), note);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (effectPreview()) {
            graphics.fill(0, 0, width, height, theme.background() | 0xFF000000);
            drawPanel(graphics);
            if (!preferences.highContrast()) drawPattern(graphics);
            if (sample == Sample.BACKDROP_BLUR && blur) ShBetterUiApi.blur().background(graphics, effectPreferences);
        } else {
            if (minecraft.level == null) extractPanorama(graphics, partialTick);
            graphics.fill(0, 0, width, height, 0x30121A26);
        }
        ControlPanelPlatform.subtitles(minecraft);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (effectPreview()) {
            if (sample == Sample.LIQUID_GLASS && !preferences.highContrast()) drawGlass(graphics);
            if (materialEnabled && (sample == Sample.CLEAR_GLASS || sample == Sample.FROSTED_GLASS)) {
                var preset = sample == Sample.CLEAR_GLASS ? GalleryMaterials.Preset.CLEAR : GalleryMaterials.Preset.FROSTED;
                GalleryMaterials.draw(graphics, new Bounds(contentX, contentY, contentWidth, canvasBottom() - contentY),
                        preset, effectPreferences);
            }
            int surface = theme.surface() | 0xFF000000;
            graphics.fill(left + INSET, top + 1, left + panelWidth - INSET, contentY - 1, surface);
            graphics.fill(left + INSET, effectControlsY() - 2, left + panelWidth - INSET, footerY + ROW_HEIGHT, surface);
            var status = effectStatus();
            if (status != null && (sample == Sample.LIQUID_GLASS || sample == Sample.BACKDROP_BLUR)) {
                graphics.fill(contentX, contentY, contentX + contentWidth, canvasBottom(), surface);
                int lineY = contentY + 4;
                for (var line : font.split(status, contentWidth - 12)) {
                    if (lineY + font.lineHeight > canvasBottom()) break;
                    graphics.text(font, line, contentX + 6, lineY, theme.text(), false);
                    lineY += font.lineHeight;
                }
            }
            if (sample == Sample.LIQUID_BUTTON) GalleryMaterials.prepareButtonBackdrop(graphics, effectPreferences);
        } else {
            drawPanel(graphics);
        }
        graphics.nextStratum();
        if (compact) {
            PanelText.drawLine(graphics, font, panel("compact"), left + 4, top + 4,
                    Math.max(1, panelWidth - 8), theme.text(), mouseX, mouseY);
        } else {
            PanelText.drawLine(graphics, font, sample == null ? getTitle() : sample.title(), contentX, top + INSET,
                    contentWidth, theme.text(), mouseX, mouseY);
            if (sample == Sample.DISCLOSURE && disclosure.expanded()) {
                PanelText.drawLine(graphics, font, label("details"), contentX, contentY + ROW_STEP + 4,
                        contentWidth, theme.text(), mouseX, mouseY);
            }
            if (sample == Sample.TEXT_DETAILS) drawDetails(graphics, mouseX, mouseY);
            if (sample == Sample.DIALOG && dialogResult != null) {
                PanelText.drawLine(graphics, font, dialogResult, contentX, contentY + ROW_STEP + 4,
                        contentWidth, theme.text(), mouseX, mouseY);
            }
            if (sample == Sample.RESET_ICON || sample == Sample.SETTINGS_ICON || sample == Sample.COPY_ICON) {
                drawIcon(graphics, mouseX, mouseY);
            }
            if (sample == Sample.STATUS_ICON) drawStatusIcons(graphics, mouseX, mouseY);
            if (sample == Sample.FRAME_TIMELINE) timeline.extract(graphics, font, theme, mouseX, mouseY);
            if (sample == null && hasDirectoryPages()) {
                int x = back.getX() + back.getWidth() + GAP;
                int available = contentX + contentWidth - 2 * (ROW_HEIGHT + GAP) - x;
                if (available >= 26) PanelText.drawLine(graphics, font, directoryPage(), x,
                        footerY + (ROW_HEIGHT - font.lineHeight) / 2, available, theme.text(), mouseX, mouseY);
            }
        }
        UiTheme.preview(theme, () -> super.extractRenderState(graphics, mouseX, mouseY, partialTick));
    }

    private void drawPanel(GuiGraphicsExtractor graphics) {
        int border = preferences.highContrast() ? theme.text() : theme.border();
        RoundedCorners.draw(graphics, left, top, panelWidth, panelHeight, 12, 1, border, theme.surface() | 0xFF000000);
    }

    private boolean hasDirectoryPages() { return !compact && directoryCapacity < SAMPLES.size(); }

    private Component directoryPage() {
        return label("directory_page", 1 + directoryIndex / directoryCapacity,
                Math.ceilDiv(SAMPLES.size(), directoryCapacity), SAMPLES.size());
    }

    private boolean effectPreview() {
        return !compact && (sample == Sample.BACKDROP_BLUR || sample == Sample.LIQUID_GLASS
                || sample == Sample.LIQUID_BUTTON || sample == Sample.CLEAR_GLASS || sample == Sample.FROSTED_GLASS);
    }

    private int effectControlsY() { return footerY - ROW_STEP; }

    private int canvasBottom() { return effectControlsY() - 6; }

    private void drawPattern(GuiGraphicsExtractor graphics) {
        int right = contentX + contentWidth;
        for (int x = contentX, stripe = 0; x < right; x += STRIPE_WIDTH, stripe++) {
            graphics.fill(x, contentY, Math.min(right, x + STRIPE_WIDTH), canvasBottom(),
                    stripe % 2 == 0 ? 0xFF18324B : 0xFFCAE9F3);
        }
        int band = contentY + 3 * (canvasBottom() - contentY) / 4;
        graphics.fill(contentX, band, right, Math.min(canvasBottom(), band + 3), 0xFFF1A553);
    }

    private void drawGlass(GuiGraphicsExtractor graphics) {
        int canvasHeight = canvasBottom() - contentY;
        int x = contentX + contentWidth / 4;
        int y = contentY + canvasHeight / 4;
        int w = contentWidth / 2;
        int h = canvasHeight / 2;
        if (opticalGlass) {
            var optics = new GlassOptics(GLASS.transmission(), GLASS.refraction() * refraction / 100,
                    GLASS.lensing(), GLASS.scattering(), GLASS.edgeHighlight(), GLASS.thickness(), GLASS.blurRadius());
            LiquidGlassSurface.draw(graphics, x, y, w, h, 12, 0x34213B56, 0x80D0E9FF, optics, effectPreferences);
            graphics.nextStratum();
        }
        RoundedCorners.draw(graphics, x, y, w, h, 12, 1, 0xD0D0E9FF, 0);
    }

    private @Nullable Component effectStatus() {
        if (!effectPreview()) return null;
        if (preferences.highContrast()) return label("effect_contrast");
        if (sample == Sample.BACKDROP_BLUR && blur && minecraft.options.getMenuBackgroundBlurriness() < 1) {
            return label("preview.menu_blur_off");
        }
        return null;
    }

    private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var details = label("long_text");
        int y = contentY + ROW_STEP + 4;
        if (!textDetails.expanded()) {
            PanelText.drawLine(graphics, font, details, contentX, y, contentWidth, theme.text(), mouseX, mouseY);
            return;
        }
        for (var line : font.split(details, contentWidth)) {
            if (y + font.lineHeight > footerY - GAP) break;
            graphics.text(font, line, contentX, y, theme.text(), false);
            y += font.lineHeight;
        }
    }

    private Component iconPath() { return Component.literal(Objects.requireNonNull(sample).icon.texture().toString()); }

    private void drawIcon(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int size = GraphicIcon.size(2);
        int y = contentY + (footerY - contentY - size) / 2;
        Objects.requireNonNull(sample).icon.draw(graphics, contentX, y, 2, theme.text(), preferences);
        PanelText.drawLine(graphics, font, iconPath(), contentX + size + GAP, y + (size - font.lineHeight) / 2,
                contentWidth - size - GAP, theme.text(), mouseX, mouseY);
    }

    private void drawStatusIcons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cell = contentWidth / WAITING_ICONS.size();
        int height = footerY - contentY;
        int scale = cell >= 40 && height >= 58 ? 2 : 1;
        int size = GraphicIcon.size(scale);
        int y = contentY + (height - size - font.lineHeight - 8) / 2;
        for (int i = 0; i < WAITING_ICONS.size(); i++) {
            int x = contentX + i * cell;
            WAITING_ICONS.get(i).draw(graphics, x + (cell - size) / 2, y, scale, theme.text(), preferences);
            var title = MOTION_LABELS.get(i);
            int labelWidth = Math.min(cell - GAP, font.width(title));
            PanelText.drawLine(graphics, font, title, x + (cell - labelWidth) / 2, y + size + 8,
                    labelWidth, theme.text(), mouseX, mouseY);
        }
    }

    private static Component label(String key, Object... args) {
        return panel("developer.gallery." + key, args);
    }

    private static Component panel(String key, Object... args) {
        return Component.translatable("sh_better_ui.panel." + key, args);
    }

    private static Component developer(String key) { return panel("developer." + key); }

    private static Component step(String key) {
        return Component.translatable("sh_better_ui.control.stepper." + key);
    }

    private enum Sample {
        BUTTON("button", GraphicIcon.RUN),
        TOGGLE("toggle", GraphicIcon.SWITCH),
        SLIDER("slider", GraphicIcon.SETTINGS),
        DISCLOSURE("disclosure", GraphicIcon.EXPAND),
        SECTIONS("sections", GraphicIcon.TOOLBAR),
        SCROLLABLE_SECTIONS("scrollable_sections", GraphicIcon.SORT),
        UNDERLINED_TABS("underlined_tabs", GraphicIcon.ORGANIZE),
        STEPPER("stepper", GraphicIcon.PLAN),
        BACKDROP_BLUR("backdrop_blur", GraphicIcon.BLUR),
        LIQUID_GLASS("liquid_glass", GraphicIcon.CUSTOM_THEME),
        CAPSULE_TOGGLE("capsule_toggle", GraphicIcon.SWITCH),
        MODE_INDICATORS("mode_indicators", GraphicIcon.ORGANIZE),
        TEXT_DETAILS("text_details", GraphicIcon.DOCUMENT),
        SEARCH("search", GraphicIcon.SEARCH),
        DIALOG("dialog", GraphicIcon.WARNING),
        LIQUID_BUTTON("liquid_button", GraphicIcon.CUSTOM_THEME),
        DARK_BUTTON("dark_button", GraphicIcon.MONITOR),
        NETWORK_TAB("network_tab", GraphicIcon.NETWORK),
        CLEAR_GLASS("clear_glass", GraphicIcon.BLUR),
        FROSTED_GLASS("frosted_glass", GraphicIcon.BLUR),
        RESET_ICON("reset_icon", GraphicIcon.RESET),
        SETTINGS_ICON("settings_icon", GraphicIcon.SETTINGS),
        COPY_ICON("copy_icon", GraphicIcon.COPY),
        STATUS_ICON("status_icon", GraphicIcon.STATUS_PENDING),
        FRAME_TIMELINE("frame_timeline", GraphicIcon.PLAN),
        DYNAMIC_STATUS("dynamic_status", GraphicIcon.STATUS_PENDING, GalleryControlSamples::new),
        BUTTON_FEEDBACK("button_feedback", GraphicIcon.RUN, GalleryControlSamples::new),
        LOADING_SPINNER("loading_spinner", GraphicIcon.STATUS_PENDING, GalleryControlSamples::new),
        LOADING_PROGRESS("loading_progress", GraphicIcon.STATUS_PENDING, GalleryControlSamples::new),
        LOADING_SKELETON("loading_skeleton", GraphicIcon.DOCUMENT, GalleryControlSamples::new),
        ATTENTION_INDICATOR("attention_indicator", GraphicIcon.ORGANIZE, GalleryControlSamples::new),
        GRADIENT_CARD("gradient_card", GraphicIcon.CUSTOM_THEME, GalleryControlSamples::new),
        SLIDE_CONFIRM("slide_confirm", GraphicIcon.SUCCESS, GalleryGestureSamples::new),
        LOCAL_MODAL("local_modal", GraphicIcon.EXPAND, GalleryGestureSamples::new),
        BOTTOM_SHEET("bottom_sheet", GraphicIcon.EXPAND, GalleryGestureSamples::new),
        ELASTIC_COLLISION("elastic_collision", GraphicIcon.MOVE, GalleryGestureSamples::new),
        PULL_REFRESH("pull_refresh", GraphicIcon.RESET, GalleryGestureSamples::new),
        INTERRUPTIBLE_ANIMATION("interruptible_animation", GraphicIcon.RUN, GalleryMotionSamples::new),
        PAGE_SLIDE("page_slide", GraphicIcon.NEXT, GalleryMotionSamples::new),
        RECIPE_TRANSITION("recipe_transition", GraphicIcon.EDIT, GalleryMotionSamples::new),
        SCREEN_CROSSFADE("screen_crossfade", GraphicIcon.SWITCH, GalleryMotionSamples::new),
        TOAST("toast", GraphicIcon.INFO, GalleryToastSample::new),
        VISUAL_ALIGNMENT("visual_alignment", GraphicIcon.ORGANIZE, GalleryAlignmentSample::new),
        DIRECTIONAL_BLUR("directional_blur", GraphicIcon.BLUR, GalleryMaterialSamples::new),
        REFRACTION_WAVE("refraction_wave", GraphicIcon.WARP, GalleryMaterialSamples::new),
        CLASSIC_GLASS_THEMES("classic_glass_themes", GraphicIcon.CUSTOM_THEME, GalleryMaterialSamples::new),
        GLASS_PANEL_SHADOW("glass_panel_shadow", GraphicIcon.CUSTOM_THEME, GalleryMaterialSamples::new),
        GLASS_TEXT_GLOW("glass_text_glow", GraphicIcon.DOCUMENT, GalleryMaterialSamples::new),
        GLASS_TOOLTIP("glass_tooltip", GraphicIcon.INFO, GalleryMaterialSamples::new),
        ICON_SHADER("icon_shader", GraphicIcon.RENDER, GalleryMaterialSamples::new),
        RADIAL_GRADIENT("radial_gradient", GraphicIcon.CUSTOM_THEME, GallerySpatialSamples::create),
        SPATIAL_SURFACE("spatial_surface", GraphicIcon.RENDER, GallerySpatialSamples::create),
        SPATIAL_SHADOW("spatial_shadow", GraphicIcon.RENDER, GallerySpatialSamples::create),
        SPATIAL_LIGHTING("spatial_lighting", GraphicIcon.LIGHT_MODE, GallerySpatialSamples::create),
        FORMATTED_TEXT_EFFECTS("formatted_text_effects", GraphicIcon.DOCUMENT, GallerySpatialSamples::create),
        EFFECT_APPEARANCE("effect_appearance", GraphicIcon.SETTINGS, GalleryAppearanceSamples::new),
        DYNAMIC_THEME("dynamic_theme", GraphicIcon.CUSTOM_THEME, GalleryDynamicThemeSample::new),
        ASCII_FIELD("ascii_field", GraphicIcon.DOCUMENT, GallerySpatialSamples::create),
        SHAPE_WAVES("shape_waves", GraphicIcon.WARP, GallerySpatialSamples::create),
        VISUAL_REVEAL("visual_reveal", GraphicIcon.EXPAND, GallerySpatialSamples::create),
        CLICK_RIPPLE("click_ripple", GraphicIcon.RUN, GalleryAppearanceSamples::new),
        FLOW_BORDER("flow_border", GraphicIcon.CUSTOM_THEME, GalleryAppearanceSamples::new),
        BREATHING_GLOW("breathing_glow", GraphicIcon.LIGHT_MODE, GalleryAppearanceSamples::new),
        CAPSULE_REVEAL("capsule_reveal", GraphicIcon.EXPAND, GalleryAppearanceSamples::new),
        DOT_MATRIX("dot_matrix", GraphicIcon.RENDER, GalleryFieldSamples::create),
        PATTERN_WAVES("pattern_waves", GraphicIcon.WARP, GalleryFieldSamples::create),
        LIGHT_RAYS("light_rays", GraphicIcon.LIGHT_MODE, GalleryFieldSamples::create),
        ELECTRIC_LOGO("electric_logo", GraphicIcon.RENDER, GalleryFieldSamples::create),
        ANTIGRAVITY("antigravity", GraphicIcon.MOVE, GalleryFieldSamples::create),
        CODE_RAIN("code_rain", GraphicIcon.DOCUMENT, GalleryFieldSamples::create),
        LASER_FLOW("laser_flow", GraphicIcon.WARP, GalleryFieldSamples::create),
        MUSIC_HUD("music_hud", GraphicIcon.MUSIC, GalleryMusicSamples::create),
        MUSIC_ISLAND("music_island", GraphicIcon.MUSIC, GalleryMusicSamples::create),
        LOCAL_MUSIC("local_music", GraphicIcon.MUSIC, GalleryMusicSamples::create),
        LYRICS("lyrics", GraphicIcon.DOCUMENT, GalleryMusicSamples::create),
        LIVE_ACTIVITY("live_activity", GraphicIcon.STATUS_READY, GalleryLiveActivitySample::new);

        private final String key;
        private final GraphicIcon icon;
        private final @Nullable BiFunction<DeveloperGalleryScreen, String, GallerySampleScreen> factory;

        Sample(String key, GraphicIcon icon) {
            this(key, icon, null);
        }

        Sample(String key, GraphicIcon icon, @Nullable BiFunction<DeveloperGalleryScreen, String, GallerySampleScreen> factory) {
            this.key = key;
            this.icon = icon;
            this.factory = factory;
        }

        Component title() { return label("sample." + key); }
    }
}
