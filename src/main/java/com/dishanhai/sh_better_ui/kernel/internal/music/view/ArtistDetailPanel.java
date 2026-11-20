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
    private MusicTextControl retry;
    private OnlineMusicScrollRegion scroll;
    private PreviewContext host;
    private Bounds area,heroArea,list;
    private int capacity;
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
        capacity = Math.max(0,(area.height()-hero-28)/30);
        list = new Bounds(area.x(), area.y()+hero, area.width(), capacity*30);
        scroll=host.add(new OnlineMusicScrollRegion(list.x(),list.y(),list.width(),list.height(),label("songs"),value->{rows.forEach(MusicTextControl::cancelInteraction);first=value;refresh();}));
        scroll.setOnBrowse(() -> host.screen().setFocused(scroll));
        scroll.onEnd(() -> visible && !model.loading() && model.more && model.page < 1000,
                () -> { model.extend(); refresh(); });
        int spill = MusicScrollRows.spill(host, capacity);
        if (spill > 0) scroll.pixelRows(30, shift -> refresh());
        for(int i=0;i<capacity+spill;i++){
            int slot=i;
            var row=MusicScrollRows.add(host, new MusicTextControl(
                    new Bounds(area.x()+6,area.y()+hero+i*30,Math.max(1,area.width()-24),28),
                    Component.empty(),host.preferences(),()-> { if (rows.get(slot).visible) play(first+slot); }), () -> list);
            row.setVariant(MusicTextControl.Variant.ROW); rows.add(row);
        }
        int y=area.bottom()-24;
        retry=button(new Bounds(area.x()+6,y,72,22),"retry",()->{model.retry();refresh();});
        refresh();
    }
    private MusicTextControl button(Bounds b,String text,Runnable action){var w=host.add(new MusicTextControl(b,label(text),host.preferences(),action));buttons.add(w);return w;}
    void open(OnlineArtist artist){first=0;model.open(artist);refresh();}
    private void play(int index){try{model.play(index);model.status="ready";}catch(RuntimeException e){model.status="play_failed";}refresh();}
    void visible(boolean value){visible=value;if(!value&&scroll!=null)scroll.cancelDrag();refresh();}
    void cancel(){model.cancel();visible(false);}
    private void refresh(){
        if(play==null)return;
        first=Math.max(0,Math.min(first,Math.max(0,model.tracks.size()-capacity)));
        play.visible=visible;play.setAvailable(visible&&!model.tracks.isEmpty()&&!model.loading()&&model.player.queue()!=null&&model.player.queue().supportsModeReplacement());
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);int index=first+i;
            int y=list.y()+i*30-scroll.rowShift();
            row.setY(y);
            row.setVisible(visible&&!model.status.equals("play_failed")&&index<model.tracks.size()&&MusicScrollRows.intersects(y,28,list));
            row.active=row.visible&&!model.loading()&&model.player.queue()!=null&&model.player.queue().supportsModeReplacement();
            if(row.visible){var track=model.tracks.get(index);row.setMessage(MusicTypography.text((index+1)+"  "+track.title()));row.setSecondary(MusicTypography.text(track.artist()+" · "+track.album()));row.setPreview(label("play_selected",track.title()));}
            else row.cancelInteraction();
        }
        for(var b:buttons){b.visible=visible;if(!visible)b.cancelInteraction();}
        retry.setVisible(visible && model.status.equals("failed"));
        retry.setAvailable(retry.visible);
        scroll.visible=visible&&!rows.isEmpty();scroll.configure(first,model.tracks.size(),capacity);
    }
    void render(UiFrame ui){
        model.poll();refresh();if(!visible)return;
        scroll.tick();
        var theme=UiTheme.current();
        OnlineMusicViewPaint.panel(ui,heroArea,GuiMotion.lerpColor(theme.background()|0xff000000,theme.accent()|0xff000000,ui.preferences().highContrast()?0:.10f),false);
        OnlineMusicViewPaint.line(ui,host.font(),MusicTypography.bold(model.artist==null?"":model.artist.name()),heroArea.x()+6,heroArea.y()+(heroArea.height()<36?1:6),Math.max(1,heroArea.width()-64),theme.text());
        OnlineMusicViewPaint.line(ui,host.font(),label(heroArea.height()<36?"short_scope":"scope",model.artist==null?"":model.artist.source(),model.page),heroArea.x()+6,heroArea.y()+(heroArea.height()<36?12:24),Math.max(1,heroArea.width()-64),theme.text());
        if(model.tracks.isEmpty()||model.status.equals("play_failed"))OnlineMusicViewPaint.paragraph(ui,host.font(),label(model.status),new Bounds(area.x()+10,area.y()+hero+6,area.width()-20,Math.max(0,area.height()-hero-34)),theme.text());
    }
}
