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
    String id();
    List<OnlineTrack> search(String query, int page, int limit) throws Exception;
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
