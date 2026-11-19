// Author: dishanhai. Original native, borderless transport control inspired by MuoniumPlayer.
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Real native keyboard/narrator input without the default glass-button chrome. */
final class MusicStageButton extends Button {
    private final UiPreferences preferences;
    private final boolean primary, cover;
    private GraphicIcon icon;
    private Integer ink;
    private boolean selected;
    void ink(int value){ink=value;}
    void selected(boolean value){selected=value;}
    MusicStageButton(Bounds bounds, Component name, GraphicIcon icon, Runnable action,
                     UiPreferences preferences, boolean primary, boolean cover) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), name, ignored -> action.run(), DEFAULT_NARRATION);
        this.preferences = preferences; this.icon = icon; this.primary = primary; this.cover = cover;
    }
    void icon(GraphicIcon value) { icon = value; }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var theme = UiTheme.current();
        int foreground = ink!=null ? (active?ink:GuiMotion.lerpColor(ink,0xff292229,.5f)) : active ? theme.text() : theme.secondaryText(0xff83909e, .5f);
        if (cover) {
            if (isHoveredOrFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), theme.accent());
            return;
        }
        if (primary || selected || isHoveredOrFocused()) {
            int fill = ink!=null ? 0x18ffffff : primary && active ? theme.text() : GuiMotion.lerpColor(theme.background() | 0xff000000, theme.text(), .1f);
            RoundedCorners.draw(g, getX(), getY(), getWidth(), getHeight(), primary?Math.min(getWidth(),getHeight()) / 2f:4,
                    preferences.highContrast() || isFocused() ? 1 : 0, theme.accent(), fill);
            if (primary && active) foreground = theme.background() | 0xff000000;
        }
        if (isFocused() && !primary) g.renderOutline(getX(),getY(),getWidth(),getHeight(),theme.accent());
        if (icon != null) icon.draw(g,getX()+(getWidth()-16)/2,getY()+(getHeight()-16)/2,1,foreground,preferences);
        else {
            var font=Minecraft.getInstance().font;
            var text=net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(MusicTypography.text(getMessage()),Math.max(1,getWidth()-12)));
            g.drawString(font,text,getX()+6,getY()+(getHeight()-MusicTypography.LINE_HEIGHT)/2,foreground,false);
        }
    }
}
