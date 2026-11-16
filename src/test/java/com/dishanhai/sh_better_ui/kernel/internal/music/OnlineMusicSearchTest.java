// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnlineMusicSearchTest {
    private static OnlineTrack track(String source, String id, String title, String artist, String album) {
        return new OnlineTrack(source, id, id, title, artist, album, 0);
    }

    @Test void matchesTitlesArtistsAndAlbumsByChineseAndPinyin() {
        var music = new OnlineMusicSearch();
        var song = track("netease", "1", "晴天", "周杰伦", "叶惠美");
        var another = track("qq", "2", "晴天", "周杰伦", "叶惠美");
        music.addAll(List.of(song, another));
        for (var query : List.of("晴天", "QINGTIAN", "qíng tiān", "qt", "zjl", "yehuimei")) {
            assertEquals(List.of(song), music.search("netease", query, 10), query);
        }
        assertEquals(List.of("晴天"), music.suggest("netease", "qingtian", 10));
        assertEquals(List.of("晴天"), music.suggest("netease", "zjl", 10));
        assertEquals(List.of(), music.search("netease", "  ", 10));
        assertEquals(List.of(), music.suggest("netease", "qt", 0));
    }

    @Test void boundsTracksByIdentityAndDeduplicatesSuggestionsBeforeLimit() {
        var music = new OnlineMusicSearch(3);
        var old = track("netease", "1", "晴天", "甲", "");
        var duplicateTitle = track("netease", "2", "晴天", "乙", "");
        var second = track("netease", "3", "晴空", "丙", "");
        music.addAll(List.of(old, duplicateTitle, second));
        assertEquals(List.of("晴天", "晴空"), music.suggest("netease", "晴", 2));
        var refreshed = track("netease", "1", "晴朗", "甲", "");
        music.addAll(List.of(refreshed, track("qq", "4", "其他", "", "")));
        assertEquals(List.of(second, refreshed), music.search("netease", "晴", 10));
        music.clear("netease");
        assertEquals(List.of(), music.search("netease", "晴", 10));
        assertEquals(1, music.search("qq", "其他", 10).size());
        music.clear();
        assertEquals(List.of(), music.search("qq", "其他", 10));
        assertThrows(IllegalArgumentException.class, () -> new OnlineMusicSearch(0));
    }
}
