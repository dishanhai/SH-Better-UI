// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.SongEncyclopedia;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/** Read-only song commentary through the player's existing AI channel. */
public final class SongAiReview {
    private final MusicChatConfig config;
    private final boolean automatic;

    SongAiReview(MusicChatConfig config, boolean automatic) {
        this.config = config;
        this.automatic = automatic && config != null;
    }

    public static SongAiReview load() throws IOException {
        return MusicChatConfig.loadSongReview();
    }

    public boolean automatic() { return automatic; }
    public boolean configured() { return config != null; }

    public CompletableFuture<String> request(String title, String artist, String album,
                                             SongEncyclopedia notes, String language) {
        if (!configured()) throw new IllegalStateException("Song review requires an AI channel");
        var request = MusicChatClient.review(config, trackData(title, artist, album, notes), language);
        var result = request.thenApply(MusicChatClient.Reply::text);
        result.whenComplete((value, error) -> { if (result.isCancelled()) request.cancel(true); });
        return result;
    }

    static JsonObject trackData(String title, String artist, String album, SongEncyclopedia notes) {
        var song = new JsonObject();
        song.addProperty("title", MusicChatClient.metadata(title));
        song.addProperty("artist", MusicChatClient.metadata(artist));
        song.addProperty("album", MusicChatClient.metadata(album));
        if (notes != null) {
            song.addProperty("genre", MusicChatClient.metadata(notes.genre()));
            song.addProperty("introduction", MusicChatClient.metadata(notes.introduction()));
        }
        return song;
    }

    public static String failureKey(Throwable error) { return MusicChatClient.describe(error).key; }
}
