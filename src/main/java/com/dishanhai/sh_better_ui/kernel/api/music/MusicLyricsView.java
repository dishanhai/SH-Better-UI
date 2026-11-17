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
    private List<LyricSweepLayout> sweepLayouts=List.of();
    private double[] tops=new double[0],heights=new double[0];
    private float[] emphasis=new float[0];
    private static final String WORD_TAG="uuk-word:";
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
        var sweeps=new ArrayList<LyricSweepLayout>(model.lines().size());
        tops=new double[model.lines().size()];heights=new double[tops.length];
        emphasis=new float[tops.length];
        double y=height*.24;
        for(int i=0;i<tops.length;i++){
            var line=model.lines().get(i);
            var text=line.text();
            var styled=MpTypography.bold(Component.literal(text.isEmpty()?" ":text));
            if(line.hasWordTiming()){
                styled=Component.empty();
                for(int word=0;word<line.words().size();word++){
                    int wordIndex=word;
                    styled.append(MpTypography.bold(Component.literal(line.words().get(word).text()))
                            .withStyle(style->style.withInsertion(WORD_TAG+wordIndex)));
                }
            }
            var parts=font.split(styled,Math.max(1,(int)((width-16)/scale)));
            lines.add(parts);
            var glyphRows=new ArrayList<List<LyricSweepLayout.Glyph>>();
            for(var part:parts){
                var glyphs=new ArrayList<LyricSweepLayout.Glyph>();
                part.accept((index,style,cp)->{
                    float advance=font.getSplitter().stringWidth(FormattedCharSequence.codepoint(cp,style));
                    glyphs.add(new LyricSweepLayout.Glyph(wordIndex(style.getInsertion()),advance));return true;
                });
                glyphRows.add(glyphs);
            }
            sweeps.add(LyricSweepLayout.measure(glyphRows,line.words().size()));
            tops[i]=y;heights[i]=Math.max(1,parts.size())*MpTypography.LINE_HEIGHT*scale;
            // Tiny viewports must not scroll through a whole screen of inter-line whitespace.
            y+=heights[i]+Math.min(10*scale,height*.25);
        }
        // End the scroll range at real text, not at the final decorative line gap.
        // A short final line stays near the reading anchor; a wrapped final paragraph
        // can still be scrolled to its last baseline.
        int last=tops.length-1;
        totalHeight=last<0?height:Math.max(tops[last]+height*.76,
                tops[last]+heights[last]+Math.min(height*.24,MpTypography.LINE_HEIGHT*scale));
        wrapped=List.copyOf(lines);sweepLayouts=List.copyOf(sweeps);layoutFont=font;layoutLanguage=language;
        measuredRevision=fontMetricsRevision;layoutDirty=false;offset=clamp(offset);
        if(isFocused()&&model.focusedIndex()>=0)reveal(model.focusedIndex());
    }
    private void reveal(int index){if(index>=0&&index<tops.length)offset=clamp(tops[index]-height*.24);}
    private int hit(double y){double local=y-getY()+offset;for(int i=0;i<tops.length;i++)if(local>=tops[i]&&local<tops[i]+heights[i])return i;return -1;}
    private void activate(int index){model.focus(index);model.seekTarget(index).ifPresent(seek);}
    @Override public void onClick(double x,double y){layout(Minecraft.getInstance().font);int hit=hit(y);if(hit>=0)activate(hit);}
    public boolean mouseScrolled(double x,double y,double amount){
        if(!visible||!active||!isMouseOver(x,y)||amount==0||!Double.isFinite(amount))return false;
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
        int current=model.activeIndex();long now=PageTransition.nowMs();
        double frameSeconds=lastFrame==0?0:Math.max(0,Math.min(.05,(now-lastFrame)/1000d));
        if(!model.browsing()&&current>=0){
            double target=clamp(tops[current]-height*.24);
            double dt=lastFrame==0?1:Math.max(0,Math.min(.05,(now-lastFrame)/1000d));
            if(preferences.reducedMotion()||lastFrame==0||Math.abs(current-observedActive)>6)offset=target;
            else offset+=(target-offset)*(1-Math.exp(-12*dt));
        }
        boolean firstFrame=lastFrame==0;
        observedActive=current;lastFrame=now;
        int hovered=isMouseOver(mx,my)?hit(my):-1;
        int ink=ContrastPolicy.readable(primary,0,backdrop,4.5);
        int muted=preferences.highContrast()?ink:ContrastPolicy.readable(secondary,0,backdrop,3);
        g.enableScissor(getX(),getY(),getX()+width,getY()+height);
        try {
            for(int i=0;i<tops.length;i++){
                double y=getY()+tops[i]-offset;if(y+heights[i]<getY()||y>getY()+height)continue;
                boolean bright=!model.synchronizedLyrics()||i==current||(i==hovered&&model.seekTarget(i).isPresent());
                emphasis[i]=LyricPresentation.approach(emphasis[i],bright?1:0,frameSeconds,preferences.reducedMotion()||firstFrame);
                int subdued=preferences.highContrast()?ink:ContrastPolicy.readable(
                        GuiMotion.lerpColor(ink,secondary,LyricPresentation.distanceFade(i-current)),0,backdrop,3);
                int color=GuiMotion.lerpColor(subdued,ink,emphasis[i]);
                var line=model.lines().get(i);
                boolean wordTiming=i==current&&line.hasWordTiming()&&!preferences.highContrast();
                int pendingInk=LyricPresentation.pendingInk(i==current,muted,subdued);
                if(isFocused()&&i==model.focusedIndex())g.renderOutline(getX()+1,(int)y-3,width-2,(int)Math.min(height,heights[i]+6),ink);
                var parts=wrapped.get(i);
                int first=Math.max(0,(int)Math.floor((getY()-y)/(MpTypography.LINE_HEIGHT*scale)));
                int last=Math.min(parts.size(),(int)Math.ceil((getY()+height-y)/(MpTypography.LINE_HEIGHT*scale)));
                boolean sweep=wordTiming&&!preferences.reducedMotion()&&LyricSweepLayout.leftToRight(line.text());
                for(int row=first;row<last;row++){
                    var part=parts.get(row);double rowY=y+row*MpTypography.LINE_HEIGHT*scale;
                    if(!sweep){
                        if(wordTiming)part=timedColors(part,line,model.positionMillis(),pendingInk,ink);
                        boolean reading=i==hovered||isFocused()&&i==model.focusedIndex()||model.browsing();
                        if(LyricWordEffects.blurFuture(model.positionMillis(),line.startMillis(),i==current,reading,
                                preferences.reducedMotion(),preferences.highContrast()))drawSoftPart(g,font,part,rowY,color);
                        else drawPart(g,font,part,rowY,color);
                        continue;
                    }
                    var sweepLayout=sweepLayouts.get(i);
                    int cursor=getX();
                    // Disjoint gray/bright masks: no stationary dark copy remains beneath lifted ink.
                    for(var span:sweepLayout.row(row)){
                        var word=line.words().get(span.word());
                        float fraction=LyricPresentation.wordFraction(model.positionMillis(),word.startMillis(),word.endMillis());
                        float reveal=sweepLayout.revealed(span,fraction);
                        if(reveal<=0)continue;
                        int left=Math.max(cursor,(int)Math.floor(getX()+8+span.x()*scale));
                        int right=(int)Math.ceil(getX()+8+(span.x()+reveal)*scale);
                        if(left>cursor)drawClippedPart(g,font,part,rowY,pendingInk,cursor,left);
                        if(right>left)drawLiftedPart(g,font,part,rowY,ink,left,right,span.x()*scale,
                                span.width()*scale,fraction);
                        cursor=Math.max(cursor,right);
                    }
                    if(cursor<getX()+width)drawClippedPart(g,font,part,rowY,pendingInk,cursor,getX()+width);
                }
            }
            if(model.lines().isEmpty())g.drawString(font,MpTypography.text(getMessage()),getX()+8,getY()+12,muted,false);
        } finally {g.disableScissor();}
    }
    /** A bounded 3x3 spatial kernel softens only future rows. No sharp center layer is retained.
     * Source-over compositing is a small-radius blur approximation, not a Gaussian FBO pipeline. */
    private void drawSoftPart(GuiGraphics g,Font font,FormattedCharSequence part,double rowY,int color){
        float radius=Math.min(.8f,scale*.35f);
        for(int y=0;y<3;y++)for(int x=0;x<3;x++){
            int alpha=Math.max(5,Math.round((color>>>24)*LyricWordEffects.WEIGHTS[x]*LyricWordEffects.WEIGHTS[y]));
            g.pose().pushPose();
            try {
                g.pose().translate(LyricWordEffects.OFFSETS[x]*radius,LyricWordEffects.OFFSETS[y]*radius,0);
                drawPart(g,font,part,rowY,(color&0xffffff)|(alpha<<24));
            }finally {g.pose().popPose();}
        }
    }
    private void drawLiftedPart(GuiGraphics g,Font font,FormattedCharSequence part,double rowY,int color,
                                int left,int right,float spanX,float spanWidth,float fraction){
        float lift=LyricWordEffects.lift(fraction,preferences.reducedMotion());
        float slope=spanWidth>0?LyricWordEffects.rise(fraction,spanWidth,preferences.reducedMotion())/spanWidth:0;
        // x is unchanged by the shear, so the real-time word reveal remains exactly aligned.
        g.enableScissor(left,(int)Math.floor(rowY)-3,right,(int)Math.ceil(rowY+MpTypography.LINE_HEIGHT*scale)+2);
        g.pose().pushPose();
        try {
            g.pose().translate(getX()+8,rowY-lift+slope*spanX,0);
            g.pose().last().pose().mul(new org.joml.Matrix4f().m01(-slope));
            g.pose().scale(scale,scale,1);g.drawString(font,part,0,0,color,false);
        }finally {g.pose().popPose();g.disableScissor();}
    }
    private void drawClippedPart(GuiGraphics g,Font font,FormattedCharSequence part,double rowY,int color,int left,int right){
        // Outside the text pose: same coordinate space as the viewport scissor, nested intersection.
        g.enableScissor(left,(int)Math.floor(rowY)-1,right,(int)Math.ceil(rowY+MpTypography.LINE_HEIGHT*scale)+1);
        try {drawPart(g,font,part,rowY,color);} finally {g.disableScissor();}
    }
    private void drawPart(GuiGraphics g,Font font,FormattedCharSequence part,double rowY,int color){
        g.pose().pushPose();
        try {g.pose().translate(getX()+8,rowY,0);g.pose().scale(scale,scale,1);g.drawString(font,part,0,0,color,false);}
        finally {g.pose().popPose();}
    }
    private static int wordIndex(String tag){
        if(tag==null||!tag.startsWith(WORD_TAG))return -1;
        try{return Integer.parseInt(tag.substring(WORD_TAG.length()));}catch(NumberFormatException ignored){return -1;}
    }
    /** Reduced motion and bidi fallback use instantaneous, timestamp-based whole-word emphasis.
     * They never simulate an unsupported left-to-right mask or invent per-character timestamps. */
    private static FormattedCharSequence timedColors(FormattedCharSequence source,MusicLyricsModel.Line line,
                                                     long position,int muted,int ink){
        return sink->source.accept((index,style,codePoint)->{
            int color=muted;String tag=style.getInsertion();
            if(tag!=null&&tag.startsWith(WORD_TAG)){
                try {
                    int wordIndex=Integer.parseInt(tag.substring(WORD_TAG.length()));
                    if(wordIndex>=0&&wordIndex<line.words().size()){
                        var word=line.words().get(wordIndex);
                        color=position>=word.startMillis()?ink:muted;
                    }
                }catch(NumberFormatException ignored) { /* foreign styles use the untimed color */ }
            }
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
