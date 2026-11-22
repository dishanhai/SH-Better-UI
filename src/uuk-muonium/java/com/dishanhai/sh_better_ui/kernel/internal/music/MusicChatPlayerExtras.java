// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.client.gui.panel.MusicChatPlatform;
import com.muoniumplayer.core.MuoniumPlayerExtension;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.LocalPlaylistStore;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.screens.hud.MuoniumHudProviders;
import com.muoniumplayer.core.screens.ncm.MusicLyricsPanel;
import com.muoniumplayer.core.screens.ncm.NCMScreen;
import com.muoniumplayer.core.screens.ncm.NCMTheme;
import com.muoniumplayer.core.screens.ncm.panels.LikeButton;
import com.muoniumplayer.core.settings.PlayerAppearance;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Main-thread bridge to the same player actions used by the visible controls. */
final class MusicChatPlayerExtras {
    private MusicChatPlayerExtras() {}

    static String openLyrics(NCMScreen player) {
        Music track = CloudMusic.currentlyPlaying;
        if (track == null) return text("extra.no_track");
        if (player.musicLyricsPanel == null) player.musicLyricsPanel = MusicLyricsPanel.takePrewarmed(track);
        return text("extra.lyrics_opened");
    }

    static String setTranslatedLyrics(boolean enabled) {
        var extension = MuoniumPlayerExtension.getInstance();
        if (extension == null) return text("extra.unavailable");
        extension.musicLyrics.showTranslation.setValue(enabled);
        return text(enabled ? "extra.translation_on" : "extra.translation_off");
    }

    static String setFloatingLyrics(boolean enabled) {
        MuoniumHudProviders.setLyricsVisible(enabled);
        return text(enabled ? "extra.floating_on" : "extra.floating_off");
    }

    static String favoriteCurrent(NCMScreen player, boolean enabled) {
        Music track = CloudMusic.currentlyPlaying;
        if (track == null) return text("extra.no_track");
        if (!LikeButton.setLiked(track, enabled, player::openConfirmation)) return text("extra.favorite_unavailable");
        // Existing player flow may still need account login, confirmation, or an asynchronous response.
        return text("extra.favorite_requested");
    }

    static String addCurrentToPlaylist(NCMScreen player, String name) {
        Music track = CloudMusic.currentlyPlaying;
        if (track == null) return text("extra.no_track");
        String target = name == null ? "" : name.strip();
        List<com.muoniumplayer.core.ncm.music.dto.PlayList> matches = LocalPlaylistStore.list().stream()
                .filter(playlist -> playlist.getName().equalsIgnoreCase(target)).toList();
        if (matches.size() != 1) {
            player.openAddToPlaylist(track);
            return text("extra.playlist_choose");
        }
        return switch (LocalPlaylistStore.addTrackWithResult(matches.get(0), track)) {
            case ADDED -> text("extra.playlist_added", matches.get(0).getName());
            case ALREADY_PRESENT -> text("extra.playlist_present", matches.get(0).getName());
            case UNAVAILABLE, SAVE_FAILED -> text("extra.playlist_failed");
        };
    }

    static String openControlCenter() {
        MusicChatPlatform.openControlCenter();
        return text("extra.control_opened");
    }

    static String setMusicHud(boolean enabled) {
        MuoniumHudProviders.setMusicInfoVisible(enabled);
        return text(enabled ? "extra.hud_on" : "extra.hud_off");
    }

    static String chooseTheme(String name) {
        String target = name == null ? "" : name.strip();
        if (target.contains(":")) return MusicChatPlatform.chooseGlobalTheme(target);
        String global = MusicChatPlatform.findGlobalTheme(target);
        if (global != null) return MusicChatPlatform.chooseGlobalTheme(global);
        NCMTheme.ThemePreset preset = NCMTheme.byIdOrNull(target);
        if (preset == null) {
            for (var candidate : NCMTheme.ThemePreset.values()) {
                if (candidate.getDisplayName().equalsIgnoreCase(target)) {
                    preset = candidate;
                    break;
                }
            }
        }
        if (preset == null || !NCMTheme.isSelectable(preset)) return text("extra.theme_unavailable");
        if (PlayerAppearance.useShbuiTheme() && preset.gradient() == null) {
            return text("extra.theme_host_active");
        }
        NCMTheme.setCurrent(preset);
        return text("extra.theme_selected", preset.getDisplayName());
    }

    private static String text(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.music_chat." + key, arguments).getString();
    }
}
