package com.dishanhai.sh_better_ui.config;

import com.dishanhai.sh_better_ui.kernel.api.EngineState;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.internal.EnginePublication;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Settings;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 山海：两个加载器共用的客户端界面设置快照。 */
public record ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                               boolean widgetBackdropBlur, ControlFeedbackSettings feedback, TabScrollSettings tabs,
                               ThemeSettings theme, DynamicThemeSettings dynamicTheme, Settings spatial) {
    public static final ClientUiSettings DEFAULT = new ClientUiSettings(GlassBackdropSettings.DEFAULT, false, false, true,
            ControlFeedbackSettings.DEFAULT, TabScrollSettings.DEFAULT);
    private static final AtomicReference<ClientUiSettings> LEGACY = new AtomicReference<>();

    public ClientUiSettings {
        Objects.requireNonNull(backdrop);
        Objects.requireNonNull(feedback);
        Objects.requireNonNull(tabs);
        Objects.requireNonNull(theme);
        Objects.requireNonNull(dynamicTheme);
        Objects.requireNonNull(spatial);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                            boolean widgetBackdropBlur, ControlFeedbackSettings feedback, TabScrollSettings tabs,
                            ThemeSettings theme, DynamicThemeSettings dynamicTheme) {
        this(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme, dynamicTheme,
                Settings.DEFAULT);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                            boolean widgetBackdropBlur, ControlFeedbackSettings feedback, TabScrollSettings tabs,
                            ThemeSettings theme) {
        this(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme, DynamicThemeSettings.DEFAULT);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                            boolean widgetBackdropBlur, ControlFeedbackSettings feedback, TabScrollSettings tabs) {
        this(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, ThemeSettings.DEFAULT);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                            boolean widgetBackdropBlur) {
        this(backdrop, highContrast, reducedMotion, widgetBackdropBlur, ControlFeedbackSettings.DEFAULT,
                TabScrollSettings.DEFAULT);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion) {
        this(backdrop, highContrast, reducedMotion, DEFAULT.widgetBackdropBlur());
    }

    public static ClientUiSettings current() {
        var engine = EnginePublication.clientEngine();
        if (engine != null && engine.lifecycle().state() == EngineState.RUNNING) {
            return engine.configs().get(UIApis.CLIENT_SETTINGS);
        }
        return Objects.requireNonNullElse(legacySnapshot(), DEFAULT);
    }

    /** Latest loader snapshot, available for the initial import and the compatibility runtime. */
    public static ClientUiSettings legacySnapshot() { return LEGACY.get(); }

    /** Updates the loader snapshot used before the Kernel starts and in compatibility mode. */
    public static void update(ClientUiSettings settings) {
        LEGACY.set(Objects.requireNonNull(settings));
    }

    static void updateBackdrop(GlassBackdropSettings backdrop) {
        LEGACY.updateAndGet(settings -> Objects.requireNonNullElse(settings, DEFAULT).withBackdrop(backdrop));
    }

    public boolean validForGlobalConfig() {
        return inRange(backdrop.blurRadiusScale(), 2f) && inRange(backdrop.strength(), 1f)
                && inRange(backdrop.tintScale(), 2f) && inRange(backdrop.grainScale(), 2f)
                && inRange(backdrop.saturationScale(), 2f) && inRange(backdrop.lift(), 0.15f)
                && inRange(backdrop.rimScale(), 2f) && inRange(backdrop.refractionScale(), 2f);
    }

    private static boolean inRange(float value, float maximum) {
        return value >= 0f && value <= maximum;
    }

    public ClientUiSettings withBackdrop(GlassBackdropSettings settings) {
        return new ClientUiSettings(settings, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withDisplay(boolean highContrast, boolean reducedMotion, boolean widgetBackdropBlur) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withFeedback(ControlFeedbackSettings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, settings, tabs, theme,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withTabs(TabScrollSettings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, settings, theme,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withTheme(ThemeSettings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, settings,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withDynamicTheme(DynamicThemeSettings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme,
                settings, spatial);
    }

    public ClientUiSettings withSpatial(Settings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme,
                dynamicTheme, settings);
    }
}
