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
    private boolean extending;
    private int requestPage;
    private CompletableFuture<OnlineArtistTracks> pending;
    ArtistDetailModel(OnlineMusicPlayer player){this.player=player;}
    void open(OnlineArtist artist){this.artist=Objects.requireNonNull(artist);load(1);}
    void load(int page){
        cancel();this.page=Math.max(1,page);tracks=List.of();more=false;
        fetch(this.page, false);
    }
    void extend() { if (more && !loading() && page < 1000) fetch(page + 1, true); }
    void retry() { if (!loading()) fetch(requestPage, extending); }
    private void fetch(int page, boolean append) {
        extending = append;
        requestPage = page;
        if(artist==null||!player.supportsArtistTracks(artist.source())){status="unavailable";return;}
        status="loading";
        try { pending=player.artistTracks(artist,this.page); }
        catch(RuntimeException e){more=false;status="failed";}
    }
    void poll(){
        if(pending==null||!pending.isDone())return;
        var request=pending;pending=null;
        try {
            var result = request.join();
            tracks = extending ? MusicPageAppend.append(tracks, result.tracks()) : result.tracks();
            page = requestPage; more = result.mayHaveMore();
            status = tracks.isEmpty() ? "empty" : "ready";
        }
        catch(RuntimeException e){more=false;status="failed";}
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
