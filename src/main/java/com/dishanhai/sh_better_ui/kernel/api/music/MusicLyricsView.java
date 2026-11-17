// Author: dishanhai. Original UUK lyric control; layout inspired by Apple's content-first Now Playing view.
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.function.LongConsumer;
import org.lwjgl.glfw.GLFW;

/** Reusable native lyric viewport with wrapped text, real timestamp seeking and bounded keyboard scrolling.
 * The host owns playback. Compare {@link #contentKey()} to the current track before executing asynchronous seeks.
 * No artwork, fonts, audio or network resources are owned by this widget. */
public final class MusicLyricsView extends AbstractWidget {
    private final MusicLyricsModel model=new MusicLyricsModel();
    private final LongConsumer seek;
    private UiPreferences preferences;
    private float scale=2f;
    private int primary=0xfffdfbfc,secondary=0xffbcb4b8,backdrop=0xff292229;
    private List<List<FormattedCharSequence>> wrapped=List.of();
    private double[] tops=new double[0],heights=new double[0];
    private double offset,totalHeight;
    private long lastFrame;
    private boolean layoutDirty=true;
    private Font layoutFont;
    private net.minecraft.locale.Language layoutLanguage;
    private static volatile long fontMetricsRevision;
    private long measuredRevision=-1;
    /** Call after a font/resource reload even when Minecraft retains its Font object. */
    public static void invalidateFontMetrics(){fontMetricsRevision++;}
    /** Explicit invalidation for an embedding host that changes font providers. */
    public void invalidateLayout(){layoutDirty=true;}
    private int observedActive=-2;
    public MusicLyricsView(Bounds bounds,Component title,UiPreferences preferences,LongConsumer seek){
        super(bounds.x(),bounds.y(),bounds.width(),bounds.height(),title);
        this.preferences=Objects.requireNonNull(preferences);this.seek=Objects.requireNonNull(seek);
    }
    public MusicLyricsModel model(){return model;}
    public Object contentKey(){return model.contentKey();}
    public void setLines(List<MusicLyricsModel.Line> lines){setLines(lines,lines);}
    public void setLines(Object contentKey,List<MusicLyricsModel.Line> lines){
        boolean changed=!Objects.equals(model.contentKey(),contentKey)||!model.lines().equals(lines);
        model.content(contentKey,lines);
        if(changed){layoutDirty=true;offset=0;lastFrame=0;observedActive=-2;}
    }
    public void playback(long positionMillis,boolean seekable){model.playback(positionMillis,seekable);}
    public void preferences(UiPreferences value){preferences=Objects.requireNonNull(value);}
    public void palette(int primary,int secondary,int backdrop){this.primary=primary;this.secondary=secondary;this.backdrop=backdrop|0xff000000;}
    public void bounds(Bounds bounds){if(width!=bounds.width()||height!=bounds.height())layoutDirty=true;setX(bounds.x());setY(bounds.y());width=bounds.width();height=bounds.height();offset=clamp(offset);}
    public void scale(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Invalid lyric scale");float next=Math.max(1,Math.min(2.5f,value));if(next!=scale){scale=next;layoutDirty=true;}}
    public boolean browsing(){return model.browsing();}
    public void follow(){
        model.follow();lastFrame=0;
        // Unsynchronized/unavailable playback has no active line to pull an old
        // manual offset back into view on re-entry. Return to the beginning.
        if(model.activeIndex()<0)offset=0;
    }
    private double clamp(double value){return Math.max(0,Math.min(Math.max(0,totalHeight-height),value));}
    private void layout(Font font){
        var language=net.minecraft.locale.Language.getInstance();
        if(!layoutDirty&&layoutFont==font&&layoutLanguage==language&&measuredRevision==fontMetricsRevision)return;
        var lines=new ArrayList<List<FormattedCharSequence>>(model.lines().size());
        tops=new double[model.lines().size()];heights=new double[tops.length];
        double y=height*.24;
        for(int i=0;i<tops.length;i++){
            var text=model.lines().get(i).text();
            var parts=font.split(MpTypography.bold(Component.literal(text.isEmpty()?" ":text)),Math.max(1,(int)((width-16)/scale)));
            lines.add(parts);tops[i]=y;heights[i]=Math.max(1,parts.size())*MpTypography.LINE_HEIGHT*scale;
            // Tiny viewports must not scroll through a whole screen of inter-line whitespace.
            y+=heights[i]+Math.min(16*scale,height*.25);
        }
        // End the scroll range at real text, not at the final decorative line gap.
        // A short final line stays near the reading anchor; a wrapped final paragraph
        // can still be scrolled to its last baseline.
        int last=tops.length-1;
        totalHeight=last<0?height:Math.max(tops[last]+height*.76,
                tops[last]+heights[last]+Math.min(height*.24,MpTypography.LINE_HEIGHT*scale));
        wrapped=List.copyOf(lines);layoutFont=font;layoutLanguage=language;
        measuredRevision=fontMetricsRevision;layoutDirty=false;offset=clamp(offset);
        if(isFocused()&&model.focusedIndex()>=0)reveal(model.focusedIndex());
    }
    private void reveal(int index){if(index>=0&&index<tops.length)offset=clamp(tops[index]-height*.24);}
    private int hit(double y){double local=y-getY()+offset;for(int i=0;i<tops.length;i++)if(local>=tops[i]&&local<tops[i]+heights[i])return i;return -1;}
    private void activate(int index){model.focus(index);model.seekTarget(index).ifPresent(seek);}
    @Override public void onClick(double x,double y){layout(Minecraft.getInstance().font);int hit=hit(y);if(hit>=0)activate(hit);}
    public boolean mouseScrolled(double x,double y,double amount){
        if(!visible||!active||!isMouseOver(x,y)||amount==0)return false;
        layout(Minecraft.getInstance().font);model.browse();offset=clamp(offset-amount*36*scale);return true;
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return mouseScrolled(x,y,vertical);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!visible||!active||!isFocused())return false;
        if(key==GLFW.GLFW_KEY_ESCAPE){
            if(model.browsing()){follow();return true;}
            return false;
        }
        layout(Minecraft.getInstance().font);
        switch(key){
            case GLFW.GLFW_KEY_UP -> model.moveFocus(-1);
            case GLFW.GLFW_KEY_DOWN -> model.moveFocus(1);
            case GLFW.GLFW_KEY_HOME -> model.focus(0);
            case GLFW.GLFW_KEY_END -> model.focus(model.lines().size()-1);
            case GLFW.GLFW_KEY_PAGE_UP -> {model.browse();offset=clamp(offset-height*.75);return true;}
            case GLFW.GLFW_KEY_PAGE_DOWN -> {model.browse();offset=clamp(offset+height*.75);return true;}
            case GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER,GLFW.GLFW_KEY_SPACE -> {activate(model.focusedIndex()<0?model.activeIndex():model.focusedIndex());return true;}
            default -> {return false;}
        }
        reveal(model.focusedIndex());return true;
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        if(width<=0||height<=0)return;
        var font=Minecraft.getInstance().font;layout(font);
        int current=model.activeIndex();int hovered=isMouseOver(mx,my)?hit(my):-1;long now=PageTransition.nowMs();
        if(!model.browsing()&&current>=0){
            double target=clamp(tops[current]-height*.24);
            double dt=lastFrame==0?1:Math.max(0,Math.min(.05,(now-lastFrame)/1000d));
            if(preferences.reducedMotion()||lastFrame==0||Math.abs(current-observedActive)>6)offset=target;
            else offset+=(target-offset)*(1-Math.exp(-12*dt));
        }
        observedActive=current;lastFrame=now;
        int ink=ContrastPolicy.readable(primary,0,backdrop,4.5);
        int muted=preferences.highContrast()?ink:ContrastPolicy.readable(secondary,0,backdrop,3);
        g.enableScissor(getX(),getY(),getX()+width,getY()+height);
        try {
            for(int i=0;i<tops.length;i++){
                double y=getY()+tops[i]-offset;if(y+heights[i]<getY()||y>getY()+height)continue;
                int color=(!model.synchronizedLyrics()||i==current||(i==hovered&&model.seekTarget(i).isPresent()))?ink:muted;
                if(isFocused()&&i==model.focusedIndex())g.renderOutline(getX()+1,(int)y-3,width-2,(int)Math.min(height,heights[i]+6),ink);
                g.pose().pushPose();
                try {g.pose().translate(getX()+8,y,0);g.pose().scale(scale,scale,1);
                    var parts=wrapped.get(i);
                    int first=Math.max(0,(int)Math.floor((getY()-y)/(MpTypography.LINE_HEIGHT*scale)));
                    int last=Math.min(parts.size(),(int)Math.ceil((getY()+height-y)/(MpTypography.LINE_HEIGHT*scale)));
                    for(int row=first;row<last;row++)g.drawString(font,parts.get(row),0,row*MpTypography.LINE_HEIGHT,color,false);
                } finally {g.pose().popPose();}
            }
            if(model.lines().isEmpty())g.drawString(font,MpTypography.text(getMessage()),getX()+8,getY()+12,muted,false);
        } finally {g.disableScissor();}
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){
        int index=model.focusedIndex()>=0?model.focusedIndex():model.activeIndex();
        output.add(NarratedElementType.TITLE,getMessage());
        if(index>=0&&index<model.lines().size())output.add(NarratedElementType.TITLE,Component.literal(model.lines().get(index).text()));
        output.add(NarratedElementType.USAGE,Component.translatable(model.synchronizedLyrics()?"sh_better_ui.music.lyrics.keyboard":"sh_better_ui.music.lyrics.unsynchronized"));
    }
}
