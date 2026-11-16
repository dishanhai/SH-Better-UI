// Author: dishanhai. Original borderless lyric presentation inspired by MuoniumPlayer.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;

/** A real keyboard/narrator button with text-only rendering, not a decorative glass control. */
final class OnlineLyricButton extends Button {
    private final UiPreferences preferences;
    private final IntConsumer scroll;
    private boolean selected;
    private float distance;
    OnlineLyricButton(int x, int y, int width, int height, Runnable action, UiPreferences preferences, IntConsumer scroll) {
        super(x, y, width, height, Component.empty(), ignored -> action.run(), DEFAULT_NARRATION);
        this.preferences = preferences;
        this.scroll = scroll;
    }
    void setSelected(boolean value) { selected = value; }
    void setDistance(float value) { distance = Math.abs(value); }
    void setPreview(Component text) { setTooltip(Tooltip.create(text)); }
    public boolean mouseScrolled(double x, double y, double amount) {
        if (!visible || !isMouseOver(x, y) || amount == 0) return false;
        scroll.accept(amount > 0 ? -1 : 1); return true;
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return mouseScrolled(x, y, vertical);
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var theme = UiTheme.current(); var font = Minecraft.getInstance().font;
        int x = getX() + 12, y = getY() + (getHeight() - font.lineHeight) / 2;
        int color = selected ? theme.accent() : preferences.highContrast() ? theme.text()
                : GuiMotion.lerpColor(theme.text(), theme.background(), Math.min(.55f, .2f + distance * .09f));
        if (isHoveredOrFocused()) {
            graphics.fill(getX(), getY(), getX() + 2, getY() + getHeight(), theme.accent());
            if (isFocused()) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, theme.accent());
                graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), theme.accent());
            }
        }
        var text = font.substrByWidth(getMessage(), Math.max(1, getWidth() - 24));
        graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(text), x, y, color, false);
        if (selected) graphics.fill(getX() + 3, y + 3, getX() + 6, y + 6, theme.accent());
    }
}
