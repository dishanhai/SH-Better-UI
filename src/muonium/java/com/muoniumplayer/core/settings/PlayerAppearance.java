// Author: dishanhai
package com.muoniumplayer.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Local presentation preferences, kept outside shared player preset exports. */
public final class PlayerAppearance {
    private static volatile boolean shbuiControls;
    private static volatile boolean shbuiTheme;
    private static volatile boolean replaceTitleWithLyrics = true;

    private PlayerAppearance() {}

    public static boolean useShbuiControls() { return shbuiControls; }
    public static boolean useShbuiTheme() { return shbuiTheme; }
    public static boolean replaceTitleWithLyrics() { return replaceTitleWithLyrics; }

    public static void setShbuiControls(boolean enabled) {
        if (shbuiControls != enabled) {
            shbuiControls = enabled;
            HudConfig.save();
        }
    }

    public static void setShbuiTheme(boolean enabled) {
        if (shbuiTheme != enabled) {
            shbuiTheme = enabled;
            HudConfig.save();
        }
    }

    public static void setReplaceTitleWithLyrics(boolean enabled) {
        if (replaceTitleWithLyrics != enabled) {
            replaceTitleWithLyrics = enabled;
            HudConfig.save();
        }
    }

    static void readPreferences(JsonObject config) {
        shbuiControls = flag(config.get("shbuiControls"));
        shbuiTheme = flag(config.get("shbuiTheme"));
        JsonElement replaceTitle = config.get("replaceTitleWithLyrics");
        replaceTitleWithLyrics = !booleanValue(replaceTitle) || replaceTitle.getAsBoolean();
    }

    private static boolean flag(JsonElement value) {
        return booleanValue(value) && value.getAsBoolean();
    }

    private static boolean booleanValue(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }

    static JsonObject withPreferences(JsonObject config) {
        config.addProperty("shbuiControls", shbuiControls);
        config.addProperty("shbuiTheme", shbuiTheme);
        config.addProperty("replaceTitleWithLyrics", replaceTitleWithLyrics);
        return config;
    }
}
