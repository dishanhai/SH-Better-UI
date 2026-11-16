// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonParser;
import com.dishanhai.sh_better_ui.kernel.internal.music.decoder.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicProtocolTest {
    private static com.google.gson.JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    @Test void parsesNeteaseIdentityAndDoesNotInventMissingSongs() {
        var results = NeteaseSource.parseSearch(json("""
                {"result":{"songs":[{"id":123,"name":"同名歌","ar":[{"name":"甲"},{"name":"乙"}],
                "al":{"name":"专辑"},"dt":9000},{"name":"broken"}]}}
                """), 20);
        assertEquals(1, results.size());
        assertEquals("123", results.get(0).id());
        assertEquals("甲 / 乙", results.get(0).artist());
        assertTrue(NeteaseSource.parseSearch(json("{}"), 20).isEmpty());
    }
    @Test void qqPreservesSongMidAndIndependentMediaMid() {
        var results = QQMusicSource.parseSearch(json("""
                {"body":{"song":{"list":[{"mid":"songA","title":"Song","file":{"media_mid":"fileB"},
                "singer":[{"name":"Artist"}],"interval":5}]}}}
                """), 20);
        assertEquals("songA", results.get(0).id());
        assertEquals("fileB", results.get(0).mediaId());
        assertEquals(5000, results.get(0).durationMs());
    }
    @Test void unavailableAudioAndPreviewAreDistinct() {
        assertNull(NeteaseSource.parseStream(json("{\"url\":null}"), 60000));
        assertNull(QQMusicSource.parseStream(json("{\"midurlinfo\":[{\"purl\":\"\"}]}"), 60000));
        var stream = NeteaseSource.parseStream(json("""
                {"url":"http://music.example/song.mp3","type":"mp3","freeTrialInfo":{"start":30,"end":60}}
                """), 180000);
        assertTrue(stream.preview());
        assertEquals(30000, stream.offsetMs());
        assertEquals(30000, stream.durationMs());
        assertEquals("https", stream.uri().getScheme());
        assertThrows(IllegalArgumentException.class, () -> MusicHttp.https("file:///secret"));
        assertThrows(IllegalArgumentException.class, () -> MusicHttp.https("https://user:secret@example.org/song"));
        assertThrows(IllegalArgumentException.class, () -> MusicHttp.cookie("x=y\r\nInjected: true"));
        assertThrows(IllegalArgumentException.class, () -> new QQMusicSource().credentials("other=not-login"));
        assertDoesNotThrow(() -> new QQMusicSource().credentials("uin=123; qm_keyst=test"));
    }
    @Test void yrcUsesAbsoluteWordTimesAndMatchesTranslation() {
        var timeline = OnlineLyrics.parse("[00:01]fallback", "[00:01]译文", "[1000,900](1000,400,0)你(1400,500,0)好", 5000);
        assertEquals("你好", timeline.line(0).text());
        assertEquals("译文", timeline.line(0).translation());
        assertEquals(1400, timeline.line(0).words().get(1).startMs());
        assertEquals(1900, timeline.line(0).words().get(1).endMs());
        assertEquals("fallback", OnlineLyrics.parse("[00:01]fallback", "", "broken", 5000).line(0).text());
    }
    @Test void importedDecoderActuallyDecodesMpegSilenceWithoutNativeOrSerializedAssets() throws Exception {
        byte[] mp3 = new byte[417 * 4];
        for (int i = 0; i < 4; i++) {
            mp3[i * 417] = (byte) 0xff; mp3[i * 417 + 1] = (byte) 0xfb; mp3[i * 417 + 2] = (byte) 0x90;
        }
        var stream = new Bitstream(new ByteArrayInputStream(mp3));
        var decoder = new Decoder();
        int frames = 0;
        Header header;
        while ((header = stream.readFrame()) != null) {
            var samples = (SampleBuffer) decoder.decodeFrame(header, stream);
            assertEquals(44100, samples.getSampleFrequency());
            assertEquals(2, samples.getChannelCount());
            assertEquals(2304, samples.getBufferLength());
            for (int i = 0; i < samples.getBufferLength(); i++) assertEquals(0, samples.getBuffer()[i]);
            stream.closeFrame(); frames++;
        }
        stream.close();
        assertEquals(4, frames);
    }
}
