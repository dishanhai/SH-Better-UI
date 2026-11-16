// Author: dishanhai
package com.dishanhai.sh_better_ui.client.theme;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.music.ClientMusic;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.Theme;
import com.dishanhai.sh_better_ui.theme.ThemeRegistry;
import com.dishanhai.sh_better_ui.theme.ThemeResolver;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.ThemeSnapshot;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.context.ContextKey;
import com.dishanhai.sh_better_ui.theme.context.ContextSnapshot;
import com.dishanhai.sh_better_ui.theme.context.ThemeCondition;
import com.dishanhai.sh_better_ui.theme.context.ThemeRule;
import com.dishanhai.sh_better_ui.theme.palette.AudioReactiveTheme;
import com.dishanhai.sh_better_ui.theme.palette.ThemePalette;
import com.dishanhai.sh_better_ui.theme.palette.ThemePaletteGenerator;
import com.dishanhai.sh_better_ui.theme.resource.ThemeJsonParser;
import com.dishanhai.sh_better_ui.theme.resource.ThemeReloadManager;
import com.dishanhai.sh_better_ui.theme.resource.ThemeResourceLoader;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.TokenExpression;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.token.TokenType;
import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.dishanhai.sh_better_ui.theme.context.ContextKeys.*;

/** Physical-client bridge. The Forge and NeoForge entry points call it from their own lifecycle events. */
public final class MinecraftThemes {
    public static final String CONFIGURED_PALETTE_ID = "sh_better_ui:configured_palette";
    private static final ThemeRegistry FALLBACK_REGISTRY = BuiltinThemes.registry();
    private static ThemeRegistry paletteRegistry;
    private static Map<String, ThemeToken<?>> paletteCatalog;
    private static final Map<String, ThemeSnapshot> SELECTED_CACHE = new LinkedHashMap<>();
    private String configuredThemeId;
    private Theme configuredDefinition;
    private ThemeRegistry configuredRegistry;
    public static boolean isConfiguredPalette(String id) {
        var current = runtime;
        return current != null && Objects.equals(id, current.configuredThemeId)
                && current.manager.registry().find(id).orElse(null) == current.configuredDefinition;
    }
    /** Uses immutable registry identity for reload invalidation; no resolver allocation on a cached frame. */
    public static UiTheme registeredPalette(String id) {
        var current = runtime;
        var registry = current == null || stopped ? FALLBACK_REGISTRY : current.manager.registry();
        var catalog = current == null || stopped ? BUILTIN_CATALOG : current.manager.tokenCatalog();
        var snapshot = registeredSnapshot(registry, catalog, id);
        return snapshot == null ? null : UiTheme.fromTokens(snapshot);
    }
    static synchronized ThemeSnapshot registeredSnapshot(ThemeRegistry registry, Map<String, ThemeToken<?>> catalog, String id) {
        if (registry != paletteRegistry || !catalog.equals(paletteCatalog)) {
            SELECTED_CACHE.clear(); paletteRegistry = registry; paletteCatalog = catalog;
        }
        if (registry.find(id).isEmpty()) return null;
        var existing = SELECTED_CACHE.get(id); if (existing != null) return existing;
        var result = new ThemeResolver().resolve(registry, catalog, java.util.List.of(id), null);
        if (SELECTED_CACHE.size() >= 32) SELECTED_CACHE.clear();
        SELECTED_CACHE.put(id, result); return result;
    }

    private static final Map<String, ThemeToken<?>> BUILTIN_CATALOG = builtinCatalog();
    private static final ThemeReloadManager RELOAD = new ThemeReloadManager(MinecraftThemes::manager);
    private static volatile ThemeResourceLoader loader = new ThemeResourceLoader(new ThemeJsonParser());
    private static volatile MinecraftThemes runtime;
    private static volatile boolean stopped;
    private static ThemeJsonParser parser = new ThemeJsonParser();

    private final ThemeManager manager = new ThemeManager(UiFrameScheduler::nowMs,
            failure -> System.getLogger("sh_better_ui.theme").log(System.Logger.Level.WARNING,
                    failure.getMessage(), failure));
    private final ThemePaletteGenerator palettes = new ThemePaletteGenerator();
    private final MinecraftThemeMusic music = new MinecraftThemeMusic(palettes);
    private final Map<ContextKey<?>, Object> external = new LinkedHashMap<>();
    private ContextSnapshot environment = ContextSnapshot.empty();
    private ClientUiSettings previousSettings;
    private ThemeSnapshot paletteSnapshot;
    private UiTheme palette;
    private String skyDimension;
    private long nextSkySample;
    private int skyColor;

