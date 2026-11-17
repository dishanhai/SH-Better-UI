// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.control;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.GuiGraphics;
import com.dishanhai.sh_better_ui.client.gui.scaled.MpTypography;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import java.util.Objects;
import java.util.function.Consumer;

/** UUK thin music timeline. Native widget is only the focus/input/narration adapter, never the renderer. */
public final class MusicSeekControl extends AbstractWidget {
    private final MusicSeekModel model=new MusicSeekModel();
    private final Consumer<MusicSeekModel.Edit> preview,commit,cancel;
    private UiPreferences preferences;
    private int ink=0xffffffff;
    private boolean timePreviewEnabled;
    private float engagement;
    private long lastFrameNanos;
    private long keyboardStep=5000,fineKeyboardStep=1000;
    private java.util.function.BiFunction<Long,Long,Component> formatter=
            (position,duration)->Component.literal(clock(position)+" / "+clock(duration));
    public MusicSeekControl(Bounds bounds,Component name,UiPreferences preferences,
                            Consumer<MusicSeekModel.Edit> onPreview,Consumer<MusicSeekModel.Edit> onCommit,
                            Consumer<MusicSeekModel.Edit> onCancel){
        super(bounds.x(),bounds.y(),bounds.width(),bounds.height(),name);
        if(bounds.width()<16||bounds.height()<12)throw new IllegalArgumentException("Seek hit area must be at least 16 x 12");
        this.preferences=Objects.requireNonNull(preferences);preview=Objects.requireNonNull(onPreview);
        commit=Objects.requireNonNull(onCommit);cancel=Objects.requireNonNull(onCancel);
    }
    /** Call every owner frame, including when hidden. Owner must validate Edit.trackKey again before backend seek. */
    public void sync(String key,long positionMs,long durationMs,boolean enabled){
        if(!visible)cancelInteraction();
        model.sync(key,positionMs,durationMs,enabled).ifPresent(cancel);active=model.enabled();
    }
    /** Override for normalized volume or other timelines; keeps narration truthful to the presented value. */
    public void setValueFormatter(java.util.function.BiFunction<Long,Long,Component> value){formatter=Objects.requireNonNull(value);}
    public void setKeyboardStep(long normal,long fine){
        if(normal<=0||fine<=0)throw new IllegalArgumentException("Keyboard steps must be positive");
        keyboardStep=normal;fineKeyboardStep=fine;
    }
    /** Opt-in for time seeking only: normalized volume controls keep their own value format. */
    public void setTimePreviewEnabled(boolean value){timePreviewEnabled=value;}
    public long previewPositionMs(){return model.positionMs();}
    public boolean isEditing(){return model.editing();}
    public void setPreferences(UiPreferences value){preferences=Objects.requireNonNull(value);}
    public void setInk(int argb){ink=argb;}
    public void cancelInteraction(){if(model!=null)model.cancel().ifPresent(cancel);}
    @Override public void setFocused(boolean value){super.setFocused(value);if(!value)cancelInteraction();}
    @Override public void setX(int value){if(value!=getX())cancelInteraction();super.setX(value);}
    @Override public void setY(int value){if(value!=getY())cancelInteraction();super.setY(value);}
    @Override public void setWidth(int value){if(value!=getWidth())cancelInteraction();super.setWidth(value);}
    @Override public void setHeight(int value){if(value!=getHeight())cancelInteraction();super.setHeight(value);}
    private double fraction(double x){return (x-getX()-4)/Math.max(1,width-8);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0||!active||!visible||!isMouseOver(x,y))return false;
        setFocused(true);model.begin(fraction(x)).ifPresent(preview);return model.editing();
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button!=0||!model.editing())return false;
        if(!active||!visible){cancelInteraction();return false;}
        model.drag(fraction(x)).ifPresent(preview);return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button!=0||!model.editing())return false;
        if(!active||!visible){cancelInteraction();return true;}
        model.drag(fraction(x)).ifPresent(preview);model.release().ifPresent(commit);return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==256&&model.editing()){cancelInteraction();return true;}
        if(!active||!visible||!isFocused()||model.editing())return false;
        long step=(mods&1)!=0?fineKeyboardStep:keyboardStep;
        switch(key){
            case 263 -> model.keyboard(-step).ifPresent(commit);
            case 262 -> model.keyboard(step).ifPresent(commit);
            case 268 -> model.seekTo(0).ifPresent(commit);
            case 269 -> model.seekTo(model.durationMs()).ifPresent(commit);
            default -> {return false;}
        }
        return true;
    }
    @Override protected void renderWidget(GuiGraphics g,int x,int y,float partial){
        if(!active)cancelInteraction();
        long now=System.nanoTime();
        double seconds=lastFrameNanos==0?0:(now-lastFrameNanos)/1_000_000_000d;
        lastFrameNanos=now;
        engagement=MusicSeekPresentation.engagement(engagement,active&&(isHoveredOrFocused()||model.editing()),
                seconds,preferences.reducedMotion());
        MusicControlRenderer.seek(g,new Bounds(getX(),getY(),width,height),model.fraction(),
                engagement,isFocused(),active,ink,preferences);
        if(timePreviewEnabled&&active&&model.editing())renderTimePreview(g);
    }
    private void renderTimePreview(GuiGraphics g){
        var font=net.minecraft.client.Minecraft.getInstance().font;
        String value=clock(model.positionMs())+" / −"+clock(model.durationMs()-model.positionMs());
        // Bubble remains within the seek width; on small controls keep the current time first.
        if(font.width(MpTypography.text(value))+12>width)value=clock(model.positionMs());
        while(!value.isEmpty()&&font.width(MpTypography.text(value))+12>width)value=value.substring(0,value.length()-1);
        var text=MpTypography.text(value);
        int bubbleWidth=Math.min(width,font.width(text)+12);
        int left=MusicSeekPresentation.bubbleX(getX(),width,bubbleWidth,model.fraction());
        int top=Math.max(0,getY()-MpTypography.LINE_HEIGHT-8);
        int background=preferences.highContrast()?0xff000000:0xf0222226;
        com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners.draw(g,left,top,bubbleWidth,
                MpTypography.LINE_HEIGHT+6,5,preferences.highContrast()?1:0,0xffffffff,background);
        g.drawString(font,text,left+6,top+3,0xffffffff,false);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out){
        out.add(NarratedElementType.TITLE,getMessage());
        out.add(NarratedElementType.POSITION,formatter.apply(model.positionMs(),model.durationMs()));
        out.add(NarratedElementType.USAGE,Component.translatable("narration.slider.usage.focused"));
    }
    private static String clock(long ms){long s=Math.max(0,ms)/1000;return s/60+":"+(s%60<10?"0":"")+s%60;}
}
