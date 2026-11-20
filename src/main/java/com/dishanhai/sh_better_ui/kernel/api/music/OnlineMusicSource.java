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
    /** Whether search results carry reliable source-owned artist IDs. */
    default boolean supportsArtistTracks() { return false; }
    /** Remote search completions for the supplied text, in provider order. */
    default List<String> suggest(String query, int limit) throws Exception { throw new UnsupportedOperationException("Search suggestions unavailable"); }
    /** Account operations require credentials and run on the caller's worker. */
    default OnlineMusicAccount account() throws Exception { throw new UnsupportedOperationException("Account unavailable"); }
    default List<OnlinePlaylist> playlists(int page, int limit) throws Exception { throw new UnsupportedOperationException("Account playlists unavailable"); }
    default List<OnlineTrack> playlist(OnlinePlaylist playlist, int page, int limit) throws Exception { throw new UnsupportedOperationException("Playlist tracks unavailable"); }
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
