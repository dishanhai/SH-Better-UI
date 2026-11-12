// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.theme.ThemeBinding;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiButtonThemeBindingTest {
    @Test
    void defaultButtonFollowsLiquidGlassPresetAndScopedOverrides() {
        try (var manager = new ThemeManager()) {
            var scope = manager.global().child("liquid_glass");
            var button = new UiButton(0, 0, 60, 20, Component.empty(), ignored -> {});
            button.bindTheme(scope);
            var idle = new DarkModeTheme.State(0, 0, 0, false, true, false);
            assertFalse(button.themePaint(idle, false).glass());
            manager.setTheme(BuiltinThemes.LIQUID_GLASS);
            var paint = button.themePaint(idle, false);
            assertTrue(paint.glass());
            assertEquals(12f, paint.radius());
            assertTrue((paint.fill() >>> 24) < 255);
            assertFalse(button.isCapsule());
            scope.configure(local -> local.override(ThemeTokens.LIQUID_GLASS, false));
            assertFalse(button.themePaint(idle, false).glass());
        }
    }

    @Test
    void translucentMaterialsShareStateTokensWhileKeepingTheirSurfaceAndRadius() {
        try (var manager = new ThemeManager()) {
            var scope = manager.global().child("materials");
            scope.configure(local -> local.override(ThemeTokens.SURFACE, ColorValue.argb(0xff334455))
                    .override(ThemeTokens.PRESSED, ColorValue.argb(0xff662211))
                    .override(ThemeTokens.RADIUS_LARGE, 9f).override(ThemeTokens.RADIUS_SMALL, 5f)
                    .override(ThemeTokens.BORDER_WIDTH, 2f));
            var glass = new UiButton(0, 0, 60, 20, Component.empty(), ignored -> {},
                    UiPreferences.DEFAULT, false, UiStyle.LIQUID_GLASS);
            var network = new UiButton(0, 0, 60, 20, Component.empty(), ignored -> {},
                    UiPreferences.DEFAULT, false, UiStyle.NETWORK);
            glass.bindTheme(scope);
            network.bindTheme(scope);
            var idle = new DarkModeTheme.State(0, 0, 0, false, true, false);
            var pressed = new DarkModeTheme.State(0, 0, 1, false, true, false);
            var contrast = new DarkModeTheme.State(0, 0, 0, true, true, true, true);
            assertEquals(9f, glass.themePaint(idle, false).radius());
            assertEquals(5f, network.themePaint(idle, false).radius());
            for (var button : new UiButton[]{glass, network}) {
                var paint = button.themePaint(idle, false);
                assertEquals(0x334455, paint.fill() & 0xffffff);
                assertTrue((paint.fill() >>> 24) < 255);
                assertEquals(2, paint.borderWidth());
                assertEquals(0x662211, button.themePaint(pressed, true).fill() & 0xffffff);
                assertEquals(255, button.themePaint(contrast, false).fill() >>> 24);
            }
            scope.close();
            assertFalse(glass.hasThemeBinding());
            assertFalse(network.hasThemeBinding());
        }
    }

    @Test
    void rebindingUsesTheNewScopeAndClosingItRestoresLegacyRendering() {
        try (var manager = new ThemeManager()) {
            var left = manager.global().child("left");
            var right = manager.global().child("right");
            left.configure(scope -> scope.override(ThemeTokens.SURFACE, ColorValue.argb(0xff442211)));
            right.configure(scope -> scope.override(ThemeTokens.SURFACE, ColorValue.argb(0xff112244)));
            var button = new UiButton(0, 0, 60, 20, Component.empty(), ignored -> {});
            var idle = new DarkModeTheme.State(0, 0, 0, false, true, false);
            button.bindTheme(left, ThemeBinding.of(ThemeTokens.SURFACE), ThemeBinding.of(ThemeTokens.TEXT));
            assertEquals(0xff442211, button.themePaint(idle, false).fill());
            button.bindTheme(right);
            left.close();
            assertEquals(0xff112244, button.themePaint(idle, false).fill());
            right.close();
            assertNull(button.themePaint(idle, false));
        }
    }
}
