// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ControlPanelSettingsTest {
    @TempDir Path config;

    @Test
    void appliesAndReloadsTheSavedClientOptions() {
        var initial = ControlPanelSettings.DEFAULT;
        var draft = initial.withTheme(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.LIQUID_GLASS))
                .withDisplay(true, true, false).withGlass(false, true, 55, 165, 200);

        ControlPanelSettings.load(config);
        assertEquals(initial, ControlPanelSettings.current());
        assertTrue(ControlPanelSettings.save(draft));
        assertEquals(draft, ControlPanelSettings.current());
        ControlPanelSettings.load(config);
        assertEquals(draft, ControlPanelSettings.current());

        var preset = GlassMaterial.preset(GlassVariant.REGULAR).optics();
        var adjusted = draft.optics(preset);
        assertEquals(preset.blurRadius() * .55f, adjusted.blurRadius(), .001f);
        assertEquals(preset.refraction() * 1.65f, adjusted.refraction(), .001f);
        assertEquals(1f, adjusted.edgeHighlight(), .001f);
        assertTrue(draft.preferences(false).highContrast());
        assertFalse(draft.preferences(false).useBackdropBlur());
    }

    @Test
    void ignoresIndividualInvalidValuesAndKeepsValidOptions() throws IOException {
        var directory = Files.createDirectory(config.resolve("sh_better_ui"));
        Files.writeString(directory.resolve("control-panel.properties"),
                "theme=unsupported\nbackgroundBlur=false\nwidgetBlur=invalid\nglassBlur=999\nglassRefraction=110\n");

        ControlPanelSettings.load(config);
        var loaded = ControlPanelSettings.current();
        assertEquals(ThemeSettings.DEFAULT, loaded.theme());
        assertFalse(loaded.backgroundBlur());
        assertTrue(loaded.widgetBlur());
        assertEquals(100, loaded.glassBlur());
        assertEquals(110, loaded.glassRefraction());
    }

    @Test
    void leavesActiveOptionsUnchangedWhenTheFileCannotBeSaved() throws IOException {
        Files.writeString(config.resolve("sh_better_ui"), "");
        ControlPanelSettings.load(config);
        var initial = ControlPanelSettings.current();
        assertFalse(ControlPanelSettings.save(initial.withDisplay(true, true, false)));
        assertEquals(initial, ControlPanelSettings.current());
    }
}
