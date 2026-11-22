// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongEncyclopediaTest {
    private static QQMusicSource provider(String info) {
        return new QQMusicSource(new QQFavorites(), (url, body, type, referer, cookies) ->
                JsonParser.parseString("""
                        {"code":0,"req":{"code":0,"data":{"track_info":{"mid":"songA","time_public":"2020-07-21"},
                        "info":%s}}}
                        """.formatted(info)).getAsJsonObject());
    }

    @Test void keepsDistinctGenreNamesAndFallsBackToTheTrackDate() throws Exception {
        var notes = provider("""
                {"genre":{"content":[{"value":"Soul"},{"value":"R&B"},null,{"value":"Soul"}]},
                 "intro":{"content":[{"value":"Liner notes"}]}}
                """).encyclopedia("songA");
        assertEquals("Soul / R&B", notes.genre());
        assertEquals("2020-07-21", notes.songDate());
        assertEquals("Liner notes", notes.introduction());
        assertEquals("", notes.label());
    }

    @Test void rejectsNotesForAnotherSong() {
        assertThrows(IOException.class, () -> provider("{}").encyclopedia("songB"));
    }
}
