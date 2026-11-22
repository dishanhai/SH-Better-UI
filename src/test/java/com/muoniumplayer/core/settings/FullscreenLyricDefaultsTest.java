// Author: dishanhai
package com.muoniumplayer.core.settings;

import com.google.gson.JsonObject;
import com.muoniumplayer.core.screens.ncm.FullscreenLyricStyle;
import com.muoniumplayer.core.screens.ncm.foliabg.FoliaBackgroundConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullscreenLyricDefaultsTest {
    @Test
    void factoryDefaultsMatchTheSelectedAppearance() {
        JsonObject hud = HudConfig.defaultsJson();
        assertEquals(FullscreenLyricStyle.CLASSIC.ordinal(), hud.get("fullscreenLyricStyle").getAsInt());
        assertFalse(hud.get("fullscreenLyricFluidStatic").getAsBoolean());
        assertTrue(hud.get("fullscreenLyricEdgeBlur").getAsBoolean());
        assertTrue(FoliaBackgroundConfig.defaultsJson().get("appleCoverBackdrop").getAsBoolean());
    }

    @Test
    void savedHudChoicesOverrideTheDefaults() {
        JsonObject previous = HudConfig.toJson();
        try {
            JsonObject saved = HudConfig.defaultsJson();
            saved.addProperty("fullscreenLyricStyle", FullscreenLyricStyle.FUME.ordinal());
            saved.addProperty("fullscreenLyricFluidStatic", true);
            saved.addProperty("fullscreenLyricEdgeBlur", false);
            HudConfig.applyJson(saved);
            assertEquals(FullscreenLyricStyle.FUME, FullscreenLyricStyle.current());
            assertTrue(HudConfig.fullscreenLyricFluidStatic);
            assertFalse(HudConfig.fullscreenLyricEdgeBlur);
        } finally {
            HudConfig.applyJson(previous);
        }
    }

    @Test
    void existingBackgroundPresetSurvivesUpgradeUntilAppleIsEnabled() {
        JsonObject previous = FoliaBackgroundConfig.toJson();
        try {
            JsonObject saved = new JsonObject();
            saved.addProperty("backgroundKind", 1);
            FoliaBackgroundConfig.applyJson(saved);
            assertEquals(1, FoliaBackgroundConfig.backgroundKind);
            assertFalse(FoliaBackgroundConfig.appleCoverBackdrop);

            saved.addProperty("appleCoverBackdrop", true);
            FoliaBackgroundConfig.applyJson(saved);
            assertEquals(1, FoliaBackgroundConfig.backgroundKind);
            assertTrue(FoliaBackgroundConfig.appleCoverBackdrop);

            JsonObject legacy = new JsonObject();
            legacy.addProperty("enabled", false);
            FoliaBackgroundConfig.applyJson(legacy);
            assertEquals(0, FoliaBackgroundConfig.backgroundKind);
            assertFalse(FoliaBackgroundConfig.appleCoverBackdrop);
        } finally {
            FoliaBackgroundConfig.applyJson(previous);
        }
    }

    @Test
    void backgroundUsesDefaultForMissingSettingAndKeepsExplicitOff() {
        JsonObject previous = FoliaBackgroundConfig.toJson();
        try {
            JsonObject saved = new JsonObject();
            saved.addProperty("appleCoverBackdrop", false);
            FoliaBackgroundConfig.applyJson(saved);
            assertFalse(FoliaBackgroundConfig.appleCoverBackdrop);

            FoliaBackgroundConfig.applyJson(new JsonObject());
            assertTrue(FoliaBackgroundConfig.appleCoverBackdrop);
        } finally {
            FoliaBackgroundConfig.applyJson(previous);
        }
    }
}
