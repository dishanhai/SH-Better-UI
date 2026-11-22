// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.music.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Request and response format used by the authenticated Apple Music web player for lyrics. */
public final class AppleMusicLyrics {
    private AppleMusicLyrics() {}

    public static boolean isCatalogSongId(String songId) {
        return songId != null && songId.matches("[1-9][0-9]{0,19}");
    }

    /** Build a relative path for a catalog song. The host supplies the authorized transport. */
    public static String requestPath(String storefront, String songId) {
        if (storefront == null || !storefront.matches("[a-z]{2}") || !isCatalogSongId(songId)) {
            throw new IllegalArgumentException("Invalid Apple Music storefront or catalog song ID");
        }
        return "/v1/catalog/" + storefront + "/songs/" + songId
                + "?include%5Bsongs%5D=lyrics,syllable-lyrics&extend=ttmlLocalizations";
    }

    /** Prefer the syllable relationship and fall back to the line relationship if it's missing. */
    public static String readTtml(JsonObject response) {
        JsonArray songs = array(response, "data");
        if (songs == null || songs.size() == 0) return "";
        JsonObject relationships = object(object(songs.get(0)), "relationships");
        String timed = relationshipTtml(relationships, "syllable-lyrics");
        return timed.isEmpty() ? relationshipTtml(relationships, "lyrics") : timed;
    }

    private static String relationshipTtml(JsonObject relationships, String name) {
        JsonArray data = array(object(relationships, name), "data");
        if (data == null) return "";
        for (JsonElement lyric : data) {
            JsonObject attributes = object(object(lyric), "attributes");
            String ttml = string(attributes, "ttml");
            if (ttml.isEmpty()) ttml = string(attributes, "ttmlLocalizations");
            if (!ttml.isEmpty()) return ttml;
        }
        return "";
    }

    private static String string(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString().strip() : "";
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent == null ? null : object(parent.get(key));
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
