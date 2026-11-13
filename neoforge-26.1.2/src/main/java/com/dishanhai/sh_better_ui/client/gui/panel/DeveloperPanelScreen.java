// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Entry point for the native developer galleries and their interactive demonstrations. */
public final class DeveloperPanelScreen extends Screen {
    private static final int INSET = 12;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_STEP = 27;
    private static final SystemToast.SystemToastId DEMO_TOAST = new SystemToast.SystemToastId();

    private final @Nullable Screen parent;
    private final UiPreferences preferences;
    private final Runnable afterReturn;
    private final List<UiButton> buttons = new ArrayList<>();
    private UiTheme theme;
    private UiButton back;
    private int returnIndex;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private boolean compact;

    public DeveloperPanelScreen(@Nullable Screen parent) {
        this(parent, UiPreferences.configured());
    }

    public DeveloperPanelScreen(@Nullable Screen parent, UiPreferences preferences) {
        this(parent, preferences, returnToFocus(parent));
    }

    DeveloperPanelScreen(@Nullable Screen parent, UiPreferences preferences, Runnable afterReturn) {
        super(label("title"));
        this.parent = parent;
        this.preferences = Objects.requireNonNull(preferences);
        this.afterReturn = Objects.requireNonNull(afterReturn);
    }

    @Override
    protected void init() {
        buttons.clear();
        theme = UiTheme.current();
        panelWidth = Math.max(1, Math.min(440, width - 16));
        panelHeight = Math.max(1, Math.min(236, height - 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        compact = panelWidth < 196 || panelHeight < 182;
        if (!compact) {
            int x = left + INSET;
            int contentWidth = panelWidth - 2 * INSET;
            int half = (contentWidth - 6) / 2;
            int right = x + half + 6;
            int rightWidth = contentWidth - half - 6;
            int y = top + 34;
            add(x, y, half, label("gallery.controls"), GraphicIcon.INTERACTION,
                    () -> openGallery(DeveloperGalleryScreen.Category.CONTROLS));
            add(right, y, rightWidth, label("gallery.materials"), GraphicIcon.TEMPLATE,
                    () -> openGallery(DeveloperGalleryScreen.Category.MATERIALS));
            y += ROW_STEP + 13;
            add(x, y, half, label("demo.dialog"), GraphicIcon.WARNING, this::confirm);
            add(right, y, rightWidth, label("demo.toast"), GraphicIcon.INFO, this::toast);
            y += ROW_STEP;
            add(x, y, half, label("demo.bottom_sheet"), GraphicIcon.EXPAND, () -> openSample("bottom_sheet"));
            add(right, y, rightWidth, label("demo.animation"), GraphicIcon.RESUME,
                    () -> openSample("interruptible_animation"));
            y += ROW_STEP + 18;
            add(x, y, half, label("example.open_dialog"), GraphicIcon.CODE, () -> openExample("dialog"));
            add(right, y, rightWidth, label("example.open_toast"), GraphicIcon.CODE, () -> openExample("toast"));
        }
        int backWidth = Math.max(1, Math.min(60, panelWidth - Math.min(INSET * 2, panelWidth - 1)));
        back = addRenderableWidget(ShBetterUiApi.controls().button(
                new Bounds(left + panelWidth - Math.min(INSET, panelWidth / 4) - backWidth,
                        top + Math.min(7, Math.max(0, panelHeight - ROW_HEIGHT)), backWidth, ROW_HEIGHT),
                CommonComponents.GUI_BACK, ignored -> onClose(), preferences, false, UiStyle.DEFAULT));
        back.setLeadingIcon(GraphicIcon.BACK);
    }

    private void add(int x, int y, int width, Component title, GraphicIcon icon, Runnable action) {
        int index = buttons.size();
        var button = ShBetterUiApi.controls().button(new Bounds(x, y, width, ROW_HEIGHT), title, ignored -> {
            returnIndex = index;
            action.run();
        }, preferences, false, UiStyle.DEFAULT);
        button.setLeadingIcon(icon);
        buttons.add(addRenderableWidget(button));
    }

    private void openGallery(DeveloperGalleryScreen.Category category) {
        ControlPanelPlatform.show(minecraft, new DeveloperGalleryScreen(this, this::restoreFocus, category, preferences));
    }

    private void openSample(String id) {
        ControlPanelPlatform.show(minecraft, DeveloperGalleryScreen.fullscreen(this, id, preferences,
                () -> returnIndex < buttons.size() ? buttons.get(returnIndex) : back));
    }

    private void openExample(String id) {
        ControlPanelPlatform.show(minecraft, new GalleryCodeScreen(this,
                Objects.requireNonNull(GalleryExamples.code(id)), preferences, this::restoreFocus));
    }

    private void confirm() {
        ControlPanelPlatform.show(minecraft, new ConfirmScreen(confirmed -> {
            ControlPanelPlatform.show(minecraft, this);
            restoreFocus();
            if (confirmed) toast();
        }, label("demo.dialog"), label("demo.dialog_details"), CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL));
    }

    private void toast() {
        SystemToast.add(ControlPanelPlatform.toasts(minecraft), DEMO_TOAST,
                label("demo.toast"), label("demo.toast_details"));
    }

    private void restoreFocus() {
        setInitialFocus(returnIndex < buttons.size() ? buttons.get(returnIndex) : back);
    }

    private static Runnable returnToFocus(@Nullable Screen parent) {
        int index = parent == null ? -1 : parent.children().indexOf(parent.getFocused());
        return () -> {
            if (parent != null && index >= 0 && index < parent.children().size()) {
                parent.setFocused(parent.children().get(index));
            }
        };
    }

    @Override
    protected void setInitialFocus() { restoreFocus(); }

    @Override
    protected void repositionElements() {
        int index = buttons.indexOf(getFocused());
        boolean wasBack = getFocused() == back;
        if (index >= 0) returnIndex = index;
        super.repositionElements();
        if (wasBack) setInitialFocus(back);
        else restoreFocus();
    }

    @Override
    public void onClose() {
        ControlPanelPlatform.show(minecraft, parent);
        afterReturn.run();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) extractPanorama(graphics, partialTick);
        graphics.fill(0, 0, width, height, 0x30121A26);
        ControlPanelPlatform.subtitles(minecraft);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int border = preferences.highContrast() ? theme.text() : theme.border();
        RoundedCorners.draw(graphics, left, top, panelWidth, panelHeight, 12, 1, border, theme.surface() | 0xFF000000);
        graphics.nextStratum();
        PanelText.drawLine(graphics, font, compact ? Component.translatable("sh_better_ui.panel.compact") : getTitle(),
                left + 8, top + 8, Math.max(1, back.getX() - left - 13), theme.text(), mouseX, mouseY);
        if (!compact) {
            PanelText.drawLine(graphics, font, label("demos"), left + INSET, top + 65,
                    panelWidth - 2 * INSET, theme.text(), mouseX, mouseY);
            PanelText.drawLine(graphics, font, label("examples"), left + INSET, top + 132,
                    panelWidth - 2 * INSET, theme.text(), mouseX, mouseY);
        }
        UiTheme.preview(theme, () -> super.extractRenderState(graphics, mouseX, mouseY, partialTick));
    }

    private static Component label(String key) {
        return Component.translatable("sh_better_ui.panel.developer." + key);
    }
}
