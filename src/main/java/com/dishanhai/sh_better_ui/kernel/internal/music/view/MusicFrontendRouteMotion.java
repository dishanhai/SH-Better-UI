// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.music.view;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

/** Paint-only route and tab motion. No widget geometry, focus or input is deferred. */
final class MusicFrontendRouteMotion {
    private static final long DURATION_MS = 180L;
    private final InterruptibleAnimation entrance = new InterruptibleAnimation(Spring.PANEL);
    private final InterruptibleAnimation selection = new InterruptibleAnimation(Spring.PANEL);
    private MusicFrontendRoutes.Route route;
    private int tab;

    record Frame(float progress, float tabPosition) {
        int headingOffset() { return Math.round(4 * (1f - progress)); }
        int contentFade() { return Math.round(52 * (1f - progress)); }

        /** Grow toward the final slot from the side nearest the mini cover, without crossing any controls. */
        Bounds artwork(Bounds slot, Bounds mini) {
            if (progress >= 1f || slot.width() <= 0 || slot.height() <= 0) return slot;
            int size = Math.max(1, Math.round(Math.min(slot.width(), slot.height()) * .64f));
            int x = Math.max(slot.x(), Math.min(slot.right() - size, mini.x() + mini.width() / 2 - size / 2));
            int y = Math.max(slot.y(), Math.min(slot.bottom() - size, mini.y() + mini.height() / 2 - size / 2));
            return interpolate(new Bounds(x, y, size, size), slot, progress);
        }
    }

    Frame sample(MusicFrontendRoutes.Route next, int nextTab, long now, boolean disableMotion) {
        if (route == null || disableMotion) {
            entrance.snapTo(1f);
            selection.snapTo(nextTab);
        } else {
            if (!route.equals(next)) {
                entrance.snapTo(0f);
                entrance.blendTo(1f, now, 0, DURATION_MS, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
            }
            if (tab != nextTab) {
                selection.blendTo(nextTab, now, 0, DURATION_MS, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
            }
        }
        route = next;
        tab = nextTab;
        return new Frame(entrance.sample(now), selection.sample(now));
    }

    void reset() {
        route = null;
        entrance.snapTo(1f);
        selection.snapTo(0f);
    }

    static Bounds interpolate(Bounds from, Bounds to, float fraction) {
        int x = Math.round(GuiMotion.lerp(from.x(), to.x(), fraction));
        int y = Math.round(GuiMotion.lerp(from.y(), to.y(), fraction));
        int right = Math.round(GuiMotion.lerp(from.right(), to.right(), fraction));
        int bottom = Math.round(GuiMotion.lerp(from.bottom(), to.bottom(), fraction));
        return new Bounds(x, y, right - x, bottom - y);
    }
}
