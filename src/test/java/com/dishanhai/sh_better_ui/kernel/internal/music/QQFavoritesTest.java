// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.platform.QQTrackFavorites.State;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QQFavoritesTest {
    private static final OnlineTrack TRACK = new OnlineTrack("qq", "songMid", "mediaMid", "", "", "", 0);

    @Test void embeddedFavoritesBatchReadsAndChangeOnlyAfterReadback() {
        var workers = new ArrayDeque<Runnable>();
        var reads = new ArrayList<List<String>>();
        var serverLiked = new java.util.concurrent.atomic.AtomicBoolean();
        var favorites = new ClientQQTrackFavorites(workers::add, () -> source((module, method, params, write) -> {
            if (method.equals("IsSongFanByMid")) {
                var mids = new ArrayList<String>();
                params.getAsJsonArray("v_songMid").forEach(value -> mids.add(value.getAsString()));
                reads.add(mids);
                return json("{\"m_fan\":{\"songMid\":" + serverLiked.get() + ",\"otherMid\":true}}");
            }
            if (method.equals("get_song_detail_yqq")) {
                return json("{\"track_info\":{\"mid\":\"songMid\",\"id\":123,\"type\":0}}");
            }
            serverLiked.set(method.equals("AddSonglist"));
            return json("{\"retCode\":0}");
        }));
        assertEquals(State.SIGNED_OUT, favorites.state("songMid"));
        favorites.credentials("uin=123; qm_keyst=fixture");
        assertEquals(State.LOADING, favorites.state("songMid"));
        assertEquals(State.LOADING, favorites.state("otherMid"));
        favorites.loadPending();
        workers.remove().run();
        assertEquals(List.of(List.of("songMid", "otherMid")), reads);
        assertEquals(State.UNLIKED, favorites.state("songMid"));
        assertEquals(State.LIKED, favorites.state("otherMid"));

        var add = favorites.set("songMid", true);
        assertEquals(State.ADDING, favorites.state("songMid"));
        assertTrue(favorites.set("songMid", true).isCompletedExceptionally());
        workers.remove().run();
        add.join();
        assertEquals(State.LIKED, favorites.state("songMid"));
        var remove = favorites.set("songMid", false);
        assertEquals(State.REMOVING, favorites.state("songMid"));
        workers.remove().run();
        remove.join();
        assertEquals(State.UNLIKED, favorites.state("songMid"));
    }

    @Test void upstreamProtocolVectorsAndFreshNonces() throws Exception {
        assertEquals("zzcec1b555gzqzg7laztguyjl2bu20r6x1w50c55f60", QQWebProtocol.sign("123"));
        assertEquals("zzcfb3415bc4nfoxmd9uik71mkomtubjfjp141a1cbbcc", QQWebProtocol.sign("hello world"));
        var vector = Base64.getDecoder().decode("UVKyyaPxYeBs+e6kkEeQpwwnsRmO4NI5JTdJvqrvU3w3elv4W1kcb8G35S4=");
        assertArrayEquals(vector, Base64.getDecoder().decode(QQWebProtocol.encode("{\"hello\":\"ag1!\"}", Arrays.copyOf(vector, 12))));
        assertNotEquals(QQWebProtocol.encode("same"), QQWebProtocol.encode("same"));
        var response = Base64.getDecoder().decode("AR3vcjr+DTBcYVz/bBhmrSIJHD7gCEvTaS25FTtcfVKpa0g9/mtCC2i7WFnqezj9SWwKKxjteVw6+2gJEg==");
        assertEquals(0, QQWebProtocol.decode(response).get("code").getAsInt());
        assertThrows(IOException.class, () -> QQWebProtocol.decode(new byte[0]));
    }

    @Test void addAndRemoveRequireIdentityAndReadback() throws Exception {
        for (boolean target : List.of(true, false)) {
            var calls = new ArrayList<String>();
            var source = source((module, method, param, write) -> {
                calls.add(method);
                if (method.equals("IsSongFanByMid")) return json("{\"m_fan\":{\"songMid\":" + (calls.size() == 1 ? !target : target) + "}}");
                if (method.equals("get_song_detail_yqq")) return json("{\"track_info\":{\"mid\":\"songMid\",\"id\":123,\"type\":13}}");
                assertTrue(write);
                assertEquals(201, param.get("dirId").getAsInt());
                var info = param.getAsJsonArray("v_songInfo").get(0).getAsJsonObject();
                assertEquals(123, info.get("songId").getAsLong());
                assertEquals(13, info.get("songType").getAsInt());
                return json("{\"retCode\":0}");
            });
            source.like(TRACK, target);
            assertEquals(List.of("IsSongFanByMid", "get_song_detail_yqq", target ? "AddSonglist" : "DelSonglist", "IsSongFanByMid"), calls);
        }
    }

    @Test void idempotentStateDoesNotWrite() throws Exception {
        var calls = new ArrayList<String>();
        var source = source((module, method, param, write) -> {
            calls.add(method); assertFalse(write); return json("{\"m_fan\":{\"songMid\":true}}");
        });
        source.like(TRACK, true);
        assertEquals(List.of("IsSongFanByMid"), calls);
    }

    @Test void incompleteStatusWrongIdentityAndUnconfirmedWriteFail() {
        for (String bad : List.of("{}", "{\"m_fan\":{}}", "{\"m_fan\":{\"songMid\":0}}")) {
            var source = source((m, method, p, w) -> json(bad));
            assertThrows(IOException.class, () -> source.like(TRACK, true));
        }
        var source = source((m, method, p, w) -> method.equals("IsSongFanByMid")
                ? json("{\"m_fan\":{\"songMid\":false}}") : json("{\"track_info\":{\"mid\":\"other\",\"id\":123,\"type\":0}}"));
        assertThrows(IOException.class, () -> source.like(TRACK, true));
        var unconfirmed = source((m, method, p, w) -> method.equals("IsSongFanByMid")
                ? json("{\"m_fan\":{\"songMid\":false}}") : method.equals("get_song_detail_yqq")
                ? json("{\"track_info\":{\"mid\":\"songMid\",\"id\":123,\"type\":0}}") : json("{\"retCode\":0}"));
        assertThrows(IOException.class, () -> unconfirmed.like(TRACK, true));
    }

    @Test void writeTimeoutIsNeverRetriedAndCancelledReadCannotWrite() {
        var methods = new ArrayList<String>();
        var source = source((m, method, p, w) -> {
            methods.add(method);
            if (w) throw new IOException("timeout");
            return method.equals("IsSongFanByMid") ? json("{\"m_fan\":{\"songMid\":false}}")
                    : json("{\"track_info\":{\"mid\":\"songMid\",\"id\":123,\"type\":0}}");
        });
        assertThrows(IOException.class, () -> source.like(TRACK, true));
        assertEquals(1, Collections.frequency(methods, "AddSonglist"));
        var cancelled = source((m, method, p, w) -> {
            assertEquals("IsSongFanByMid", method); Thread.currentThread().interrupt();
            return json("{\"m_fan\":{\"songMid\":false}}");
        });
        try { assertThrows(java.io.InterruptedIOException.class, () -> cancelled.like(TRACK, true)); }
        finally { Thread.interrupted(); }
    }

    @Test void allResponseLayersMustSucceedAndAuthErrorsRemainDistinct() throws Exception {
        assertEquals(0, QQWebProtocol.data(json("{\"code\":0,\"req\":{\"code\":0,\"data\":{\"retCode\":0}}}"), true).get("retCode").getAsInt());
        for (String bad : List.of("{}", "{\"code\":0,\"req\":{\"code\":80105,\"data\":{\"retCode\":0}}}",
                "{\"code\":0,\"req\":{\"code\":0,\"data\":{}}}",
                "{\"code\":0,\"req\":{\"code\":0,\"data\":{\"retCode\":1}}}",
                "{\"code\":0,\"req\":{\"code\":0,\"data\":{\"retCode\":0,\"subcode\":1}}}"))
            assertThrows(IOException.class, () -> QQWebProtocol.data(json(bad), true));
        assertThrows(OnlineMusicSource.LoginRequired.class, () -> QQWebProtocol.data(json("{\"code\":0,\"req\":{\"code\":1000}}"), true));
        assertFalse(assertThrows(IOException.class, () -> QQWebProtocol.data(json("{\"code\":0,\"req\":{\"code\":2000}}"), true)) instanceof OnlineMusicSource.LoginRequired);
    }

    @Test void signedRequestEncryptsTheSameJsonAndKeepsCredentialsOnOfficialHost() throws Exception {
        var param = json("{\"dirId\":201}");
        var calls = new ArrayList<String>();
        LoginHttp.Transport transport = (uri, body, type, referer, cookie) -> {
            calls.add(uri.getHost());
            assertEquals("https", uri.getScheme());
            assertEquals("u6.y.qq.com", uri.getHost());
            assertEquals("/cgi-bin/musics.fcg", uri.getPath());
            assertEquals("text/plain", type);
            assertEquals("https://y.qq.com/", referer);
            assertTrue(cookie.contains("qm_keyst=fixture"));
            var query = new HashMap<String, String>();
            for (var part : uri.getRawQuery().split("&")) {
                var pair = part.split("=", 2); query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            assertEquals("ag-1", query.get("encoding"));
            String plaintext;
            try {
                var encrypted = Base64.getDecoder().decode(body);
                var cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode("vTBfEND/dLbvVNq4NbXhzw=="), "AES"),
                        new GCMParameterSpec(128, Arrays.copyOf(encrypted, 12)));
                plaintext = new String(cipher.doFinal(Arrays.copyOfRange(encrypted, 12, encrypted.length)), StandardCharsets.UTF_8);
            } catch (Exception failure) { throw new AssertionError(failure); }
            assertEquals(QQWebProtocol.sign(plaintext), query.get("sign"));
            var request = json(plaintext);
            assertEquals(param, request.getAsJsonObject("req").getAsJsonObject("param"));
            assertEquals("fixture", request.getAsJsonObject("comm").get("authst").getAsString());
            return encodedResponse("{\"code\":0,\"req\":{\"code\":0,\"data\":{\"retCode\":0}}}");
        };
        QQWebProtocol.request("123", "fixture", "music.musicasset.PlaylistDetailWrite", "AddSonglist", param, true, transport);
        assertEquals(List.of("u6.y.qq.com"), calls);
        calls.clear();
        assertThrows(IOException.class, () -> QQWebProtocol.request("123", "fixture", "m", "write", param, true,
                (uri, body, type, referer, cookie) -> {
                    calls.add(uri.getHost()); return new MusicHttp.Response(302, Map.of("Location", List.of("https://untrusted.example/")), new byte[0]);
                }));
        assertEquals(1, calls.size());
    }

    @Test void snapshotsSerializeOppositeWrites() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var methods = new CopyOnWriteArrayList<String>();
        var liked = new java.util.concurrent.atomic.AtomicBoolean();
        var source = source((m, method, p, write) -> {
            methods.add(method);
            if (method.equals("IsSongFanByMid")) return json("{\"m_fan\":{\"songMid\":" + liked.get() + "}}");
            if (method.equals("get_song_detail_yqq")) return json("{\"track_info\":{\"mid\":\"songMid\",\"id\":123,\"type\":0}}");
            if (method.equals("AddSonglist")) {
                entered.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test timeout"); }
                catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException(); }
            }
            liked.set(method.equals("AddSonglist")); return json("{\"retCode\":0}");
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var add = executor.submit(() -> { source.requestSnapshot().like(TRACK, true); return null; });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var remove = executor.submit(() -> { source.requestSnapshot().like(TRACK, false); return null; });
            release.countDown(); add.get(3, TimeUnit.SECONDS); remove.get(3, TimeUnit.SECONDS);
            assertFalse(liked.get());
            assertEquals(1, Collections.frequency(methods, "AddSonglist"));
            assertEquals(1, Collections.frequency(methods, "DelSonglist"));
        } finally { release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS)); }
    }

    @Test void cancellingWhileAnotherSnapshotWritesDoesNotSendTheQueuedMutation() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var queued = new CountDownLatch(1);
        var writes = new CopyOnWriteArrayList<String>();
        var liked = new java.util.concurrent.atomic.AtomicBoolean();
        var source = source((m, method, p, write) -> {
            if (method.equals("IsSongFanByMid")) return json("{\"m_fan\":{\"songMid\":" + liked.get() + "}}");
            if (!write) return json("{\"track_info\":{\"mid\":\"songMid\",\"id\":123,\"type\":0}}");
            writes.add(method); entered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test timeout"); }
            catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException(); }
            liked.set(true); return json("{\"retCode\":0}");
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> { source.like(TRACK, true); return null; });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var second = executor.submit(() -> { queued.countDown(); source.requestSnapshot().like(TRACK, false); return null; });
            assertTrue(queued.await(3, TimeUnit.SECONDS)); assertTrue(second.cancel(true));
            release.countDown(); first.get(3, TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS)); }
        assertEquals(List.of("AddSonglist"), writes);
        assertTrue(liked.get());
    }

    private static MusicHttp.Response encodedResponse(String json) {
        int[] key = {122, 63, 140, 29, 94, 155, 47, 10, 108, 77, 126, 139, 31, 58, 92, 157, 14, 43, 111, 74, 129};
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) bytes[i] ^= (byte) key[i % key.length];
        return new MusicHttp.Response(200, Map.of(), bytes);
    }

    private static QQMusicSource source(QQFavorites.Rpc rpc) {
        var source = new QQMusicSource(new QQFavorites(rpc));
        source.credentials("uin=123; qm_keyst=fixture"); return source;
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    @Test void appliedWriteWithLostReadbackIsUnconfirmedAndReadReconcilesWithoutAnotherWrite() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var state = new java.util.concurrent.atomic.AtomicBoolean();
        var verify = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var readbackFails = new java.util.concurrent.atomic.AtomicBoolean(true);
        var source = source((module, method, parameters, write) -> {
            if (write) { count.incrementAndGet(); state.set(true); return json("{\"retCode\":0}"); }
            if (method.equals("get_song_detail_yqq")) return json("{\"track_info\":{\"mid\":\"songMid\",\"id\":123,\"type\":0}}");
            if (state.get() && readbackFails.get()) {
                verify.countDown();
                try { if (!release.await(3,TimeUnit.SECONDS)) throw new IOException("fixture timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("fixture interrupted"); }
                throw new java.net.SocketTimeoutException("fixture readback");
            }
            return json("{\"m_fan\":{\"songMid\":" + state.get() + "}}");
        });
        try (var player = new OnlinePlayerSession(List.of(source))) {
            player.credentials("qq", "uin=123; qm_keyst=fixture");
            var registry = player.operations();
            var update = registry.startLike(TRACK, true);
            assertTrue(verify.await(3, TimeUnit.SECONDS));
            update.detach();
            release.countDown();
            assertThrows(ExecutionException.class, () -> update.observe().get(3, TimeUnit.SECONDS));
            var limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while (update.snapshot().state()==SessionMusicOperations.State.RUNNING && System.nanoTime()<limit) Thread.onSpinWait();
            assertEquals(SessionMusicOperations.Error.UNKNOWN, update.snapshot().error());
            var presenter = new MusicOperationNotices();
            assertEquals(List.of("operation_like_unknown"), presenter.observe(registry.snapshots(), true).stream()
                    .filter(event -> !event.cancel()).map(MusicOperationNotices.Update::key).toList());
            assertTrue(presenter.observe(registry.snapshots(), true).isEmpty());
            assertTrue(state.get());
            readbackFails.set(false);
            // QQ checks current state first. A matching read does not issue another write.
            source.like(TRACK, true);
            assertEquals(1,count.get());
        } finally { release.countDown(); }
        assertThrows(MusicWriteRejected.class, () -> QQWebProtocol.data(
                json("{\"code\":0,\"req\":{\"code\":0,\"data\":{\"retCode\":1}}}"), true));
    }
    @Test void missingTrackBeforeDispatchDoesNotReportUnconfirmedWrite() {
        var source = source((module,method,param,write)->{
            assertFalse(write);
            return method.equals("IsSongFanByMid") ? json("{\"m_fan\":{\"songMid\":false}}") : new JsonObject();
        });
        var error = assertThrows(QQFavorites.NotSent.class, () -> source.like(TRACK,true));
        assertEquals(SessionMusicOperations.Error.UNAVAILABLE,
                SessionMusicOperations.classify(SessionMusicOperations.Kind.LIKE,error));
    }
}
