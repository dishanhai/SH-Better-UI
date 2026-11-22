// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicTypography;
import com.dishanhai.sh_better_ui.kernel.api.music.*;
import com.dishanhai.sh_better_ui.kernel.api.music.control.MusicTextControl;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Two independently focusable credits sharing a row's visual and input clip. */
final class MusicTrackCredits {
    private final PreviewContext host;
    private final MusicTextControl artist, album;
    private OnlineTrack track;

    MusicTrackCredits(PreviewContext host, Supplier<Bounds> clip,
                      BiConsumer<OnlineArtist, MusicTextControl> openArtist,
                      BiConsumer<OnlineAlbum, MusicTextControl> openAlbum) {
        this.host = host;
        artist = control(clip, () -> openArtist.accept(track.artists().get(0), artistControl()));
        album = control(clip, () -> openAlbum.accept(track.release(), albumControl()));
    }

    private MusicTextControl artistControl() { return artist; }
    private MusicTextControl albumControl() { return album; }

    private MusicTextControl control(Supplier<Bounds> clip, Runnable action) {
        var button = MusicScrollRows.add(host, new MusicTextControl(new Bounds(0, 0, 12, 16),
                Component.empty(), host.preferences(), action), clip);
        button.setVariant(MusicTextControl.Variant.LINK);
        button.setVisible(false);
        return button;
    }

    boolean bind(OnlineMusicPlayer player, OnlineTrack next, int x, int y, int width) {
        if (!Objects.equals(track, next)) cancel();
        track = next;
        boolean hasArtist = next != null && !next.artists().isEmpty() && player.supportsArtistTracks(next.source());
        boolean hasAlbum = next != null && next.release() != null && player.supportsAlbumDetail(next.source());
        if ((!hasArtist && !hasAlbum) || width < 24) {
            artist.setVisible(false);
            album.setVisible(false);
            return false;
        }
        String artistName = hasArtist ? next.artists().get(0).name() : next.artist();
        String albumName = hasAlbum ? next.release().name() : next.album();
        int artistWidth = artistName.isBlank() ? 0 : Math.min(albumName.isBlank() ? width : width / 2,
                host.font().width(MusicTypography.text(artistName)) + 8);
        bind(artist, "artist.open", artistName, hasArtist, x, y, artistWidth);
        bind(album, "album.open", albumName, hasAlbum, x + artistWidth + 2, y, width - artistWidth - 2);
        return true;
    }

    private static void bind(MusicTextControl control, String label, String name, boolean enabled, int x, int y, int max) {
        control.setVisible(!name.isBlank() && max >= 12);
        control.setAvailable(control.visible && enabled);
        if (!control.visible) return;
        Component title = MusicTypography.text(name);
        if (!control.getMessage().equals(title)) {
            control.rebind(title);
            control.setPreview(enabled ? Component.translatable("sh_better_ui.online_music." + label, name) : title);
        }
        control.setX(x);
        control.setY(y);
        control.setWidth(max);
    }

    void cancel() {
        artist.cancelInteraction();
        album.cancelInteraction();
    }
}
