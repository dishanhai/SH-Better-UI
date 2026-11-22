// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource.LoginRequired;
import com.dishanhai.sh_better_ui.kernel.api.music.platform.QQMusicPlaylists.AddResult;
import com.dishanhai.sh_better_ui.kernel.api.music.platform.QQMusicPlaylists.Playlist;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ClientQQMusicPlaylistsTest {
    private static final String COOKIES = "qqmusic_uin=12345; qm_keyst=test";
    private static final Playlist TARGET = new Playlist(7000000012L, 15, "Running", 0);
    private static final JsonObject OWNED = json("""
            {"v_playlist":[
              {"tid":7000000012,"dirId":15,"dirName":"Running","uin":"12345"},
              {"tid":7000000013,"dirId":201,"dirName":"Favorites","uin":"12345"},
              {"tid":7000000014,"dirId":18,"dirName":"Someone else's","uin":"9988"},
              {"tid":7000000015,"dirName":"No writable directory"}
            ]}
            """);

    @Test void listsOnlyEditableTargetsAndCreatesPrivatePlaylist() {
        var client = client((module, method, params, write) -> {
            if ("GetPlaylistByUin".equals(method)) {
                assertFalse(write);
                assertEquals("12345", params.get("uin").getAsString());
                return OWNED;
            }
            assertEquals("AddPlaylist", method);
            assertTrue(write);
            assertEquals(2, params.get("dirShow").getAsInt());
            assertEquals("Running", params.get("dirName").getAsString());
            return json("""
                    {"retCode":0,"result":{"tid":7000000012,"dirId":15,"dirName":"Running"}}
                    """);
        });
        assertEquals(List.of(TARGET), client.list().join());
        assertEquals(TARGET, client.create(" Running ").join());
    }

    @Test void addsUsingConfirmedSongAndDirectoryIdsAndSkipsDuplicate() {
        var added = new AtomicBoolean();
        var client = client((module, method, params, write) -> switch (method) {
            case "GetPlaylistByUin" -> OWNED;
            case "CgiGetDiss" -> detail(1, added.get());
            case "get_song_detail_yqq" -> json("""
                    {"track_info":{"mid":"songMID","id":42,"type":13}}
                    """);
            case "AddSonglist" -> {
                assertTrue(added.compareAndSet(false, true));
                assertTrue(write);
                assertEquals(15, params.get("dirId").getAsInt());
                assertEquals(7000000012L, params.get("tid").getAsLong());
                var song = params.getAsJsonArray("v_songInfo").get(0).getAsJsonObject();
                assertEquals(42, song.get("songId").getAsInt());
                assertEquals(1, song.get("songType").getAsInt());
                yield json("{\"retCode\":0}");
            }
            default -> throw new AssertionError(method);
        });
        assertEquals(AddResult.ADDED, client.add(TARGET, "songMID").join());
        assertEquals(AddResult.ALREADY_PRESENT, client.add(TARGET, "songMID").join());
    }

    @Test void rejectsUnownedPlaylistAndChecksCredentialsAgainBeforeWrite() {
        var credentials = new AtomicReference<>(COOKIES);
        var ownsDetail = new AtomicBoolean();
        var client = new ClientQQMusicPlaylists(credentials::get, Runnable::run,
                (module, method, params, write) -> switch (method) {
                    case "GetPlaylistByUin" -> OWNED;
                    case "CgiGetDiss" -> detail(ownsDetail.get() ? 1 : 0, false);
                    case "get_song_detail_yqq" -> {
                        credentials.set("qqmusic_uin=67890; qm_keyst=test2");
                        yield json("{\"track_info\":{\"mid\":\"songMID\",\"id\":42,\"type\":0}}");
                    }
                    default -> throw new AssertionError(method);
                });
        assertInstanceOf(IOException.class, assertThrows(CompletionException.class,
                () -> client.add(TARGET, "songMID").join()).getCause());
        ownsDetail.set(true);
        assertInstanceOf(LoginRequired.class, assertThrows(CompletionException.class,
                () -> client.add(TARGET, "songMID").join()).getCause());
        assertFalse(client.available());
    }

    @Test void acknowledgementWithoutReadbackIsAFailure() {
        var client = client((module, method, params, write) -> switch (method) {
            case "GetPlaylistByUin" -> OWNED;
            case "CgiGetDiss" -> detail(1, false);
            case "get_song_detail_yqq" -> json("{\"track_info\":{\"mid\":\"songMID\",\"id\":42,\"type\":0}}");
            case "AddSonglist" -> json("{\"retCode\":0}");
            default -> throw new AssertionError(method);
        });
        assertInstanceOf(IOException.class, assertThrows(CompletionException.class,
                () -> client.add(TARGET, "songMID").join()).getCause());
    }

    private static ClientQQMusicPlaylists client(QQFavorites.Rpc rpc) {
        return new ClientQQMusicPlaylists(() -> COOKIES, Runnable::run, rpc);
    }

    private static JsonObject detail(int owner, boolean hasSong) {
        return json("""
                {"dirinfo":{"id":7000000012,"dirid":15,"owndir":%d},"total_song_num":%d,"songlist":%s}
                """.formatted(owner, hasSong ? 1 : 0, hasSong ? "[{\"mid\":\"songMID\"}]" : "[]"));
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
