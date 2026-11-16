package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrystalDriftIntegrationTest {
    @Test
    void builtinRegistryPublishesIndependentRendererAndEditableSchema() {
        var spec = ShBetterUiApi.backgrounds().find(CrystalDrift.ID).orElseThrow();
        assertEquals(CrystalDrift.ID, spec.settingsGroup());
        assertEquals(CrystalDriftProfile.DEFAULT, spec.defaultProfile());
        var first = spec.factory().create(null);
        var second = spec.factory().create(null);
        assertNotSame(first, second);
        assertInstanceOf(CrystalDrift.class, first);
        first.close(); second.close();
        var template = ThemeEffects.template(CrystalDrift.ID);
        assertEquals(96, template.get("count").getAsInt());
        assertEquals("FULL", template.get("placement").getAsString());
        var fields = ThemeEffectFields.fields(CrystalDrift.ID);
        assertTrue(fields.stream().anyMatch(f -> f.name().equals("accentColor") && f.kind() == ThemeEffectFields.Kind.ARGB));
        assertTrue(fields.stream().anyMatch(f -> f.name().equals("placement") && f.options().size() == 4));
        assertTrue(fields.stream().anyMatch(f -> f.name().equals("holdToGather") && f.kind() == ThemeEffectFields.Kind.TOGGLE));
        var override = new JsonObject();
        override.addProperty("material", "CHROME");
        override.addProperty("placement", "LEFT");
        assertDoesNotThrow(() -> ThemeEffects.validate(CrystalDrift.ID, override));
        override.addProperty("material", "UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> ThemeEffects.validate(CrystalDrift.ID, override));
    }
}
