package com.dishanhai.sh_better_ui;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
import com.dishanhai.sh_better_ui.config.DynamicThemeSettings;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.config.ThemeOverrides;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.PlatformLiveActivityNetwork;
import com.dishanhai.sh_better_ui.kernel.internal.page.network.PlatformPageNetwork;
import com.dishanhai.sh_better_ui.kernel.internal.page.container.PlatformPageMenus;
import com.dishanhai.sh_better_ui.kernel.internal.richtext.network.PlatformRichSyncNetwork;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.concurrent.ConcurrentCommentedConfig;
import com.electronwill.nightconfig.core.io.WritingException;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

/** 山海：共用 UI 庫的共同入口，不載入任何客戶端或可選整合類別。 */
@Mod(ShBetterUi.MOD_ID)
public final class ShBetterUi {
    public static final String MOD_ID = "sh_better_ui";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String BACKDROP_SECTION = "glassBackdrop";
    private static final String UI_SECTION = "ui";
    private static volatile ModConfig clientConfig;

    public ShBetterUi(ModContainer container) {
        PlatformLiveActivityNetwork.register(container.getEventBus());
        PlatformPageNetwork.register(container.getEventBus());
        PlatformPageMenus.register(container.getEventBus());
        PlatformRichSyncNetwork.register(container.getEventBus());
        var defaults = ClientUiSettings.DEFAULT;
        var backdrop = defaults.backdrop();
        var builder = new ModConfigSpec.Builder().push(BACKDROP_SECTION);
        var enabled = builder.define("enabled", backdrop.enabled());
        var radius = builder.defineInRange("blurRadiusScale", backdrop.blurRadiusScale(), 0.0, 2.0);
        var strength = builder.defineInRange("strength", backdrop.strength(), 0.0, 1.0);
        var tint = builder.defineInRange("tintScale", backdrop.tintScale(), 0.0, 2.0);
        var grain = builder.defineInRange("grainScale", backdrop.grainScale(), 0.0, 2.0);
        var saturation = builder.defineInRange("saturationScale", backdrop.saturationScale(), 0.0, 2.0);
        var lift = builder.defineInRange("lift", backdrop.lift(), 0.0, 0.15);
        var rim = builder.defineInRange("rimScale", backdrop.rimScale(), 0.0, 2.0);
        var refraction = builder.defineInRange("refractionScale", backdrop.refractionScale(), 0.0, 2.0);
        var denseSampling = builder.define("denseSampling", backdrop.denseSampling());
        builder.pop().push(UI_SECTION);
        var highContrast = builder.define("highContrast", defaults.highContrast());
        var reducedMotion = builder.define("reducedMotion", defaults.reducedMotion());
        var widgetBackdropBlur = builder.define("widgetBackdropBlur", defaults.widgetBackdropBlur());
        var feedback = defaults.feedback();
        var hoverAnimation = builder.define("hoverAnimation", feedback.hoverAnimation());
        var pressAnimation = builder.define("pressAnimation", feedback.pressAnimation());
        var selectionPulse = builder.define("selectionPulse", feedback.selectionPulse());
        var tooltipDelay = builder.defineInRange("tooltipDelayMs", feedback.tooltipDelayMs(), 0,
                ControlFeedbackSettings.MAX_TOOLTIP_DELAY_MS);
        var tabs = defaults.tabs();
        var tabWheel = builder.define("tabWheelEnabled", tabs.wheelEnabled());
        var tabDrag = builder.define("tabDragEnabled", tabs.dragEnabled());
        var tabAutoReveal = builder.define("tabAutoReveal", tabs.autoReveal());
        var tabStep = builder.defineInRange("tabScrollStep", tabs.scrollStep(), TabScrollSettings.MIN_SCROLL_STEP,
                TabScrollSettings.MAX_SCROLL_STEP);
        var themeMode = builder.defineEnum("themeMode", defaults.theme().mode());
        var themeBase = builder.defineEnum("themeCustomBase", defaults.theme().customBase());
        var themeBackground = builder.define("themeCustomBackground", "", ThemeSettings::validColor);
        var themeSurface = builder.define("themeCustomSurface", "", ThemeSettings::validColor);
        var themeBorder = builder.define("themeCustomBorder", "", ThemeSettings::validColor);
        var themeText = builder.define("themeCustomText", "", ThemeSettings::validColor);
        var themeAccent = builder.define("themeCustomAccent", "", ThemeSettings::validColor);
        var themeEffects = builder.define("themeCustomEffects", "{}", ThemeOverrides::valid);
        var themeBackgroundEffect = builder.define("themeBackgroundEffect", ThemeSettings.DEFAULT_BACKGROUND_EFFECT,
                ThemeSettings::validBackgroundEffect);
        var hudBackgroundEffect = builder.define("hudBackgroundEffect", "", ThemeSettings::validHudBackgroundEffect);
        var hudBlurRadius = builder.defineInRange("hudBlurRadius", defaults.theme().hudBlurRadius().intValue(),
                0, ThemeSettings.MAX_HUD_BLUR_RADIUS);
        var dynamicTheme = builder.define("dynamicFixedTheme", "", DynamicThemeSettings::validFixedTheme);
        var dynamicSpeed = builder.defineInRange("dynamicAnimationPercent", defaults.dynamicTheme().animationPercent(),
                0, DynamicThemeSettings.MAX_ANIMATION_PERCENT);
        var dynamicLowPerformance = builder.define("dynamicLowPerformance", defaults.dynamicTheme().lowPerformance());
        var spatialEnabled = builder.define("spatialEnabled", defaults.spatial().enabled());
        var spatialShadows = builder.define("spatialShadows", defaults.spatial().shadows());
        var spatialLighting = builder.define("spatialLighting", defaults.spatial().lighting());
        var spatialBlur = builder.define("spatialBlur", defaults.spatial().blur());
        var spatialQuality = builder.defineEnum("spatialQuality", defaults.spatial().quality());
        var spatialProfiling = builder.define("spatialProfiling", defaults.spatial().profiling());
        var spec = builder.pop().build();
        container.getEventBus().addListener((ModConfigEvent event) -> {
            if (event.getConfig().getSpec() != spec) return;
            boolean loaded = spec.isLoaded();
            clientConfig = loaded ? event.getConfig() : null;
            ClientUiSettings.update(loaded
                    ? new ClientUiSettings(new GlassBackdropSettings(enabled.get(), radius.get().floatValue(),
                            strength.get().floatValue(), tint.get().floatValue(), grain.get().floatValue(),
                            saturation.get().floatValue(), lift.get().floatValue(), rim.get().floatValue(),
                            refraction.get().floatValue(), denseSampling.get()),
                            highContrast.get(), reducedMotion.get(), widgetBackdropBlur.get(),
                            new ControlFeedbackSettings(hoverAnimation.get(), pressAnimation.get(),
                                    selectionPulse.get(), tooltipDelay.get()),
                            new TabScrollSettings(tabWheel.get(), tabDrag.get(), tabAutoReveal.get(), tabStep.get()),
                            new ThemeSettings(themeMode.get(), themeBase.get(), themeBackground.get(),
                                    themeSurface.get(), themeBorder.get(), themeText.get(), themeAccent.get(),
                                        new ThemeOverrides(themeEffects.get()), themeBackgroundEffect.get(),
                                        hudBackgroundEffect.get(), hudBlurRadius.get()),
                                new DynamicThemeSettings(dynamicTheme.get(), dynamicSpeed.get(), dynamicLowPerformance.get()),
                                new SpatialStyle.Settings(spatialEnabled.get(), spatialShadows.get(), spatialLighting.get(),
                                        spatialBlur.get(), spatialQuality.get(), spatialProfiling.get()))
                    : defaults);
        });
        container.registerConfig(ModConfig.Type.CLIENT, spec);
    }

