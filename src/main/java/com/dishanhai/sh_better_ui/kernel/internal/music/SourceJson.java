// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music;

import com.google.gson.*;

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
}
