// Author: dishanhai. Registry-backed native theme miniature, no per-card background capture.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** One keyboard-focusable card. Preview pixels are illustrative; only activation stages a theme. */
final class UiThemePreviewCard extends AbstractWidget {
    private final String id;
    private final MpThemeBrowser browser;
    private final UiPreferences preferences;
    UiThemePreviewCard(Bounds bounds, String id, MpThemeBrowser browser, UiPreferences preferences) {
        super(bounds.x(),bounds.y(),bounds.width(),bounds.height(),browser.name(id));
        this.id=id; this.browser=browser; this.preferences=preferences; refreshLabel();
        com.dishanhai.sh_better_ui.client.api.PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
    }
    String themeId() { return id; }
    void refreshLabel() {
        var name = browser.name(id); setMessage(name);
        setTooltip(Tooltip.create(Component.translatable("sh_better_ui.mp_theme.card_hint",name)));
    }
    boolean selected() { return browser.selectedId().equals(id); }
    @Override public void onClick(double x,double y) { if(active && visible) browser.select(id); }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(!active || !visible || !isFocused()) return false;
        if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER || key==GLFW.GLFW_KEY_SPACE) {
            browser.select(id); return true;
        }
        return false;
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta) {
        var p=browser.palette(id); if(p==null) return;
        var font=Minecraft.getInstance().font;
        int x=getX(), y=getY();
        int frame=DarkModeTheme.surfaceColor() | 0xff000000;
        int outline=selected() || isFocused() ? DarkModeTheme.accentColor() : DarkModeTheme.borderColor();
        RoundedCorners.draw(g,x,y,width,height,8,isFocused() || selected()?2:1,outline,frame);
        int sx=x+8, sy=y+8, sw=width-16, sh=46;
        // Known canvas permits mathematically valid contrast without reading GPU pixels.
        int canvas=p.light()?0xffe8eef5:0xff101722;
        g.fill(sx,sy,sx+sw,sy+sh,canvas);
        g.fill(sx,sy,sx+sw,sy+sh,p.background());
        g.fill(sx+3,sy+3,sx+sw-3,sy+sh-3,p.surface());
        int backing=ContrastPolicy.composite(p.surface(),ContrastPolicy.composite(p.background(),canvas));
        int ink=ContrastPolicy.readable(p.text(),0,backing,4.5);
        int accentText=ContrastPolicy.readable(p.text(),p.accent(),backing,4.5);
        g.fill(sx+5,sy+5,sx+22,sy+sh-5,p.secondarySurface());
        g.fill(sx+26,sy+8,sx+sw-10,sy+10,ink);
        g.fill(sx+26,sy+15,sx+sw-22,sy+17,ink & 0x00ffffff | 0x99000000);
        g.fill(sx+26,sy+24,sx+sw-10,sy+37,p.accent());
        g.fill(sx+32,sy+29,sx+sw-16,sy+31,accentText);
        int text=ContrastPolicy.readable(DarkModeTheme.textColor(),frame,frame,preferences.highContrast()?7:4.5);
        g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),width-16),x+8,y+60,text,false);
        if(selected()) g.drawString(font,Component.translatable("sh_better_ui.mp_theme.selected"),x+8,y+73,text,false);
        else if(isHoveredOrFocused()) g.drawString(font,Component.translatable("sh_better_ui.mp_theme.stage"),x+8,y+73,text,false);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE,Component.translatable("sh_better_ui.mp_theme.card_hint",getMessage()));
        output.add(NarratedElementType.USAGE,Component.translatable(selected()?"sh_better_ui.mp_theme.selected":"sh_better_ui.mp_theme.stage"));
    }
}
