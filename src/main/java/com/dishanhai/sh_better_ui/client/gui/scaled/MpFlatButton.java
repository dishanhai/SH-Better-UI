// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;

/** Page-local quiet button. Keeps vanilla focus, activation, narration and hit testing. */
public final class MpFlatButton extends Button {
    private UiPreferences preferences;
    private final boolean primary;
    private boolean selected;
    private Component display;
    private long pressedAt=Long.MIN_VALUE;
    private final DarkModeTheme.Interaction motion=DarkModeTheme.Interaction.control();
    public MpFlatButton(int x,int y,int w,int h,Component label,OnPress action,UiPreferences preferences,boolean primary){
        super(x,y,w,h,label,action,DEFAULT_NARRATION);this.preferences=preferences;this.primary=primary;
    }
    public void preferences(UiPreferences value){preferences=value;}
    public void displayText(Component value){display=value;}
    public void selected(boolean value){selected=value;}
    @Override public void onPress(){pressedAt=PageTransition.nowMs();super.onPress();}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        var theme=UiTheme.current();var state=motion.sample(isHovered(),pressedAt!=Long.MIN_VALUE&&PageTransition.nowMs()-pressedAt<110,isFocused(),active,preferences,PageTransition.nowMs());
        int fill=primary||selected?theme.accent():theme.secondarySurface()|0xff000000;
        if(preferences.highContrast())fill=primary||selected?0xffffffff:0xff000000;
        int ink=ContrastPolicy.readable(theme.text(),fill,fill,preferences.highContrast()?7:4.5);
        if(!active){fill=GuiMotion.lerpColor(fill,theme.background()|0xff000000,.5f);ink=GuiMotion.lerpColor(ink,fill,.48f);}
        else if(!(primary||selected))fill=GuiMotion.lerpColor(fill,theme.elevatedSurface()|0xff000000,state.hover()*.65f);
        fill=GuiMotion.lerpColor(fill,0xff000000,state.press()*.12f);
        int edge=isFocused()?theme.accent():preferences.highContrast()?0xffffffff:0;
        RoundedCorners.draw(g,getX(),getY(),width,height,5,isFocused()||preferences.highContrast()?1:0,edge,fill);
        var font=Minecraft.getInstance().font;
        var label=net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(MpTypography.text(display==null?getMessage():display),Math.max(0,width-12)));
        g.drawString(font,label,getX()+(width-font.width(label))/2,getY()+(height-MpTypography.LINE_HEIGHT)/2,ink,false);
    }
}
