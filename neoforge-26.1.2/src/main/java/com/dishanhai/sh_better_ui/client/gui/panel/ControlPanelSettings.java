// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.config.ThemeOverrides;
import com.dishanhai.sh_better_ui.config.ThemePaletteArchive;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
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
                                   int glassBlur, int glassRefraction, int glassRim, ControlFeedbackSettings feedback) {
    public static final int MAX_GLASS_SCALE = 200;
    public static final ControlPanelSettings DEFAULT = new ControlPanelSettings(ThemeSettings.DEFAULT.withBackgroundEffect("nativeBackdrop"),
            false, false, true, true, true, 100, 100, 100);
    private static final System.Logger LOGGER = System.getLogger(ControlPanelSettings.class.getName());
    private static volatile ControlPanelSettings settings = DEFAULT;
    private static @Nullable Path file;

    public ControlPanelSettings {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(feedback, "feedback");
        if (!validScale(glassBlur) || !validScale(glassRefraction) || !validScale(glassRim)) {
            throw new IllegalArgumentException("Glass scale is outside [0, 200]");
        }
    }

    /** Compatibility constructor for callers predating configurable control feedback. */
    public ControlPanelSettings(ThemeSettings theme, boolean highContrast, boolean reducedMotion,
                                boolean backgroundBlur, boolean widgetBlur, boolean glassEnabled,
                                int glassBlur, int glassRefraction, int glassRim) {
        this(theme, highContrast, reducedMotion, backgroundBlur, widgetBlur, glassEnabled,
                glassBlur, glassRefraction, glassRim, ControlFeedbackSettings.DEFAULT);
    }

    public static ControlPanelSettings current() { return settings; }

    public UiPreferences preferences(boolean vanillaHighContrast) {
        return new UiPreferences(highContrast || vanillaHighContrast, reducedMotion, widgetBlur, feedback);
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
                glassEnabled, glassBlur, glassRefraction, glassRim, feedback);
    }

    ControlPanelSettings withDisplay(boolean contrast, boolean motion, boolean blur) {
        return new ControlPanelSettings(theme, contrast, motion, backgroundBlur, blur,
                glassEnabled, glassBlur, glassRefraction, glassRim, feedback);
    }

    ControlPanelSettings withGlass(boolean background, boolean enabled, int blur, int refraction, int rim) {
        return new ControlPanelSettings(theme, highContrast, reducedMotion, background, widgetBlur,
                enabled, blur, refraction, rim, feedback);
    }

    ControlPanelSettings withFeedback(ControlFeedbackSettings next) {
        return new ControlPanelSettings(theme, highContrast, reducedMotion, backgroundBlur, widgetBlur,
                glassEnabled, glassBlur, glassRefraction, glassRim, next);
    }

    static synchronized void load(Path configDirectory) {
        file = configDirectory.resolve("sh_better_ui").resolve("control-panel.properties");
        settings = DEFAULT;
        if (!Files.exists(file)) return;
        try (var input = Files.newInputStream(file)) {
            var properties = new Properties();
            properties.load(input);
            var theme = readTheme(properties);
            settings = new ControlPanelSettings(theme, flag(properties, "highContrast", DEFAULT.highContrast),
                    flag(properties, "reducedMotion", DEFAULT.reducedMotion),
                    flag(properties, "backgroundBlur", DEFAULT.backgroundBlur),
                    flag(properties, "widgetBlur", DEFAULT.widgetBlur),
                    flag(properties, "glassEnabled", DEFAULT.glassEnabled),
                    scale(properties, "glassBlur", DEFAULT.glassBlur),
                    scale(properties, "glassRefraction", DEFAULT.glassRefraction),
                    scale(properties, "glassRim", DEFAULT.glassRim),
                    new ControlFeedbackSettings(
                            flag(properties, "hoverAnimation", ControlFeedbackSettings.DEFAULT.hoverAnimation()),
                            flag(properties, "pressAnimation", ControlFeedbackSettings.DEFAULT.pressAnimation()),
                            flag(properties, "selectionPulse", ControlFeedbackSettings.DEFAULT.selectionPulse()),
                            boundedNumber(properties, "tooltipDelayMs", ControlFeedbackSettings.DEFAULT.tooltipDelayMs(),
                                    ControlFeedbackSettings.MAX_TOOLTIP_DELAY_MS)));
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read the SHBUI control panel settings.", failure);
        }
    }

    /** Saves only the theme against the latest applied snapshot under the settings lock. */
    public static synchronized boolean saveTheme(ThemeSettings theme) {
        return save(settings.withTheme(theme));
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
            writeTheme(properties, draft.theme);
            properties.setProperty("highContrast", Boolean.toString(draft.highContrast));
            properties.setProperty("reducedMotion", Boolean.toString(draft.reducedMotion));
            properties.setProperty("backgroundBlur", Boolean.toString(draft.backgroundBlur));
            properties.setProperty("widgetBlur", Boolean.toString(draft.widgetBlur));
            properties.setProperty("glassEnabled", Boolean.toString(draft.glassEnabled));
            properties.setProperty("glassBlur", Integer.toString(draft.glassBlur));
            properties.setProperty("glassRefraction", Integer.toString(draft.glassRefraction));
            properties.setProperty("glassRim", Integer.toString(draft.glassRim));
            properties.setProperty("hoverAnimation", Boolean.toString(draft.feedback.hoverAnimation()));
            properties.setProperty("pressAnimation", Boolean.toString(draft.feedback.pressAnimation()));
            properties.setProperty("selectionPulse", Boolean.toString(draft.feedback.selectionPulse()));
            properties.setProperty("tooltipDelayMs", Integer.toString(draft.feedback.tooltipDelayMs()));
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

    /** Mode-only older 26.x files continue to inherit all absent fields. Dormant legacy data is retained. */
    static ThemeSettings readTheme(Properties properties) {
        var defaults = DEFAULT.theme().withMode(mode(properties.getProperty("theme")));
        try {
            return new ThemeSettings(defaults.mode(), ThemeSettings.Base.valueOf(properties.getProperty("theme.base", defaults.customBase().name())),
                    properties.getProperty("theme.background", defaults.background()), properties.getProperty("theme.surface", defaults.surface()),
                    properties.getProperty("theme.border", defaults.border()), properties.getProperty("theme.text", defaults.text()),
                    properties.getProperty("theme.accent", defaults.accent()),
                    new ThemeOverrides(properties.getProperty("theme.effects", "{}")),
                    properties.getProperty("theme.backgroundEffect", defaults.backgroundEffect()),
                    properties.getProperty("theme.hudBackgroundEffect", defaults.hudBackgroundEffect()),
                    Integer.parseInt(properties.getProperty("theme.hudBlurRadius", defaults.hudBlurRadius().toString())),
                    properties.getProperty("theme.registeredThemeId", ""),
                    new ThemePaletteArchive(properties.getProperty("theme.paletteArchive", "{}")));
        } catch (IllegalArgumentException invalid) {
            LOGGER.log(System.Logger.Level.WARNING, "Invalid saved theme; preserving other client options and using the selected mode defaults.", invalid);
            return defaults;
        }
    }
    static void writeTheme(Properties properties, ThemeSettings theme) {
        properties.setProperty("theme", theme.mode().name());
        properties.setProperty("theme.base", theme.customBase().name());
        properties.setProperty("theme.background", theme.background()); properties.setProperty("theme.surface", theme.surface());
        properties.setProperty("theme.border", theme.border()); properties.setProperty("theme.text", theme.text());
        properties.setProperty("theme.accent", theme.accent()); properties.setProperty("theme.effects", theme.effects().json());
        properties.setProperty("theme.backgroundEffect", theme.backgroundEffect());
        properties.setProperty("theme.hudBackgroundEffect", theme.hudBackgroundEffect());
        properties.setProperty("theme.hudBlurRadius", theme.hudBlurRadius().toString());
        properties.setProperty("theme.registeredThemeId", theme.registeredThemeId());
        properties.setProperty("theme.paletteArchive", theme.paletteArchive().json());
    }

    private static boolean flag(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static int scale(Properties properties, String key, int fallback) {
        return boundedNumber(properties, key, fallback, MAX_GLASS_SCALE);
    }

    private static int boundedNumber(Properties properties, String key, int fallback, int maximum) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try {
            int number = Integer.parseInt(value);
            return number >= 0 && number <= maximum ? number : fallback;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static boolean validScale(int value) { return value >= 0 && value <= MAX_GLASS_SCALE; }

    private static ThemeSettings.Mode mode(@Nullable String value) {
        if (value != null) {
            for (var choice : ThemeSettings.Mode.values()) {
                if (choice.name().equals(value)) return choice;
            }
        }
        return ThemeSettings.DEFAULT.mode();
    }
}
