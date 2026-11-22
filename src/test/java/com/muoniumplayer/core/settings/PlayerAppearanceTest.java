package com.muoniumplayer.core.settings;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAppearanceTest {
    @Test
    void oldSettingsAndIndependentPreferencesRoundTrip() {
        JsonObject previous = PlayerAppearance.withPreferences(new JsonObject());
        try {
            PlayerAppearance.readPreferences(new JsonObject());
            assertFalse(PlayerAppearance.useShbuiControls());
            assertFalse(PlayerAppearance.useShbuiTheme());
            assertTrue(PlayerAppearance.replaceTitleWithLyrics());
            for (boolean controls : new boolean[]{false, true}) {
                for (boolean theme : new boolean[]{false, true}) {
                    JsonObject saved = new JsonObject();
                    saved.addProperty("shbuiControls", controls);
                    saved.addProperty("shbuiTheme", theme);
                    saved.addProperty("replaceTitleWithLyrics", false);
                    PlayerAppearance.readPreferences(saved);
                    assertEquals(controls, PlayerAppearance.useShbuiControls());
                    assertEquals(theme, PlayerAppearance.useShbuiTheme());
                    assertFalse(PlayerAppearance.replaceTitleWithLyrics());
                    assertEquals(saved, PlayerAppearance.withPreferences(new JsonObject()));
                }
            }
            JsonObject invalid = new JsonObject();
            invalid.addProperty("shbuiControls", "true");
            invalid.add("shbuiTheme", new JsonObject());
            invalid.addProperty("replaceTitleWithLyrics", "false");
            PlayerAppearance.readPreferences(invalid);
            assertFalse(PlayerAppearance.useShbuiControls());
            assertFalse(PlayerAppearance.useShbuiTheme());
            assertTrue(PlayerAppearance.replaceTitleWithLyrics());
        } finally {
            PlayerAppearance.readPreferences(previous);
        }
    }
}
