// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.blur.BackgroundBlur;
import com.dishanhai.sh_better_ui.client.glass.LiquidGlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** Native settings screen for the 26.x client. Preview changes are kept in the screen's draft. */
public final class ControlPanelScreen extends Screen {
    private static final int PADDING = 12;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_STEP = 27;
    private static final int STATUS_SPACE = 16;
    private static final int TOOLS_TAB = 3;
    private static final int FOOTER_TAB_ORDER = 1;
    private static final List<ThemeSettings.Mode> THEMES = List.of(ThemeSettings.Mode.DARK, ThemeSettings.Mode.LIGHT,
            ThemeSettings.Mode.LIQUID_GLASS, ThemeSettings.Mode.HUD_GLASS);
    private static final List<UiSectionBar.Section> TABS = List.of(
            new UiSectionBar.Section(label("section.glass"), GraphicIcon.BLUR),
            new UiSectionBar.Section(label("section.display"), GraphicIcon.MONITOR),
            new UiSectionBar.Section(label("section.appearance"), GraphicIcon.CUSTOM_THEME),
            new UiSectionBar.Section(label("section.tools"), GraphicIcon.INFO));

    private final @Nullable Screen parent;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private final List<Runnable> refreshRows = new ArrayList<>();
    private final int[] scrollRows = new int[TABS.size() + ToolPage.values().length - 1];
    private ToolPage toolPage = ToolPage.INDEX;
    private int overviewTicks;
    private ControlPanelSettings saved;
    private ControlPanelSettings draft;
    private @Nullable ControlPanelSettings beforeReset;
    private @Nullable Component status;
    private UiPreferences preferences;
    private UiTheme theme;
    private @Nullable UiSectionBar tabs;
    private UiButton back;
    private @Nullable UiButton defaults;
    private @Nullable UiButton apply;
    private @Nullable UiButton up;
    private @Nullable UiButton down;
    private int tab;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentWidth;
    private int contentY;
    private int footerY;
    private int visibleRows;
    private boolean compact;

    public ControlPanelScreen(@Nullable Screen parent) {
        super(label("title"));
        this.parent = parent;
        saved = ControlPanelSettings.current();
        draft = saved;
    }

    public static void open(Minecraft minecraft) {
        var parent = ControlPanelPlatform.screen(minecraft);
        if (!(parent instanceof ControlPanelScreen)) {
            ControlPanelPlatform.show(minecraft, new ControlPanelScreen(parent));
        }
    }

