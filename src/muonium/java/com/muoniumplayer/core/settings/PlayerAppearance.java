// Author: dishanhai
package com.muoniumplayer.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Local presentation preferences, kept outside shared player preset exports. */
public final class PlayerAppearance {
    private static volatile boolean shbuiControls;
    private static volatile boolean shbuiTheme;

    private PlayerAppearance() {}

    public static boolean useShbuiControls() { return shbuiControls; }
    public static boolean useShbuiTheme() { return shbuiTheme; }

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

    static void readPreferences(JsonObject config) {
        shbuiControls = flag(config.get("shbuiControls"));
        shbuiTheme = flag(config.get("shbuiTheme"));
    }

    private static boolean flag(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }

    static JsonObject withPreferences(JsonObject config) {
        config.addProperty("shbuiControls", shbuiControls);
        config.addProperty("shbuiTheme", shbuiTheme);
        return config;
    }
}
