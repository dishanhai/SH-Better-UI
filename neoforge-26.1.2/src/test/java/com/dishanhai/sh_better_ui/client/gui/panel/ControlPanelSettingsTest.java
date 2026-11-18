// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
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
    private Object savedSettings;
    private Object savedFile;

    @org.junit.jupiter.api.BeforeEach
    void isolateSettings() throws ReflectiveOperationException {
        var state = ControlPanelSettings.class.getDeclaredField("settings");
        var file = ControlPanelSettings.class.getDeclaredField("file");
        state.setAccessible(true); file.setAccessible(true);
        savedSettings = state.get(null); savedFile = file.get(null);
        state.set(null, ControlPanelSettings.DEFAULT); file.set(null, null);
    }

    @org.junit.jupiter.api.AfterEach
    void restoreSettings() throws ReflectiveOperationException {
        var state = ControlPanelSettings.class.getDeclaredField("settings");
        var file = ControlPanelSettings.class.getDeclaredField("file");
        state.setAccessible(true); file.setAccessible(true);
        state.set(null, savedSettings); file.set(null, savedFile);
    }

    @Test
    void feedbackRoundTripsAndUnrelatedEditsPreserveIt() {
        var feedback = new ControlFeedbackSettings(false, false, false, 2000);
        var draft = ControlPanelSettings.DEFAULT.withFeedback(feedback)
                .withTheme(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.LIQUID_GLASS))
                .withDisplay(true, true, false).withGlass(false, true, 70, 120, 160);
        assertEquals(feedback, draft.feedback());
        assertEquals(feedback, draft.preferences(false).feedback());
        assertEquals(feedback, draft.preferences(true).feedback());
        assertTrue(draft.preferences(true).highContrast());
        assertTrue(draft.preferences(false).reducedMotion());
        ControlPanelSettings.load(config);
        assertTrue(ControlPanelSettings.save(draft));
        ControlPanelSettings.load(config);
        assertEquals(draft, ControlPanelSettings.current());
        var reset = draft.withFeedback(ControlFeedbackSettings.DEFAULT);
        assertEquals(ControlFeedbackSettings.DEFAULT, reset.feedback());
        assertEquals(draft, reset.withFeedback(feedback));
    }

    @Test
    void legacyFilesAndConstructorUseFeedbackDefaults() throws IOException {
        var directory = Files.createDirectory(config.resolve("sh_better_ui"));
        Files.writeString(directory.resolve("control-panel.properties"), "highContrast=true\n");
        ControlPanelSettings.load(config);
        assertEquals(ControlFeedbackSettings.DEFAULT, ControlPanelSettings.current().feedback());
        assertTrue(ControlPanelSettings.current().highContrast());
        var legacy = new ControlPanelSettings(ThemeSettings.DEFAULT, false, false, true, true, true, 100, 100, 100);
        assertEquals(ControlFeedbackSettings.DEFAULT, legacy.feedback());
    }

    @Test
    void malformedFeedbackFieldsFallbackIndividually() throws IOException {
        var directory = Files.createDirectory(config.resolve("sh_better_ui"));
        var path = directory.resolve("control-panel.properties");
        for (String delay : new String[]{"-1", "2001", "bad", "999999999999999"}) {
            Files.writeString(path, "hoverAnimation=false\npressAnimation=invalid\nselectionPulse=false\n"
                    + "tooltipDelayMs=" + delay + "\nglassRefraction=130\n");
            ControlPanelSettings.load(config);
            assertEquals(new ControlFeedbackSettings(false, true, false, 0), ControlPanelSettings.current().feedback());
            assertEquals(130, ControlPanelSettings.current().glassRefraction());
        }
        Files.writeString(path, "tooltipDelayMs=750\n");
        ControlPanelSettings.load(config);
        assertEquals(750, ControlPanelSettings.current().feedback().tooltipDelayMs());
    }


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
        assertFalse(ControlPanelSettings.save(initial.withDisplay(true, true, false)
                .withFeedback(new ControlFeedbackSettings(false, false, false, 850))));
        assertEquals(initial, ControlPanelSettings.current());
    }
}
