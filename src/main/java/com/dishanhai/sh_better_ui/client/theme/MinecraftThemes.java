// Author: dishanhai
package com.dishanhai.sh_better_ui.client.theme;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.client.music.ClientMusic;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
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

    private ThemePreferences syncPreferences() {
        var settings = ClientUiSettings.current();
        var preferences = manager.preferences();
        if (settings == previousSettings) return preferences;
        if (previousSettings == null || !settings.theme().equals(previousSettings.theme())) {
            configureEnvironmentPalette(manager, UiTheme.resolve(settings.theme()));
        }
        var preset = settings.theme().mode().fixedThemeId();
        if (previousSettings == null || !previousSettings.dynamicTheme().equals(settings.dynamicTheme())
                || !Objects.equals(preset, previousSettings.theme().mode().fixedThemeId())) {
            preferences = settings.dynamicTheme().applyTo(preferences);
            if (preset != null) preferences = preferences.withFixedTheme(preset);
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