    private MinecraftThemes() {
        var dim = Set.<ContextKey<?>>of(DIMENSION);
        var transition = TransitionSpec.of(450, Easing.STANDARD);
        manager.registerRule(ThemeRule.builder("sh_better_ui:dimension/nether").theme(BuiltinThemes.NETHER)
                .when(ThemeCondition.of(dim, ctx -> ctx.get(DIMENSION).equals("minecraft:the_nether")))
                .priority(100).stability(250).debounce(80).transition(transition).build());
        manager.registerRule(ThemeRule.builder("sh_better_ui:dimension/end").theme(BuiltinThemes.END)
                .when(ThemeCondition.of(dim, ctx -> ctx.get(DIMENSION).equals("minecraft:the_end")))
                .priority(100).stability(250).debounce(80).transition(transition).build());
        syncPreferences();
    }

    /** Returns the client manager for registration and opt-in screen scopes. Call from the client thread. */
    public static ThemeManager manager() { return get().manager; }

    /** Resolves the palette shared by native renderers using the current transition frame. */
    public static UiTheme resolvePalette(UiTheme fallback) {
        var instance = runtime;
        if (instance == null || stopped) return fallback;
        var minecraft = Minecraft.getInstance();
        if (minecraft == null || !minecraft.isSameThread()) return fallback;
        if (instance.manager.showingEnvironmentDefault()) return fallback;
        var snapshot = instance.manager.snapshot();
        if (snapshot != instance.paletteSnapshot) {
            instance.palette = UiTheme.fromTokens(snapshot);
            instance.paletteSnapshot = snapshot;
        }
        return instance.palette;
    }

    static void configureEnvironmentPalette(ThemeManager manager, UiTheme classic) {
        var defaults = new LinkedHashMap<ThemeToken<?>, TokenExpression<?>>();
        if (classic.light()) {
            defaults.putAll(manager.registry().find(BuiltinThemes.LIGHT_ID).orElseThrow().tokens());
        }
        defaults.putAll(Map.of(
                ThemeTokens.BACKGROUND, new TokenExpression.Literal<>(ColorValue.argb(classic.background())),
                ThemeTokens.SURFACE, new TokenExpression.Literal<>(ColorValue.argb(classic.surface())),
                ThemeTokens.BORDER, new TokenExpression.Literal<>(ColorValue.argb(classic.border())),
                ThemeTokens.TEXT, new TokenExpression.Literal<>(ColorValue.argb(classic.text())),
                ThemeTokens.ACCENT, new TokenExpression.Literal<>(ColorValue.argb(classic.accent()))));
        manager.environmentDefaults(defaults);
    }

    /** Previews the draft preset without changing the published theme or its selection. */
    public static UiTheme previewPalette(ClientUiSettings draft, UiTheme fallback) {
        if (draft.theme().mode() == ThemeSettings.Mode.CUSTOM) return fallback;
        var preset = draft.theme().mode().fixedThemeId();
        var applied = ClientUiSettings.current().theme().mode().fixedThemeId();
        if (!Objects.equals(preset, applied)) return fallback;
        return resolvePalette(fallback);
    }

    /** Captured world, player, window and local preferences. Absent game data uses typed key defaults. */
    public static ContextSnapshot environment() { return get().environment; }

    /** Publishes a third-party context field on the client thread, for the next environment capture. */
    public static <T> void publishExternal(ContextKey<T> key, T value) {
        var instance = get();
        if (key.source() != ContextKey.Source.EXTERNAL || key.id().startsWith("sh_better_ui:")) {
            throw new IllegalArgumentException("Expected a third-party external context key: " + key.id());
        }
        if (!instance.external.containsKey(key) && instance.external.size() >= 128) {
            throw new IllegalStateException("Too many external theme context fields");
        }
        if (instance.external.keySet().stream().anyMatch(existing -> existing.id().equals(key.id()) && !existing.equals(key))) {
            throw new IllegalArgumentException("Conflicting context key: " + key.id());
        }
        instance.external.put(key, ContextSnapshot.of(Map.of(key, value)).get(key));
    }

    /** Removes a third-party field. Rules see the key's fallback at the next capture. */
    public static void removeExternal(ContextKey<?> key) { get().external.remove(key); }

    /** Registers a pure worker-side JSON literal decoder for the next resource reload. */
    public static <T> void registerResourceCodec(TokenType<T> type, Function<JsonElement, T> codec) {
        get();
        parser = parser.withCodec(type, codec);
        loader = new ThemeResourceLoader(parser);
    }

