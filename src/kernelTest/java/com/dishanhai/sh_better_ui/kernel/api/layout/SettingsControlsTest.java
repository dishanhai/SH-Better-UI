// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.layout;

import com.dishanhai.sh_better_ui.kernel.api.controls.SwitchRowState;

/** Standalone contracts for small GUI scales and controlled switch gestures. */
public final class SettingsControlsTest {
    public static void main(String[] args) {
        for (int width : new int[]{240, 280, 320, 427, 640, 960, 1920}) {
            for (int height : new int[]{180, 240, 360, 540, 1080}) {
                var page = SettingsPanelLayout.of(width, height, 6);
                require(page.panel().inside(width, height), "panel stays on screen");
                require(page.content().inside(width, height), "content stays on screen");
                require(!page.content().overlaps(page.footer()), "footer stays clear of settings");
                require(!page.content().overlaps(page.navigation()), "navigation stays clear of settings");
                require(page.capacity() >= 1, "settings remain reachable");
                require(page.row(page.capacity() - 1).bottom() <= page.content().bottom(), "last row fits");
            }
        }
        for (int width : new int[]{180, 240, 280, 320, 427, 640, 960}) {
            for (int height : new int[]{96, 132, 150, 180, 240, 360}) {
                for (int count = 1; count <= 6; count++) {
                    var wheel = RadialMenuLayout.of(width, height, count);
                    require(wheel.slots().size() == count, "all radial actions are reachable");
                    for (int i = 0; i < count; i++) {
                        var box = wheel.slots().get(i);
                        require(box.inside(width, height), "radial action stays on screen");
                        require(wheel.compact() || !box.overlaps(wheel.center()), "center text stays clear");
                        for (int j = 0; j < i; j++) require(!box.overlaps(wheel.slots().get(j)), "radial actions do not overlap");
                    }
                }
            }
        }
        var state = new SwitchRowState(false);
        require(!state.value(), "controlled switch starts with confirmed state");
        require(state.press(true), "press is captured");
        require(!state.release(false), "release outside cancels");
        state.press(true);
        state.cancel();
        require(!state.release(true), "lost focus cancels release");
        state.press(true);
        require(state.release(true), "release inside activates");
        require(!state.value(), "activation does not invent server confirmation");
        state.setPending(true);
        require(!state.press(true), "pending changes cannot be submitted twice");
        state.setValue(true);
        state.setPending(false);
        require(state.value() && state.press(true), "confirmed state restores interactivity");
        state.setPending(true);
        require(!state.release(true), "becoming pending cancels an in-flight press");
        System.out.println("Settings layout, radial bounds and switch gesture checks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
