// Author: dishanhai
package com.dishanhai.sh_better_ui.client.theme;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.DynamicThemeSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.context.ContextSnapshot;
import com.dishanhai.sh_better_ui.theme.context.ThemeCondition;
import com.dishanhai.sh_better_ui.theme.context.ThemeRule;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.dishanhai.sh_better_ui.theme.context.ContextKeys.DIMENSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftThemesTest {
    @Test
    void savedDynamicSelectionDrivesTheSharedPaletteAndCanBeCleared() {
        var classic = UiTheme.resolve(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.LIGHT));
        try (var manager = new ThemeManager()) {
            assertSame(classic, palette(manager, classic));

            var fixed = new DynamicThemeSettings(BuiltinThemes.NETHER, 0, false);
            manager.preferences(fixed.applyTo(manager.preferences()));
            assertEquals(0xff211419, palette(manager, classic).background(0xff0b101b));
            assertEquals(manager.snapshot().resolve(ThemeTokens.ACCENT).argb(), palette(manager, classic).accent());

            manager.preferences(new DynamicThemeSettings("", 0, false).applyTo(manager.preferences()));
            assertSame(classic, palette(manager, classic));
        }
    }

    @Test
    void fixedDarkAndMissingThemesUseTheDynamicSlateFallback() {
        var classic = UiTheme.resolve(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.LIGHT));
        try (var manager = new ThemeManager()) {
            for (var id : new String[]{BuiltinThemes.DARK, "test:missing"}) {
                manager.preferences(new DynamicThemeSettings(id, 0, false).applyTo(manager.preferences()));
                assertEquals(0xff0f172a, palette(manager, classic).background(0xff0b101b));
            }
        }
    }

    private static UiTheme palette(ThemeManager manager, UiTheme classic) {
        return manager.showingEnvironmentDefault() ? classic : UiTheme.fromTokens(manager.snapshot());
    }

    @Test
    void dimensionTransitionsUseClassicAndCustomPalettesAtBothEnds() {
        var clock = new AtomicLong();
        var light = UiTheme.resolve(ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.LIGHT));
        var custom = new UiTheme(true, 0xffeeaa66, light.surface(), light.border(), light.text(), light.accent(), 1);
        try (var manager = new ThemeManager(clock::get, failure -> { throw failure; })) {
            var nether = ContextSnapshot.of(Map.of(DIMENSION, "minecraft:the_nether"));
            var overworld = ContextSnapshot.of(Map.of(DIMENSION, "minecraft:overworld"));
            manager.registerRule(ThemeRule.builder("example:nether").theme(BuiltinThemes.NETHER)
                    .when(ThemeCondition.of(Set.of(DIMENSION), ctx -> ctx.get(DIMENSION).equals("minecraft:the_nether")))
                    .transition(TransitionSpec.of(400, Easing.STANDARD)).build());
            for (var classic : new UiTheme[]{light, custom}) {
                MinecraftThemes.configureEnvironmentPalette(manager, classic);
                assertTrue(manager.showingEnvironmentDefault());
                assertEquals(0xfff8fafc, manager.snapshot().resolve(ThemeTokens.SURFACE_OVERLAY).argb());
                manager.environment(nether);
                assertFalse(manager.showingEnvironmentDefault());
                assertEquals(classic.background(), manager.snapshot().resolve(ThemeTokens.BACKGROUND).argb());
                clock.addAndGet(100);
                manager.frame();
                assertEquals(ColorValue.interpolateOklab(ColorValue.argb(classic.background()), ColorValue.argb(0xff211419),
                                Easing.STANDARD.applyAsDouble(.25)), manager.snapshot().resolve(ThemeTokens.BACKGROUND));
                clock.addAndGet(300);
                manager.frame();
                assertEquals(0xff211419, palette(manager, classic).background(0xff000000));
                manager.environment(overworld);
                clock.addAndGet(300);
                manager.frame();
                assertFalse(manager.showingEnvironmentDefault());
                clock.addAndGet(100);
                manager.frame();
                assertSame(classic, palette(manager, classic));
            }
            manager.preferences(manager.preferences().withReducedMotion(true));
            manager.environment(nether);
            assertEquals(0xff211419, palette(manager, custom).background(0xff000000));
            manager.environment(overworld);
            assertSame(custom, palette(manager, custom));
        }
    }
}
