// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
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
    private UiButton fullscreenButton;
    private UiButton codeButton;
    protected boolean compact;
    private Bounds panel;
    private boolean expanded;
    private int revision;

    GallerySampleScreen(DeveloperGalleryScreen parent, String id) {
        this(parent, id, Component.translatable(KEY + "sample." + id));
    }

    GallerySampleScreen(DeveloperGalleryScreen parent, String id, Component title) {
        super(title);
        this.parent = parent;
        this.id = id;
    }

    @Override
    protected final void init() {
        revision++;
        preferences = parent.previewPreferences();
        theme = UiTheme.current();
        beforeSampleInit();
        int panelWidth = Math.max(1, parent.fullscreenOnly() || expanded ? width - 16 : Math.min(500, width - 16));
        int panelHeight = Math.max(1, parent.fullscreenOnly() || expanded ? height - 16 : Math.min(306, height - 16));
        panel = new Bounds((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
        int footer = panel.bottom() - INSET - FOOTER_HEIGHT;
        int contentY = panel.y() + 35;
        compact = panelWidth < 156 || panelHeight < 126 || !previewAvailable();
        initialFocus = null;
        fullscreenButton = null;
        codeButton = null;
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
        int right = body.right();
        if (!parent.fullscreenOnly()) {
            fullscreenButton = addButton(new Bounds(right - FOOTER_HEIGHT, panel.y() + 6,
                            FOOTER_HEIGHT, FOOTER_HEIGHT),
                    Component.translatable(KEY + (expanded ? "exit_fullscreen" : "fullscreen")),
                    ignored -> setExpanded(!expanded), false);
            fullscreenButton.setIcon(expanded ? GraphicIcon.COLLAPSE : GraphicIcon.EXPAND);
            right -= FOOTER_HEIGHT + 5;
        }
        String example = sampleCode();
        if (example != null) {
            codeButton = addButton(new Bounds(right - FOOTER_HEIGHT, panel.y() + 6, FOOTER_HEIGHT, FOOTER_HEIGHT),
                    Component.translatable(KEY + "code_open"), ignored -> openCode(example), false);
            codeButton.setIcon(GraphicIcon.CODE);
        }
    }

    private void openCode(String code) {
        ControlPanelPlatform.show(minecraft, new GalleryCodeScreen(this, code, preferences, () -> {
            if (codeButton != null) setInitialFocus(codeButton);
        }));
    }

    private void setExpanded(boolean value) {
        cancelEdits();
        expanded = value;
        rebuildWidgets();
        if (fullscreenButton != null) setInitialFocus(fullscreenButton);
    }

    protected abstract void initSample();

    protected void beforeSampleInit() {}

    protected String sampleCode() { return GalleryExamples.code(id); }

    protected boolean previewAvailable() { return parent.routeAvailable(); }

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
        if (event.isEscape() && expanded && !parent.fullscreenOnly()) {
            setExpanded(false);
            return true;
        }
        boolean handled = super.keyPressed(event);
        restoreUsableFocus();
        return handled;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int before = revision;
        boolean handled = super.mouseClicked(event, doubleClick);
        if (revision != before) {
            setDragging(false);
            if (fullscreenButton != null) setInitialFocus(fullscreenButton);
            else setInitialFocus();
        }
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
    public final void tick() {
        if (!previewAvailable()) {
            parent.returnFromStandalone(minecraft);
            return;
        }
        if (!minecraft.isWindowActive()) cancelEdits();
        tickSample();
    }

    protected void tickSample() {}

    @Override
    public void removed() { cancelEdits(); }

    @Override
    public void onClose() {
        if (expanded && !parent.fullscreenOnly()) setExpanded(false);
        else parent.returnFromStandalone(minecraft);
    }

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
            var lastHeaderButton = codeButton == null ? fullscreenButton : codeButton;
            int titleWidth = lastHeaderButton == null ? body.width() : lastHeaderButton.getX() - body.x() - 5;
            PanelText.drawLine(graphics, font, getTitle(), body.x(), panel.y() + INSET,
                    Math.max(1, titleWidth), theme.text(), mouseX, mouseY);
            if (previewAvailable()) extractSample(graphics, mouseX, mouseY, partialTick);
        }
        UiTheme.preview(theme, () -> super.extractRenderState(graphics, mouseX, mouseY, partialTick));
    }
}
