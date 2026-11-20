// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.dishanhai.sh_better_ui.kernel.api.music.OnlineArtist;
import com.google.gson.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

final class SourceJson {
    static JsonObject object(JsonObject parent, String key) {
        var value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    static JsonArray array(JsonObject parent, String key) {
        var value = parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    static String text(JsonObject parent, String key) {
        var value = parent.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
    static long number(JsonObject parent, String key) {
        try { return Long.parseLong(text(parent, key)); } catch (NumberFormatException ignored) { return 0; }
    }
    static String artists(JsonArray array) {
        var names = new java.util.ArrayList<String>();
        for (var entry : array) if (entry.isJsonObject()) names.add(text(entry.getAsJsonObject(), "name"));
        return String.join(" / ", names);
    }
    /** Only stable provider IDs may make the rendered artist name navigable. */
    static List<OnlineArtist> artistIdentities(JsonArray array, String source, Function<JsonObject, String> id) {
        var artists = new LinkedHashMap<String, OnlineArtist>();
        for (var entry : array) {
            if (!entry.isJsonObject()) continue;
            var artist = entry.getAsJsonObject();
            String key = id.apply(artist);
            String name = text(artist, "name").strip();
            if (key.isEmpty() || name.isEmpty() || name.length() > 200) continue;
            artists.putIfAbsent(key, new OnlineArtist(source, key, name));
            if (artists.size() == 32) break;
        }
        return List.copyOf(artists.values());
    }
}
