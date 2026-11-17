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
        var host=UiTheme.current();
        int frame=preferences.highContrast()?0xff000000:GuiMotion.lerpColor(host.background()|0xff000000,host.surface()|0xff000000,.76f);
        int outline=selected() || isFocused() ? host.accent() : preferences.highContrast()?0xffffffff:0;
        if(isHoveredOrFocused())frame=GuiMotion.lerpColor(frame,host.elevatedSurface()|0xff000000,.3f);
        RoundedCorners.draw(g,x,y,width,height,8,isFocused()||selected()||preferences.highContrast()?1:0,outline,frame);
        int sx=x+8,sy=y+8,sw=width-16,sh=56;
        int canvas=p.light()?0xffe8eef5:0xff101722;
        int backing=ContrastPolicy.composite(p.surface(),ContrastPolicy.composite(p.background(),canvas));
        int ink=ContrastPolicy.readable(p.text(),0,backing,4.5);
        int soft=GuiMotion.lerpColor(ink,backing,.65f);
        RoundedCorners.draw(g,sx,sy,sw,sh,5,0,0,ContrastPolicy.composite(p.background(),canvas));
        // A miniature real music application: navigation, cover, metadata, queue and transport.
        int side=22,main=sx+side+5,mainW=sw-side-10;
        RoundedCorners.draw(g,sx+3,sy+3,side-1,sh-6,3,0,0,ContrastPolicy.composite(p.secondarySurface(),backing));
        for(int row=0;row<4;row++){
            int yy=sy+9+row*8;
            RoundedCorners.draw(g,sx+6,yy,side-7,3,1,0,0,row==1?p.accent():soft&0xffffff|0x45000000);
        }
        int cover=23;
        RoundedCorners.draw(g,main,sy+6,cover,cover,4,0,0,p.accent());
        RoundedCorners.draw(g,main+5,sy+11,13,13,6,0,0,ink&0xffffff|0x30000000);
        g.fill(main+cover+5,sy+8,main+mainW,sy+10,ink);
        g.fill(main+cover+5,sy+14,main+mainW-4,sy+16,soft);
        g.fill(main+cover+5,sy+23,main+mainW-8,sy+25,p.accent());
        for(int row=0;row<2;row++){
            int yy=sy+34+row*6;
            g.fill(main,yy,main+mainW,yy+2,soft&0xffffff|0x60000000);
        }
        RoundedCorners.draw(g,main,sy+47,6,6,3,0,0,p.accent());
        g.fill(main+11,sy+49,main+mainW,sy+51,soft&0xffffff|0x45000000);
        g.fill(main+11,sy+49,main+11+Math.max(1,(mainW-11)/3),sy+51,p.accent());
        int text=ContrastPolicy.readable(host.text(),frame,frame,preferences.highContrast()?7:4.5);
        String name=getMessage().getString().replaceFirst("^Muonium\\s*[·/]?\\s*", "");
        var label=net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(MpTypography.text(Component.literal(name)),width-30));
        g.drawString(font,label,x+9,y+70,text,false);
        if(selected())g.drawString(font,MpTypography.text(Component.literal("✓")),x+width-17,y+70,host.accent(),false);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE,Component.translatable("sh_better_ui.mp_theme.card_hint",getMessage()));
        output.add(NarratedElementType.USAGE,Component.translatable(selected()?"sh_better_ui.mp_theme.selected":"sh_better_ui.mp_theme.stage"));
    }
}
