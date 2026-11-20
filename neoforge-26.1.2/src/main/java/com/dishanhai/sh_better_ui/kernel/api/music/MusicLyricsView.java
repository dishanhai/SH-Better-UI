// Author: dishanhai. Original UUK lyric control; layout inspired by Apple's content-first Now Playing view.
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.function.LongConsumer;
import java.util.function.DoubleConsumer;
import org.lwjgl.glfw.GLFW;

/** Reusable native lyric viewport with wrapped text, real timestamp seeking and bounded keyboard scrolling.
 * The host owns playback. Compare {@link #contentKey()} to the current track before executing asynchronous seeks.
 * No artwork, fonts, audio or network resources are owned by this widget. */
public final class MusicLyricsView extends AbstractWidget {
    private final MusicLyricsModel model=new MusicLyricsModel();
    private final LongConsumer seek;
    private DoubleConsumer onManualScroll = delta -> {};
    private UiPreferences preferences;
    private float scale=2f;
    private int primary=0xfffdfbfc,secondary=0xffbcb4b8,backdrop=0xff292229;
    private List<List<FormattedCharSequence>> wrapped=List.of();
    private Map<Integer,LyricGlyphRuns> timedRows=Map.of();
    private final LyricFollowMotion followMotion=new LyricFollowMotion();
    private double[] tops=new double[0],heights=new double[0];
    private float[] emphasis=new float[0];
    private double offset,totalHeight;
    private long lastFrame,observedPosition=-1;
    private boolean layoutDirty=true;
    private boolean completeRowsOnly;
    private boolean immersiveReading;
    private boolean seekingPreview, previewChanged;
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
    /** Receives actual logical-pixel movement from explicit wheel/keyboard browsing only.
     * Programmatic playback, follow, content and seek changes never invoke this callback. */
    public void setOnManualScroll(DoubleConsumer listener) { onManualScroll = Objects.requireNonNull(listener); }
    private void manualScrollTo(double next) {
        double previous = offset;
        offset = clamp(next);
        if (offset != previous) onManualScroll.accept(offset - previous);
    }
    public MusicLyricsModel model(){return model;}
    public Object contentKey(){return model.contentKey();}
    public void setLines(List<MusicLyricsModel.Line> lines){setLines(lines,lines);}
    public void setLines(Object contentKey,List<MusicLyricsModel.Line> lines){
        boolean changed=!Objects.equals(model.contentKey(),contentKey)||!model.lines().equals(lines);
        model.content(contentKey,lines);
        if(changed){layoutDirty=true;offset=0;lastFrame=0;observedActive=-2;observedPosition=-1;emphasis=new float[0];}
    }
    public void playback(long positionMillis,boolean seekable){playback(positionMillis,seekable,false);}
    /** A scrub position controls the lyric viewport but never makes a lyric line seekable. */
    public void playback(long positionMillis,boolean seekable,boolean preview){
        previewChanged|=seekingPreview!=preview;
        seekingPreview=preview;
        if(preview&&model.browsing())follow();
        model.playback(positionMillis,seekable&&!preview);
    }
    public void preferences(UiPreferences value){preferences=Objects.requireNonNull(value);}
    public void palette(int primary,int secondary,int backdrop){this.primary=primary;this.secondary=secondary;this.backdrop=backdrop|0xff000000;}
    public void bounds(Bounds bounds){if(width!=bounds.width()||height!=bounds.height())layoutDirty=true;setX(bounds.x());setY(bounds.y());width=bounds.width();height=bounds.height();offset=clamp(offset);}
    public void scale(float value){if(!Float.isFinite(value))throw new IllegalArgumentException("Invalid lyric scale");float next=Math.max(1,Math.min(2.5f,value));if(next!=scale){scale=next;layoutDirty=true;}}
    /** Compact hosts may omit clipped edge rows; ordinary player scrolling keeps partial rows. */
    public void completeRowsOnly(boolean value){completeRowsOnly=value;}
    static boolean completeRowVisible(double rowY,double rowHeight,double top,double viewportHeight){
        return Double.isFinite(rowY)&&Double.isFinite(rowHeight)&&Double.isFinite(top)
                &&Double.isFinite(viewportHeight)&&rowHeight>0&&viewportHeight>=rowHeight
                &&rowY>=top&&rowY-top<=viewportHeight-rowHeight;
    }
    /** Stronger visual hierarchy and stable resting baselines for a full-screen player. */
    public void immersiveReading(boolean value){immersiveReading=value;}
    public boolean browsing(){return model.browsing();}
    public void follow(){
        model.follow();
        // Unsynchronized/unavailable playback has no active line to pull an old
        // manual offset back into view on re-entry. Return to the beginning.
        if(!model.synchronizedLyrics()||model.positionMillis()<0)offset=0;
        followMotion.reset(offset);
    }
    private double clamp(double value){return Math.max(0,Math.min(Math.max(0,totalHeight-height),value));}
    private void layout(Font font){
        var language=net.minecraft.locale.Language.getInstance();
        if(!layoutDirty&&layoutFont==font&&layoutLanguage==language&&measuredRevision==fontMetricsRevision)return;
        var lines=new ArrayList<List<FormattedCharSequence>>(model.lines().size());
        var timed=new HashMap<Integer,LyricGlyphRuns>();
        tops=new double[model.lines().size()];heights=new double[tops.length];
        if(emphasis.length!=tops.length)emphasis=new float[tops.length];
        double y=height*.24;
        for(int i=0;i<tops.length;i++){
            var line=model.lines().get(i);
            var text=line.text();
            var styled=MusicTypography.bold(Component.literal(text.isEmpty()?" ":text));
            boolean timedLine=line.hasWordTiming();
            if(timedLine){
                styled=Component.empty();
                for(int word=0;word<line.words().size();word++){
                    int wordIndex=word;
                    styled.append(MusicTypography.bold(Component.literal(line.words().get(word).text()))
                            .withStyle(style->style.withInsertion(LyricGlyphRuns.tag(wordIndex))));
                }
            }
            var parts=font.split(styled,Math.max(1,(int)((width-16)/scale)));
            lines.add(parts);
            if(timedLine)timed.put(i,LyricGlyphRuns.measure(parts,line.words().size(),
                    (cp,style)->font.getSplitter().stringWidth(FormattedCharSequence.codepoint(cp,style))));
            tops[i]=y;heights[i]=Math.max(1,parts.size())*MusicTypography.LINE_HEIGHT*scale;
            // Tiny viewports must not scroll through a whole screen of inter-line whitespace.
            y+=heights[i]+Math.min(10*scale,height*.25);
        }
        // End the scroll range at real text, not at the final decorative line gap.
        // A short final line stays near the reading anchor; a wrapped final paragraph
        // can still be scrolled to its last baseline.
        int last=tops.length-1;
        totalHeight=last<0?height:Math.max(tops[last]+height*.76,
                tops[last]+heights[last]+Math.min(height*.24,MusicTypography.LINE_HEIGHT*scale));
        wrapped=List.copyOf(lines);timedRows=Map.copyOf(timed);layoutFont=font;layoutLanguage=language;
        measuredRevision=fontMetricsRevision;layoutDirty=false;offset=clamp(offset);
        if(isFocused()&&model.focusedIndex()>=0)reveal(model.focusedIndex());
    }
    private void reveal(int index){if(index>=0&&index<tops.length)offset=clamp(tops[index]-height*.24);}
    private int hit(double y){double local=y-getY()+offset;for(int i=0;i<tops.length;i++)if(local>=tops[i]&&local<tops[i]+heights[i])return i;return -1;}
    private void activate(int index){model.focus(index);model.seekTarget(index).ifPresent(seek);}
    @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick){layout(Minecraft.getInstance().font);int hit=hit(event.y());if(hit>=0)activate(hit);}
    public boolean mouseScrolled(double x,double y,double amount){
        if(!visible||!active||!isMouseOver(x,y)||amount==0||!Double.isFinite(amount))return false;
        layout(Minecraft.getInstance().font);model.browse();manualScrollTo(offset-amount*36*scale);return true;
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return mouseScrolled(x,y,vertical);}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        int key=event.key();
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
            case GLFW.GLFW_KEY_PAGE_UP -> {model.browse();manualScrollTo(offset-height*.75);return true;}
            case GLFW.GLFW_KEY_PAGE_DOWN -> {model.browse();manualScrollTo(offset+height*.75);return true;}
            case GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER,GLFW.GLFW_KEY_SPACE -> {activate(model.focusedIndex()<0?model.activeIndex():model.focusedIndex());return true;}
            default -> {return false;}
        }
        double previous = offset;
        reveal(model.focusedIndex());
        if (offset != previous) onManualScroll.accept(offset - previous);
        return true;
    }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        if(width<=0||height<=0)return;
        var font=Minecraft.getInstance().font;layout(font);
        int current=model.activeIndex();long now=net.minecraft.util.Util.getMillis();
        double frameSeconds=lastFrame==0?0:Math.max(0,(now-lastFrame)/1000d);
        long position=model.positionMillis();
        boolean snap=seekingPreview||previewChanged||lastFrame==0||frameSeconds>.38||preferences.reducedMotion()
                ||LyricFollowMotion.discontinuity(observedPosition,position,frameSeconds)
                ||(current>=0&&observedActive>=0&&Math.abs(current-observedActive)>6);
        LyricInterlude gap=current<0&&model.synchronizedLyrics()&&!model.browsing()&&!completeRowsOnly
                ?LyricInterlude.at(model.lines(),position):null;
        double gapY=gap==null?0:tops[gap.next()]-MusicTypography.LINE_HEIGHT*scale;
        int previewAnchor=seekingPreview||previewChanged?model.previewAnchor():-1;
        double previousOffset=offset;
        if(!model.browsing()&&(current>=0||gap!=null||previewAnchor>=0)){
            double target=clamp((current>=0?tops[current]:gap!=null?gapY:tops[previewAnchor])-height*.24);
            if(snap)followMotion.reset(target);
            offset=clamp(followMotion.step(target,frameSeconds));
        }else followMotion.reset(offset);
        boolean stillRows=immersiveReading&&!seekingPreview
                &&(preferences.reducedMotion()||(!previewChanged&&offset==previousOffset&&followMotion.settled()));
        double framebufferScale=stillRows?Minecraft.getInstance().getWindow().getGuiScale():1d;
        previewChanged=false;observedPosition=position;observedActive=current;lastFrame=now;
        int hovered=isMouseOver(mx,my)?hit(my):-1;
        int ink=ContrastPolicy.readable(primary,0,backdrop,4.5);
        int muted=preferences.highContrast()?ink:ContrastPolicy.readable(secondary,0,backdrop,3);
        g.enableScissor(getX(),getY(),getX()+width,getY()+height);
        try {
            for(int i=0;i<tops.length;i++){
                double y=getY()+tops[i]-offset;if(y+heights[i]<getY()||y>getY()+height)continue;
                boolean reading=i==hovered||isFocused()&&i==model.focusedIndex();
                boolean bright=!model.synchronizedLyrics()||i==current||reading;
                emphasis[i]=LyricPresentation.approach(emphasis[i],bright?1:0,frameSeconds,snap);
                boolean depthShading=model.synchronizedLyrics()&&position>=0&&!completeRowsOnly
                        &&!preferences.reducedMotion()&&!preferences.highContrast();
                double pitch=Math.max(MusicTypography.LINE_HEIGHT*scale,heights[i])+Math.min(10*scale,height*.25);
                float distanceFade=depthShading?LyricWordEffects.distanceInkFade(y,getY()+height*.24,pitch)
                        :LyricPresentation.distanceFade(i-current);
                if(immersiveReading&&depthShading&&!model.browsing()&&current>=0)
                    distanceFade=LyricWordEffects.stageInkFade(distanceFade,y,getY()+height*.24,pitch);
                int subdued=preferences.highContrast()?ink:ContrastPolicy.readable(
                        GuiMotion.lerpColor(ink,secondary,distanceFade),0,backdrop,3);
                int color=GuiMotion.lerpColor(subdued,ink,emphasis[i]);
                var line=model.lines().get(i);
                var timed=timedRows.get(i);
                boolean wordTiming=i==current&&timed!=null&&!preferences.highContrast();
                int pendingInk=LyricPresentation.pendingInk(i==current,muted,subdued);
                if(isFocused()&&i==model.focusedIndex())g.outline(getX()+1,(int)y-3,width-2,(int)Math.min(height,heights[i]+6),ink);
                var parts=wrapped.get(i);
                int first=Math.max(0,(int)Math.floor((getY()-y)/(MusicTypography.LINE_HEIGHT*scale)));
                int last=Math.min(parts.size(),(int)Math.ceil((getY()+height-y)/(MusicTypography.LINE_HEIGHT*scale)));
                boolean sweep=wordTiming&&!preferences.reducedMotion()&&LyricSweepLayout.leftToRight(line.text());
                for(int row=first;row<last;row++){
                    var part=parts.get(row);double rowY=y+row*MusicTypography.LINE_HEIGHT*scale;
                    if(completeRowsOnly&&!completeRowVisible(rowY,MusicTypography.LINE_HEIGHT*scale,getY(),height))continue;
                    float edge=LyricWordEffects.rowOpacity(depthShading&&!model.browsing()&&i!=current&&!reading,
                            rowY-getY()+MusicTypography.LINE_HEIGHT*scale*.5,height,
                            Math.min(height*.22,MusicTypography.LINE_HEIGHT*scale*1.65));
                    if(edge<.035f)continue;
                    double paintY=LyricWordEffects.restingBaseline(rowY,framebufferScale,stillRows);
                    // Render one glyph image. Shifted copies (even cross-faded) double dense strokes.
                    if(sweep){
                        int pending=GuiMotion.lerpColor(color,pendingInk,emphasis[i]);
                        drawTimedRow(g,font,timed.rows().get(row),timed.geometry(),line,
                                position,paintY,opacity(pending,edge),opacity(color,edge),!snap&&!model.browsing());
                    }else {
                        if(wordTiming)part=timedColors(part,line,position,pendingInk,ink);
                        drawPart(g,font,part,paintY,opacity(color,edge));
                    }
                }
            }
            if(gap!=null&&gap.opacity()>.035f){
                double markerY=getY()+gapY-offset+MusicTypography.LINE_HEIGHT*scale*.42;
                if(markerY>=getY()&&markerY<getY()+height)drawInterlude(g,markerY,ink,gap);
            }
            if(model.lines().isEmpty())g.text(font,MusicTypography.text(getMessage()),getX()+8,getY()+12,muted,false);
        } finally {g.disableScissor();}
    }
    private void drawInterlude(GuiGraphicsExtractor g,double y,int ink,LyricInterlude gap){
        int size=Math.max(3,Math.round(scale*2.1f));
        for(int i=0;i<3;i++){
            int x=getX()+8+i*(size+Math.round(scale*2.2f));
            int markerY=(int)Math.round(y);
            float level=preferences.reducedMotion()?1:LyricWordEffects.smooth(gap.progress()*3-i);
            RoundedCorners.draw(g,x,markerY,size,size,size*.5f,0,0,opacity(ink,gap.opacity()*(.32f+.68f*level)));
        }
    }

    private static int opacity(int color,float multiplier){
        return (color&0xffffff)|(Math.max(4,Math.round((color>>>24)*multiplier))<<24);
    }

    private void drawTimedRow(GuiGraphicsExtractor g,Font font,List<LyricGlyphRuns.Run> runs,LyricSweepLayout layout,
                              MusicLyricsModel.Line line,long position,double rowY,int pending,int ink,boolean motion){
        for(var run:runs){
            float fraction=0,width=0;
            if(run.word()>=0){
                var word=line.words().get(run.word());
                fraction=LyricPresentation.wordFraction(position,word.startMillis(),word.endMillis());
                width=layout.width(run.word());
            }
            float lift=motion?LyricWordEffects.lift(fraction,scale):0;
            float verticalScale=LyricWordEffects.verticalScale(fraction,motion);
            float slope=motion&&width>0?LyricWordEffects.rise(fraction,width*scale,scale)/(width*scale):0;
            var text=run.text();int color=fraction>=1?ink:pending;
            if(fraction>0&&fraction<1){
                final float progress=fraction, wordWidth=width;
                text=sink->{
                    for(var glyph:run.glyphs()){
                        float center=run.wordOffset()+glyph.x()-run.x()+glyph.advance()*.5f;
                        float mix=LyricWordEffects.coverage(progress,wordWidth,center,glyph.advance());
                        int rgb=GuiMotion.lerpColor(pending,ink,mix)&0xffffff;
                        if(!sink.accept(glyph.index(),glyph.style().withColor(rgb),glyph.codePoint()))return false;
                    }
                    return true;
                };
            }
            float x=getX()+8+run.x()*scale;
            // Bottom-anchor the expansion; wrapping and hit targets keep their original geometry.
            float baseline=(float)(rowY-lift-slope*run.wordOffset()*scale
                    +MusicTypography.LINE_HEIGHT*scale*(1-verticalScale));
            g.pose().pushMatrix();
            try {
                g.pose().translate(x,baseline);
                if(slope!=0)g.pose().mul(new org.joml.Matrix3x2f(1,-slope,0,1,0,0));
                g.pose().scale(scale,scale*verticalScale);g.text(font,text,0,0,color,false);
            }finally {g.pose().popMatrix();}
        }
    }

    private void drawPart(GuiGraphicsExtractor g,Font font,FormattedCharSequence part,double rowY,int color){
        g.pose().pushMatrix();
        try {g.pose().translate(getX()+8,(float)rowY);g.pose().scale(scale,scale);g.text(font,part,0,0,color,false);}
        finally {g.pose().popMatrix();}
    }
    /** Reduced motion and bidi fallback use instantaneous, timestamp-based whole-word emphasis.
     * They never simulate an unsupported left-to-right mask or invent per-character timestamps. */
    private static FormattedCharSequence timedColors(FormattedCharSequence source,MusicLyricsModel.Line line,
                                                     long position,int muted,int ink){
        return sink->source.accept((index,style,codePoint)->{
            int color=muted;
            int wordIndex=LyricGlyphRuns.wordIndex(style.getInsertion());
            if(wordIndex>=0&&wordIndex<line.words().size()&&position>=line.words().get(wordIndex).startMillis())color=ink;
            return sink.accept(index,style.withColor(color&0xffffff),codePoint);
        });
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){
        int index=model.focusedIndex()>=0?model.focusedIndex():model.activeIndex();
        output.add(NarratedElementType.TITLE,getMessage());
        if(index>=0&&index<model.lines().size())output.add(NarratedElementType.TITLE,Component.literal(model.lines().get(index).text()));
        output.add(NarratedElementType.USAGE,Component.translatable(model.synchronizedLyrics()?"sh_better_ui.music.lyrics.keyboard":"sh_better_ui.music.lyrics.unsynchronized"));
    }
}
