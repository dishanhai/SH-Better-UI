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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Readable and copyable source for the 26.x controls exposed by the gallery. */
final class GalleryCodeScreen extends Screen {
    private static final int INSET = 12;
    private static final int BUTTON_HEIGHT = 22;
    private static final int GAP = 5;

    private final Screen parent;
    private final String code;
    private final UiPreferences preferences;
    private final Runnable afterReturn;
    private List<FormattedCharSequence> lines = List.of();
    private UiTheme theme;
    private UiButton back;
    private UiButton previous;
    private UiButton next;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int textX;
    private int textY;
    private int visibleLines;
    private int firstLine;
    private boolean compact;

    GalleryCodeScreen(Screen parent, String code, UiPreferences preferences, Runnable afterReturn) {
        super(label("code_title"));
        this.parent = parent;
        this.code = code;
        this.preferences = preferences;
        this.afterReturn = afterReturn;
    }

    @Override
    protected void init() {
        theme = UiTheme.current();
        panelWidth = Math.max(1, Math.min(600, width - 16));
        panelHeight = Math.max(1, Math.min(344, height - 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        compact = panelWidth < 156 || panelHeight < 126;
        int footerY = top + panelHeight - INSET - BUTTON_HEIGHT;
        textX = left + INSET;
        textY = top + 35;
        int contentWidth = Math.max(1, panelWidth - 2 * INSET);
        int backWidth = Math.max(1, Math.min(70, contentWidth / 3));
        back = button(new Bounds(textX, Math.max(top, footerY), backWidth, BUTTON_HEIGHT),
                CommonComponents.GUI_BACK, ignored -> onClose());
        back.setLeadingIcon(GraphicIcon.BACK);
        if (compact) return;
        lines = font.split(Component.literal(code), contentWidth);
        visibleLines = Math.max(1, (footerY - textY - GAP) / font.lineHeight);
        firstLine = Math.clamp(firstLine, 0, Math.max(0, lines.size() - visibleLines));
        int right = textX + contentWidth;
        previous = button(new Bounds(right - 2 * BUTTON_HEIGHT - GAP, footerY, BUTTON_HEIGHT, BUTTON_HEIGHT),
                Component.translatable("sh_better_ui.pages.studio.previous"), ignored -> scroll(-visibleLines));
        next = button(new Bounds(right - BUTTON_HEIGHT, footerY, BUTTON_HEIGHT, BUTTON_HEIGHT),
                Component.translatable("sh_better_ui.pages.studio.next"), ignored -> scroll(visibleLines));
        previous.setIcon(GraphicIcon.PREVIOUS);
        next.setIcon(GraphicIcon.NEXT);
        int copyX = textX + backWidth + GAP;
        int copyWidth = right - 2 * BUTTON_HEIGHT - 2 * GAP - copyX;
        var copy = button(new Bounds(copyX, footerY, Math.max(1, copyWidth), BUTTON_HEIGHT), label("copy"), pressed -> {
            minecraft.keyboardHandler.setClipboard(code);
            pressed.setMessage(label("copied"));
            triggerImmediateNarration(true);
        });
        copy.setLeadingIcon(GraphicIcon.COPY);
        updateButtons();
    }

    private UiButton button(Bounds bounds, Component title, Button.OnPress action) {
        return addRenderableWidget(ShBetterUiApi.controls().button(bounds, title, action, preferences, false, UiStyle.DEFAULT));
    }

    private boolean scroll(int delta) {
        int target = Math.clamp(firstLine + delta, 0, Math.max(0, lines.size() - visibleLines));
        if (target == firstLine) return false;
        firstLine = target;
        updateButtons();
        return true;
    }

    private void updateButtons() {
        previous.active = firstLine > 0;
        next.active = firstLine + visibleLines < lines.size();
        if (getFocused() == previous && !previous.active || getFocused() == next && !next.active) {
            setInitialFocus(back);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!compact && deltaY != 0 && scroll(deltaY < 0 ? 3 : -3)) return true;
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!compact && event.key() == GLFW.GLFW_KEY_PAGE_UP && scroll(-visibleLines)) return true;
        if (!compact && event.key() == GLFW.GLFW_KEY_PAGE_DOWN && scroll(visibleLines)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!compact && (getFocused() == previous && !previous.active || getFocused() == next && !next.active)) {
            setDragging(false);
            setInitialFocus(back);
        }
        return handled;
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
                left + 8, top + 8, Math.max(1, panelWidth - 16), theme.text(), mouseX, mouseY);
        if (!compact) {
            for (int index = firstLine; index < Math.min(lines.size(), firstLine + visibleLines); index++) {
                graphics.text(font, lines.get(index), textX, textY + (index - firstLine) * font.lineHeight, theme.text(), false);
            }
        }
        UiTheme.preview(theme, () -> super.extractRenderState(graphics, mouseX, mouseY, partialTick));
    }

    private static Component label(String key) {
        return Component.translatable("sh_better_ui.panel.developer.gallery." + key);
    }
}
