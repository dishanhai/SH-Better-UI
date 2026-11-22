// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.screens.ncm.NCMPanel;
import com.muoniumplayer.core.screens.ncm.NCMScreen;
import net.minecraft.network.chat.Component;

/** Maps the embedded player's current transport state to the UUK chat page. */
public final class MusicChatEntry {
    private final NCMScreen player;
    private MusicChatScreen page;

    public MusicChatEntry(NCMScreen player) {
        this.player = player;
    }

    public static String label() { return Component.translatable("sh_better_ui.music_chat.title").getString(); }

    public void open() {
        if (page != null && player.getCurrentPanel() == page) {
            player.setPendingPanelNavigationKey(null);
            return;
        }
        if (page == null) page = new MusicChatScreen(player, MusicChatEntry::snapshot);
        player.setCurrentPanel(page);
    }

    public boolean isPage(NCMPanel panel) {
        return page != null && panel == page;
    }

    public boolean closeSettings(NCMPanel panel) {
        return isPage(panel) && page.closeSettings();
    }

    public void pageHidden(NCMPanel panel) {
        if (isPage(panel)) page.suspend();
    }

    public void hostClosed() {
        if (page != null) page.resetSession();
    }

    private static MusicChatClient.Song snapshot() {
        var playback = CloudMusic.getSnapshot();
        var track = playback.music;
        if (track == null) return null;
        return new MusicChatClient.Song(track.getName(), track.getArtistsName(),
                track.getAlbum() == null ? "" : track.getAlbum().getName(), track.getSource().name(),
                playback.positionMs, playback.durationMs, playback.playing);
    }
}
