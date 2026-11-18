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
        assertEquals(ControlPanelSettings.DEFAULT.theme(), loaded.theme());
        assertFalse(loaded.backgroundBlur());
        assertTrue(loaded.widgetBlur());
        assertEquals(100, loaded.glassBlur());
        assertEquals(110, loaded.glassRefraction());
    }

    @Test void completeThemeRoundTripsWithoutDroppingDormantLegacyFields() {
        var archive = com.dishanhai.sh_better_ui.config.ThemePaletteArchive.EMPTY.with("example:legacy",
                new com.dishanhai.sh_better_ui.config.ThemePaletteArchive.Palette(ThemeSettings.Base.LIGHT,
                        "#112233", "#223344", "#334455", "#445566", "#556677"));
        var theme = new ThemeSettings(ThemeSettings.Mode.CUSTOM, ThemeSettings.Base.LIGHT,
                "#123456", "#234567", "#345678", "#456789", "#56789a",
                new com.dishanhai.sh_better_ui.config.ThemeOverrides("{\"legacyEffect\":{\"strength\":0.6}}"),
                "example:unmigrated", "legacyHudEffect", 7, "example:legacy", archive);
        var draft = ControlPanelSettings.DEFAULT.withTheme(theme).withDisplay(true,true,false).withGlass(false,true,80,120,60);
        ControlPanelSettings.load(config); assertTrue(ControlPanelSettings.save(draft));
        ControlPanelSettings.load(config); assertEquals(draft,ControlPanelSettings.current());
        assertEquals("example:unmigrated",ControlPanelSettings.current().theme().backgroundEffect());
    }

    @Test void oldModeOnlyCustomFilesLoadWithoutDiscardingTheirMode() throws IOException {
        Files.createDirectory(config.resolve("sh_better_ui"));
        Files.writeString(config.resolve("sh_better_ui/control-panel.properties"),"theme=CUSTOM\nreducedMotion=true\n");
        ControlPanelSettings.load(config);
        assertEquals(ControlPanelSettings.DEFAULT.theme().withMode(ThemeSettings.Mode.CUSTOM),ControlPanelSettings.current().theme());
        assertTrue(ControlPanelSettings.current().reducedMotion());
    }

    @Test void malformedThemeDoesNotResetUnrelatedClientOptions() throws IOException {
        Files.createDirectory(config.resolve("sh_better_ui"));
        Files.writeString(config.resolve("sh_better_ui/control-panel.properties"),
                "theme=CUSTOM\ntheme.accent=invalid\nreducedMotion=true\nbackgroundBlur=false\n");
        ControlPanelSettings.load(config);
        assertEquals(ControlPanelSettings.DEFAULT.theme().withMode(ThemeSettings.Mode.CUSTOM),ControlPanelSettings.current().theme());
        assertTrue(ControlPanelSettings.current().reducedMotion()); assertFalse(ControlPanelSettings.current().backgroundBlur());
    }

    @Test
    void leavesActiveOptionsUnchangedWhenTheFileCannotBeSaved() throws IOException {
        Files.writeString(config.resolve("sh_better_ui"), "");
        ControlPanelSettings.load(config);
        var initial = ControlPanelSettings.current();
        var changedTheme = initial.theme().withMode(ThemeSettings.Mode.CUSTOM)
                .withPalette(ThemeSettings.Base.LIGHT, "#123456", "#234567", "#345678", "#456789", "#56789a")
                .withBackgroundEffect("nativeGradient");
        assertFalse(ControlPanelSettings.save(initial.withDisplay(true, true, false).withTheme(changedTheme)));
        assertEquals(initial, ControlPanelSettings.current());
    }
}