    /** Opts in to worker-side generation of a low frequency palette from the active track's bounded cover. */
    public static void albumPaletteEnabled(boolean enabled) { get().music.enabled(enabled); }

    /** Latest cover palette, if enabled and available for the current track. */
    public static Optional<ThemePalette> albumPalette() { return Optional.ofNullable(get().music.palette()); }

    /** Gets local audio effects without changing tokens. Capture must already be enabled by the user. */
    public static AudioReactiveTheme.Effects audioEffects() {
        var instance = get();
        return instance.music.effects(instance.manager.preferences(), UiFrameScheduler.nowMs());
    }

    /** Generates a palette from the last captured biome or sky color without reading live world objects. */
    public static ThemePalette environmentPalette(boolean sky, boolean light) {
        var instance = get();
        var key = sky ? SKY_COLOR : BIOME_COLOR;
        return instance.palettes.fromSeed(ColorValue.argb(instance.environment.get(key)), light);
    }

    private static MinecraftThemes get() {
        var minecraft = Minecraft.getInstance();
        if (minecraft == null || !minecraft.isSameThread()) {
            throw new IllegalStateException("Dynamic themes require the Minecraft client thread");
        }
        if (stopped) throw new IllegalStateException("Minecraft theme runtime is closed");
        if (runtime == null) runtime = new MinecraftThemes();
        return runtime;
    }

    private static Map<String, ThemeToken<?>> builtinCatalog() {
        var values = new LinkedHashMap<String, ThemeToken<?>>();
        ThemeTokens.all().forEach(token -> values.put(token.id(), token));
        return Map.copyOf(values);
    }

    public record PreparedReload(long ticket, ThemeResourceLoader.Batch batch) {}

    public static CompletableFuture<PreparedReload> prepareReload(ResourceManager resources,
                                                                 java.util.concurrent.Executor executor) {
        long ticket = RELOAD.begin();
        var instance = runtime;
        var catalog = instance == null ? BUILTIN_CATALOG : instance.manager.tokenCatalog();
        var resourceLoader = loader;
        return CompletableFuture.supplyAsync(() -> {
            var batch = prepare(resources, resourceLoader, catalog);
            if (batch.error() != null) throw new IllegalArgumentException(batch.error());
            return new PreparedReload(ticket, batch);
        }, executor);
    }

    public static boolean commitReload(PreparedReload prepared) {
        return !stopped && RELOAD.commit(prepared.ticket(), prepared.batch());
    }

    /** Prepares the pack layer off-thread, then commits after the native preparation barrier. */
    public static PreparableReloadListener reloadListener() {
        return (barrier, resources, preparationProfiler, reloadProfiler, prepareExecutor, applyExecutor) ->
                prepareReload(resources, prepareExecutor).thenCompose(barrier::wait)
                        .thenAcceptAsync(MinecraftThemes::commitReload, Minecraft.getInstance());
    }

    private static ThemeResourceLoader.Batch prepare(ResourceManager resources, ThemeResourceLoader resourceLoader,
                                                     Map<String, ThemeToken<?>> catalog) {
        try {
            var entries = new ArrayList<ThemeResourceLoader.Resource>();
            var stacks = resources.listResourceStacks(ThemeResourceLoader.DIRECTORY,
                    path -> path.getPath().endsWith(".json"));
            for (var entry : stacks.entrySet()) {
                var path = entry.getKey();
                String id = ThemeResourceLoader.themeId(path.getNamespace(), path.getPath());
                // Vanilla provides each stack in low-to-high pack order.
                for (var resource : entry.getValue()) {
                    entries.add(new ThemeResourceLoader.Resource(id, resource.sourcePackId(), resource::open));
                    if (entries.size() > 4096) return ThemeResourceLoader.Batch.failed("Too many theme resources");
                }
            }
            return resourceLoader.prepare(entries, catalog);
        } catch (RuntimeException failure) {
            return ThemeResourceLoader.Batch.failed("Could not enumerate theme resources: " + failure.getMessage());
        }
    }

    /** Client tick adapter. World data is captured only on this thread. */
    public static void tick(Minecraft minecraft) {
        if (!stopped) get().capture(minecraft, true);
    }

    /** Frame adapter. Called after the shared frame clock is initialized. */
    public static void frame() {
        var instance = runtime;
        if (instance != null && !stopped) {
            instance.syncPreferences();
            instance.manager.frame();
        }
    }

    public static void disconnect(Minecraft minecraft) {
        var instance = runtime;
        if (instance != null && !stopped) instance.capture(minecraft, false);
    }