    @Override
    protected void init() {
        rows.clear();
        refreshRows.clear();
        tabs = null;
        apply = null;
        defaults = null;
        up = null;
        down = null;
        syncStyle();
        panelWidth = Math.max(1, Math.min(472, width - 16));
        panelHeight = Math.max(1, Math.min(258, height - 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        footerY = top + panelHeight - PADDING - ROW_HEIGHT;
        compact = panelWidth < 152 || panelHeight < 124;
        if (compact) {
            back = button(left, Math.max(0, height - ROW_HEIGHT - 3), panelWidth,
                    CommonComponents.GUI_BACK, this::onClose, false);
            return;
        }

        contentX = left + PADDING;
        contentY = Math.min(top + 57, footerY - STATUS_SPACE - ROW_HEIGHT);
        contentWidth = panelWidth - 2 * PADDING;
        visibleRows = 1 + (footerY - STATUS_SPACE - contentY - ROW_HEIGHT) / ROW_STEP;
        int tabsY = Math.min(top + 28, contentY - ROW_HEIGHT - 5);
        tabs = ShBetterUiApi.controls().sectionBar(new Bounds(contentX, tabsY, contentWidth, ROW_HEIGHT),
                preferences, TABS, tab, this::selectTab);
        tabs.buttons().forEach(this::addRenderableWidget);
        initRows();

        int column = (contentWidth - 8) / 3;
        back = button(contentX, footerY, column, CommonComponents.GUI_BACK, this::onClose, false);
        defaults = button(contentX + column + 4, footerY, column, label("reset"), this::resetDraft, false);
        apply = button(contentX + 2 * (column + 4), footerY, contentWidth - 2 * (column + 4),
                label("apply"), this::applyDraft, true);
        back.setTabOrderGroup(FOOTER_TAB_ORDER);
        defaults.setTabOrderGroup(FOOTER_TAB_ORDER);
        apply.setTabOrderGroup(FOOTER_TAB_ORDER);
        apply.setTooltip(Tooltip.create(label("apply.tooltip")));
        refresh();
    }

    @Override
    protected void repositionElements() {
        cancelDrag();
        var focus = focusAnchor();
        super.repositionElements();
        if (focus != null) restoreFocus(focus);
    }

    private void selectTab(int selected) {
        if (tab == selected) return;
        cancelDrag();
        tab = selected;
        initRows();
        refresh();
    }

    private void initRows() {
        rows.forEach(this::removeWidget);
        if (up != null) removeWidget(up);
        if (down != null) removeWidget(down);
        up = null;
        down = null;
        rows.clear();
        refreshRows.clear();
        switch (tab) {
            case 0 -> {
                toggle("enabled", "enabled.tooltip", ControlPanelSettings::backgroundBlur,
                        (settings, enabled) -> settings.withGlass(enabled, settings.glassEnabled(),
                                settings.glassBlur(), settings.glassRefraction(), settings.glassRim()));
                toggle("optical_glass", "optical_glass.tooltip", ControlPanelSettings::glassEnabled,
                        (settings, enabled) -> settings.withGlass(settings.backgroundBlur(), enabled,
                                settings.glassBlur(), settings.glassRefraction(), settings.glassRim()));
                slider("radius", ControlPanelSettings::glassBlur,
                        (settings, value) -> settings.withGlass(settings.backgroundBlur(), settings.glassEnabled(),
                                value, settings.glassRefraction(), settings.glassRim()));
                slider("refraction", ControlPanelSettings::glassRefraction,
                        (settings, value) -> settings.withGlass(settings.backgroundBlur(), settings.glassEnabled(),
                                settings.glassBlur(), value, settings.glassRim()));
                slider("rim", ControlPanelSettings::glassRim,
                        (settings, value) -> settings.withGlass(settings.backgroundBlur(), settings.glassEnabled(),
                                settings.glassBlur(), settings.glassRefraction(), value));
            }
            case 1 -> {
                toggle("contrast", "contrast.tooltip", ControlPanelSettings::highContrast,
                        (settings, enabled) -> settings.withDisplay(enabled, settings.reducedMotion(), settings.widgetBlur()));
                toggle("motion", "motion.tooltip", ControlPanelSettings::reducedMotion,
                        (settings, enabled) -> settings.withDisplay(settings.highContrast(), enabled, settings.widgetBlur()));
                toggle("widget_blur", "widget_blur.tooltip", ControlPanelSettings::widgetBlur,
                        (settings, enabled) -> settings.withDisplay(settings.highContrast(), settings.reducedMotion(), enabled));
            }
            case 2 -> {
                for (var mode : THEMES) {
                    var key = "theme." + mode.name().toLowerCase(java.util.Locale.ROOT);
                    var choice = button(contentX, contentY, contentWidth, label(key),
                            () -> edit(draft.withTheme(draft.theme().withMode(mode))), false);
                    choice.setLeadingIcon(UiTheme.icon(mode));
                    refreshRows.add(() -> choice.setSelected(draft.theme().mode() == mode));
                    rows.add(choice);
                }
            }
            case TOOLS_TAB -> initTools();
            default -> throw new IllegalStateException("Invalid panel tab");
        }
        if (rows.size() > visibleRows) {
            int buttonX = contentX + contentWidth - ROW_HEIGHT;
            up = button(buttonX, contentY, ROW_HEIGHT, label("scroll_up"), () -> scroll(-1), false);
            down = button(buttonX, contentY + (visibleRows - 1) * ROW_STEP, ROW_HEIGHT,
                    label("scroll_down"), () -> scroll(1), false);
            up.setIcon(GraphicIcon.UPLOAD);
            down.setIcon(GraphicIcon.DOWNLOAD);
            up.setTooltip(Tooltip.create(label("scroll_up")));
            down.setTooltip(Tooltip.create(label("scroll_down")));
            // At one visible row, put both buttons side by side.
            if (visibleRows == 1) up.setX(buttonX - ROW_HEIGHT - 4);
        }
        positionRows();
    }

    private void initTools() {
        switch (toolPage) {
            case INDEX -> {
                toolButton(label("section.overview"), GraphicIcon.INFO, () -> showToolPage(ToolPage.OVERVIEW));
                toolButton(label("section.links"), GraphicIcon.GITHUB, () -> showToolPage(ToolPage.LINKS));
                var shortcut = toolButton(label("section.shortcut"), GraphicIcon.KEYBOARD, this::openKeyBindings);
                shortcut.setTooltip(Tooltip.create(label("shortcut.manage.tooltip")));
                refreshRows.add(() -> {
                    var mapping = ControlPanelClient.panelKey();
                    shortcut.setMessage(mapping == null ? label("section.shortcut")
                            : label("shortcut.binding", mapping.getTranslatedKeyMessage()));
                });
                toolButton(label("developer.gallery.title"), GraphicIcon.CODE, this::openDeveloperGallery);
            }
            case OVERVIEW -> {
                var snapshot = PanelOverview.capture(minecraft);
                for (var message : snapshot) {
                    var row = new StringWidget(contentX, contentY, contentWidth, ROW_HEIGHT, overviewText(message), font) {
                        @Override
                        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                             float partialTick) {
                            if (isFocused()) {
                                graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(),
                                        getY() + getHeight(), theme.text());
                            }
                            super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
                        }
                    };
                    row.active = true;
                    row.setTooltip(Tooltip.create(message));
                    rows.add(addRenderableWidget(row));
                }
                overviewTicks = 0;
                refreshRows.add(this::refreshOverview);
            }
            case LINKS -> {
                for (var link : FeedbackLink.values()) {
                    var entry = toolButton(label(link.key), link.icon, () -> openLink(link));
                    entry.setTooltip(Tooltip.create(label(link.key + ".tooltip")));
                }
            }
        }
    }

