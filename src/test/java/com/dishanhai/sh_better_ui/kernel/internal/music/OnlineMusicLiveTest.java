// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in integration probe: public search, granted streams and lyrics, never audio output. */
@EnabledIfEnvironmentVariable(named = "SHBUI_MUSIC_LIVE", matches = "1")
class OnlineMusicLiveTest {
    @Test void publicNeteaseAndQQEndpoints() throws Exception {
        var netease = new NeteaseSource();
        var songs = netease.search("卡农", 1, 5);
        assertFalse(songs.isEmpty(), "NetEase public search");
        boolean playable = false;
        for (var track : songs) {
            var stream = netease.resolve(track);
            if (stream == null) continue;
            var bytes = new MusicHttp().audio(stream.uri());
            assertTrue(bytes.length > 1024);
            var bits = new com.dishanhai.sh_better_ui.kernel.internal.music.decoder.Bitstream(new java.io.ByteArrayInputStream(bytes));
            try {
                var header = bits.readFrame();
                assertNotNull(header, "Returned resource is MPEG audio");
                var decoded = new com.dishanhai.sh_better_ui.kernel.internal.music.decoder.Decoder().decodeFrame(header, bits);
                assertNotNull(decoded);
            } finally { bits.close(); }
            netease.lyrics(track);
            System.out.println("NetEase: search, granted MP3 download, decode and lyrics request succeeded.");
            playable = true; break;
        }
        assertTrue(playable, "At least one public result must yield decodable MP3");
        var qq = new QQMusicSource();
        var qqSongs = qq.search("卡农", 1, 5);
        assertFalse(qqSongs.isEmpty(), "QQ public search");
        qq.lyrics(qqSongs.get(0));
        try {
            var stream = qq.resolve(qqSongs.get(0));
            if (stream == null) System.out.println("QQ: search and lyrics succeeded; first result has no granted audio.");
            else {
                var bytes = new MusicHttp().audio(stream.uri());
                var bits = new com.dishanhai.sh_better_ui.kernel.internal.music.decoder.Bitstream(new java.io.ByteArrayInputStream(bytes));
                try {
                    var header = bits.readFrame(); assertNotNull(header);
                    assertNotNull(new com.dishanhai.sh_better_ui.kernel.internal.music.decoder.Decoder().decodeFrame(header, bits));
                } finally { bits.close(); }
                System.out.println("QQ: search, granted MP3 download, decode and lyrics request succeeded.");
            }
        }
        catch (com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicSource.LoginRequired expected) {
            System.out.println("QQ standard stream explicitly requires login; no account credentials used.");
        }
    }
}