    public static void shutdown() {
        stopped = true;
        RELOAD.begin();
        var instance = runtime;
        runtime = null;
        if (instance != null) {
            instance.music.close();
            instance.manager.close();
        }
    }

    private String configureUserPalette(ThemeSettings settings) {
        configuredThemeId = availableConfiguredId(manager, configuredThemeId, configuredDefinition);
        var theme = configuredPalette(manager, settings, configuredThemeId);
        manager.register(theme, configuredDefinition == null || manager.registry().find(configuredThemeId).isEmpty()
                ? ThemeRegistry.DuplicatePolicy.REJECT : ThemeRegistry.DuplicatePolicy.REPLACE_CODE);
        configuredDefinition = theme; configuredRegistry = manager.registry();
        return configuredThemeId;
    }
    static String availableConfiguredId(ThemeManager manager, String ownedId, Theme ownedDefinition) {
        if (ownedId != null && manager.registry().find(ownedId).orElse(null) == ownedDefinition) return ownedId;
        int suffix = 0; String candidate = CONFIGURED_PALETTE_ID;
        while (manager.registry().find(candidate).isPresent()) candidate = CONFIGURED_PALETTE_ID + "_" + ++suffix;
        return candidate;
    }
    static ThemePreferences selectionPreferences(ClientUiSettings settings, ThemePreferences previous, String derivedId) {
        var result = settings.dynamicTheme().applyTo(previous);
        String selected = settings.theme().mode() == ThemeSettings.Mode.CUSTOM && !settings.theme().registeredThemeId().isEmpty()
                ? derivedId : settings.theme().mode().fixedThemeId();
        return selected == null ? result : result.withFixedTheme(selected);
    }

    /** Derives from the source material rather than flattening it into five opaque colors. */
    static Theme configuredPalette(ThemeManager manager, ThemeSettings settings, String id) {
        String parent = settings.registeredThemeId();
        if (parent.isEmpty() || parent.equals(id) || manager.registry().find(parent).isEmpty())
            parent = settings.customBase() == ThemeSettings.Base.LIGHT ? BuiltinThemes.LIGHT_ID : BuiltinThemes.DARK;
        var original = registeredSnapshot(manager.registry(), manager.tokenCatalog(), parent);
        var builder = Theme.builder(id).parent(parent).source("SHBUI configured palette (runtime)");
        applyRgb(builder, original, ThemeTokens.BACKGROUND, settings.background());
        applyRgb(builder, original, ThemeTokens.SURFACE, settings.surface());
        applyRgb(builder, original, ThemeTokens.SURFACE_SECONDARY, settings.surface());
        applyRgb(builder, original, ThemeTokens.SURFACE_ELEVATED, settings.surface());
        applyRgb(builder, original, ThemeTokens.SURFACE_OVERLAY, settings.surface());
        applyRgb(builder, original, ThemeTokens.BORDER, settings.border());
        applyRgb(builder, original, ThemeTokens.TEXT, settings.text());
        applyRgb(builder, original, ThemeTokens.ACCENT, settings.accent());
        applyRgb(builder, original, ThemeTokens.PRIMARY, settings.accent());
        applyRgb(builder, original, ThemeTokens.BORDER_FOCUSED, settings.accent());
        if (!settings.accent().isEmpty()) {
            int primary = (original.resolve(ThemeTokens.PRIMARY).argb() & 0xff000000)
                    | Integer.parseInt(settings.accent().substring(1), 16);
            int background = original.resolve(ThemeTokens.BACKGROUND).argb() | 0xff000000;
            int text = com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy.readable(
                    original.resolve(ThemeTokens.ON_PRIMARY).argb(), primary, background, 4.5);
            builder.token(ThemeTokens.ON_PRIMARY, ColorValue.argb(text));
        }
        return builder.build();
    }
    private static void applyRgb(Theme.Builder builder, ThemeSnapshot base, ThemeToken<ColorValue> token, String rgb) {
        if (!rgb.isEmpty()) builder.token(token, ColorValue.argb((base.resolve(token).argb() & 0xff000000)
                | Integer.parseInt(rgb.substring(1), 16)));
    }

