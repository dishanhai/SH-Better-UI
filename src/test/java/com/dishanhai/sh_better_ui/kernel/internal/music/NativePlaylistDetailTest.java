// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativePlaylistDetailTest {
    @Test void qqRequiresExistingSessionAndPreservesProviderPositionsAcrossPages() throws Exception {
        var offsets = new ArrayList<Integer>();
        var qq = qq((offset, limit) -> {
            offsets.add(offset);
            assertEquals(100, limit);
            return qqPage(102, offset == 0 ? java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> "id" + i).toList() : List.of("id0", "next"));
        });
        assertThrows(OnlineMusicSource.LoginRequired.class, () -> qq.playlistDetail("42"));
        qq.credentials("uin=42; qm_keyst=fixture");
        var detail = qq.playlistDetail("42");
        assertEquals(List.of(0, 100), offsets);
        assertEquals(102, detail.playlist().trackCount());
        assertEquals(List.of("id99", "id0", "next"), detail.tracks().subList(99, 102).stream().map(OnlineTrack::id).toList());
    }

    @Test void qqRejectsMissingAndRepeatedPagesWithoutOfferingPartialTracks() {
        for (int count : new int[]{100, 101, 200, 1001}) {
            var qq = qq((offset, limit) -> qqPage(count == 100 ? 101 : count,
                    count == 101 && offset == 100 ? List.of() : java.util.stream.IntStream.range(0, 100)
                            .mapToObj(i -> "id" + i).toList()));
            qq.credentials("uin=42; qm_keyst=fixture");
            assertThrows(IOException.class, () -> qq.playlistDetail("42"));
        }
    }

    @Test void qqNeverAcceptsConflictingEchoedIdentities() {
        var qq = qq((offset, limit) -> {
            var data = qqPage(1, List.of("one"));
            data.getAsJsonObject("dirinfo").addProperty("tid", 43);
            return data;
        });
        qq.credentials("uin=42; qm_keyst=fixture");
        assertThrows(IOException.class, () -> qq.playlistDetail("42"));
    }

    @Test void neteaseRestoresOriginalOrderAndRepeatedIdsFromUnorderedSongDetails() throws Exception {
        var calls = new ArrayList<String>();
        var source = new NeteaseSource((url, payload, type, referer, cookie) -> {
            assertTrue(cookie.contains("MUSIC_U=fixture"));
            calls.add(url);
            if (url.contains("/playlist/detail")) return json("{\"code\":200,\"playlist\":{\"id\":42,\"name\":\"List\",\"trackCount\":3,\"trackIds\":[{\"id\":6},{\"id\":9},{\"id\":6}]}}");
            assertTrue(url.contains("/song/detail"));
            return json("{\"code\":200,\"songs\":[{\"id\":9,\"name\":\"Second\"},{\"id\":6,\"name\":\"First\"}]}");
        });
        source.credentials("MUSIC_U=fixture");
        var detail = source.playlistDetail("42");
        assertEquals(List.of("6", "9", "6"), detail.tracks().stream().map(OnlineTrack::id).toList());
        assertEquals(2, calls.size());
    }

    @Test void neteaseRejectsIncompleteSnapshotOrMissingMetadata() {
        for (String count : List.of("1", "2")) {
            var source = new NeteaseSource((url, payload, type, referer, cookie) -> url.contains("/playlist/detail")
                    ? json("{\"code\":200,\"playlist\":{\"id\":42,\"trackCount\":" + count + ",\"trackIds\":[{\"id\":6}]}}")
                    : json("{\"code\":200,\"songs\":[]}"));
            source.credentials("MUSIC_U=fixture");
            assertThrows(IOException.class, () -> source.playlistDetail("42"));
        }
    }

    private static QQMusicSource qq(java.util.function.BiFunction<Integer, Integer, JsonObject> page) {
        return new QQMusicSource(new QQFavorites(), (url, body, type, referer, cookie) -> {
            assertEquals("https://u.y.qq.com/cgi-bin/musicu.fcg", url);
            var req = json(body).getAsJsonObject("req");
            assertEquals("CgiGetDiss", req.get("method").getAsString());
            var param = req.getAsJsonObject("param");
            assertEquals(42, param.get("disstid").getAsInt());
            var result = json("{\"code\":0,\"req\":{\"code\":0}}");
            result.getAsJsonObject("req").add("data", page.apply(param.get("song_begin").getAsInt(), param.get("song_num").getAsInt()));
            return result;
        });
    }
    private static JsonObject qqPage(int total, List<String> ids) {
        var result = json("{\"dirinfo\":{\"id\":42,\"title\":\"List\"}}");
        result.addProperty("total_song_num", total);
        var songs = new JsonArray();
        for (String id : ids) {
            var song = new JsonObject(); song.addProperty("mid", id); song.addProperty("name", id);
            songs.add(song);
        }
        result.add("songlist", songs);
        return result;
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
