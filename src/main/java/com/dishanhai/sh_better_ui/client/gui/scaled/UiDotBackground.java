// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.api.BackgroundLayer;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.audio.ClientMusicPulse;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Full-screen backdrop with screen-local pointer state and a rainbow dot matrix by default. */
public final class UiDotBackground {
    private static final DotMatrixProfile BASE = DotMatrixProfile.VIOLET
            .withPalette(new DotMatrixProfile.Palette(0xFF0B101B, 0x72FFFFFF, 0xFFFFFFFF, 0f))
            .withGrid(new DotMatrixProfile.Grid(8, 2, 1f, 27L))
            .withMotion(new DotMatrixProfile.Motion(DotMatrixProfile.Effect.TWINKLE,
                    DotMatrixProfile.VIOLET.motion().periodMs(), 0f, 0, 0f))
            .withInteraction(new DotMatrixProfile.Interaction(0, DotMatrixProfile.VIOLET.interaction().rippleRadius()))
            .withPointerLens(DotMatrixProfile.PointerLens.DEFAULT)
            .withGradient(DotMatrixProfile.Gradient.RAINBOW);

    private final UiDotMatrix dots = new UiDotMatrix(BASE);
    private final UiPointerTracker tracker = new UiPointerTracker();
    private UiTheme theme;
    private DotMatrixProfile themed;
    private BackgroundLayer effectLayer;
    private String activeEffect;

    static DotMatrixProfile defaultProfile(UiTheme theme) {
        return BASE.withTheme(theme);
    }

    public void renderThemed(Screen owner, GuiGraphics graphics, int width, int height, UiPreferences preferences,
                             boolean allowPointer) {
        var themeSettings = ThemeEffects.settings().theme();
        var registry = ShBetterUiApi.backgrounds();
        boolean hud = UiTheme.current().hudGlass();
        String effect = hud ? themeSettings.hudBackgroundEffect() : themeSettings.backgroundEffect();
        boolean dots = effect.equals(ThemeSettings.DEFAULT_BACKGROUND_EFFECT);
        boolean registered = !dots && registry.find(effect).isPresent();
        if (hud && (preferences.highContrast() || (!dots && !registered))) {
            reset();
            var client = Minecraft.getInstance();
            if (preferences.highContrast() || client == null || client.level == null) {
                DarkModeTheme.background(graphics, 0, 0, width, height);
            }
            return;
        }
        if ((!hud && themeSettings.mode() != ThemeSettings.Mode.CUSTOM) || preferences.highContrast() || !registered) {
            closeEffect();
            render(owner, graphics, width, height, preferences, allowPointer);
            return;
        }

        if (effectLayer == null) {
            resetDots();
            effectLayer = registry.layer(null);
        }
        DarkModeTheme.background(graphics, 0, 0, width, height);
        var mouse = allowPointer ? pointer(owner) : null;
        effectLayer.draw(effect, graphics, Minecraft.getInstance().font, new Bounds(0, 0, width, height), mouse,
                mouse == null ? Double.NaN : mouse.x(), mouse == null ? Double.NaN : mouse.y(),
                PageTransition.nowMs(), preferences);
        activeEffect = effect;
    }

    public void render(Screen owner, GuiGraphics graphics, int width, int height, UiPreferences preferences) {
        render(owner, graphics, width, height, preferences, true);
    }

    public void render(Screen owner, GuiGraphics graphics, int width, int height, UiPreferences preferences,
                       boolean allowPointer) {
        var current = UiTheme.current();
        if (!current.equals(theme)) {
            theme = current;
            themed = defaultProfile(current);
        }
        var area = new Bounds(0, 0, width, height);
        long now = PageTransition.nowMs();
        var mouse = allowPointer && !preferences.highContrast() ? pointer(owner) : null;
        UiPointerTracker.Frame pointer = null;
        if (mouse == null) {
            resetDots();
        } else {
            pointer = tracker.update(area, graphics.pose().last().pose(), mouse, true, now);
        }
        dots.setProfile(ThemeEffects.profile("dotMatrix", themed));
        dots.render(graphics, area, pointer, now, preferences, ClientMusicPulse.global().level());
    }

    private static UiMouseState pointer(Screen owner) {
        var client = Minecraft.getInstance();
        if (!UiFrameScheduler.canCommitFor(owner) || !client.isWindowActive() || client.getOverlay() != null) return null;
        var overlay = UiOverlayHost.current(owner);
        if (overlay != null && overlay.isModalOpen()) return null;
        var mouse = UiMouseState.read();
        if (!mouse.focused() || mouse.grabbed()) return null;
        var local = UiLocalModal.current(owner);
        return local == null || !local.blocksPointer(mouse.x(), mouse.y()) ? mouse : null;
    }

    public void pulse(int width, int height, double x, double y) {
        var area = new Bounds(0, 0, width, height);
        if (effectLayer == null) dots.pulse(area, x, y);
        else effectLayer.click(activeEffect, area, x, y, PageTransition.nowMs());
    }

    public void reset() {
        closeEffect();
        resetDots();
    }

    private void closeEffect() {
        if (effectLayer == null) return;
        var previous = effectLayer;
        effectLayer = null;
        activeEffect = null;
        previous.close();
    }

    private void resetDots() {
        tracker.reset();
        dots.resetPointer();
    }
}
