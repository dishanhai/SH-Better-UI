// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Shared layout and native widget lifecycle for standalone gallery scenes. */
abstract class GallerySampleScreen extends Screen {
    private static final String KEY = "sh_better_ui.panel.developer.gallery.";
    private static final int INSET = 12;
    private static final int FOOTER_HEIGHT = 22;

    protected final String id;
    protected final DeveloperGalleryScreen parent;
    protected Bounds body;
    protected UiPreferences preferences;
    protected UiTheme theme;
    protected AbstractWidget initialFocus;
    protected UiButton back;
    protected boolean compact;
    private Bounds panel;

    GallerySampleScreen(DeveloperGalleryScreen parent, String id) {
        super(Component.translatable(KEY + "sample." + id));
        this.parent = parent;
        this.id = id;
    }

    @Override
    protected final void init() {
        preferences = UiPreferences.configured();
        theme = UiTheme.current();
        int panelWidth = Math.max(1, Math.min(500, width - 16));
        int panelHeight = Math.max(1, Math.min(306, height - 16));
        panel = new Bounds((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
        int footer = panel.bottom() - INSET - FOOTER_HEIGHT;
        int contentY = panel.y() + 35;
        compact = panelWidth < 156 || panelHeight < 126;
        initialFocus = null;
        body = new Bounds(panel.x() + INSET, contentY, Math.max(0, panel.width() - INSET * 2),
                Math.max(0, footer - contentY - 5));
        if (compact) {
            back = addButton(new Bounds(panel.x(), Math.max(0, panel.bottom() - FOOTER_HEIGHT),
                    panelWidth, FOOTER_HEIGHT), CommonComponents.GUI_BACK, ignored -> onClose(), false);
            return;
        }
        initSample();
        back = addButton(new Bounds(body.x(), footer, Math.min(96, body.width() / 2), FOOTER_HEIGHT),
                CommonComponents.GUI_BACK, ignored -> onClose(), false);
    }

    protected abstract void initSample();

    protected void extractSample(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    protected void cancelInteraction() {}

    protected final Component text(String suffix, Object... args) {
        return Component.translatable(KEY + id + "." + suffix, args);
    }

    protected final UiButton addButton(Bounds bounds, Component message, Button.OnPress action, boolean primary) {
        return addRenderableWidget(ShBetterUiApi.controls().button(bounds, message, action, preferences, primary, UiStyle.DEFAULT));
    }

    protected final void extractFooterStatus(GuiGraphicsExtractor graphics, Component status, int mouseX, int mouseY) {
        if (CommonComponents.EMPTY.equals(status)) return;
        int x = back.getX() + back.getWidth() + 4;
        int width = body.right() - x;
        var lines = font.split(status, width);
        int textHeight = lines.size() * font.lineHeight;
        if (textHeight > back.getHeight()) {
            int y = back.getY() + (back.getHeight() - font.lineHeight) / 2;
            PanelText.drawLine(graphics, font, status, x, y, width, theme.text(), mouseX, mouseY);
            return;
        }
        int y = back.getY() + (back.getHeight() - textHeight) / 2;
        for (var line : lines) {
            graphics.text(font, line, x, y, theme.text(), false);
            y += font.lineHeight;
        }
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(initialFocus != null && initialFocus.isActive() ? initialFocus : back);
    }

    @Override
    protected void repositionElements() {
        var focused = getFocused();
        int slot = focused == null ? -1 : children().indexOf(focused);
        boolean focusedBack = focused == back;
        cancelEdits();
        super.repositionElements();
        if (focusedBack) setInitialFocus(back);
        else if (!compact && slot >= 0 && slot < children().size()
                && children().get(slot) instanceof AbstractWidget widget && widget.active && widget.visible) {
            setInitialFocus(widget);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && getFocused() instanceof UiSlider slider && slider.keyPressed(event)) {
            setDragging(false);
            return true;
        }
        boolean handled = super.keyPressed(event);
        restoreUsableFocus();
        return handled;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        restoreUsableFocus();
        return handled;
    }

    private void restoreUsableFocus() {
        if (getFocused() instanceof AbstractWidget widget && !widget.isActive()) {
            setDragging(false);
            setInitialFocus();
        }
    }

    @Override
    public void tick() {
        if (!minecraft.isWindowActive()) cancelEdits();
    }

    @Override
    public void removed() { cancelEdits(); }

    @Override
    public void onClose() { parent.returnFromStandalone(); }

    private void cancelEdits() {
        for (var child : children()) {
            if (child instanceof UiSlider slider) slider.cancelEdit();
        }
        cancelInteraction();
        setDragging(false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) extractPanorama(graphics, partialTick);
        graphics.fill(0, 0, width, height, 0x30121A26);
        ControlPanelPlatform.subtitles(minecraft);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        RoundedCorners.draw(graphics, panel.x(), panel.y(), panel.width(), panel.height(), 12, 1,
                preferences.highContrast() ? theme.text() : theme.border(), theme.surface() | 0xFF000000);
        graphics.nextStratum();
        if (compact) {
            PanelText.drawLine(graphics, font, Component.translatable("sh_better_ui.panel.compact"), panel.x() + 4,
                    panel.y() + 4, Math.max(1, panel.width() - 8), theme.text(), mouseX, mouseY);
        } else {
            PanelText.drawLine(graphics, font, getTitle(), body.x(), panel.y() + INSET, body.width(), theme.text(), mouseX, mouseY);
            extractSample(graphics, mouseX, mouseY, partialTick);
        }
        UiTheme.preview(theme, () -> super.extractRenderState(graphics, mouseX, mouseY, partialTick));
    }
}
