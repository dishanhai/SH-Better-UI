// Author: dishanhai. Original borderless lyric presentation inspired by MuoniumPlayer.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
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
    private boolean selected, stage;
    private float stageScale = 1f;
    void setStage(boolean stage, float scale) { this.stage=stage; this.stageScale=Math.max(1f,Math.min(2f,scale)); }
    private boolean pointerFocus, pointerFocusPending;
    private float distance;
    OnlineLyricButton(int x, int y, int width, int height, Runnable action, UiPreferences preferences, IntConsumer scroll) {
        super(x, y, width, height, Component.empty(), ignored -> action.run(), DEFAULT_NARRATION);
        this.preferences = preferences;
        this.scroll = scroll;
    }
    @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { pointerFocus = true; pointerFocusPending = true; super.onClick(event, doubleClick); }
    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused && pointerFocusPending) { pointerFocus = true; pointerFocusPending = false; }
        else if (!focused && !pointerFocusPending) pointerFocus = false;
    }
    boolean keyboardFocused() { return isFocused() && !pointerFocus; }
    void setSelected(boolean value) { selected = value; }
    void setDistance(float value) { distance = Math.abs(value); }
    void setPreview(Component text) { setTooltip(Tooltip.create(MusicTypography.text(text))); }
    public boolean mouseScrolled(double x, double y, double amount) {
        if (!visible || !isMouseOver(x, y) || amount == 0) return false;
        scroll.accept(amount > 0 ? -1 : 1); return true;
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return mouseScrolled(x, y, vertical);
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var theme = UiTheme.current(); var font = Minecraft.getInstance().font;
        // MP-like focus hierarchy without per-glyph blur or duplicate glow draw calls.
        // Reserve scaled width before truncation so a focused line never bleeds into the scrollbar.
        float scale = lyricScale(selected, distance, preferences.highContrast()) * (stage ? stageScale : 1f);
        int x = getX() + 12, y = getY() + Math.round((getHeight() - MusicTypography.LINE_HEIGHT * scale) / 2f);
        var paint = LyricTextPalette.sample(theme.text(), theme.accent(), theme.background() | 0xff000000, distance, preferences.highContrast());
        int color = selected ? paint.active() : paint.text();
        if (isHoveredOrFocused()) {
            graphics.fill(getX(), getY(), getX() + 2, getY() + getHeight(), theme.accent());
            if (isFocused()) graphics.outline(getX(), getY(), getWidth(), getHeight(), theme.accent());
        }
        var text = font.substrByWidth(MusicTypography.text(getMessage()), Math.max(1, (int) ((getWidth() - 24) / scale)));
        if (paint.protection() != 0) graphics.fill(x - 3, y - 2,
                x + Math.min(getWidth() - 24, Math.round(font.width(text) * scale)) + 3, y + Math.round(MusicTypography.LINE_HEIGHT * scale) + 2, paint.protection());
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(text), 0, 0, color, paint.shadow());
        } finally { graphics.pose().popMatrix(); }
        if (selected && !stage) graphics.fill(getX() + 3, y + 3, getX() + 6, y + 6, theme.accent());
    }
    static float lyricScale(boolean selected, float distance, boolean highContrast) {
        if (highContrast) return 1f;
        if (selected) return 1.12f;
        float safe = Float.isFinite(distance) ? Math.abs(distance) : 4f;
        return 1f - Math.min(3f, safe) * .025f;
    }
}
