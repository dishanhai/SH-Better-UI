// Author: dishanhai. Transport-only tests: no network, audio, account or game runtime.
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeteaseSearchArtworkTest {
    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }
    private static final String SEARCH = """
            {"code":200,"result":{"songs":[
              {"id":2,"name":"First","duration":1234,"album":{"name":"Original","picId":20},"artists":[{"name":"Singer"}]},
              {"id":1,"name":"Second","album":{"picUrl":"https://p1.music.126.net/existing.jpg"}},
              {"id":3,"name":"Third","album":{"picId":30}},
              {"id":2,"name":"Duplicate search occurrence","album":{"picId":20}}
            ]}}
            """;
    @Test void onePublicBatchEnrichesOnlyMissingCoversAndPreservesOrderAndAllMetadata() throws Exception {
        var calls = new ArrayList<String>();
        var source = new NeteaseSource((url, body, type, referer, cookie) -> {
            calls.add(url);
            if (calls.size() == 1) return json(SEARCH);
            assertNull(body); assertNull(type);
            assertTrue(url.startsWith("https://music.163.com/api/song/detail/?"));
            String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
            assertTrue(decoded.contains("ids=[2,3]"));
            return json("""
                    {"code":200,"songs":[
                    {"id":999,"album":{"picUrl":"https://p1.music.126.net/unrelated.jpg"}},
                    {"id":3,"name":"Wrong replacement title","album":{"picUrl":"https://p1.music.126.net/third.jpg"}},
                    {"id":2,"album":{"picUrl":"http://p1.music.126.net/first.jpg"}},
                    {"id":2,"album":{"picUrl":"https://p1.music.126.net/duplicate.jpg"}}]}
                    """);
        });
        var tracks = source.search("query", 1, 20);
        assertEquals(2, calls.size());
        assertEquals(List.of("2", "1", "3", "2"), tracks.stream().map(t -> t.id()).toList());
        assertEquals("First", tracks.get(0).title()); assertEquals("Original", tracks.get(0).album());
        assertEquals("Singer", tracks.get(0).artist()); assertEquals(1234, tracks.get(0).durationMs());
        assertEquals("2", tracks.get(0).mediaId()); assertEquals("netease", tracks.get(0).source());
        assertEquals("https://p1.music.126.net/first.jpg?param=512y512", tracks.get(0).artworkUrl());
        assertEquals("https://p1.music.126.net/existing.jpg?param=512y512", tracks.get(1).artworkUrl());
        assertEquals("Third", tracks.get(2).title());
        assertEquals(tracks.get(0).artworkUrl(), tracks.get(3).artworkUrl());
    }
    @Test void completeAndEmptyPagesDoNotTriggerDetailRequest() throws Exception {
        for (String response : List.of("{\"code\":200,\"result\":{\"songs\":[]}}",
                "{\"code\":200,\"result\":{\"songs\":[{\"id\":1,\"al\":{\"picUrl\":\"https://p1.music.126.net/a.jpg\"}}]}}")) {
            var count = new AtomicInteger();
            new NeteaseSource((u,b,t,r,c) -> { count.incrementAndGet(); return json(response); }).search("q",1,20);
            assertEquals(1,count.get());
        }
    }
    @Test void pageLimitBoundsTheSingleBatchAndInvalidLimitDoesNotIssueRequests() throws Exception {
        var count = new AtomicInteger();
        var source = new NeteaseSource((u,b,t,r,c) -> {
            if (count.incrementAndGet()==1) return json(SEARCH);
            assertTrue(URLDecoder.decode(u,StandardCharsets.UTF_8).contains("ids=[2]"));
            return json("{\"code\":200,\"songs\":[]}");
        });
        assertEquals(1,source.search("q",1000,1).size()); assertEquals(2,count.get());
        assertThrows(IllegalArgumentException.class,()->source.search("q",1,101)); assertEquals(2,count.get());
    }
    @Test void optionalMetadataFailuresAndInvalidUrlsKeepSuccessfulSearch() throws Exception {
        for(String reply:List.of("{\"code\":500}","{\"code\":200}","{\"code\":200,\"songs\":[{\"id\":2,\"album\":{\"picUrl\":\"file:///tmp/cover\"}}]}")) {
            var count=new AtomicInteger();
            var source=new NeteaseSource((u,b,t,r,c)->count.incrementAndGet()==1?json(SEARCH):json(reply));
            assertEquals(NeteaseSource.parseSearch(json(SEARCH),20),source.search("q",1,20));
        }
        for(IOException failure:List.of(new IOException("network"),new java.net.SocketTimeoutException("timeout"))) {
            var count=new AtomicInteger();
            var source=new NeteaseSource((u,b,t,r,c)->{if(count.incrementAndGet()==1)return json(SEARCH);throw failure;});
            assertEquals(NeteaseSource.parseSearch(json(SEARCH),20),source.search("q",1,20));
        }
    }
    @Test void searchAndDetailShareOneCredentialsSnapshotDespiteConcurrentSignOut() throws Exception {
        var cookies=new ArrayList<String>(); var holder=new NeteaseSource[1];
        holder[0]=new NeteaseSource((u,b,t,r,c)->{
            cookies.add(c); holder[0].credentials("new_session");
            return cookies.size()==1?json(SEARCH):json("{\"code\":200,\"songs\":[]}");
        });
        holder[0].credentials("original_session"); holder[0].search("q",1,20);
        assertEquals(2,cookies.size()); assertEquals(cookies.get(0),cookies.get(1));
        assertTrue(cookies.get(1).endsWith("original_session"));
    }
    @Test void cancellationIsNeverConvertedIntoSuccessfulSearchResults() {
        var count=new AtomicInteger();
        var source=new NeteaseSource((u,b,t,r,c)->{
            if(count.incrementAndGet()==1)return json(SEARCH);
            throw new InterruptedIOException("cancelled");
        });
        assertThrows(InterruptedIOException.class,()->source.search("q",1,20));
        var afterSearch=new NeteaseSource((u,b,t,r,c)->{Thread.currentThread().interrupt();return json(SEARCH);});
        try { assertThrows(InterruptedIOException.class,()->afterSearch.search("q",1,20)); }
        finally { Thread.interrupted(); }
    }
}
