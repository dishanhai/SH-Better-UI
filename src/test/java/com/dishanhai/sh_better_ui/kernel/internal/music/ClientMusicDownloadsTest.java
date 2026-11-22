// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.MusicDownloads.*;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ClientMusicDownloadsTest {
    private static final OnlineTrack TRACK = new OnlineTrack("qq", "Song01", "Media01", "A:/Song", "Artist", "", 200_000);
    private static final String COOKIE = "qqmusic_uin=12345; qm_keyst=fixture";
    private static final byte[] FLAC = {'f', 'L', 'a', 'C', 0, 0, 0, 0};
    private static final String GRANT = """
            {"sip":["https://dl.stream.qqmusic.qq.com/"],"midurlinfo":[
                {"songmid":"Song01","filename":"F000Media01.flac","result":0,"purl":"F000Media01.flac?vkey=fixture"}
            ]}
            """;

    @TempDir Path root;

    @Test void membershipAndPurchaseStopBeforeAnyFileRequest() throws Exception {
        for (int month : List.of(0, 1)) {
            var calls = new AtomicInteger();
            var downloads = service(1, month, calls, (uri, size, stream, progress) -> fail("Protected file transfer"));
            var options = downloads.options(TRACK, COOKIE).get(5, TimeUnit.SECONDS);
            assertEquals(month == 1 ? Access.MEMBERSHIP : Access.PURCHASE, options.access());
            downloads.start(TRACK, Quality.LOSSLESS, COOKIE);
            var task = await(downloads, State.FAILED);
            assertEquals(options.access(), task.access());
            assertEquals(0, calls.get());
        }
        assertFalse(Files.exists(root.resolve("downloads")));
    }

    @Test void loginAndExactQualityAreCheckedBeforeTransfer() throws Exception {
        var calls = new AtomicInteger();
        var downloads = service(0, 0, calls, (uri, size, stream, progress) -> fail("Unexpected transfer"));
        downloads.start(TRACK, Quality.LOSSLESS, "");
        assertEquals(Access.SIGN_IN, await(downloads, State.FAILED).access());
        downloads.start(TRACK, Quality.HIGH, COOKIE);
        assertEquals(Access.UNAVAILABLE, await(downloads, State.FAILED).access());
        assertEquals(0, calls.get());
    }

    @Test void savesToLibraryWithoutReplacingFiles() throws Exception {
        var downloads = service(0, 0, new AtomicInteger(), (uri, size, stream, progress) -> {
            assertEquals("/F000Media01.flac", uri.getPath());
            stream.write(FLAC);
            progress.accept(FLAC.length);
        });
        Files.createDirectories(downloads.directory());
        Path existing = downloads.directory().resolve("Artist - A__Song [Song01-lossless].flac");
        Files.writeString(existing, "existing file");
        downloads.start(TRACK, Quality.LOSSLESS, COOKIE);
        var task = await(downloads, State.COMPLETE);
        assertEquals(FLAC.length, task.received());
        assertEquals("existing file", Files.readString(existing));
        assertArrayEquals(FLAC, Files.readAllBytes(task.file()));
        assertFalse(task.file().equals(existing));
        assertEquals(2, downloads.scan(downloads.directory()).get(5, TimeUnit.SECONDS).size());
    }

    @Test void cancellationDiscardsPartialAndDeduplicatesActiveTask() throws Exception {
        var entered = new CountDownLatch(1);
        var stopped = new CountDownLatch(1);
        var downloads = service(0, 0, new AtomicInteger(), (uri, size, stream, progress) -> {
            stream.write(FLAC, 0, 4);
            entered.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException failure) { throw new InterruptedIOException(); }
            finally { stopped.countDown(); }
        });
        long id = downloads.start(TRACK, Quality.LOSSLESS, COOKIE);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(id, downloads.start(TRACK, Quality.LOSSLESS, COOKIE));
        } finally {
            downloads.cancel(id);
        }
        assertTrue(stopped.await(5, TimeUnit.SECONDS));
        assertEquals(State.CANCELLED, downloads.tasks().get(0).state());
        assertTrue(downloads.scan(downloads.directory()).get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test void rejectsPreviewReplacementAndIdentifiesLocalProtectedFiles() throws Exception {
        assertThrows(IOException.class, () -> QQDownload.grant(
                JsonParser.parseString(GRANT.replace("purl\":\"F000", "purl\":\"RS01")).getAsJsonObject(),
                TRACK.id(), "F000Media01.flac", FLAC.length));
        Path protectedFile = root.resolve("track.mflac");
        Files.write(protectedFile, new byte[]{1, 2, 3, 4, 'c', 'e', 'x', 0});
        assertEquals(LocalFormat.QQ_PROTECTED, ClientMusicDownloads.inspect(protectedFile).format());
        Files.write(protectedFile, FLAC);
        assertEquals(LocalFormat.STANDARD_FLAC, ClientMusicDownloads.inspect(protectedFile).format());
    }

    private ClientMusicDownloads service(int payDown, int payMonth, AtomicInteger grants,
                                         ClientMusicDownloads.Transfer transfer) {
        String detail = """
                {"code":0,"data":[{"mid":"Song01","type":0,
                    "pay":{"pay_down":%d,"pay_month":%d},
                    "file":{"media_mid":"Media01","size_flac":8}}]}
                """.formatted(payDown, payMonth);
        return new ClientMusicDownloads(root.resolve("downloads"), () -> new QQMusicSource(new QQFavorites(),
                (url, body, type, referer, cookie) -> {
                    if (url.contains("fcg_play_single_song")) return JsonParser.parseString(detail).getAsJsonObject();
                    grants.incrementAndGet();
                    assertTrue(body.contains("F000Media01.flac"));
                    assertTrue(body.contains("UrlGetVkey"));
                    return JsonParser.parseString("{\"code\":0,\"req\":{\"code\":0,\"data\":" + GRANT + "}}").getAsJsonObject();
                }), transfer);
    }

    private static Task await(ClientMusicDownloads downloads, State state) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Task task = downloads.tasks().get(0);
            if (task.state() == state) return task;
            Thread.sleep(5);
        }
        return fail("Expected " + state + ", got " + downloads.tasks());
    }
}
