// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlinePlaylist;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicAccountProtocolTest {
    @Test void qqPlaylistSongsPreserveMediaIdentityAndOrder() throws Exception {
        var body = JsonParser.parseString("""
                {"songlist":[
                  {"songmid":"first","strMediaMid":"mediaFirst","songname":"One","albumname":"Album","interval":5},
                  {"mid":"second","title":"Two","file":{"media_mid":"mediaSecond"},"singer":[{"name":"Singer"}]}
                ]}
                """).getAsJsonObject();
        var songs = QQMusicSource.parseSongList(body, 20);
        assertEquals(2, songs.size());
        assertEquals("first", songs.get(0).id());
        assertEquals("mediaFirst", songs.get(0).mediaId());
        assertEquals("Album", songs.get(0).album());
        assertEquals("second", songs.get(1).id());
        assertEquals("Singer", songs.get(1).artist());
        assertEquals(1, QQMusicSource.parseSongList(body, 1).size());
        assertThrows(IOException.class, () -> QQMusicSource.parseSongList(JsonParser.parseString("{}").getAsJsonObject(), 20));
    }

    @Test void qqPlaylistArraysDistinguishEmptyFromMalformedResponses() throws Exception {
        var empty = JsonParser.parseString("{\"data\":{\"v_playlist\":[]}}").getAsJsonObject();
        assertTrue(QQMusicSource.requireArray(empty, "v_playlist").isEmpty());
        var missing = JsonParser.parseString("{\"data\":{}}").getAsJsonObject();
        assertThrows(IOException.class, () -> QQMusicSource.requireArray(missing, "v_playlist"));
    }

    @Test void accountOperationsRejectMissingCredentialsAndWrongSourceWithoutNetwork() {
        var netease = new NeteaseSource();
        var qq = new QQMusicSource();
        assertThrows(OnlineMusicSource.LoginRequired.class, netease::account);
        assertThrows(OnlineMusicSource.LoginRequired.class, qq::account);
        assertThrows(OnlineMusicSource.LoginRequired.class, () -> netease.liked(1, 20));
        assertThrows(OnlineMusicSource.LoginRequired.class, () -> qq.liked(1, 20));
        assertThrows(IllegalArgumentException.class, () -> qq.playlist(new OnlinePlaylist("netease", "1", "", 0), 1, 20));
        assertThrows(IllegalArgumentException.class, () -> netease.like(new OnlineTrack("qq", "1", "1", "", "", "", 0), true));
        assertThrows(IllegalArgumentException.class, () -> netease.playlists(0, 20));
        assertTrue(netease.supportsLikes());
        assertTrue(qq.supportsLikes());
        assertThrows(OnlineMusicSource.LoginRequired.class, () -> qq.like(new OnlineTrack("qq", "1", "1", "", "", "", 0), true));
        assertThrows(IllegalArgumentException.class, () -> qq.like(new OnlineTrack("netease", "1", "1", "", "", "", 0), true));
    }

    @Test void requestsKeepTheCredentialsPresentWhenScheduled() {
        var netease = new NeteaseSource();
        var qq = new QQMusicSource();
        var capturedNetease = netease.requestSnapshot();
        var capturedQq = qq.requestSnapshot();
        netease.credentials("MUSIC_U=token");
        qq.credentials("uin=123; qm_keyst=token");
        assertThrows(OnlineMusicSource.LoginRequired.class, capturedNetease::account);
        assertThrows(OnlineMusicSource.LoginRequired.class, capturedQq::account);
    }
}
