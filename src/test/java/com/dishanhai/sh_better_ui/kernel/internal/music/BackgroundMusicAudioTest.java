// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.internal.music.view.OnlineMusicViewImpl;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.URI;
import java.nio.file.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in local audio-device test. Supply a user-authorized or generated >=10-second MP3. */
@EnabledIfEnvironmentVariable(named="SHBUI_BACKGROUND_TEST_MP3", matches=".+")
class BackgroundMusicAudioTest {
    private Object previousRenderThread;
    private java.lang.reflect.Field renderThread;
    @org.junit.jupiter.api.BeforeEach void bindRenderThread() throws Exception {
        renderThread = com.mojang.blaze3d.systems.RenderSystem.class.getDeclaredField("renderThread");
        renderThread.setAccessible(true); previousRenderThread = renderThread.get(null);
        renderThread.set(null, Thread.currentThread());
    }
    @org.junit.jupiter.api.AfterEach void restoreRenderThread() throws Exception {
        renderThread.set(null, previousRenderThread);
    }

    private static void await(BooleanSupplier check) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while(!check.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(20);
        assertTrue(check.getAsBoolean());
    }
    @Test void realMp3OutputAdvancesAfterViewRemovalAndRespondsOnReentry() throws Exception {
        byte[] audio=Files.readAllBytes(Path.of(System.getenv("SHBUI_BACKGROUND_TEST_MP3")));
        var track=new OnlineTrack("local-test","tone","tone","Generated test tone","Test","",20000);
        OnlineMusicSource source=new OnlineMusicSource() {
            public String id(){return "local-test";}
            public List<OnlineTrack> search(String query,int page,int limit){return List.of(track);}
            public Stream resolve(OnlineTrack ignored){return new Stream(URI.create("https://example.invalid/local-test"),0,20000,false);}
            public LyricTimeline lyrics(OnlineTrack ignored){return LyricTimeline.empty();}
        };
        try(var session=new OnlinePlayerSession(List.of(source),Mp3Output::new,uri->audio)) {
            session.volume(.05f); session.play(track);
            await(()->session.snapshot().state()==OnlineMusicPlayer.State.PLAYING);
            new OnlineMusicViewImpl(session,false).close();
            long before=session.snapshot().positionMs();
            await(()->session.snapshot().positionMs()>before+250);
            var reopened=new OnlineMusicViewImpl(session,false);
            reopened.player().pause(); assertEquals(OnlineMusicPlayer.State.PAUSED,session.snapshot().state());
            long paused=session.snapshot().positionMs(); Thread.sleep(250);
            assertTrue(session.snapshot().positionMs()-paused<100);
            reopened.player().resume(); await(()->session.snapshot().positionMs()>paused+250);
            reopened.player().stop(); assertEquals(OnlineMusicPlayer.State.IDLE,session.snapshot().state());
            reopened.close();
        }
    }
}
