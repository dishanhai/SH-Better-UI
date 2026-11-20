// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.*;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Artist identity and this provider-search page only; never presents matches as a popularity chart. */
final class ArtistDetailPanel {
    final ArtistDetailModel model;
    private final List<MusicTextControl> rows=new ArrayList<>(),buttons=new ArrayList<>();
    private MusicTransportControl play;
    private MusicTextControl previous,next,retry;
    private OnlineMusicScrollRegion scroll;
    private PreviewContext host;
    private Bounds area,heroArea;
    private int hero;
    private int first;
    private boolean visible;
    ArtistDetailPanel(OnlineMusicPlayer player){model=new ArtistDetailModel(player);}
    private static Component label(String key,Object...args){return MusicTypography.text(Component.translatable("sh_better_ui.online_music.artist."+key,args));}
    void build(PreviewContext host,OnlineMusicLayout layout){
        this.host=host;area=layout.content();rows.clear();buttons.clear();
        hero=area.height()<110?0:Math.min(64,Math.max(36,area.height()/3));
        heroArea=hero==0?layout.sourceBar():new Bounds(area.x(),area.y(),area.width(),hero-4);
        int diameter=Math.min(44,Math.max(20,heroArea.height()-8));
        play=host.add(new MusicTransportControl(new Bounds(heroArea.right()-diameter-4,heroArea.y()+2,diameter,diameter),label("play"),MusicTransportControl.Glyph.PLAY,host.preferences(),()->play(0)));
        int count=Math.max(0,(area.height()-hero-28)/30);
        for(int i=0;i<count;i++){
            int slot=i;
            var row=host.add(new MusicTextControl(new Bounds(area.x()+6,area.y()+hero+i*30,Math.max(1,area.width()-24),28),Component.empty(),host.preferences(),()->play(first+slot)));
            row.setVariant(MusicTextControl.Variant.ROW); rows.add(row);
        }
        scroll=host.add(new OnlineMusicScrollRegion(area.x(),area.y()+hero,area.width(),Math.max(1,count*30),label("songs"),value->{first=value;refresh();}));
        int y=area.bottom()-24;
        previous=button(new Bounds(area.x()+6,y,56,22),"previous",()->load(model.page-1));
        retry=button(new Bounds(area.x()+66,y,Math.max(42,Math.min(70,area.width()-138)),22),"retry",()->load(model.page));
        next=button(new Bounds(area.right()-62,y,56,22),"next",()->load(model.page+1));
        refresh();
    }
    private MusicTextControl button(Bounds b,String text,Runnable action){var w=host.add(new MusicTextControl(b,label(text),host.preferences(),action));buttons.add(w);return w;}
    void open(OnlineArtist artist){first=0;model.open(artist);refresh();}
    private void load(int page){first=0;model.load(page);refresh();}
    private void play(int index){try{model.play(index);model.status="ready";}catch(RuntimeException e){model.status="play_failed";}refresh();}
    void visible(boolean value){visible=value;if(!value&&scroll!=null)scroll.cancelDrag();refresh();}
    void cancel(){model.cancel();visible(false);}
    private void refresh(){
        if(play==null)return;
        first=Math.max(0,Math.min(first,Math.max(0,model.tracks.size()-rows.size())));
        play.visible=visible;play.setAvailable(visible&&!model.tracks.isEmpty()&&!model.loading()&&model.player.queue()!=null&&model.player.queue().supportsModeReplacement());
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);int index=first+i;row.visible=visible&&!model.status.equals("play_failed")&&index<model.tracks.size();row.active=row.visible&&!model.loading()&&model.player.queue()!=null&&model.player.queue().supportsModeReplacement();
            if(row.visible){var track=model.tracks.get(index);row.setMessage(MusicTypography.text((index+1)+"  "+track.title()));row.setSecondary(MusicTypography.text(track.artist()+" · "+track.album()));row.setPreview(label("play_selected",track.title()));}
            else row.cancelInteraction();
        }
        for(var b:buttons){b.visible=visible;if(!visible)b.cancelInteraction();}
        previous.active=visible&&!model.loading()&&model.page>1;
        next.active=visible&&!model.loading()&&model.more;
        retry.active=visible&&!model.loading();
        scroll.visible=visible&&!rows.isEmpty()&&model.tracks.size()>rows.size();scroll.configure(first,model.tracks.size(),Math.max(1,rows.size()));
    }
    void render(UiFrame ui){
        model.poll();refresh();if(!visible)return;
        var theme=UiTheme.current();
        OnlineMusicViewPaint.panel(ui,heroArea,GuiMotion.lerpColor(theme.background()|0xff000000,theme.accent()|0xff000000,ui.preferences().highContrast()?0:.10f),false);
        OnlineMusicViewPaint.line(ui,host.font(),MusicTypography.bold(model.artist==null?"":model.artist.name()),heroArea.x()+6,heroArea.y()+(heroArea.height()<36?1:6),Math.max(1,heroArea.width()-64),theme.text());
        OnlineMusicViewPaint.line(ui,host.font(),label(heroArea.height()<36?"short_scope":"scope",model.artist==null?"":model.artist.source(),model.page),heroArea.x()+6,heroArea.y()+(heroArea.height()<36?12:24),Math.max(1,heroArea.width()-64),theme.text());
        if(model.tracks.isEmpty()||model.status.equals("play_failed"))OnlineMusicViewPaint.paragraph(ui,host.font(),label(model.status),new Bounds(area.x()+10,area.y()+hero+6,area.width()-20,Math.max(0,area.height()-hero-34)),theme.text());
    }
}
