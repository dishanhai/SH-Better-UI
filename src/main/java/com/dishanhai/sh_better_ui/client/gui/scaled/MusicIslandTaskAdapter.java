// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.music.MusicService;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Render-thread projection of real immutable service observations; never starts or retries work. */
public final class MusicIslandTaskAdapter {
    public static final String LYRICS = "music:lyrics", PROVIDER = "music:provider";
    public record Update(String id, String messageKey, String detail, boolean cancel, long ttlMs) {}
    private record Identity(String provider, String session, String track) {}
    private Identity identity;
    private String source;
    private String providerId;
    private long revision = Long.MIN_VALUE;
    private MusicService.LyricsState lyrics;
    private MusicSnapshot.State provider;

    public void reset() { source=null; providerId=null; identity=null; revision=Long.MIN_VALUE; lyrics=null; provider=null; }

    public List<Update> observe(MusicService.Frame frame) {
        var updates=new ArrayList<Update>();
        var snapshot=frame.snapshot();
        var session=snapshot.session();
        String nextSource = snapshot.providerId() + "\u0000" + (session == null ? "" : session.id());
        // Revisions are local to a provider/session, not comparable across source switches.
        boolean sourceChanged = !Objects.equals(source, nextSource);
        if (!sourceChanged && frame.revision()<revision) return List.of();
        var next=session==null||session.track()==null ? null
                : new Identity(snapshot.providerId(),session.id(),session.track().key());
        boolean changed=sourceChanged||!Objects.equals(identity,next)||revision!=frame.revision();
        if(changed) updates.add(new Update(LYRICS,"","",true,0));
        if(sourceChanged||provider!=snapshot.state()) {
            updates.add(new Update(PROVIDER,"","",true,0));
            if(snapshot.state()==MusicSnapshot.State.TEMPORARILY_UNAVAILABLE)
                updates.add(new Update(PROVIDER,"provider_unavailable","",false,5000));
            else if(Objects.equals(providerId, snapshot.providerId()) && provider==MusicSnapshot.State.TEMPORARILY_UNAVAILABLE && snapshot.state()==MusicSnapshot.State.AVAILABLE)
                updates.add(new Update(PROVIDER,"provider_recovered","",false,3500));
        }
        var nextLyrics=frame.lyrics().state();
        String title=session==null||session.track()==null ? "" : session.track().title().orElse("");
        if(next!=null && (changed||nextLyrics!=lyrics)) {
            if(nextLyrics==MusicService.LyricsState.LOADING)
                updates.add(new Update(LYRICS,"lyrics_loading",title,false,8000));
            else if(!changed && lyrics==MusicService.LyricsState.LOADING) {
                updates.add(new Update(LYRICS,"","",true,0));
                if(nextLyrics==MusicService.LyricsState.AVAILABLE || nextLyrics==MusicService.LyricsState.UNAVAILABLE)
                    updates.add(new Update(LYRICS,nextLyrics==MusicService.LyricsState.AVAILABLE?"lyrics_ready":"lyrics_unavailable",title,false,3500));
            }
        }
        providerId=snapshot.providerId();source=nextSource;identity=next;revision=frame.revision();lyrics=nextLyrics;provider=snapshot.state();
        return List.copyOf(updates);
    }
}
