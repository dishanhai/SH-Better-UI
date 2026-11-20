package com.muoniumplayer.core.ncm.apple;

import com.muoniumplayer.core.ncm.music.dto.Music;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds official song links from a stored catalog identity. */
public final class AppleMusicLink {
    private static final Set<String> STOREFRONTS = Arrays.stream(Locale.getISOCountries())
            .map(code -> code.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());

    private AppleMusicLink() {}

    public static String forTrack(Music track, String storefront) {
        if (track == null || !track.isApple() || storefront == null || !STOREFRONTS.contains(storefront)) {
            return "";
        }
        String id = track.getAppleCatalogId();
        if (!id.matches("[1-9][0-9]{0,19}")) return "";
        return "https://music.apple.com/" + storefront + "/song/" + id;
    }
}
