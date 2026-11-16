// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonParser;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class OnlineArtworkTest {
    @Test void restrictsProviderHostsAndNeverAcceptsCredentialsOrNonWebSchemes() throws Exception {
        assertEquals("https", OnlineArtwork.validated("http://p1.music.126.net/a.jpg").getScheme());
        assertEquals("y.gtimg.cn", OnlineArtwork.validated("https://y.gtimg.cn/a.jpg").getHost());
        assertEquals("https://p1.music.126.net/a%2Fb.jpg?x=a%2Bb",
                OnlineArtwork.validated("http://p1.music.126.net/a%2Fb.jpg?x=a%2Bb").toString());
        for (var value : new String[]{"file:///etc/passwd", "http://127.0.0.1/a", "https://y.gtimg.cn.evil.test/a",
                "https://evil@y.gtimg.cn/a", "https://y.gtimg.cn:443/a", "https://y.gtimg.cn/a#token", "https://music.126.net/a"})
            assertThrows(IOException.class, () -> OnlineArtwork.validated(value), value);
    }
    @Test void boundedImageNormalizationAndMalformedImages() throws Exception {
        var image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "jpg", bytes);
        var normalized = ImageIO.read(new ByteArrayInputStream(OnlineArtwork.normalize(bytes.toByteArray())));
        assertEquals(512, normalized.getWidth()); assertEquals(256, normalized.getHeight());
        assertThrows(IOException.class, () -> OnlineArtwork.normalize(new byte[0]));
        assertThrows(IOException.class, () -> OnlineArtwork.normalize(new byte[OnlineArtwork.MAX_BYTES + 1]));
        assertThrows(IOException.class, () -> OnlineArtwork.normalize(new byte[]{1, 2, 3}));
        bytes.reset(); ImageIO.write(new BufferedImage(2049, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
        byte[] oversized = bytes.toByteArray();
        assertThrows(IOException.class, () -> OnlineArtwork.normalize(oversized));
    }
    @Test void streamingBudgetCancelsBeforeAcceptingOversizeBody() {
        var subscriber = new OnlineArtwork.BoundedBody();
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        subscriber.onSubscribe(new java.util.concurrent.Flow.Subscription() {
            public void request(long count) {}
            public void cancel() { cancelled.set(true); }
        });
        subscriber.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[OnlineArtwork.MAX_BYTES])));
        subscriber.onNext(java.util.List.of(java.nio.ByteBuffer.wrap(new byte[1])));
        assertTrue(cancelled.get());
        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }

    @Test void readsProviderMetadataWithoutGuessingCoversFromTrackNames() {
        var ne = JsonParser.parseString("{\"result\":{\"songs\":[{\"id\":1,\"name\":\"Song\",\"al\":{\"name\":\"Album\",\"picUrl\":\"https://p1.music.126.net/cover.jpg\"}}]}}").getAsJsonObject();
        assertEquals("https://p1.music.126.net/cover.jpg?param=512y512", NeteaseSource.parseSearch(ne, 20).get(0).artworkUrl());
        var qq = JsonParser.parseString("{\"data\":{\"song\":{\"list\":[{\"mid\":\"song123\",\"album\":{\"mid\":\"album456\"}}]}}}").getAsJsonObject();
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R500x500M000album456.jpg", QQMusicSource.parseSearch(qq, 20).get(0).artworkUrl());
        assertEquals("", new OnlineTrack("qq", "1", "1", "Song", "", "", 1).artworkUrl());
    }
}