    private ThemePreferences syncPreferences() {
        var settings = ClientUiSettings.current();
        var preferences = manager.preferences();
        boolean registryChanged = manager.registry() != configuredRegistry;
        if (settings == previousSettings && !(settings.theme().mode() == ThemeSettings.Mode.CUSTOM && !settings.theme().registeredThemeId().isEmpty() && registryChanged)) return preferences;
        if (previousSettings == null || !settings.theme().equals(previousSettings.theme())) {
            configureEnvironmentPalette(manager, UiTheme.resolve(settings.theme()));
        }
        var preset = settings.theme().mode() == ThemeSettings.Mode.CUSTOM && !settings.theme().registeredThemeId().isEmpty()
                ? configureUserPalette(settings.theme()) : settings.theme().mode().fixedThemeId();
        if (previousSettings == null || !previousSettings.dynamicTheme().equals(settings.dynamicTheme())
                || !settings.theme().equals(previousSettings.theme()) || registryChanged) {
            preferences = selectionPreferences(settings, preferences, preset);
        }
        if (previousSettings == null || previousSettings.highContrast() != settings.highContrast()) {
            preferences = preferences.withHighContrast(settings.highContrast());
        }
        if (previousSettings == null || previousSettings.reducedMotion() != settings.reducedMotion()) {
            preferences = preferences.withReducedMotion(settings.reducedMotion());
        }
        previousSettings = settings;
        manager.preferences(preferences);
        return manager.preferences();
    }

    private void capture(Minecraft minecraft, boolean includeWorld) {
        var preferences = syncPreferences();
        var level = includeWorld ? minecraft.level : null;
        var player = includeWorld ? minecraft.player : null;
        var values = new LinkedHashMap<ContextKey<?>, Object>();
        var window = minecraft.getWindow();
        values.put(GUI_SCALE, Math.max(1, (int) window.getGuiScale()));
        values.put(WINDOW_WIDTH, window.getScreenWidth());
        values.put(WINDOW_HEIGHT, window.getScreenHeight());
        values.put(LANGUAGE, minecraft.getLanguageManager().getSelected());
        values.put(RELOAD_VERSION, manager.resourceVersion());
        values.put(HIGH_CONTRAST, preferences.highContrast());
        values.put(REDUCED_MOTION, preferences.reducedMotion());
        values.put(USER_PREFERENCES, Map.of("mode", preferences.mode().name(), "animationSpeed",
                preferences.animationSpeed(), "musicReactive", preferences.musicReactive(),
                "lowPerformance", preferences.lowPerformance()));
        if (level != null) {
            values.put(WORLD_PRESENT, true);
            String dimension = level.dimension().location().toString();
            values.put(DIMENSION, dimension);
            values.put(WEATHER, level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear");
            values.put(DAY_TIME, Math.floorMod(level.getDayTime(), 24_000L));
            if (player != null) {
                var position = player.blockPosition();
                var biome = level.getBiome(position);
                biome.unwrapKey().ifPresent(key -> values.put(BIOME, key.location().toString()));
                values.put(BIOME_COLOR, biome.value().getGrassColor(position.getX(), position.getZ()) | 0xff000000);
                long now = UiFrameScheduler.rawNowMs();
                if (!dimension.equals(skyDimension) || now >= nextSkySample) {
                    var sky = level.getSkyColor(player.position(), 1);
                    skyColor = ColorValue.rgb(channel(sky.x), channel(sky.y), channel(sky.z)).argb();
                    skyDimension = dimension;
                    nextSkySample = now + 250;
                }
                values.put(SKY_COLOR, skyColor);
                values.put(LIGHT_LEVEL, level.getMaxLocalRawBrightness(position));
                values.put(PLAYER_STATUS, !player.isAlive() ? "dead" : player.isSpectator() ? "spectator"
                        : player.isCreative() ? "creative" : "survival");
            }
        } else skyDimension = null;
        var session = ClientMusic.frame().snapshot().session();
        var playback = session == null ? MusicSnapshot.PlaybackStatus.STOPPED : session.status();
        values.put(AUDIO_PLAYBACK, playback.name().toLowerCase(Locale.ROOT));
        var audio = preferences.musicReactive() && preferences.animate()
                ? ClientMusic.audio().orElse(null) : null;
        if (audio != null) {
            values.put(AUDIO_BASS, (double) audio.bass());
            values.put(AUDIO_MID, (double) audio.mid());
            values.put(AUDIO_TREBLE, (double) audio.treble());
            values.put(AUDIO_LEVEL, (double) audio.rms());
        }
        music.capture(session, preferences, manager.snapshot().resolve(ThemeTokens.LIGHT), minecraft);
        if (music.palette() != null) values.put(ALBUM_COLOR, music.palette().primary().argb());
        values.putAll(external);
        var captured = ContextSnapshot.of(values);
        if (!captured.data().equals(environment.data())) {
            environment = new ContextSnapshot(environment.version() + 1, captured.data());
            manager.environment(environment);
        }
    }

    private static int channel(double color) { return (int) Math.round(Math.max(0, Math.min(1, color)) * 255); }
}
