// Author: dishanhai
package com.dishanhai.sh_better_ui.config;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThemePaletteEditTest {
    @Test void paletteEditsPreserveBackgroundAndDormantHudSettings() {
        var initial = ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM)
                .withBackgroundEffect("sh_better_ui:image").withHudBackgroundEffect("laserFlow").withHudBlurRadius(5)
                .withEffects(new ThemeOverrides("{\"glass\":{\"strength\":0.5}}"));
        var next = initial.withPalette(ThemeSettings.Base.LIGHT, "#123456", "", "", "", "aabbcc");
        assertEquals(0xFF123456, UiTheme.resolve(next).background());
        assertEquals(0xFFAABBCC, UiTheme.resolve(next).accent());
        assertTrue(UiTheme.resolve(next).light());
        assertEquals(initial.backgroundEffect(), next.backgroundEffect());
        assertEquals(initial.hudBackgroundEffect(), next.hudBackgroundEffect());
        assertEquals(initial.hudBlurRadius(), next.hudBlurRadius());
        assertEquals(initial.effects(), next.effects());
        assertEquals("", initial.background());
        assertEquals(next, new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(next), ThemeSettings.class));
    }
}
