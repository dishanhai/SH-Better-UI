// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** UI-thread observed, one explicit page request at a time. Never implicitly paginates or plays. */
final class ArtistDetailModel {
    final OnlineMusicPlayer player;
    OnlineArtist artist;
    int page=1;
    String status="empty";
    List<OnlineTrack> tracks=List.of();
    boolean more;
    private CompletableFuture<OnlineArtistTracks> pending;
    ArtistDetailModel(OnlineMusicPlayer player){this.player=player;}
    void open(OnlineArtist artist){this.artist=Objects.requireNonNull(artist);load(1);}
    void load(int page){
        cancel();this.page=Math.max(1,page);tracks=List.of();more=false;
        if(artist==null||!player.supportsArtistTracks(artist.source())){status="unavailable";return;}
        status="loading";
        try { pending=player.artistTracks(artist,this.page); }
        catch(RuntimeException e){status="failed";}
    }
    void poll(){
        if(pending==null||!pending.isDone())return;
        var request=pending;pending=null;
        try{var result=request.join();tracks=result.tracks();more=result.mayHaveMore();status=tracks.isEmpty()?"empty":"ready";}
        catch(RuntimeException e){status="failed";}
    }
    boolean loading(){return pending!=null;}
    void cancel(){if(pending!=null){pending.cancel(true);pending=null;status="cancelled";}}
    void play(int index){
        if(index<0||index>=tracks.size()||loading())return;
        var queue=player.queue();
        if(queue==null||!queue.supportsModeReplacement())throw new UnsupportedOperationException("Artist context playback unavailable");
        queue.replace(tracks,index,OnlineMusicQueue.Mode.SEQUENTIAL);
    }
}
