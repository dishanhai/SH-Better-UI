// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;
import com.dishanhai.sh_better_ui.kernel.internal.music.view.MusicCommentsDrawer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicComments.Order.*;
import static org.junit.jupiter.api.Assertions.*;

class OnlineMusicCommentsTest {
    @Test void neteaseReadsGuestPagesAndDoesNotPersistOrSendTheAccountCookie() throws Exception {
        var provider = new NeteaseSource((url, body, type, referer, cookie) -> {
            assertTrue(url.startsWith("https://music.163.com/api/v1/resource/comments/R_SO_4_123?"));
            assertTrue(url.contains("offset=20"));
            assertFalse(cookie.contains("private"));
            return json("""
                    {"code":200,"total":41,"more":true,"comments":[
                      {"commentId":9,"user":{"nickname":"Listener"},"content":"first\\r\\nsecond",
                       "time":1735689600000,"likedCount":7}]}
                    """);
        });
        try (var session = new OnlinePlayerSession(List.of(provider))) {
            assertEquals(List.of(HOT, LATEST), session.commentOrders("netease"));
            assertTrue(session.commentOrders("apple").isEmpty());
            session.credentials("netease", "MUSIC_U=private");
            var page = session.comments(track("netease", "123"), LATEST, 2).get(3, TimeUnit.SECONDS);
            assertEquals(41L, page.getTotal());
            assertTrue(page.getHasMore());
            var entry = page.getEntries().get(0);
            assertEquals("first\nsecond", entry.getContent());
            assertEquals("2025-01-01", entry.getDate().toString());
            assertEquals(7L, entry.getLikes());
            assertNull(entry.getReplies());
        }
    }

    @Test void qqResolvesTheExactSongMidAndKeepsTheFeaturedListFinite() throws Exception {
        var provider = new QQMusicSource(new QQFavorites(), (url, body, type, referer, cookie) -> {
            assertTrue(cookie.isEmpty());
            if (url.contains("play_single_song")) {
                assertTrue(url.contains("songmid=songB") || url.contains("songmid=songC"));
                return json("""
                        {"code":0,"data":[{"mid":"songA","id":111},{"mid":"songB","id":222}]}
                        """);
            }
            if (url.contains("musicu.fcg")) return json("{\"code\":0,\"req\":{\"code\":10000}}");
            assertTrue(url.contains("topid=222"));
            return json("""
                    {"code":0,"topid":"222","morecomment":1,"comment":{"commenttotal":120,"commentlist":[]},
                     "hot_comment":{"commenttotal":12,"commentlist":[
                       {"commentid":"a","nick":"Listener","rootcommentcontent":"Featured","praisenum":4,"time":44295107}]}}
                    """);
        });
        var song = track("qq", "songB");
        var page = provider.comments(song, HOT, 1, 20);
        assertEquals("Featured", page.getEntries().get(0).getContent());
        assertEquals(4L, page.getEntries().get(0).getLikes());
        assertNull(page.getEntries().get(0).getDate());
        assertFalse(page.getHasMore());
        assertTrue(provider.comments(song, HOT, 2, 20).getEntries().isEmpty());
        assertThrows(IOException.class, () -> provider.comments(track("qq", "songC"), HOT, 1, 20));
    }

    @Test void qqReadsAuthorAvatarAndCanonicalDateAndUsesReplySequence() throws Exception {
        var provider = new QQMusicSource(new QQFavorites(), (url, body, type, referer, cookie) -> {
            assertTrue(cookie.isEmpty());
            var query = java.net.URI.create(url).getRawQuery();
            var data = json(java.net.URLDecoder.decode(query.substring("data=".length()), java.nio.charset.StandardCharsets.UTF_8));
            var request = data.getAsJsonObject("req");
            var parameters = request.getAsJsonObject("param");
            assertEquals("101101739", parameters.get("BizId").getAsString());
            if (request.get("method").getAsString().equals("GetHotCommentList")) {
                return json("""
                        {"code":0,"req":{"code":0,"data":{"TotalCmNum":12358,"CommentList":{"Total":1,"Comments":[
                          {"CmId":"1!root","Nick":"Listener","Content":"Comment","PraiseNum":2434,"ReplyCnt":20,
                           "Avatar":"http://y.gtimg.cn/music/avatar.jpg","PubTime":1461600748}]}}}}
                        """);
            }
            assertEquals("GetReplyCommentList", request.get("method").getAsString());
            assertEquals("1!root", parameters.get("RootCmId").getAsString());
            String cursor = parameters.get("LastCommentSeqNo").getAsString();
            assertTrue(cursor.equals("0") || cursor.equals("13"));
            return json("""
                    {"code":0,"req":{"code":0,"data":{"CommentList":{"HasMore":1,"Total":20,"Comments":[
                      {"CmId":"reply","Nick":"Other","Content":"A reply","SeqNo":"13",
                       "RepliedComments":[{"Nick":"Listener"}]}]}}}}
                    """);
        });
        var song = track("qq", "101101739");
        var root = provider.comments(song, HOT, 1, 20).getEntries().get(0);
        assertEquals("2016-04-26", root.getDate().toString());
        assertEquals("https://y.gtimg.cn/music/avatar.jpg", root.getAvatar());
        var replies = provider.commentReplies(song, root, null, 10);
        assertEquals("Listener", replies.getEntries().get(0).getReplyingTo());
        assertEquals("13", replies.getNextCursor());
        assertNull(provider.commentReplies(song, root, "13", 10).getNextCursor());
    }

