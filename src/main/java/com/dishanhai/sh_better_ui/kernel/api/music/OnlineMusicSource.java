// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music;

import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import java.net.URI;
import java.util.List;

/** Blocking provider operations. The player invokes these on its workers, never the render thread. */
public interface OnlineMusicSource {
    /** Blocking provider boundary, called only by the login worker. A fresh attempt must isolate its cookies. */
    interface QrChallenge extends AutoCloseable {
        OnlineMusicLogin.Image image();
        Poll poll() throws Exception;
        @Override default void close() {}
    }
    /** Provider-only result. Cookies must be nonempty on CONFIRMED; never log or display them. */
    record Poll(OnlineMusicLogin.State state, String cookie) {
        @Override public String toString() { return "Poll[" + state + "]"; }
    }
    default QrChallenge qrLogin() throws Exception { throw new UnsupportedOperationException("QR login unavailable"); }
    /** Capture request credentials without network I/O. A source with mutable credentials must override. */
    default OnlineMusicSource requestSnapshot() { return this; }
    String id();
    List<OnlineTrack> search(String query, int page, int limit) throws Exception;
    /** A catalog may offer comment browsing without offering playback. */
    default boolean supportsPlayback() { return true; }
    /** Orders provided by the source, with the initial order first. Empty means no comments. */
    default List<OnlineMusicComments.Order> commentOrders() { return List.of(); }
    /** Public comments for the exact source track, using one-based pages. */
    default OnlineMusicComments comments(OnlineTrack track, OnlineMusicComments.Order order, int page, int limit) throws Exception {
        throw new UnsupportedOperationException("Music comments unavailable");
    }
    default OnlineMusicComments.ReplyPage commentReplies(
            OnlineTrack track, OnlineMusicComments.Entry parent, String cursor, int limit) throws Exception {
        throw new UnsupportedOperationException("Music comment replies unavailable");
    }
    /** Whether search results carry reliable source-owned artist IDs. */
    default boolean supportsArtistTracks() { return false; }
    default OnlineArtistTracks artistTracks(OnlineArtist artist, int page, int limit) throws Exception {
        var raw = List.copyOf(search(artist.name(), page, limit));
        var matches = raw.stream().limit(limit)
                .filter(song -> artist.source().equals(song.source()) && song.artists().stream()
                        .anyMatch(contributor -> artist.id().equals(contributor.id())))
                .toList();
        return new OnlineArtistTracks(matches, page < 1000 && raw.size() >= limit);
    }
    default boolean supportsArtistOverview() { return false; }
    default OnlineArtistOverview artistOverview(OnlineArtist artist, int page, int limit) throws Exception {
        throw new UnsupportedOperationException();
    }
    default boolean supportsAlbumDetail() { return false; }
    default OnlineAlbumDetail albumDetail(OnlineAlbum album) throws Exception { throw new UnsupportedOperationException(); }
    /** Remote search completions for the supplied text, in provider order. */
    default List<String> suggest(String query, int limit) throws Exception { throw new UnsupportedOperationException("Search suggestions unavailable"); }
    /** Account operations require credentials and run on the caller's worker. */
    default OnlineMusicAccount account() throws Exception { throw new UnsupportedOperationException("Account unavailable"); }
    default List<OnlinePlaylist> playlists(int page, int limit) throws Exception { throw new UnsupportedOperationException("Account playlists unavailable"); }
    default List<OnlineTrack> playlist(OnlinePlaylist playlist, int page, int limit) throws Exception { throw new UnsupportedOperationException("Playlist tracks unavailable"); }
    /** A complete ordered detail, if implemented by this source. Do not return a partial playlist. */
    default boolean supportsPlaylistDetail() { return false; }
    default OnlinePlaylistDetail playlistDetail(String playlistId) throws Exception {
        throw new UnsupportedOperationException("Ordered playlist detail unavailable");
    }
    default List<OnlineTrack> liked(int page, int limit) throws Exception { throw new UnsupportedOperationException("Liked tracks unavailable"); }
    /** Whether this provider supports modifying liked tracks. */
    default boolean supportsLikes() { return false; }
    default void like(OnlineTrack track, boolean liked) throws Exception { throw new UnsupportedOperationException("Liking tracks unavailable"); }
    /** Only playable MP3 streams. Null means the platform did not grant a stream. */
    Stream resolve(OnlineTrack track) throws Exception;
    LyricTimeline lyrics(OnlineTrack track) throws Exception;
    /** Optional, session-only credentials supplied explicitly by the owner. Never persist or log these. */
    default void credentials(String cookie) { throw new UnsupportedOperationException("Source has no account support"); }
    final class LoginRequired extends java.io.IOException {
        public LoginRequired() { super("Music source requires a signed-in session"); }
    }

    /** A preview uses its own absolute lyric offset and actual length. */
    record Stream(URI uri, long offsetMs, long durationMs, boolean preview) {}
}
