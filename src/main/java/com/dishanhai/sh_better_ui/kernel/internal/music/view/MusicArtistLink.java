// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineArtist;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineMusicPlayer;
import com.dishanhai.sh_better_ui.kernel.api.music.OnlineTrack;

/** The visible credit points only to a real artist in the currently playing provider. */
record MusicArtistLink(OnlineTrack track, OnlineArtist artist, long epoch, long routeGeneration) {
    static MusicArtistLink create(OnlineMusicPlayer player, OnlineTrack track, long epoch, long routeGeneration) {
        if (track == null || !player.sources().contains(track.source()) || !player.supportsArtistTracks(track.source()))
            return null;
        return track.artists().stream().filter(credit -> credit.source().equals(track.source())).findFirst()
                .map(credit -> new MusicArtistLink(track, credit, epoch, routeGeneration)).orElse(null);
    }

    boolean current(OnlineMusicPlayer player, OnlineTrack current, long currentEpoch, long currentGeneration) {
        return track == current && epoch == currentEpoch && routeGeneration == currentGeneration
                && player.sources().contains(artist.source()) && player.supportsArtistTracks(artist.source());
    }

    MusicFrontendRoutes.Route route() {
        return new MusicFrontendRoutes.Route(MusicFrontendRoutes.Page.ARTIST, artist.source(), artist.id());
    }
}
