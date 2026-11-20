package com.dishanhai.sh_better_ui.kernel.internal.music;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ArtistQueueContextTest {
    static OnlineTrack song(String id){return new OnlineTrack("test",id,id,id,"Artist","",1000);}
    static final class Source implements OnlineMusicSource {
        boolean reject;
        public String id(){return "test";}
        public List<OnlineTrack> search(String q,int page,int limit){return List.of();}
        public Stream resolve(OnlineTrack t){return null;}
        public LyricTimeline lyrics(OnlineTrack t){return LyricTimeline.empty();}
        public void credentials(String cookie){}
        public OnlineMusicSource requestSnapshot(){if(reject)throw new IllegalStateException("unavailable");return this;}
    }
    @Test void rejectedContextKeepsOldQueueAndModeWhileSuccessCommitsSequentialTogether(){
        var source=new Source();
        try(var p=new OnlinePlayerSession(List.of(source))){
            var q=p.queue();var a=song("a");var b=song("b");q.replace(List.of(a,b),0);q.mode(OnlineMusicQueue.Mode.SHUFFLE);
            var before=q.snapshot();source.reject=true;
            assertThrows(IllegalStateException.class,()->q.replace(List.of(b,a),1,OnlineMusicQueue.Mode.SEQUENTIAL));
            assertEquals(before,q.snapshot());
            assertThrows(IllegalArgumentException.class,()->q.replace(List.of(song("bad/id")),0,OnlineMusicQueue.Mode.SEQUENTIAL));
            assertEquals(before,q.snapshot());source.reject=false;
            q.replace(List.of(b,a,b),2,OnlineMusicQueue.Mode.SEQUENTIAL);
            assertEquals(OnlineMusicQueue.Mode.SEQUENTIAL,q.snapshot().mode());assertEquals(2,q.snapshot().index());assertEquals(List.of(b,a,b),q.snapshot().tracks());
        }
    }
}