    public static boolean saveGlassBackdrop(GlassBackdropSettings settings) {
        return saveClientUiSettings(ClientUiSettings.current().withBackdrop(settings));
    }

    public static boolean saveClientUiSettings(ClientUiSettings settings) {
        if (com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.hasNativeAuthority()) {
            return com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel.saveClientSettings(settings);
        }
        ModConfig config = clientConfig;
        if (config == null || !(config.getSpec() instanceof ModConfigSpec spec)) return false;
        var loaded = config.getLoadedConfig();
        if (loaded == null || !(loaded.config() instanceof ConcurrentCommentedConfig data)) return false;
        CommentedConfig previousBackdrop = data.get(BACKDROP_SECTION);
        CommentedConfig previousUi = data.get(UI_SECTION);
        if (previousBackdrop == null || previousUi == null) return false;

        var backdrop = settings.backdrop();
        CommentedConfig updatedBackdrop = copySection(data, previousBackdrop);
        updatedBackdrop.set("enabled", backdrop.enabled());
        replaceScale(updatedBackdrop, "blurRadiusScale", backdrop.blurRadiusScale());
        replaceScale(updatedBackdrop, "strength", backdrop.strength());
        replaceScale(updatedBackdrop, "tintScale", backdrop.tintScale());
        replaceScale(updatedBackdrop, "grainScale", backdrop.grainScale());
        replaceScale(updatedBackdrop, "saturationScale", backdrop.saturationScale());
        replaceScale(updatedBackdrop, "lift", backdrop.lift());
        replaceScale(updatedBackdrop, "rimScale", backdrop.rimScale());
        replaceScale(updatedBackdrop, "refractionScale", backdrop.refractionScale());
        updatedBackdrop.set("denseSampling", backdrop.denseSampling());
        CommentedConfig updatedUi = copySection(data, previousUi);
        updatedUi.set("highContrast", settings.highContrast());
        updatedUi.set("reducedMotion", settings.reducedMotion());
        updatedUi.set("widgetBackdropBlur", settings.widgetBackdropBlur());
        var feedback = settings.feedback();
        updatedUi.set("hoverAnimation", feedback.hoverAnimation());
        updatedUi.set("pressAnimation", feedback.pressAnimation());
        updatedUi.set("selectionPulse", feedback.selectionPulse());
        updatedUi.set("tooltipDelayMs", feedback.tooltipDelayMs());
        var tabs = settings.tabs();
        updatedUi.set("tabWheelEnabled", tabs.wheelEnabled());
        updatedUi.set("tabDragEnabled", tabs.dragEnabled());
        updatedUi.set("tabAutoReveal", tabs.autoReveal());
        updatedUi.set("tabScrollStep", tabs.scrollStep());
        var theme = settings.theme();
        updatedUi.set("themeMode", theme.mode().name());
        updatedUi.set("themeCustomBase", theme.customBase().name());
        updatedUi.set("themeCustomBackground", theme.background());
        updatedUi.set("themeCustomSurface", theme.surface());
        updatedUi.set("themeCustomBorder", theme.border());
        updatedUi.set("themeCustomText", theme.text());
        updatedUi.set("themeCustomAccent", theme.accent());
        updatedUi.set("themeCustomEffects", theme.effects().json());
        updatedUi.set("themeBackgroundEffect", theme.backgroundEffect());
        updatedUi.set("hudBackgroundEffect", theme.hudBackgroundEffect());
        updatedUi.set("hudBlurRadius", theme.hudBlurRadius());
        var dynamic = settings.dynamicTheme();
        updatedUi.set("dynamicFixedTheme", dynamic.fixedTheme());
        updatedUi.set("dynamicAnimationPercent", dynamic.animationPercent());
        updatedUi.set("dynamicLowPerformance", dynamic.lowPerformance());
        var spatial = settings.spatial();
        updatedUi.set("spatialEnabled", spatial.enabled());
        updatedUi.set("spatialShadows", spatial.shadows());
        updatedUi.set("spatialLighting", spatial.lighting());
        updatedUi.set("spatialBlur", spatial.blur());
        updatedUi.set("spatialQuality", spatial.quality().name());
        updatedUi.set("spatialProfiling", spatial.profiling());

        replaceSections(data, updatedBackdrop, updatedUi);
        spec.afterReload();
        try {
            loaded.save();
        } catch (WritingException error) {
            replaceSections(data, previousBackdrop, previousUi);
            spec.afterReload();
            LOGGER.warn("Could not save {}", config.getFileName(), error);
            return false;
        }
        ClientUiSettings.update(settings);
        return true;
    }

    private static CommentedConfig copySection(CommentedConfig data, CommentedConfig previous) {
        CommentedConfig copy = data.createSubConfig();
        for (var entry : previous.entrySet()) {
            copy.set(entry.getKey(), entry.getRawValue());
        }
        copy.putAllComments(previous);
        return copy;
    }

    private static void replaceSections(ConcurrentCommentedConfig data, CommentedConfig backdrop, CommentedConfig ui) {
        data.bulkCommentedUpdate(root -> {
            root.set(BACKDROP_SECTION, backdrop);
            root.set(UI_SECTION, ui);
        });
    }

    private static void replaceScale(CommentedConfig section, String key, float value) {
        Number previous = section.get(key);
        if (previous.floatValue() != value) section.set(key, Double.valueOf(Float.toString(value)));
    }
}
