// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSearchHistoryTest {
    @TempDir Path directory;

    @Test void reloadKeepsRecentUniqueQueriesScopedBySourceAndMode() {
        Path file = directory.resolve("search-history.json");
        var history = new MusicSearchHistory(file);
        history.record("QQ:songs", "夜曲");
        history.record("QQ:songs", " Halo ");
        history.record("NETEASE:songs", "Halo");
        history.record("NETEASE:playlists", "爵士");
        history.record("QQ:songs", "HALO");
        history.record("QQ:songs", "  ");

        var reopened = new MusicSearchHistory(file);
        assertEquals(List.of("HALO", "夜曲"), reopened.recent("QQ:songs"));
        assertEquals(List.of("Halo"), reopened.recent("NETEASE:songs"));
        assertEquals(List.of("爵士"), reopened.recent("NETEASE:playlists"));
        assertTrue(reopened.recent("QQ:playlists").isEmpty());
    }

    @Test void historyLimitAndClearUndoSurviveReload() {
        Path file = directory.resolve("search-history.json");
        var history = new MusicSearchHistory(file);
        for (int i = 0; i < 12; i++) history.record("QQ:songs", "query " + i);
        var removed = history.clear("QQ:songs");
        assertEquals(MusicSearchHistory.PER_SCOPE_LIMIT, removed.size());
        assertEquals("query 11", removed.getFirst());
        assertEquals("query 4", removed.getLast());
        assertTrue(new MusicSearchHistory(file).recent("QQ:songs").isEmpty());

        history.restore("QQ:songs", removed);
        assertEquals(removed, new MusicSearchHistory(file).recent("QQ:songs"));
    }

    @Test void malformedEntriesDoNotHideValidSavedQueries() throws Exception {
        Path file = directory.resolve("search-history.json");
        Files.writeString(file, """
                [{"scope":"QQ:songs","query":[]},null,{"scope":"QQ:songs","query":"夜曲"}]
                """);
        assertEquals(List.of("夜曲"), new MusicSearchHistory(file).recent("QQ:songs"));
    }
}
