package com.muoniumplayer.core.settings;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAppearanceTest {
    @Test
    void oldSettingsDefaultToTheOriginalPresentationAndBothOptionsRoundTripSeparately() {
        JsonObject previous = PlayerAppearance.withPreferences(new JsonObject());
        try {
            PlayerAppearance.readPreferences(new JsonObject());
            assertFalse(PlayerAppearance.useShbuiControls());
            assertFalse(PlayerAppearance.useShbuiTheme());
            for (boolean controls : new boolean[]{false, true}) {
                for (boolean theme : new boolean[]{false, true}) {
                    JsonObject saved = new JsonObject();
                    saved.addProperty("shbuiControls", controls);
                    saved.addProperty("shbuiTheme", theme);
                    PlayerAppearance.readPreferences(saved);
                    assertEquals(controls, PlayerAppearance.useShbuiControls());
                    assertEquals(theme, PlayerAppearance.useShbuiTheme());
                    assertEquals(saved, PlayerAppearance.withPreferences(new JsonObject()));
                }
            }
            JsonObject invalid = new JsonObject();
            invalid.addProperty("shbuiControls", "true");
            invalid.add("shbuiTheme", new JsonObject());
            PlayerAppearance.readPreferences(invalid);
            assertFalse(PlayerAppearance.useShbuiControls());
            assertFalse(PlayerAppearance.useShbuiTheme());
        } finally {
            PlayerAppearance.readPreferences(previous);
        }
    }
}