    private UiButton toolButton(Component title, GraphicIcon icon, Runnable action) {
        var entry = addRenderableWidget(ShBetterUiApi.controls().button(
                new Bounds(contentX, contentY, contentWidth, ROW_HEIGHT), title, pressed -> {
                    setFocused(pressed);
                    action.run();
                }, preferences, false, UiStyle.DEFAULT));
        entry.setLeadingIcon(icon);
        rows.add(entry);
        return entry;
    }

    private void showToolPage(ToolPage page) {
        var previous = toolPage;
        toolPage = page;
        status = null;
        clearFocus();
        initRows();
        refresh();
        focusToolPage(previous);
    }

    private void focusToolPage(ToolPage previous) {
        int focus = toolPage == ToolPage.INDEX ? previous.ordinal() - 1 : scrollRows[scrollIndex()];
        revealRow(focus);
        setInitialFocus(rows.get(focus));
    }

    private Component overviewText(Component message) {
        return message.copy().withColor(theme.text() & 0xFFFFFF);
    }

    private void refreshOverview() {
        var snapshot = PanelOverview.capture(minecraft);
        for (int i = 0; i < snapshot.size(); i++) {
            var row = rows.get(i);
            var value = overviewText(snapshot.get(i));
            if (!value.equals(row.getMessage())) {
                row.setMessage(value);
                row.setTooltip(Tooltip.create(snapshot.get(i)));
            }
        }
    }

    private void openKeyBindings() {
        var focus = focusAnchor();
        ControlPanelPlatform.show(minecraft, new KeyBindsScreen(this, minecraft.options) {
            @Override
            public void onClose() {
                super.onClose();
                refresh();
                restoreFocus(focus);
            }
        });
    }

