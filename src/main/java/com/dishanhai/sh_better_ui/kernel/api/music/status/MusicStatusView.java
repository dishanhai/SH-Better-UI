// Author: dishanhai. Original content-first status presentation; no bundled third-party artwork.
package com.dishanhai.sh_better_ui.kernel.api.music.status;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicControlRenderer;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextLayout;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTransportControl;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;
import java.util.Objects;

/** Non-interactive, reusable status/empty/error presentation. The owner supplies truthful localized
 * copy and an explicit busy flag, keeps retry/cancel controls outside these bounds, and exposes
 * {@link #narration()} to its screen narrator. No network, timers, progress estimates or actions.
 * For long copy use {@link #requiredHeight} in a scrollable parent; rendering clips to its bounds.
 * Instances cache wrapping. Call {@link #invalidateFontMetrics()} after resource/font reload. */
public final class MusicStatusView {
    private Component title=Component.empty(),detail=Component.empty();
    private boolean busy,dirty=true;
    private MusicTransportControl.Glyph icon;
    private Font measuredFont;
    private Language measuredLanguage;
    private int measuredWidth=-1,textInset,contentHeight;
    private List<FormattedCharSequence> titleLines=List.of(),detailLines=List.of();
    private static volatile long metricsRevision;
    private long measuredRevision=-1;
    public static void invalidateFontMetrics(){metricsRevision++;}
    public void invalidateLayout(){dirty=true;}
    /** Null icon means no illustration. Busy never follows automatically from the wording. */
    public void setContent(Component title,Component detail,boolean busy,MusicTransportControl.Glyph icon){
        Objects.requireNonNull(title);Objects.requireNonNull(detail);
        if(!this.title.equals(title)||!this.detail.equals(detail)||this.busy!=busy||this.icon!=icon){
            this.title=title.copy();this.detail=detail.copy();this.busy=busy;this.icon=icon;dirty=true;
        }
    }
    public Component narration(){return title.copy().append(". ").append(detail);}
    public boolean busy(){return busy;}
    public int requiredHeight(Font font,int width){layout(font,width);return contentHeight;}
    private void layout(Font font,int width){
        var language=Language.getInstance();
        if(!dirty&&measuredFont==font&&measuredLanguage==language&&measuredWidth==width&&measuredRevision==metricsRevision)return;
        textInset=width>=240&&(busy||icon!=null)?32:0;
        int available=Math.max(1,width-16-textInset);
        titleLines=title.equals(Component.empty())?List.of():font.split(MusicTextLayout.bound(MpTypography.bold(title)).text(),available);
        detailLines=detail.equals(Component.empty())?List.of():font.split(MusicTextLayout.bound(MpTypography.text(detail)).text(),available);
        contentHeight=16+titleLines.size()*14+detailLines.size()*14+(!titleLines.isEmpty()&&!detailLines.isEmpty()?6:0);
        if(textInset>0)contentHeight=Math.max(40,contentHeight);
        measuredFont=font;measuredLanguage=language;measuredWidth=width;measuredRevision=metricsRevision;dirty=false;
    }
    public void render(UiFrame frame,Font font,Bounds bounds){
        if(bounds.width()<=0||bounds.height()<=0)return;
        layout(font,bounds.width());
        var g=frame.graphics();var theme=UiTheme.current();
        int primary=theme.text(),secondary=MpPageChrome.muted(theme,frame.preferences());
        int top=bounds.y()+8,x=bounds.x()+8+textInset;
        g.enableScissor(bounds.x(),bounds.y(),bounds.right(),bounds.bottom());
        try {
            if(textInset>0){
                if(busy){
                    for(int i=0;i<3;i++){
                        int phase=frame.preferences().reducedMotion()?0:(int)Math.floorMod(frame.nowMs()/260,3);
                        int color=frame.preferences().reducedMotion()||i==phase?primary:secondary;
                        RoundedCorners.draw(g,bounds.x()+8+i*7,top+9,3,3,1.5f,0,0,color);
                    }
                }else if(icon!=null)MusicControlRenderer.transport(g,new Bounds(bounds.x()+8,top,24,24),icon,false,false,false,true,secondary,frame.preferences());
            }
            int y=top;
            for(var line:titleLines){if(y+12>bounds.bottom())break;g.drawString(font,line,x,y,primary,false);y+=14;}
            if(!titleLines.isEmpty()&&!detailLines.isEmpty())y+=6;
            for(var line:detailLines){if(y+12>bounds.bottom())break;g.drawString(font,line,x,y,secondary,false);y+=14;}
        }finally{g.disableScissor();}
    }
}
