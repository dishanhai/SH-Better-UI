// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerQueueTest {
    private static final OnlineTrack A = new OnlineTrack("test","a","a","A","","",1000);
    private static final OnlineTrack B = new OnlineTrack("test","b","b","B","","",1000);
    private static OnlineMusicPlayer player(OnlineMusicQueue persistent, List<OnlineTrack> plays) {
        return (OnlineMusicPlayer) Proxy.newProxyInstance(PlayerQueueTest.class.getClassLoader(),
                new Class<?>[]{OnlineMusicPlayer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("queue")) return persistent;
                    if (method.getName().equals("play")) plays.add((OnlineTrack) args[0]);
                    return null;
                });
    }
    private static OnlineMusicPlayer.Snapshot frame(OnlineMusicPlayer.State state) {
        return new OnlineMusicPlayer.Snapshot(A,state,0,1000,OnlineMusicPlayer.LyricsState.EMPTY,
                LyricTimeline.empty(),OnlineMusicPlayer.Failure.NONE,false);
    }
    @Test void legacyPlayerKeepsLocalQueueAndSuspensionSemantics() {
        var plays = new ArrayList<OnlineTrack>(); var queue = new PlayerQueue(player(null, plays));
        queue.replace(List.of(A,B),0); assertEquals(List.of(A),plays);
        queue.observe(frame(OnlineMusicPlayer.State.PLAYING)); queue.suspend();
        queue.observe(frame(OnlineMusicPlayer.State.ENDED)); assertEquals(List.of(A),plays);
        queue.next(); assertEquals(List.of(A,B),plays);
    }
    @Test void persistentQueueReentryUsesSameSnapshotAndViewNeverDoublePlaysOrAdvances() {
        var calls = new ArrayList<String>();
        OnlineMusicQueue persistent = (OnlineMusicQueue) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicQueue.class}, (proxy, method, args) -> {
                    if (method.getName().equals("snapshot")) return new OnlineMusicQueue.Snapshot(List.of(A,B),1,true);
                    calls.add(method.getName()); return null;
                });
        var plays = new ArrayList<OnlineTrack>(); var player = player(persistent,plays);
        var first = new PlayerQueue(player); first.suspend();
        var reopened = new PlayerQueue(player);
        assertEquals(List.of(A,B),reopened.tracks()); assertEquals(1,reopened.index());
        assertTrue(reopened.canPrevious()); assertFalse(reopened.canNext()); assertTrue(reopened.canUndo());
        reopened.observe(frame(OnlineMusicPlayer.State.PLAYING)); reopened.observe(frame(OnlineMusicPlayer.State.ENDED));
        assertTrue(calls.isEmpty());
        reopened.replace(List.of(A,B),0); reopened.select(1); reopened.next(); reopened.previous();
        reopened.remove(0); reopened.clear(); reopened.undo();
        assertEquals(List.of("replace","select","next","previous","remove","clear","undo"),calls);
        assertTrue(plays.isEmpty());
    }
}
