// Modified for SHBUI by dishanhai. See META-INF/third-party/muonium-player-2.6.0/SOURCE.
package com.muoniumplayer.core.ncm.apple;

/** Retains parsing of legacy Apple cache identifiers for extensions and existing playlists. */
public final class ApplePlaybackResolver {

    public static final String PSEUDO_URL_PREFIX = "muonium-apple://song/";

    private ApplePlaybackResolver() {
    }

    /**
     * The embedded PCM decoder has no native Apple subscription stream. Returning null selects
     * the existing cross-source and preview fallback. Official subscription playback uses MusicKit.
     * @deprecated Kept for callers compiled against the upstream player.
     */
    @Deprecated
    public static String resolvePseudoUrl(String songId) {
        return null;
    }

    /** 从伪 URL 还原歌曲 id；不是本协议的 URL 返回 null。 */
    public static String songIdFromPseudoUrl(String playUrl) {
        if (playUrl == null || !playUrl.startsWith(PSEUDO_URL_PREFIX)) return null;
        String id = playUrl.substring(PSEUDO_URL_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }
}
