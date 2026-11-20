// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class NativeArtistDiscoveryTest {
    private static final OnlineArtist ARTIST = new OnlineArtist("test", "known", "Same name");

    @Test void neteasePreservesStableIdsButNotGuestOrDuplicateIds() {
        var input = JsonParser.parseString("""
                {"result":{"songs":[{"id":6,"name":"Duet","ar":[
                    {"id":11,"name":"First"},{"id":0,"name":"Guest"},
                    {"id":12,"name":"Second"},{"id":11,"name":"First"},{"name":"Unknown"}
                ]}]}}
                """).getAsJsonObject();
        var track = NeteaseSource.parseSearch(input, 20).get(0);
        assertTrue(track.artist().contains("Guest"));
        assertEquals(List.of(new OnlineArtist("netease", "11", "First"), new OnlineArtist("netease", "12", "Second")), track.artists());
    }

    @Test void qqPreservesCanonicalMidButDoesNotGuessOneFromNumericId() {
        var input = JsonParser.parseString("""
                {"data":{"song":{"list":[{"songmid":"song1","songname":"Duet","singer":[
                    {"mid":"MIDfirst","name":"First"},{"singermid":"MIDsecond","name":"Second"},
                    {"id":999,"name":"No MID"}
                ]}]}}}
                """).getAsJsonObject();
        assertEquals(List.of(new OnlineArtist("qq", "MIDfirst", "First"), new OnlineArtist("qq", "MIDsecond", "Second")),
                QQMusicSource.parseSearch(input, 20).get(0).artists());
    }

    @Test void artistRequestPreservesProviderOrderAndRejectsSameNameDifferentIdentity() throws Exception {
        var page = new ArrayList<>(List.of(
                song("1", new OnlineArtist("test", "other", ARTIST.name())),
                song("2", new OnlineArtist("test", ARTIST.id(), "Alternate displayed name")),
                song("3", ARTIST)));
        for (int i = 0; page.size() < 20; i++) page.add(song("filler" + i));
        var provider = new Provider() {
            @Override public List<OnlineTrack> search(String query, int remotePage, int limit) {
                assertEquals(ARTIST.name(), query); assertEquals(2, remotePage); assertEquals(20, limit);
                return page;
            }
        };
        try (var player = player(provider)) {
            assertTrue(player.supportsArtistTracks("test"));
            var result = player.artistTracks(ARTIST, 2).get(3, TimeUnit.SECONDS);
            assertEquals(List.of("2", "3"), result.tracks().stream().map(OnlineTrack::id).toList());
            assertTrue(result.mayHaveMore(), "sparse filtered pages must not hide possible next pages");
            assertEquals(OnlineMusicPlayer.State.IDLE, player.snapshot().state());
        }
    }

    @Test void accountChangeInvalidatesInFlightArtistPage() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var provider = new Provider() {
            @Override public List<OnlineTrack> search(String query, int page, int limit) throws Exception {
                entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS));
                return List.of(song("stale", ARTIST));
            }
        };
        try (var player = player(provider)) {
            var pending = player.artistTracks(ARTIST, 1);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            player.credentials("test", "");
            release.countDown();
            assertTrue(pending.isCancelled());
        } finally { release.countDown(); }
    }

    @Test void legacyTracksAreDisplayOnlyAndCannotClaimForeignArtistMetadata() {
        assertTrue(new OnlineTrack("test", "1", "1", "title", "Artist", "", 0).artists().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> song("bad", new OnlineArtist("other", "1", "Artist")));
    }

    private static OnlineTrack song(String id, OnlineArtist... contributors) {
        return new OnlineTrack("test", id, id, "title", ARTIST.name(), "", 0, "", List.of(contributors));
    }

    private static OnlinePlayerSession player(OnlineMusicSource provider) {
        return new OnlinePlayerSession(List.of(provider),
                () -> { throw new AssertionError("Discovery must not open audio"); },
                uri -> { throw new AssertionError("Discovery must not download audio"); });
    }

    private abstract static class Provider implements OnlineMusicSource {
        @Override public String id() { return "test"; }
        @Override public boolean supportsArtistTracks() { return true; }
        @Override public void credentials(String credentials) {}
        @Override public Stream resolve(OnlineTrack track) { throw new AssertionError("Discovery must not resolve audio"); }
        @Override public LyricTimeline lyrics(OnlineTrack track) { throw new AssertionError("Discovery must not load lyrics"); }
    }
}