    @Test void kugouMetadataAndPaginationStayProviderOwned() throws Exception {
        var page = MusicCommentFeeds.INSTANCE.kugou(json("""
                {"status":1,"err_code":0,"count":23,"current_page":2,"maxPage":3,
                 "list":[{"id":1,"user_name":"Listener","content":"Comment","addtime":"2025-01-01 08:10:01",
                          "like":{"likenum":632},"reply_num":2}]}
                """), 2, 10);
        assertTrue(page.getHasMore());
        assertEquals("2025-01-01", page.getEntries().get(0).getDate().toString());
        assertEquals(2L, page.getEntries().get(0).getReplies());
        assertEquals(632L, page.getEntries().get(0).getLikes());
        assertFalse(new KugouCommentSource().supportsPlayback());
        assertThrows(IOException.class, () -> MusicCommentFeeds.INSTANCE.kugou(
                json("{\"status\":1,\"err_code\":0,\"count\":1,\"current_page\":1}"), 1, 20));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SHBUI_MUSIC_COMMENTS_LIVE", matches = "1")
    void liveGuestReadsAndKugouSearchUseTheProductionAdapters() throws Exception {
        var netease = new NeteaseSource();
        var qq = new QQMusicSource();
        var kugou = new KugouCommentSource();
        var kugouSong = kugou.search("hello", 1, 5).get(0);
        try (var session = new OnlinePlayerSession(List.of(netease, qq, kugou))) {
            var neteaseSong = track("netease", "186016");
            var qqSong = track("qq", "237773700");
            for (var song : List.of(neteaseSong, qqSong, kugouSong)) {
                var order = song.source().equals("kugou") ? DEFAULT : LATEST;
                var first = session.comments(song, order, 1).get(20, TimeUnit.SECONDS);
                var second = session.comments(song, order, 2).get(20, TimeUnit.SECONDS);
                assertFalse(first.getEntries().isEmpty(), song.source());
                assertFalse(second.getEntries().isEmpty(), song.source());
                assertNotEquals(first.getEntries().get(0).getId(), second.getEntries().get(0).getId(), song.source());
            }
            for (var song : List.of(neteaseSong, qqSong)) {
                var hot = session.comments(song, HOT, 1).get(20, TimeUnit.SECONDS);
                assertFalse(hot.getEntries().isEmpty(), song.source());
            }
            var red = track("qq", "101101739");
            var featured = session.comments(red, HOT, 1).get(20, TimeUnit.SECONDS);
            var corrected = featured.getEntries().stream().filter(it -> it.getAuthor().equals("权是光!")).findFirst().orElseThrow();
            assertEquals("2016-04-26", corrected.getDate().toString());
            var qqParent = featured.getEntries().stream().filter(it -> it.getReplies() != null && it.getReplies() > 10).findFirst().orElseThrow();
            var neteaseParent = session.comments(neteaseSong, HOT, 1).get(20, TimeUnit.SECONDS).getEntries().get(0);
            var kugouParent = session.comments(kugouSong, DEFAULT, 1).get(20, TimeUnit.SECONDS).getEntries().stream()
                    .filter(it -> it.getReplies() != null && it.getReplies() > 10).findFirst().orElseThrow();
            var songs = List.of(red, neteaseSong, kugouSong);
            var parents = List.of(qqParent, neteaseParent, kugouParent);
            for (int index = 0; index < songs.size(); index++) {
                var song = songs.get(index);
                var parent = parents.get(index);
                assertNotNull(parent.getAvatar(), song.source());
                assertTrue(OnlineArtwork.downloadAvatar(parent.getAvatar()).length > 0, song.source());
                var first = session.commentReplies(song, parent, null).get(20, TimeUnit.SECONDS);
                assertFalse(first.getEntries().isEmpty(), song.source());
                assertNotNull(first.getNextCursor(), song.source());
                var second = session.commentReplies(song, parent, first.getNextCursor()).get(20, TimeUnit.SECONDS);
                assertFalse(second.getEntries().isEmpty(), song.source());
                assertNotEquals(first.getEntries().get(0).getId(), second.getEntries().get(0).getId(), song.source());
            }
        }
    }

    @Test void embeddedPlayerUsesCatalogIdsAndRejectsUnsupportedAndHashOnlyTracks() {
        assertEquals("237773700", MusicCommentsDrawer.reference(
                "qq", "237773700", "001qvvgF38HVc4", null, "Song", "Artist").id());
        assertEquals("001qvvgF38HVc4", MusicCommentsDrawer.reference(
                "qq", "invalid", "001qvvgF38HVc4", null, "Song", "Artist").id());
        assertEquals("28823537", MusicCommentsDrawer.reference(
                "kugou", "filehash", null, "28823537", "Song", "Artist").id());
        assertNull(MusicCommentsDrawer.reference("kugou", "filehash", null, "", "Song", "Artist"));
        assertNull(MusicCommentsDrawer.reference("apple", "186016", null, null, "Song", "Artist"));
    }

    private static OnlineTrack track(String source, String id) {
        return new OnlineTrack(source, id, "media", "", "", "", 0);
    }

    private static JsonObject json(String raw) { return JsonParser.parseString(raw).getAsJsonObject(); }
}
