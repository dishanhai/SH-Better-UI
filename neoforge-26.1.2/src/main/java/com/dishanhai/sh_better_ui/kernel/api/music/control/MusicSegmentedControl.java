// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** One keyboard focus stop, with release-to-commit choices. Owners must cancel before replacing
 * the bound source and use setVisible/setAvailable rather than writing inherited public fields. */
public final class MusicSegmentedControl extends AbstractWidget {
    private final List<Component> choices;
    private final IntConsumer onChange;
    private final Runnable feedback;
    private final InterruptibleAnimation thumb;
    private UiPreferences preferences;
    private int selected, pending=-1, pressedKey=-1;
    private boolean pointer;

    public MusicSegmentedControl(Bounds bounds, Component label, List<Component> choices, int selected,
                                  UiPreferences preferences, IntConsumer onChange) {
        this(bounds,label,choices,selected,preferences,onChange,null);
    }
    /** Feedback injection is optional; null keeps normal Minecraft activation audio. */
    public MusicSegmentedControl(Bounds b, Component label, List<Component> choices, int selected,
                                  UiPreferences preferences, IntConsumer onChange, Runnable feedback) {
        super(b.x(),b.y(),b.width(),b.height(),Objects.requireNonNull(label));
        this.choices=List.copyOf(choices);
        if(choices.size()<2||choices.size()>5)throw new IllegalArgumentException("Requires 2 to 5 choices");
        if(b.width()<choices.size()*24||b.height()<20)throw new IllegalArgumentException("Requires 24px per choice and 20px height");
        checkIndex(selected);this.selected=selected;
        this.preferences=Objects.requireNonNull(preferences);this.onChange=Objects.requireNonNull(onChange);this.feedback=feedback;
        thumb=new InterruptibleAnimation(Spring.INDICATOR,selected);
    }
    private void checkIndex(int value){if(value<0||value>=choices.size())throw new IllegalArgumentException("Invalid choice index");}
    public int selectedIndex(){return selected;}
    public int choiceCount(){return choices.size();}
    /** Silent authoritative synchronization. A changed selection invalidates an outstanding press. */
    public void setSelectedIndex(int value){checkIndex(value);if(selected!=value){cancelInteraction();select(value);}}
    public void setPreferences(UiPreferences value){preferences=Objects.requireNonNull(value);}
    public void setVisible(boolean value){visible=value;if(!value)cancelInteraction();}
    public void setAvailable(boolean value){active=value;if(!value||!visible)cancelInteraction();}
    public boolean isArmed(){return pending>=0;}
    public void cancelInteraction(){pointer=false;pending=-1;pressedKey=-1;}
    @Override public void setFocused(boolean value){super.setFocused(value);if(!value)cancelInteraction();}
    @Override public void setX(int value){if(value!=getX())cancelInteraction();super.setX(value);}
    @Override public void setY(int value){if(value!=getY())cancelInteraction();super.setY(value);}
    @Override public void setWidth(int value){if(value!=getWidth())cancelInteraction();super.setWidth(value);}
    @Override public void setHeight(int value){if(value!=getHeight())cancelInteraction();super.setHeight(value);}
    private int at(double x,double y){
        if(!isMouseOver(x,y)||width<=0)return -1;
        return Math.min(choices.size()-1,(int)((x-getX())*choices.size()/width));
    }
    // Native 26.x event adapters; primitive overloads retain the shared input contract.
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return mouseClicked(event.x(), event.y(), event.button()); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return mouseReleased(event.x(), event.y(), event.button()); }
    @Override public boolean keyPressed(KeyEvent event) { return keyPressed(event.key(), event.scancode(), event.modifiers()); }
    @Override public boolean keyReleased(KeyEvent event) { return keyReleased(event.key(), event.scancode(), event.modifiers()); }
    public boolean mouseClicked(double x,double y,int button){
        int index=at(x,y);if(button!=0||!active||!visible||index<0)return false;
        cancelInteraction();pending=index;pointer=true;setFocused(true);return true;
    }
    public boolean mouseReleased(double x,double y,int button){
        if(button!=0||!pointer)return false;
        int index=pending;cancelInteraction();
        if(active&&visible&&at(x,y)==index)commit(index);return true;
    }
    public boolean keyPressed(int key,int scan,int mods){
        if(key==256&&isArmed()){cancelInteraction();return true;}
        if(!active||!visible||!isFocused()||pointer)return false;
        int target=switch(key){case 263,265->Math.max(0,selected-1);case 262,264->Math.min(choices.size()-1,selected+1);
            case 268->0;case 269->choices.size()-1;case 32,257,335->selected;default->-1;};
        if(target<0)return false;
        if(pressedKey==-1){pressedKey=key;pending=target;}return true;
    }
    public boolean keyReleased(int key,int scan,int mods){
        if(key!=pressedKey||pressedKey==-1)return false;
        int index=pending;cancelInteraction();if(active&&visible&&isFocused())commit(index);return true;
    }
    private void select(int value){selected=value;thumb.blendTo(value,now(),0,140,InterruptibleAnimation.BlendCurve.SMOOTHSTEP);}
    private void commit(int value){
        if(value==selected)return;
        select(value);
        if(feedback==null)playDownSound(Minecraft.getInstance().getSoundManager());else feedback.run();
        onChange.accept(value);
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        if(!active)cancelInteraction();
        var theme=UiTheme.current();int ink=theme.text();boolean contrast=preferences.highContrast();
        if(!active)ink=alpha(ink,.45f);
        int radius=Math.min(9,height/2);
        RoundedCorners.draw(g,getX(),getY(),width,height,radius,contrast?1:0,ink,alpha(ink,contrast?.10f:.065f));
        float position=thumb.sample(now(),preferences);
        float cell=(width-4f)/choices.size();
        int left=getX()+2+Math.round(position*cell),right=getX()+2+Math.round((position+1)*cell);
        RoundedCorners.draw(g,left,getY()+2,Math.max(1,right-left),height-4,Math.max(2,radius-2),contrast?1:0,ink,
                contrast?theme.surface():GuiMotion.lerpColor(theme.surface(),theme.accent(),.10f));
        var font=Minecraft.getInstance().font;
        for(int i=0;i<choices.size();i++){
            int x=getX()+2+Math.round(i*cell),w=Math.round((i+1)*cell)-Math.round(i*cell);
            boolean armed=pending==i;boolean hovered=active&&at(mx,my)==i;
            if(armed||hovered&&i!=selected)
                RoundedCorners.draw(g,x,getY()+2,w,height-4,Math.max(2,radius-2),0,0,alpha(ink,armed?.13f:.06f));
            if(isFocused()&&i==(pending<0?selected:pending))
                RoundedCorners.draw(g,x,getY()+2,w,height-4,Math.max(2,radius-2),1,theme.accent(),0);
            var text=MusicTextLayout.ellipsize(font,choices.get(i),Math.max(0,w-12),i==selected?MpTypography.SEMIBOLD:MpTypography.FONT);
            g.text(font,text,x+Math.max(0,(w-font.width(text))/2),getY()+(height-MpTypography.LINE_HEIGHT)/2,ink,false);
        }
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){
        int index=pending<0?selected:pending;
        output.add(NarratedElementType.TITLE,getMessage().copy().append(Component.literal(": ")).append(choices.get(index)));
        output.add(NarratedElementType.POSITION,Component.translatable("narrator.position.object_list",index+1,choices.size()));
    }
    private static int alpha(int color,float factor){return (color&0xffffff)|(Math.round((color>>>24)*factor)<<24);}
    private static long now(){return System.nanoTime()/1_000_000L;}
}
