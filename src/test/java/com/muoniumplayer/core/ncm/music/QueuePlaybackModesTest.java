// Author: dishanhai
package com.muoniumplayer.core.ncm.music;

import com.google.gson.Gson;
import com.muoniumplayer.core.ncm.music.dto.Music;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.muoniumplayer.core.ncm.music.CloudMusic.PlayMode.*;
import static org.junit.jupiter.api.Assertions.*;

class QueuePlaybackModesTest {
    @Test void repeatAndShuffleKeepTheirOwnStates() {
        assertEquals(ShuffleOnce, Sequential.withShuffle(true));
        assertEquals(ShuffleAll, ShuffleOnce.nextRepeat());
        assertEquals(ShuffleOne, ShuffleAll.nextRepeat());
        assertEquals(ShuffleOnce, ShuffleOne.nextRepeat());
        assertEquals(LoopInList, ShuffleAll.withShuffle(false));
        assertEquals(LoopSingle, ShuffleOne.withShuffle(false));
        assertEquals(ShuffleOne, Random.nextRepeat());
        assertFalse(ShuffleOnce.repeatsAll());
    }

    @Test void autoplaySkipsMatchingTracksAnywhereInTheCurrentQueue() {
        Music first = track(7);
        Music current = track(8);
        Music fresh = track(9);
        assertSame(fresh, QueueAutoplay.nextUnplayed(List.of(first, current), List.of(track(7), track(8), fresh)));
        assertNull(QueueAutoplay.nextUnplayed(List.of(first, current), List.of(track(7), track(8))));
        assertEquals("Artist", QueueAutoplay.artist(current));
    }

    private static Music track(long id) {
        return new Gson().fromJson("{\"id\":" + id + ",\"ar\":[{\"name\":\"Artist\"}]}", Music.class);
    }
}
