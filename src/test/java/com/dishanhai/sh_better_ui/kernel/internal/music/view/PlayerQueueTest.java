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
    @Test void editingDelegatesOnlyWhenTheBackendAdvertisesSupport() {
        var plays = new ArrayList<OnlineTrack>(); var calls = new ArrayList<String>();
        var supported = new boolean[]{true};
        OnlineMusicQueue persistent = (OnlineMusicQueue) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicQueue.class}, (proxy, method, args) -> {
                    if (method.getName().equals("supportsEditing")) return supported[0];
                    calls.add(method.getName()); return null;
                });
        var queue = new PlayerQueue(player(persistent, plays));
        assertTrue(queue.supportsEditing()); queue.append(A); queue.move(0, 1);
        assertEquals(List.of("append", "move"), calls); assertTrue(plays.isEmpty());
        supported[0] = false; queue.append(B); queue.move(1, 0);
        assertFalse(queue.supportsEditing()); assertEquals(2, calls.size());
        var legacy = new PlayerQueue(player(null, plays));
        assertFalse(legacy.supportsEditing()); legacy.append(A); legacy.move(0,1); assertTrue(plays.isEmpty());
    }
    @Test void legacyPlayerKeepsLocalQueueAndSuspensionSemantics() {
        var plays = new ArrayList<OnlineTrack>(); var queue = new PlayerQueue(player(null, plays));
        queue.replace(List.of(A,B),0); assertEquals(List.of(A),plays);
        queue.observe(frame(OnlineMusicPlayer.State.PLAYING)); queue.suspend();
        queue.observe(frame(OnlineMusicPlayer.State.ENDED)); assertEquals(List.of(A),plays);
        queue.next(); assertEquals(List.of(A,B),plays);
    }
    @Test void explicitSequentialStartKeepsDuplicatePositionAndReplacesShuffleAtomically() {
        var calls = new ArrayList<List<Object>>();
        var items = List.of(A, B, A);
        var persistent = (OnlineMusicQueue) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OnlineMusicQueue.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "supportsModeReplacement" -> true;
                        case "snapshot" -> new OnlineMusicQueue.Snapshot(items, 0, false,
                                OnlineMusicQueue.Mode.SHUFFLE, false, true);
                        case "replace" -> { calls.add(List.of(args)); yield null; }
                        default -> throw new AssertionError("Unexpected queue call: " + method.getName());
                    };
                });
        var plays = new ArrayList<OnlineTrack>();
        new PlayerQueue(player(persistent, plays)).replaceSequential(items, 2);
        assertEquals(List.of(List.of(items, 2, OnlineMusicQueue.Mode.SEQUENTIAL)), calls);
        assertTrue(plays.isEmpty());
        new PlayerQueue(player(null, plays)).replaceSequential(items, 1);
        assertEquals(List.of(B), plays);
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