    private void openDeveloperGallery() {
        var focus = focusAnchor();
        ControlPanelPlatform.show(minecraft, new DeveloperGalleryScreen(this, () -> restoreFocus(focus)));
    }

    private void openLink(FeedbackLink link) {
        if (!minecraft.options.chatLinks().get()) {
            status = label("links.unavailable.details");
            triggerImmediateNarration(true);
        } else if (minecraft.options.chatLinksPrompt().get()) {
            var focus = focusAnchor();
            ControlPanelPlatform.show(minecraft, new ConfirmLinkScreen(open -> {
                if (open) Util.getPlatform().openUri(link.url);
                ControlPanelPlatform.show(minecraft, this);
                restoreFocus(focus);
            }, link.url, true));
        } else {
            Util.getPlatform().openUri(link.url);
        }
    }

    private void toggle(String key, String hint, Predicate<ControlPanelSettings> value,
                        BiFunction<ControlPanelSettings, Boolean, ControlPanelSettings> update) {
        var control = ShBetterUiApi.controls().toggle(new Bounds(contentX, contentY, contentWidth, ROW_HEIGHT),
                value.test(draft), preferences, UiStyle.DEFAULT,
                enabled -> label(key, Component.translatable(enabled ? "options.on" : "options.off")),
                enabled -> edit(update.apply(draft, enabled)));
        control.button().setTooltip(Tooltip.create(label(hint)));
        rows.add(addRenderableWidget(control.button()));
        refreshRows.add(() -> control.setValue(value.test(draft)));
    }

    private void slider(String key, ToIntFunction<ControlPanelSettings> value,
                        BiFunction<ControlPanelSettings, Integer, ControlPanelSettings> update) {
        var control = ShBetterUiApi.controls().slider(new Bounds(contentX, contentY, contentWidth, ROW_HEIGHT),
                0, ControlPanelSettings.MAX_GLASS_SCALE, 5, value.applyAsInt(draft), preferences,
                percent -> label("scale", label(key), percent), percent -> edit(update.apply(draft, percent)));
        control.setTooltip(Tooltip.create(label(key + ".tooltip")));
        control.setOnCommit(percent -> {}, percent -> edit(update.apply(draft, percent)));
        rows.add(addRenderableWidget(control));
        refreshRows.add(() -> {
            control.setValue(value.applyAsInt(draft));
            control.active = draft.glassEnabled() && !preferences.highContrast();
        });
    }

    private UiButton button(int x, int y, int width, Component text, Runnable action, boolean primary) {
        return addRenderableWidget(ShBetterUiApi.controls().button(new Bounds(x, y, width, ROW_HEIGHT),
                text, ignored -> action.run(), preferences, primary, UiStyle.DEFAULT));
    }

