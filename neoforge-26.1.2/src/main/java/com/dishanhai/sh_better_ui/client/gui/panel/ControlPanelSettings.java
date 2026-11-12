// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

/** Saved 26.x client options. The screen edits a separate draft until Apply succeeds. */
public record ControlPanelSettings(ThemeSettings theme, boolean highContrast, boolean reducedMotion,
                                   boolean backgroundBlur, boolean widgetBlur, boolean glassEnabled,
                                   int glassBlur, int glassRefraction, int glassRim) {
    public static final int MAX_GLASS_SCALE = 200;
    public static final ControlPanelSettings DEFAULT = new ControlPanelSettings(ThemeSettings.DEFAULT,
            false, false, true, true, true, 100, 100, 100);
    private static final System.Logger LOGGER = System.getLogger(ControlPanelSettings.class.getName());
    private static volatile ControlPanelSettings settings = DEFAULT;
    private static @Nullable Path file;

    public ControlPanelSettings {
        Objects.requireNonNull(theme, "theme");
        if (!validScale(glassBlur) || !validScale(glassRefraction) || !validScale(glassRim)) {
            throw new IllegalArgumentException("Glass scale is outside [0, 200]");
        }
    }

    public static ControlPanelSettings current() { return settings; }

    public UiPreferences preferences(boolean vanillaHighContrast) {
        return new UiPreferences(highContrast || vanillaHighContrast, reducedMotion, widgetBlur);
    }

    public GlassOptics optics(GlassOptics preset) {
        float highlight = preset.edgeHighlight();
        float rim = glassRim <= 100 ? highlight * glassRim / 100
                : highlight + (1 - highlight) * (glassRim - 100) / 100;
        return new GlassOptics(preset.transmission(), Math.min(32, preset.refraction() * glassRefraction / 100),
                preset.lensing(), preset.scattering(), rim,
                preset.thickness(), Math.min(32, preset.blurRadius() * glassBlur / 100));
    }

    ControlPanelSettings withTheme(ThemeSettings next) {
        return new ControlPanelSettings(next, highContrast, reducedMotion, backgroundBlur, widgetBlur,
                glassEnabled, glassBlur, glassRefraction, glassRim);
    }

    ControlPanelSettings withDisplay(boolean contrast, boolean motion, boolean blur) {
        return new ControlPanelSettings(theme, contrast, motion, backgroundBlur, blur,
                glassEnabled, glassBlur, glassRefraction, glassRim);
    }

    ControlPanelSettings withGlass(boolean background, boolean enabled, int blur, int refraction, int rim) {
        return new ControlPanelSettings(theme, highContrast, reducedMotion, background, widgetBlur,
                enabled, blur, refraction, rim);
    }

    static synchronized void load(Path configDirectory) {
        file = configDirectory.resolve("sh_better_ui").resolve("control-panel.properties");
        settings = DEFAULT;
        if (!Files.exists(file)) return;
        try (var input = Files.newInputStream(file)) {
            var properties = new Properties();
            properties.load(input);
            var theme = ThemeSettings.DEFAULT.withMode(mode(properties.getProperty("theme")));
            settings = new ControlPanelSettings(theme, flag(properties, "highContrast", DEFAULT.highContrast),
                    flag(properties, "reducedMotion", DEFAULT.reducedMotion),
                    flag(properties, "backgroundBlur", DEFAULT.backgroundBlur),
                    flag(properties, "widgetBlur", DEFAULT.widgetBlur),
                    flag(properties, "glassEnabled", DEFAULT.glassEnabled),
                    scale(properties, "glassBlur", DEFAULT.glassBlur),
                    scale(properties, "glassRefraction", DEFAULT.glassRefraction),
                    scale(properties, "glassRim", DEFAULT.glassRim));
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read the SHBUI control panel settings.", failure);
        }
    }

    static synchronized boolean save(ControlPanelSettings draft) {
        Objects.requireNonNull(draft, "draft");
        if (file == null) return false;
        Path temporary = null;
        try {
            Path directory = file.getParent();
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "control-panel", ".properties.tmp");
            var properties = new Properties();
            properties.setProperty("theme", draft.theme.mode().name());
            properties.setProperty("highContrast", Boolean.toString(draft.highContrast));
            properties.setProperty("reducedMotion", Boolean.toString(draft.reducedMotion));
            properties.setProperty("backgroundBlur", Boolean.toString(draft.backgroundBlur));
            properties.setProperty("widgetBlur", Boolean.toString(draft.widgetBlur));
            properties.setProperty("glassEnabled", Boolean.toString(draft.glassEnabled));
            properties.setProperty("glassBlur", Integer.toString(draft.glassBlur));
            properties.setProperty("glassRefraction", Integer.toString(draft.glassRefraction));
            properties.setProperty("glassRim", Integer.toString(draft.glassRim));
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "SH-Better-UI NeoForge 26 client options");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            settings = draft;
            return true;
        } catch (IOException failure) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save the SHBUI control panel settings.", failure);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    LOGGER.log(System.Logger.Level.WARNING, "Could not remove the temporary settings file.", failure);
                }
            }
        }
    }

    private static boolean flag(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static int scale(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try {
            int number = Integer.parseInt(value);
            return validScale(number) ? number : fallback;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static boolean validScale(int value) { return value >= 0 && value <= MAX_GLASS_SCALE; }

    private static ThemeSettings.Mode mode(@Nullable String value) {
        if (value != null) {
            for (var choice : ThemeSettings.Mode.values()) {
                if (choice != ThemeSettings.Mode.CUSTOM && choice.name().equals(value)) return choice;
            }
        }
        return ThemeSettings.DEFAULT.mode();
    }
}
