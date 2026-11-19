// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import java.util.Objects;

/** Unframed UUK transport glyph, with independent custom drawing and release-to-activate input. */
public final class MusicTransportControl extends AbstractWidget {
    public enum Glyph { PREVIOUS, PLAY, PAUSE, NEXT, BACK, LYRICS, QUEUE, STOP, CANCEL, SEARCH, FAVORITE, REMOVE, USER }
    private Glyph glyph;
    private final TransportGlyphMotion glyphMotion = new TransportGlyphMotion();
    private UiPreferences preferences;
    private final Runnable action,feedback;
    private boolean pointerPressed;
    private int pressedKey=-1,ink=0xffffffff;
    public MusicTransportControl(Bounds bounds,Component name,Glyph glyph,UiPreferences preferences,Runnable action){
        this(bounds,name,glyph,preferences,action,null);
    }
    /** Optional feedback policy; null retains native activation sound. Tests can supply a counting callback. */
    public MusicTransportControl(Bounds bounds,Component name,Glyph glyph,UiPreferences preferences,Runnable action,Runnable feedback){
        super(bounds.x(),bounds.y(),bounds.width(),bounds.height(),name);
        if(bounds.width()<12||bounds.height()<12)throw new IllegalArgumentException("Transport hit area must be at least 12 x 12");
        this.glyph=Objects.requireNonNull(glyph);this.preferences=Objects.requireNonNull(preferences);
        this.action=Objects.requireNonNull(action);this.feedback=feedback;
    }
    public void setGlyph(Glyph value){if(glyph!=value)cancelInteraction();glyph=Objects.requireNonNull(value);}
    public void setPreferences(UiPreferences value){preferences=Objects.requireNonNull(value);}
    public void setInk(int argb){ink=argb;}
    public void cancelInteraction(){pointerPressed=false;pressedKey=-1;}
    /** Call when the owner hides, disables, loses its window, or detaches this control. */
    public void setAvailable(boolean value){active=value;if(!value||!visible)cancelInteraction();}
    @Override public void setFocused(boolean value){super.setFocused(value);if(!value)cancelInteraction();}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0||!active||!visible||!isMouseOver(x,y))return false;
        pointerPressed=true;pressedKey=-1;setFocused(true);return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button!=0||!pointerPressed)return false;
        pointerPressed=false;if(active&&visible&&isMouseOver(x,y))activate();return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==256&&(pointerPressed||pressedKey!=-1)){cancelInteraction();return true;}
        if(pointerPressed||!active||!visible||!isFocused()||(key!=32&&key!=257&&key!=335))return false;
        if(pressedKey==-1)pressedKey=key;return true;
    }
    @Override public boolean keyReleased(int key,int scan,int mods){
        if(key!=pressedKey)return false;pressedKey=-1;
        if(active&&visible&&isFocused())activate();return true;
    }
    private void activate(){
        if(feedback!=null)feedback.run();else playDownSound(Minecraft.getInstance().getSoundManager());
        action.run();
    }
    @Override protected void renderWidget(GuiGraphics g,int x,int y,float partial){
        if(!active)cancelInteraction();
        float glyphWeight = glyphMotion.sample(glyph, System.nanoTime() / 1_000_000L,
                !active || preferences.reducedMotion() || preferences.highContrast());
        MusicControlRenderer.transport(g,new Bounds(getX(),getY(),width,height),glyph,glyphWeight,isHovered(),isFocused(),
                pointerPressed||pressedKey!=-1,active,ink,preferences);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
}
