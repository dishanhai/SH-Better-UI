// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.muoniumplayer.core.ncm.music.NeteaseMusicService;
import com.muoniumplayer.core.ncm.music.dto.Artist;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.ncm.music.dto.PlayList;
import com.muoniumplayer.core.screens.ncm.NCMScreen;
import com.muoniumplayer.core.screens.ncm.panels.PlaylistPanel;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public final class PlayingTrackPages {
    private PlayingTrackPages() {}

    public static String firstArtist(Music track) {
        if (track == null || track.getArtists() == null) return "";
        return track.getArtists().stream().filter(Objects::nonNull)
                .map(Artist::getName).filter(name -> name != null && !name.isBlank())
                .map(String::strip).filter(name -> !name.equalsIgnoreCase("Unknown"))
                .findFirst().orElse("");
    }

    public static void open(NCMScreen screen, Music track, boolean album) {
        if (track == null) return;
        String artist = firstArtist(track);
        String albumName = track.getAlbum() == null ? "" : track.getAlbum().getName();
        String name = album ? albumName : artist;
        if (name == null || name.isBlank()) return;

        String title = Component.translatable("sh_better_ui.playing_metadata." + (album ? "album" : "artist"),
                name.strip()).getString();
        String keyword = album && !artist.isBlank() ? name + " " + artist : name;
        screen.openPlayingTrackPage(title, () -> {
            var candidates = NeteaseMusicService.search(track.getSource(), keyword, 50);
            var matches = candidates.stream().filter(candidate -> candidate.getSource() == track.getSource())
                    .filter(candidate -> matchesArtist(candidate.getArtists(), artist))
                    .filter(candidate -> !album || (candidate.getAlbum() != null
                            && sameName(albumName, candidate.getAlbum().getName())));
            List<Music> songs = Stream.concat(Stream.of(track), matches).distinct().toList();
            var playlist = new PlayList(0, title, "", songs.size(), 0, null, "", 0);
            playlist.setPlatform(track.getSource());
            playlist.setSearchMode(true);
            playlist.setMusics(new CopyOnWriteArrayList<>(songs));
            playlist.setMusicsQueried(true);
            playlist.setMusicsLoaded(true);
            return new PlaylistPanel(playlist);
        });
    }

    private static boolean matchesArtist(List<Artist> artists, String name) {
        return name.isBlank() || (artists != null && artists.stream().filter(Objects::nonNull)
                .anyMatch(artist -> sameName(name, artist.getName())));
    }

    private static boolean sameName(String expected, String actual) {
        return actual != null && expected.strip().equalsIgnoreCase(actual.strip());
    }
}