    private void positionRows() {
        int limit = Math.max(0, rows.size() - visibleRows);
        int index = scrollIndex();
        int first = scrollRows[index] = Math.clamp(scrollRows[index], 0, limit);
        int reserved = up == null ? 0 : (visibleRows == 1 ? 2 * (ROW_HEIGHT + 4) : ROW_HEIGHT + 4);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            int position = i - first;
            row.visible = position >= 0 && position < visibleRows;
            if (!row.visible && getFocused() == row) clearFocus();
            row.setY(contentY + position * ROW_STEP);
            row.setWidth(contentWidth - reserved);
            if (row instanceof StringWidget text) text.setMaxWidth(contentWidth - reserved);
        }
        if (up != null) up.active = first > 0;
        if (down != null) down.active = first < limit;
    }

    private boolean scroll(int distance) {
        if (compact) return false;
        int index = scrollIndex();
        int start = scrollRows[index];
        int next = Math.clamp(start + distance, 0, Math.max(0, rows.size() - visibleRows));
        if (start == next) return false;
        cancelDrag();
        scrollRows[index] = next;
        positionRows();
        return true;
    }

    private void revealRow(int row) {
        int index = scrollIndex();
        int first = scrollRows[index];
        if (row < first || row >= first + visibleRows) {
            scrollRows[index] = row < first ? row : row - visibleRows + 1;
            positionRows();
        }
    }

    private boolean page(int direction) {
        boolean focusedRow = rows.contains(getFocused());
        if (!scroll(direction * visibleRows)) return false;
        if (focusedRow && getFocused() == null) {
            for (var row : rows) {
                if (row.visible && row.active) {
                    setInitialFocus(row);
                    return true;
                }
            }
            if (down != null && down.active) setInitialFocus(down);
            else if (up != null && up.active) setInitialFocus(up);
            else setInitialFocus(back);
        }
        return true;
    }

    private int scrollIndex() {
        return tab == TOOLS_TAB ? tab + toolPage.ordinal() : tab;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var previous = toolPage;
        boolean handled = super.mouseClicked(event, doubleClick);
        if (toolPage != previous) {
            setDragging(false);
            focusToolPage(previous);
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= contentX && mouseX < contentX + contentWidth && mouseY >= contentY && mouseY < footerY
                && deltaY != 0 && scroll(deltaY < 0 ? 1 : -1)) return true;
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void syncStyle() {
        preferences = draft.preferences(minecraft.options.highContrast().get());
        theme = UiTheme.resolve(draft.theme());
    }

    private void refresh() {
        syncStyle();
        for (var widget : children()) {
            if (widget instanceof UiButton button) button.setPreferences(preferences);
            if (widget instanceof UiSlider slider) slider.setPreferences(preferences);
        }
        refreshRows.forEach(Runnable::run);
        if (defaults != null) {
            boolean undo = beforeReset != null;
            defaults.setMessage(label(undo ? "reset.undo" : "reset"));
            defaults.setTooltip(Tooltip.create(label(undo ? "reset.undo.tooltip" : "reset.settings.tooltip")));
            defaults.active = undo || !draft.equals(ControlPanelSettings.DEFAULT);
        }
        if (apply != null) apply.active = !draft.equals(saved);
    }

    private void edit(ControlPanelSettings changed) {
        draft = changed;
        status = null;
        refresh();
    }

    private void resetDraft() {
        cancelDrag();
        var previous = beforeReset;
        beforeReset = previous == null ? draft : null;
        edit(previous == null ? ControlPanelSettings.DEFAULT : previous);
    }

    private void applyDraft() {
        if (ControlPanelSettings.save(draft)) {
            saved = draft;
            beforeReset = null;
            status = label("saved");
        } else {
            status = label("save_failed");
        }
        refresh();
        triggerImmediateNarration(true);
    }

    private void cancelDrag() {
        if (getFocused() instanceof UiSlider slider) slider.cancelEdit();
        setDragging(false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && getFocused() instanceof UiSlider slider && slider.keyPressed(event)) {
            setDragging(false);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN && page(1)) return true;
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP && page(-1)) return true;
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        if (!minecraft.isWindowActive()) cancelDrag();
        if (!compact && tab == TOOLS_TAB && toolPage == ToolPage.OVERVIEW && ++overviewTicks >= 20) {
            overviewTicks = 0;
            refreshOverview();
        }
    }

    @Override
    public void removed() { cancelDrag(); }

    @Override
    public void onClose() {
        cancelDrag();
        if (tab == TOOLS_TAB && toolPage != ToolPage.INDEX && !compact) {
            showToolPage(ToolPage.INDEX);
            return;
        }
        if (saved.equals(draft)) {
            ControlPanelPlatform.show(minecraft, parent);
            return;
        }
        var focus = focusAnchor();
        var confirmation = new ConfirmScreen(discard -> {
            ControlPanelPlatform.show(minecraft, discard ? parent : this);
            if (!discard) restoreFocus(focus);
        }, label("discard.title"), label("discard.settings"), label("discard.action"), CommonComponents.GUI_CANCEL);
        ControlPanelPlatform.show(minecraft, confirmation);
    }

    private void restoreFocus(@Nullable Supplier<@Nullable AbstractWidget> focus) {
        var target = focus == null ? null : focus.get();
        if (target != null && target.active && !target.visible) {
            int row = rows.indexOf(target);
            if (row >= 0) revealRow(row);
        }
        setInitialFocus(target != null && target.visible && target.active ? target : back);
    }

    private @Nullable Supplier<@Nullable AbstractWidget> focusAnchor() {
        var focused = getFocused();
        if (focused == null) return null;
        if (focused == back) return () -> back;
        if (focused == defaults) return () -> defaults;
        if (focused == apply) return () -> apply;
        if (focused == up) return () -> up;
        if (focused == down) return () -> down;
        int row = rows.indexOf(focused);
        if (row >= 0) return () -> row < rows.size() ? rows.get(row) : null;
        int section = tabs == null ? -1 : tabs.buttons().indexOf(focused);
        return section < 0 ? null : () -> tabs == null ? null : tabs.buttons().get(section);
    }

    @Override
    public Component getNarrationMessage() {
        var message = statusMessage();
        var title = currentTitle();
        return message == null ? title : CommonComponents.joinForNarration(title, message);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) extractPanorama(graphics, partialTick);
        BackgroundBlur.request(graphics,
                new UiPreferences(preferences.highContrast(), preferences.reducedMotion(), draft.backgroundBlur()));
        graphics.fill(0, 0, width, height, 0x30121A26);
        ControlPanelPlatform.subtitles(minecraft);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int border = preferences.highContrast() ? theme.text() : theme.border();
        if (!compact && draft.glassEnabled()) {
            var material = GlassMaterial.preset(theme.hudGlass() ? GlassVariant.CLEAR : GlassVariant.REGULAR);
            var surfacePreferences = new UiPreferences(preferences.highContrast(), preferences.reducedMotion(), true);
            LiquidGlassSurface.draw(graphics, left, top, panelWidth, panelHeight, 12,
                    theme.surface() & 0xFFFFFF | 0xA0000000, border,
                    draft.optics(material.optics()), surfacePreferences);
        } else {
            RoundedCorners.draw(graphics, left, top, panelWidth, panelHeight, 12, 1, border, theme.surface() | 0xFF000000);
        }
        graphics.nextStratum();
        if (compact) {
            PanelText.drawLine(graphics, font, label("compact"), left + 4, Math.max(0, top + 5),
                    Math.max(1, panelWidth - 8), theme.text(), mouseX, mouseY);
        } else {
            PanelText.drawLine(graphics, font, currentTitle(), contentX, top + PADDING,
                    contentWidth, theme.text(), mouseX, mouseY);
            var message = statusMessage();
            if (message != null) PanelText.drawLine(graphics, font, message, contentX, footerY - 13,
                    contentWidth, theme.text(), mouseX, mouseY);
        }
        UiTheme.preview(theme, () -> super.extractRenderState(graphics, mouseX, mouseY, partialTick));
    }

    private Component currentTitle() {
        return tab == TOOLS_TAB && toolPage.title != null ? label(toolPage.title) : getTitle();
    }

    private @Nullable Component statusMessage() {
        return status != null ? status : !draft.equals(saved) ? label("unsaved") : null;
    }

    private static Component label(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.panel." + key, arguments);
    }

    private enum ToolPage {
        INDEX(null), OVERVIEW("section.overview"), LINKS("section.links");

        final @Nullable String title;

        ToolPage(@Nullable String title) { this.title = title; }
    }

    private enum FeedbackLink {
        GITHUB("github", GraphicIcon.GITHUB, "https://github.com/dishanhai/SH-Better-UI"),
        ISSUES("issues", GraphicIcon.GITHUB_ISSUE, "https://github.com/dishanhai/SH-Better-UI/issues"),
        AFDIAN("afdian", GraphicIcon.AFDIAN, "https://afdian.com/a/dishanhai?utm_source=copylink&utm_medium=link");

        final String key;
        final GraphicIcon icon;
        final String url;

        FeedbackLink(String key, GraphicIcon icon, String url) {
            this.key = "links." + key;
            this.icon = icon;
            this.url = url;
        }
    }
}
